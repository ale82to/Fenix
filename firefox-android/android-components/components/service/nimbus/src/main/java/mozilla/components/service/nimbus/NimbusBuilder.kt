/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.service.nimbus

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
//import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.base.utils.NamedThreadFactory
import org.mozilla.experiments.nimbus.AbstractNimbusBuilder
import org.mozilla.experiments.nimbus.NimbusDelegate
import java.util.concurrent.Executors

// private val logger = Logger("service/Nimbus")


class NimbusBuilder(context: Context) : AbstractNimbusBuilder<NimbusApi>(context) {
    override fun createDelegate(): NimbusDelegate =
        NimbusDelegate(
            dbScope = createNamedCoroutineScope("NimbusDbScope"),
            fetchScope = createNamedCoroutineScope("NimbusFetchScope"),
            errorReporter = errorReporter,
            logger = { _ -> },
        )

    override fun newNimbus(
        appInfo: NimbusAppInfo,
        serverSettings: NimbusServerSettings?,
    ) = Nimbus(
        context,
        appInfo = appInfo,
        coenrollingFeatureIds = getCoenrollingFeatureIds(),
        server = serverSettings,
        deviceInfo = createDeviceInfo(),
        delegate = createDelegate(),
    )

    override fun newNimbusDisabled() = NimbusDisabled(context)
}

private fun createNamedCoroutineScope(name: String) = CoroutineScope(
    Executors.newSingleThreadExecutor(
        NamedThreadFactory(name),
    ).asCoroutineDispatcher(),
)
