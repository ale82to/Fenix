package mozilla.components.concept.push

import android.content.Context

/**
 * Implemented by push services like Firebase Cloud Messaging SDKs to allow
 * the [PushProcessor] to manage their lifecycle.
 */
interface PushService {

    /**
     * Starts the push service.
     */
    fun start(context: Context)

    /**
     * Stops the push service.
     */
    fun stop()

    /**
     * Tells the push service to delete the registration token.
     */
    fun deleteToken()

    /**
     * If the push service is support on the device.
     */
    fun isServiceAvailable(context: Context): Boolean

    companion object {
        /**
         * Message key for "channel ID" in a push message.
         */
        const val MESSAGE_KEY_CHANNEL_ID = "chid"
    }

}