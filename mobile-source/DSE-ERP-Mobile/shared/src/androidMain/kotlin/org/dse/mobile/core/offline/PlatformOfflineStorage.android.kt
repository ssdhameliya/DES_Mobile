package org.dse.mobile.core.offline

import android.content.Context
import java.io.File

private var androidApplicationContext: Context? = null

/** Called once by the Android launcher before the shared Compose UI starts. */
fun installAndroidPlatformContext(context: Context) {
    androidApplicationContext = context.applicationContext
}

private fun storageDirectory(): File {
    val context = androidApplicationContext
        ?: error("Android platform context was not installed before offline storage access")
    return File(context.filesDir, "dse-erp-mobile/offline").apply { mkdirs() }
}

actual fun platformOfflineRead(key: String): String? = runCatching {
    val file = File(storageDirectory(), "${stableFileKey(key)}.json")
    if (!file.isFile) null else file.readText(Charsets.UTF_8)
}.getOrNull()

actual fun platformOfflineWrite(key: String, value: String) {
    runCatching {
        val target = File(storageDirectory(), "${stableFileKey(key)}.json")
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(value, Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.writeText(value, Charsets.UTF_8)
            temp.delete()
        }
    }.getOrThrow()
}

actual fun platformOfflineRemove(key: String) {
    runCatching { File(storageDirectory(), "${stableFileKey(key)}.json").delete() }
}

actual fun platformOfflineNowMillis(): Long = System.currentTimeMillis()

private fun stableFileKey(value: String): String {
    var hash = -3750763034362895579L
    value.forEach { ch -> hash = hash xor ch.code.toLong(); hash *= 1099511628211L }
    return hash.toULong().toString(16)
}
