package mozilla.components.feature.webnotifications

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.permission.SitePermissionsStorage
import mozilla.components.concept.engine.webnotifications.WebNotification
import mozilla.components.concept.engine.webnotifications.WebNotificationDelegate
import mozilla.components.support.base.android.NotificationsDelegate
import mozilla.components.support.base.ids.SharedIdsHelper
import mozilla.components.support.ktx.kotlin.getOrigin
import kotlin.coroutines.CoroutineContext

private const val NOTIFICATION_CHANNEL_ID = "mozac.feature.webnotifications.generic.channel"
private const val PENDING_INTENT_TAG = "mozac.feature.webnotifications.generic.pendingintent"
internal const val NOTIFICATION_ID = 1
internal const val SUMMARY_NOTIFICATION_ID = 0 // Separate ID for the group summary

@Suppress("LongParameterList")
class WebNotificationFeature(
    private val context: Context,
    private val engine: Engine,
    browserIcons: BrowserIcons,
    @DrawableRes smallIcon: Int,
    private val sitePermissionsStorage: SitePermissionsStorage,
    private val activityClass: Class<out Activity>?,
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
    private val notificationsDelegate: NotificationsDelegate,
) : WebNotificationDelegate {

    private val nativeNotificationBridge = NativeNotificationBridge(browserIcons, smallIcon)
    private val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        try {
            engine.registerWebNotificationDelegate(this)
        } catch (e: UnsupportedOperationException) {
            // Handle exception if necessary
        }
    }

    override fun onShowNotification(webNotification: WebNotification) {
        CoroutineScope(coroutineContext).launch {
            if (!webNotification.triggeredByWebExtension) {
                val origin = webNotification.sourceUrl?.getOrigin() ?: return@launch
                val permissions = sitePermissionsStorage.findSitePermissionsBy(
                    origin,
                    private = webNotification.privateBrowsing,
                ) ?: return@launch
            }

            ensureNotificationGroupAndChannelExists()

            // Cancel any existing notification with the same tag and ID
            notificationsDelegate.notificationManagerCompat.cancel(webNotification.tag, NOTIFICATION_ID)

            val notification = nativeNotificationBridge.convertToAndroidNotification(
                webNotification,
                context,
                NOTIFICATION_CHANNEL_ID,
                activityClass,
                SharedIdsHelper.getNextIdForTag(context, PENDING_INTENT_TAG),
            )

            // Display the notification
            notificationsDelegate.notify(webNotification.tag, NOTIFICATION_ID, notification)

            // Set timeout for individual and group notifications
            val individualTimeoutMillis = 200L
            val groupTimeoutMillis = 3500L // 2 seconds for summary group notifications

            Handler(Looper.getMainLooper()).postDelayed({
                notificationsDelegate.notificationManagerCompat.cancel(webNotification.tag, NOTIFICATION_ID)
                cancelGroupIfEmpty() // Checks and cancels the group if it's empty
            }, individualTimeoutMillis)

            Handler(Looper.getMainLooper()).postDelayed({
                systemNotificationManager.cancel(SUMMARY_NOTIFICATION_ID) // Cancel the group notification
            }, groupTimeoutMillis)
        }
    }

    override fun onCloseNotification(webNotification: WebNotification) {
        notificationsDelegate.notificationManagerCompat.cancel(webNotification.tag, NOTIFICATION_ID)
        cancelGroupIfEmpty() // Checks and cancels the group if it's empty
    }

    private fun ensureNotificationGroupAndChannelExists() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.mozac_feature_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
            channel.setShowBadge(true)
            channel.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            notificationsDelegate.notificationManagerCompat.createNotificationChannel(channel)
        }
    }

    private fun cancelGroupIfEmpty() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNotifications = systemNotificationManager.activeNotifications
            if (activeNotifications.none { it.notification.channelId == NOTIFICATION_CHANNEL_ID }) {
                systemNotificationManager.cancel(SUMMARY_NOTIFICATION_ID) // Cancels the group notification
            }
        }
    }
}
