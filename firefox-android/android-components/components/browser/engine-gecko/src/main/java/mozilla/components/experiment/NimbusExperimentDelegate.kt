/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.experiment

import mozilla.components.browser.engine.gecko.GeckoNimbus
//import mozilla.components.support.base.log.logger.Logger
//import org.json.JSONObject
//import org.mozilla.experiments.nimbus.internal.FeatureHolder
//import org.mozilla.geckoview.ExperimentDelegate
//import org.mozilla.geckoview.ExperimentDelegate.ExperimentException
//import org.mozilla.geckoview.ExperimentDelegate.ExperimentException.ERROR_FEATURE_NOT_FOUND
//import org.mozilla.geckoview.GeckoResult

/**
 * Default Nimbus [ExperimentDelegate] implementation to communicate with mobile Gecko and GeckoView.
 
class NimbusExperimentDelegate : ExperimentDelegate {

   
    override fun onGetExperimentFeature(feature: String): GeckoResult<JSONObject> {
        val result = GeckoResult<JSONObject>()
        val nimbusFeature = GeckoNimbus.getFeature(feature)
        if (nimbusFeature != null) {
            result.complete(nimbusFeature.toJSONObject())
        } else {
            //logger.warn("Could not find Nimbus feature '$feature' to retrieve experiment information.")
            result.completeExceptionally(ExperimentException(ERROR_FEATURE_NOT_FOUND))
        }
        return result
    }




class NimbusExperimentDelegate : ExperimentDelegate {

    override fun onGetExperimentFeature(feature: String): GeckoResult<JSONObject> {
        val result = GeckoResult<JSONObject>()
        result.completeExceptionally(ExperimentException(ERROR_FEATURE_NOT_FOUND))
        return result
    }
}

    
    override fun onRecordExposureEvent(feature: String): GeckoResult<Void> {
        return recordWithFeature(feature) { it.recordExposure() }
    }

   
    override fun onRecordExperimentExposureEvent(feature: String, slug: String): GeckoResult<Void> {
        return recordWithFeature(feature) { it.recordExperimentExposure(slug) }
    }

    
    override fun onRecordMalformedConfigurationEvent(feature: String, part: String): GeckoResult<Void> {
        return recordWithFeature(feature) { it.recordMalformedConfiguration(part) }
    }

   
   private fun recordWithFeature(featureId: String, closure: (FeatureHolder<*>) -> Unit): GeckoResult<Void> {
    val result = GeckoResult<Void>()
    // Nullified code: The function does nothing and completes the result successfully with null.
    result.complete(null)
    return result
}

}


*/



