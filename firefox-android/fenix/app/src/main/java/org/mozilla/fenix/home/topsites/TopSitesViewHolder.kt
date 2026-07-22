/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.topsites

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import mozilla.components.lib.state.ext.observeAsComposableState
import org.mozilla.fenix.components.components
import org.mozilla.fenix.compose.ComposeViewHolder
import org.mozilla.fenix.home.sessioncontrol.TopSiteInteractor


class TopSitesViewHolder(
    composeView: ComposeView,
    viewLifecycleOwner: LifecycleOwner,
    private val interactor: TopSiteInteractor,
) : ComposeViewHolder(composeView, viewLifecycleOwner) {

    @Composable
    override fun Content() {
        val topSites =
            components.appStore.observeAsComposableState { state -> state.topSites }.value
                ?: emptyList()

        TopSites(
            topSites = topSites,
            onTopSiteClick = { topSite ->
                interactor.onSelectTopSite(topSite, topSites.indexOf(topSite))
            },
            onTopSiteLongClick = interactor::onTopSiteLongClicked,
            //onTopSiteDoubleClick = {
            //interactor.onSponsorPrivacyClicked()
            // },
            onOpenInPrivateTabClicked = interactor::onOpenInPrivateTabClicked,
            onRenameTopSiteClicked = interactor::onRenameTopSiteClicked,
            onRemoveTopSiteClicked = interactor::onRemoveTopSiteClicked,
            onSettingsClicked = interactor::onSettingsClicked,
            onSponsorPrivacyClicked = interactor::onSponsorPrivacyClicked,
        )
    }
    companion object {
        val LAYOUT_ID = View.generateViewId()
    }
}
