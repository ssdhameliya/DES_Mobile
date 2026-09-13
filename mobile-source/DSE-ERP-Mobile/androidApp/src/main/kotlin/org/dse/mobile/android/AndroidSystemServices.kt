package org.dse.mobile.android

import android.Manifest
import android.appwidget.AppWidgetManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

internal object DashboardWidgetPublisher {
    private const val PREFS = "dse.erp.dashboard.widget"

    fun publish(
        context: Context,
        salesToday: Double,
        receivables: Double,
        purchases: Double,
        payables: Double,
        bankBalance: Double,
        expenseMonth: Double,
        updatedAtMillis: Long,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("salesToday", salesToday.toBits())
            .putLong("receivables", receivables.toBits())
            .putLong("purchases", purchases.toBits())
            .putLong("payables", payables.toBits())
            .putLong("bankBalance", bankBalance.toBits())
            .putLong("expenseMonth", expenseMonth.toBits())
            .putLong("updatedAtMillis", updatedAtMillis)
            .apply()
        DseDashboardWidgetProvider.updateAll(context)
    }

    fun value(context: Context, key: String): Double {
        val bits = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(key, 0L)
        return Double.fromBits(bits)
    }
}

class DseDashboardWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, DseDashboardWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_dashboard)
            views.setTextViewText(R.id.widget_sales, money(DashboardWidgetPublisher.value(context, "salesToday")))
            views.setTextViewText(R.id.widget_receivables, money(DashboardWidgetPublisher.value(context, "receivables")))
            views.setTextViewText(R.id.widget_purchases, money(DashboardWidgetPublisher.value(context, "purchases")))
            views.setTextViewText(R.id.widget_payables, money(DashboardWidgetPublisher.value(context, "payables")))
            views.setTextViewText(R.id.widget_bank, money(DashboardWidgetPublisher.value(context, "bankBalance")))
            views.setTextViewText(R.id.widget_expense, money(DashboardWidgetPublisher.value(context, "expenseMonth")))
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("dseerp://dashboard"), context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 1100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(widgetId, views)
        }

        private fun money(value: Double): String = String.format(Locale.US, "%,.2f", value)
    }
}

internal object ShippingNotificationController {
    private const val CHANNEL_ID = "dse_shipping_status"
    private const val PREFS = "dse.erp.shipping.notifications"

    fun start(context: Context, invoiceNo: String, customer: String, transporter: String, vehicle: String): Pair<Boolean, String> {
        if (invoiceNo.isBlank()) return false to "Invoice number is required for shipping status."
        if (!notificationsAllowed(context)) return false to "Enable Android notification permission before starting shipping status."
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(stageKey(invoiceNo))) return true to "A shipping status notification is already running for $invoiceNo."
        prefs.edit()
            .putString(stageKey(invoiceNo), "Ready")
            .putString(customerKey(invoiceNo), customer)
            .putString(transporterKey(invoiceNo), transporter)
            .putString(vehicleKey(invoiceNo), vehicle)
            .apply()
        return notify(context, invoiceNo, "Ready")
    }

    fun update(context: Context, invoiceNo: String, stage: String): Pair<Boolean, String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(stageKey(invoiceNo))) return false to "No running shipping status notification was found for $invoiceNo."
        prefs.edit().putString(stageKey(invoiceNo), stage.ifBlank { "Updated" }).apply()
        val result = notify(context, invoiceNo, stage.ifBlank { "Updated" })
        return if (result.first) true to "Shipping status updated to ${stage.ifBlank { "Updated" }}." else result
    }

    fun end(context: Context, invoiceNo: String): Pair<Boolean, String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(stageKey(invoiceNo))) return false to "No running shipping status notification was found for $invoiceNo."
        NotificationManagerCompat.from(context).cancel(notificationId(invoiceNo))
        prefs.edit()
            .remove(stageKey(invoiceNo))
            .remove(customerKey(invoiceNo))
            .remove(transporterKey(invoiceNo))
            .remove(vehicleKey(invoiceNo))
            .apply()
        return true to "Shipping status notification ended for $invoiceNo."
    }

    private fun notify(context: Context, invoiceNo: String, stage: String): Pair<Boolean, String> = runCatching {
        createChannel(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val customer = prefs.getString(customerKey(invoiceNo), "").orEmpty()
        val transporter = prefs.getString(transporterKey(invoiceNo), "").orEmpty()
        val vehicle = prefs.getString(vehicleKey(invoiceNo), "").orEmpty()
        val details = listOf(customer, transporter, vehicle).filter { it.isNotBlank() }.joinToString(" • ")
        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse("dseerp://sales/$invoiceNo"), context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, notificationId(invoiceNo), openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dse_notification)
            .setContentTitle("Shipping $invoiceNo • $stage")
            .setContentText(details.ifBlank { "Jasvi Industries shipping status" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(details.ifBlank { "Jasvi Industries shipping status" }))
            .setContentIntent(pending)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(invoiceNo), notification)
        true to "Shipping status notification started for $invoiceNo."
    }.getOrElse { false to (it.message ?: "Unable to publish Android shipping status notification.") }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Jasvi Industries Shipping Status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ongoing presentation-only shipping status from Jasvi Industries Mobile"
                },
            )
        }
    }

    private fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notificationId(invoiceNo: String): Int = 20_000 + (invoiceNo.hashCode() and 0x0FFF)
    private fun stageKey(invoiceNo: String) = "shipping.$invoiceNo.stage"
    private fun customerKey(invoiceNo: String) = "shipping.$invoiceNo.customer"
    private fun transporterKey(invoiceNo: String) = "shipping.$invoiceNo.transporter"
    private fun vehicleKey(invoiceNo: String) = "shipping.$invoiceNo.vehicle"
}
