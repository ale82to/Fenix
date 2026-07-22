/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.lib.push.firebase

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
//import com.google.android.gms.common.util.VisibleForTesting
import androidx.annotation.VisibleForTesting
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.concept.push.PushError
import mozilla.components.concept.push.PushProcessor
import mozilla.components.concept.push.PushService
import mozilla.components.concept.push.PushService.Companion.MESSAGE_KEY_CHANNEL_ID
//import mozilla.components.support.base.log.logger.Logger
import java.io.IOException
import kotlin.coroutines.CoroutineContext

abstract class AbstractFirebasePushService(
    internal val coroutineContext: CoroutineContext = Dispatchers.IO,
) : FirebaseMessagingService(), PushService {

    @VisibleForTesting
    
   //internal val googleApiAvailability: GoogleApiAvailability
     //  get() = GoogleApiAvailability.getInstance()

    
    override fun start(context: Context) {
        FirebaseApp.initializeApp(context)
    }

    override fun onNewToken(newToken: String) {
        PushProcessor.requireInstance.onNewToken(newToken)
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    override fun onMessageReceived(message: RemoteMessage) {
      
        val chId = message.data.getOrElse(MESSAGE_KEY_CHANNEL_ID) { null }

        if (chId == null) {
            return
        } 

        
        try {
            PushProcessor.requireInstance.onMessageReceived(message.data)
        } catch (e: IllegalStateException) {
            throw (e)
        } catch (e: Exception) {
            PushProcessor.requireInstance.onError(PushError.Rust(e))
        }
    }

   
    final override fun stop() {
        stopSelf()
    }

    
    override fun deleteToken() {
        CoroutineScope(coroutineContext).launch {
            try {
                FirebaseMessaging.getInstance().deleteToken()
            } catch (e: IOException) {
                //logger.error("Force registration renewable failed.", e)
            }
        }
    }

    override fun isServiceAvailable(context: Context): Boolean {
        //return googleApiAvailability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
            return true
    }
}
