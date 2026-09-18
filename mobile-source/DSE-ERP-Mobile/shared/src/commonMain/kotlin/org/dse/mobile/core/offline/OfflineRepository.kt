package org.dse.mobile.core.offline

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dse.mobile.core.api.ApiDataSource
import org.dse.mobile.core.api.ApiResult
import org.dse.mobile.core.api.DseErpHttpClient
import org.dse.mobile.core.model.*

@Serializable
enum class OfflineMutationKind {
    CREATE_SALE,
    UPDATE_SALE,
    CREATE_PURCHASE,
    UPDATE_PURCHASE,
    CREATE_FINANCE,
    UPDATE_FINANCE,
}

@Serializable
enum class OfflineMutationState { QUEUED, CONFLICT, FAILED, AMBIGUOUS }

@Serializable
data class OfflineMutation(
    val id: String,
    val kind: OfflineMutationKind,
    val label: String,
    val payload: String,
    val createdAtMillis: Long,
    val attempts: Int = 0,
    val state: OfflineMutationState = OfflineMutationState.QUEUED,
    val lastError: String = "",
)

data class OfflineCacheStatus(
    val cachedEntries: Int,
    val lastUpdatedMillis: Long?,
    val pendingMutations: Int,
)

@Serializable
data class OfflineAuthSnapshot(
    val serverUrl: String,
    val user: UserPayload,
    val permissions: List<EffectivePermission>,
    val validatedAtMillis: Long,
)

/**
 * Mobile-only durable cache/outbox.
 *
 * Cached reads are explicitly tagged as CACHE so screens never present stale data as live.
 * Writes are never queued after an ambiguous transport/decode failure because server v10.0.16
 * does not expose an idempotency-key contract. Offline drafts may only be queued after a
 * preflight health check proves that the server is unreachable before the business write begins.
 */
