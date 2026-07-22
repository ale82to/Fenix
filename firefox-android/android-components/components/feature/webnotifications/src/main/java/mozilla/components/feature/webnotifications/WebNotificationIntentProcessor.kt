package mozilla.components.feature.webnotifications

import android.content.Intent
import android.os.Parcelable
import mozilla.components.concept.engine.Engine
import mozilla.components.feature.intent.processing.IntentProcessor
import mozilla.components.support.utils.ext.getParcelableCompat

/**
 * Intent processor that tries matching a web notification and delegating a click interaction with it.
 */
class WebNotificationIntentProcessor(
    private val engine: Engine,
) : IntentProcessor {
    /**
     * Processes an incoming intent expected to contain information about a web notification.
     * If such information is available this will inform the web notification about it being clicked.
     */
    override fun process(intent: Intent): Boolean {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val engineNotification =
            intent.extras?.getParcelableCompat(NativeNotificationBridge.EXTRA_ON_CLICK, Parcelable::class.java)

        return when (engineNotification) {
            null -> false
            else -> {
                engine.handleWebNotificationClick(engineNotification)
                true
            }
        }
    }

}