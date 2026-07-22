/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.metrics

import android.content.Context
import android.os.RemoteException
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
//import mozilla.components.support.base.log.logger.Logger
import org.json.JSONException
import org.json.JSONObject
import org.mozilla.fenix.FeatureFlags
import org.mozilla.fenix.GleanMetrics.MetaAttribution
import org.mozilla.fenix.GleanMetrics.PlayStoreAttribution
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.utils.Settings
import java.io.UnsupportedEncodingException
import java.net.URLDecoder


class InstallReferrerMetricsService(private val context: Context) : MetricsService {
    //private val logger = Logger("InstallReferrerMetricsService")
    override val type = MetricServiceType.Data

    private var referrerClient: InstallReferrerClient? = null

    override fun start() {
        // If UTM parameters are already known, no need to proceed further
        if (context.settings().utmParamsKnown) {
            return
        }

        // Start the timer as before
        val timerId = PlayStoreAttribution.attributionTime.start()

        // Simulate setting the UTM params known without using the InstallReferrerClient
        context.settings().utmParamsKnown = true

        // Stop and accumulate the timer with a valid timerId
        PlayStoreAttribution.attributionTime.stopAndAccumulate(timerId)
    }

    override fun stop() {
        // As we're not using referrerClient, we don't need to end any connections
        referrerClient = null
    }

    override fun track(event: Event) = Unit

    override fun shouldTrack(event: Event): Boolean = false
}

data class UTMParams(
    val source: String,
    val medium: String,
    val campaign: String,
    val content: String,
    val term: String,
) {

    companion object {
        const val UTM_SOURCE = "utm_source"
        const val UTM_MEDIUM = "utm_medium"
        const val UTM_CAMPAIGN = "utm_campaign"
        const val UTM_CONTENT = "utm_content"
        const val UTM_TERM = "utm_term"

       
        fun parseUTMParameters(installReferrerResponse: String): UTMParams {
            val utmParams = mutableMapOf<String, String>()
            val params = installReferrerResponse.split("&")

            for (param in params) {
                val keyValue = param.split("=")
                if (keyValue.size == 2) {
                    val key = keyValue[0]
                    val value = keyValue[1]
                    utmParams[key] = value
                }
            }

            return UTMParams(
                source = utmParams[UTM_SOURCE] ?: "",
                medium = utmParams[UTM_MEDIUM] ?: "",
                campaign = utmParams[UTM_CAMPAIGN] ?: "",
                content = utmParams[UTM_CONTENT] ?: "",
                term = utmParams[UTM_TERM] ?: "",
            )
        }

       
        fun fromSettings(settings: Settings): UTMParams =
            with(settings) {
                UTMParams(
                    source = utmSource,
                    medium = utmMedium,
                    campaign = utmCampaign,
                    content = utmContent,
                    term = utmTerm,
                )
            }
    }

   
    fun intoSettings(settings: Settings) {
        with(settings) {
            utmSource = source
            utmMedium = medium
            utmCampaign = campaign
            utmTerm = term
            utmContent = content
        }
    }

   
    fun isEmpty(): Boolean {
        return source.isBlank() &&
            medium.isBlank() &&
            campaign.isBlank() &&
            term.isBlank() &&
            content.isBlank()
    }

   
    fun recordInstallReferrer(settings: Settings) {
        if (isEmpty()) {
            return
        }
        intoSettings(settings)

        PlayStoreAttribution.source.set(source)
        PlayStoreAttribution.medium.set(medium)
        PlayStoreAttribution.campaign.set(campaign)
        PlayStoreAttribution.content.set(content)
        PlayStoreAttribution.term.set(term)
    }
}


data class MetaParams(
    val app: String,
    val t: String,
    val data: String,
    val nonce: String,
) {
    companion object {
       // private val logger = Logger("MetaParams")
        private const val APP = "app"
        private const val T = "t"
        private const val SOURCE = "source"
        private const val DATA = "data"
        private const val NONCE = "nonce"

        @Suppress("ReturnCount")
        internal fun extractMetaAttribution(contentString: String?): MetaParams? {
            if (contentString == null) {
                return null
            }
            val decodedContentString = try {
                // content string can be in percent format
                URLDecoder.decode(contentString, "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                //logger.error("failed to decode content string", e)
                // can't recover from this
                return null
            }

            val data: String
            val nonce: String

            val contentJson = try {
                JSONObject(decodedContentString)
            } catch (e: JSONException) {
               // logger.error("content is not JSON", e)
                // can't recover from this
                return null
            }

            val app = try {
                contentJson.optString(APP) ?: ""
            } catch (e: JSONException) {
                //logger.error("failed to extract app", e)
                // this is an acceptable outcome
                ""
            }

            val t = try {
                contentJson.optString(T) ?: ""
            } catch (e: JSONException) {
                //logger.error("failed to extract t", e)
                // this is an acceptable outcome
                ""
            }

            try {
                val source = contentJson.optJSONObject(SOURCE)
                data = source?.optString(DATA) ?: ""
                nonce = source?.optString(NONCE) ?: ""
            } catch (e: JSONException) {
               // logger.error("failed to extract data or nonce", e)
                // can't recover from this
                return null
            }

            if (data.isBlank() || nonce.isBlank()) {
                return null
            }

            return MetaParams(
                app = app,
                t = t,
                data = data,
                nonce = nonce,
            )
        }
    }

  
    fun recordMetaAttribution() {
        MetaAttribution.app.set(app)
        MetaAttribution.t.set(t)
        MetaAttribution.data.set(data)
        MetaAttribution.nonce.set(nonce)
    }
}