object OfflineRepository {
    private const val QUEUE_BASE = "offline.queue.v2"
    private const val INDEX_BASE = "offline.cache.index.v2"
    private const val MAX_CACHE_ENTRIES = 100
    private const val MAX_CACHE_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    private const val MAX_OFFLINE_AUTH_AGE_MS = 8L * 60L * 60L * 1000L
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }
    private var activeScope: String = "anonymous"

    fun activateScope(scopeId: String) {
        activeScope = stableKey(scopeId.trim().ifBlank { "anonymous" })
    }

    fun deactivateScope() {
        activeScope = "anonymous"
    }

    fun saveAuthSnapshot(serverUrl: String, user: UserPayload, permissions: List<EffectivePermission>) {
        val snapshot = OfflineAuthSnapshot(serverUrl.trim(), user, permissions, platformOfflineNowMillis())
        platformOfflineWrite(authKey(serverUrl), json.encodeToString(snapshot))
    }

    fun readAuthSnapshot(serverUrl: String): OfflineAuthSnapshot? = runCatching {
        platformOfflineRead(authKey(serverUrl))?.let { json.decodeFromString<OfflineAuthSnapshot>(it) }
            ?.takeIf { snapshot -> platformOfflineNowMillis() - snapshot.validatedAtMillis <= MAX_OFFLINE_AUTH_AGE_MS }
    }.getOrNull()

    fun clearAuthSnapshot(serverUrl: String) {
        platformOfflineRemove(authKey(serverUrl))
    }

    private fun authKey(serverUrl: String): String = "auth.v2.${stableKey(serverUrl.trim().lowercase())}"
    private fun scoped(key: String): String = "scope.$activeScope.$key"
    private fun queueKey(): String = scoped(QUEUE_BASE)
    private fun indexKey(): String = scoped(INDEX_BASE)

    fun cacheKey(area: String, page: Int, size: Int, query: String): String =
        "cache.$area.$page.$size.${stableKey(query.trim().lowercase())}"

    internal inline fun <reified T> saveCache(key: String, value: T) {
        val storageKey = scoped(key)
        val now = platformOfflineNowMillis()
        platformOfflineWrite(storageKey, json.encodeToString(value))
        platformOfflineWrite("$storageKey.ts", now.toString())
        val keys = cacheIndex().toMutableSet()
        keys += storageKey
        platformOfflineWrite(indexKey(), json.encodeToString(keys.toList().sorted()))
        pruneCache()
    }

    internal data class CacheEntry<T>(val value: T, val timestamp: Long)

    internal inline fun <reified T> readCacheEntry(key: String): CacheEntry<T>? {
        val storageKey = scoped(key)
        val timestamp = platformOfflineRead("$storageKey.ts")?.toLongOrNull() ?: return null
        if (platformOfflineNowMillis() - timestamp > MAX_CACHE_AGE_MS) {
            platformOfflineRemove(storageKey)
            platformOfflineRemove("$storageKey.ts")
            removeFromCacheIndex(storageKey)
            return null
        }
        val value = runCatching { platformOfflineRead(storageKey)?.let { json.decodeFromString<T>(it) } }.getOrNull() ?: return null
        return CacheEntry(value, timestamp)
    }

    internal inline suspend fun <reified T> cached(
        key: String,
        crossinline network: suspend () -> ApiResult<T>,
    ): ApiResult<T> = when (val result = network()) {
        is ApiResult.Success -> {
            if (result.source == ApiDataSource.LIVE) saveCache(key, result.value)
            result
        }
        is ApiResult.NetworkError -> readCacheEntry<T>(key)?.let { entry ->
            ApiResult.Success(entry.value, ApiDataSource.CACHE, entry.timestamp)
        } ?: result
        else -> result
    }

    fun queue(): List<OfflineMutation> = runCatching {
        platformOfflineRead(queueKey())?.let { json.decodeFromString<List<OfflineMutation>>(it) }
    }.getOrNull().orEmpty()

    fun enqueue(kind: OfflineMutationKind, label: String, payload: String): OfflineMutation {
        val now = platformOfflineNowMillis()
        val item = OfflineMutation(
            id = "$now-${stableKey("$label|$payload")}",
            kind = kind,
            label = label,
            payload = payload,
            createdAtMillis = now,
        )
        persistQueue(queue() + item)
        return item
    }

    fun enqueueSale(record: SaleRecord, create: Boolean): OfflineMutation = enqueue(
        if (create) OfflineMutationKind.CREATE_SALE else OfflineMutationKind.UPDATE_SALE,
        if (create) "New Sale" else "Sale ${record.invoiceNo}",
        json.encodeToString(record),
    )

    fun enqueuePurchase(record: PurchaseRecord, create: Boolean): OfflineMutation = enqueue(
        if (create) OfflineMutationKind.CREATE_PURCHASE else OfflineMutationKind.UPDATE_PURCHASE,
        if (create) "New Purchase" else "Purchase ${record.invoiceNo}",
        json.encodeToString(record),
    )

    fun enqueueFinance(record: FinanceRecord, create: Boolean): OfflineMutation = enqueue(
        if (create) OfflineMutationKind.CREATE_FINANCE else OfflineMutationKind.UPDATE_FINANCE,
        if (create) "New ${record.voucherType.ifBlank { "Finance Entry" }}" else "Voucher ${record.voucherNo}",
        json.encodeToString(record),
    )

    fun remove(id: String) = persistQueue(queue().filterNot { it.id == id })

    suspend fun retry(id: String, api: DseErpHttpClient): String {
        val item = queue().firstOrNull { it.id == id } ?: return "Outbox item no longer exists."

        // A retry is explicit user intent. We still preflight so that a known-offline device does not
        // create a stream of ambiguous attempts.
        if (api.health() is ApiResult.NetworkError) {
            update(item.copy(attempts = item.attempts + 1, state = OfflineMutationState.QUEUED, lastError = "Server health check failed"))
            return "${item.label} is still queued because the server is unreachable."
        }

        val result: ApiResult<*> = try {
            when (item.kind) {
                OfflineMutationKind.CREATE_SALE, OfflineMutationKind.CREATE_PURCHASE, OfflineMutationKind.CREATE_FINANCE ->
                    return "${item.label} is a legacy offline CREATE. v1.2.13 will not replay it without a server idempotency key. Verify ERP and recreate the document manually if it is not present."
                OfflineMutationKind.UPDATE_SALE -> api.updateSale(json.decodeFromString<SaleRecord>(item.payload))
                OfflineMutationKind.UPDATE_PURCHASE -> api.updatePurchase(json.decodeFromString<PurchaseRecord>(item.payload))
                OfflineMutationKind.UPDATE_FINANCE -> api.updateFinance(json.decodeFromString<FinanceRecord>(item.payload))
            }
        } catch (e: Exception) {
            ApiResult.DecodeError(e.message ?: "Unable to decode queued change")
        }

        return when (result) {
            is ApiResult.Success<*> -> {
                remove(id)
                "${item.label} synced successfully."
            }
            is ApiResult.Conflict -> {
                update(item.copy(attempts = item.attempts + 1, state = OfflineMutationState.CONFLICT, lastError = result.message))
                "${item.label} has a row-version conflict. Reload the live record before editing again."
            }
            is ApiResult.NetworkError -> {
                val state = if (result.requestMayHaveReachedServer) OfflineMutationState.AMBIGUOUS else OfflineMutationState.QUEUED
                update(item.copy(attempts = item.attempts + 1, state = state, lastError = result.message))
                if (state == OfflineMutationState.AMBIGUOUS)
                    "${item.label} may have reached the server. Do not retry until you verify the ERP record to avoid a duplicate."
                else
                    "${item.label} is still queued because the server is unreachable."
            }
            is ApiResult.DecodeError -> {
                update(item.copy(attempts = item.attempts + 1, state = OfflineMutationState.AMBIGUOUS, lastError = result.message))
                "${item.label} received an unreadable server response. Verify the ERP record before retrying."
            }
            is ApiResult.Forbidden -> {
                update(item.copy(attempts = item.attempts + 1, state = OfflineMutationState.FAILED, lastError = result.message))
                "${item.label} was not synced because permission was denied."
            }
            else -> {
                val message = when (result) {
                    is ApiResult.Unauthorized -> result.message
                    is ApiResult.NotFound -> result.message
                    is ApiResult.ServerError -> "HTTP ${result.status}: ${result.message}"
                    is ApiResult.UnsafeEndpoint -> result.message
                    is ApiResult.NotImplemented -> result.message
                    else -> result.toString()
                }
                update(item.copy(attempts = item.attempts + 1, state = OfflineMutationState.FAILED, lastError = message))
                "${item.label} was not synced: $message"
            }
        }
    }

    fun biometricLoginEnabled(): Boolean = platformOfflineRead("settings.biometricLogin") == "true"

    fun setBiometricLoginEnabled(enabled: Boolean) {
        platformOfflineWrite("settings.biometricLogin", enabled.toString())
    }

    fun widgetSharingEnabled(): Boolean = platformOfflineRead(scoped("settings.widgetSharing")) == "true"

    fun setWidgetSharingEnabled(enabled: Boolean) {
        platformOfflineWrite(scoped("settings.widgetSharing"), enabled.toString())
    }

    fun status(): OfflineCacheStatus {
        val keys = cacheIndex()
        val newest = keys.mapNotNull { platformOfflineRead("$it.ts")?.toLongOrNull() }.maxOrNull()
        return OfflineCacheStatus(keys.size, newest, queue().size)
    }

    fun clearCache() {
        cacheIndex().forEach {
            platformOfflineRemove(it)
            platformOfflineRemove("$it.ts")
        }
        platformOfflineRemove(indexKey())
    }

    fun clearScopeData() {
        clearCache()
        platformOfflineRemove(queueKey())
    }

    private fun cacheIndex(): List<String> = runCatching {
        platformOfflineRead(indexKey())?.let { json.decodeFromString<List<String>>(it) }
    }.getOrNull().orEmpty()

    private fun removeFromCacheIndex(storageKey: String) {
        val remaining = cacheIndex().filterNot { it == storageKey }
        if (remaining.isEmpty()) platformOfflineRemove(indexKey())
        else platformOfflineWrite(indexKey(), json.encodeToString(remaining))
    }

    private fun pruneCache() {
        val now = platformOfflineNowMillis()
        val entries = cacheIndex().map { key -> key to (platformOfflineRead("$key.ts")?.toLongOrNull() ?: 0L) }
        val expired = entries.filter { (_, ts) -> ts <= 0L || now - ts > MAX_CACHE_AGE_MS }.map { it.first }.toMutableSet()
        val survivors = entries.filterNot { it.first in expired }.sortedByDescending { it.second }
        survivors.drop(MAX_CACHE_ENTRIES).forEach { expired += it.first }
        if (expired.isNotEmpty()) {
            expired.forEach {
                platformOfflineRemove(it)
                platformOfflineRemove("$it.ts")
            }
            val remaining = entries.map { it.first }.filterNot { it in expired }
            if (remaining.isEmpty()) platformOfflineRemove(indexKey())
            else platformOfflineWrite(indexKey(), json.encodeToString(remaining.sorted()))
        }
    }

    private fun update(item: OfflineMutation) = persistQueue(queue().map { if (it.id == item.id) item else it })

    private fun persistQueue(items: List<OfflineMutation>) {
        if (items.isEmpty()) platformOfflineRemove(queueKey())
        else platformOfflineWrite(queueKey(), json.encodeToString(items))
    }

    /** Stable 64-bit FNV-1a key, encoded unsigned hex; avoids JVM/Native hashCode differences. */
    private fun stableKey(value: String): String {
        var hash = -3750763034362895579L // 14695981039346656037 unsigned
        for (ch in value) {
            hash = hash xor ch.code.toLong()
            hash *= 1099511628211L
        }
        return hash.toULong().toString(16)
    }
}
