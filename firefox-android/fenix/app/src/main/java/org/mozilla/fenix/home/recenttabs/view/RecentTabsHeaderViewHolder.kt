/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.recenttabs.view

import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import org.mozilla.fenix.R
import org.mozilla.fenix.compose.ComposeViewHolder
import org.mozilla.fenix.compose.home.HomeSectionHeader
import org.mozilla.fenix.home.recenttabs.interactor.RecentTabInteractor

/**
 * View holder for the recent tabs header and "Show all" button.
 *
 * @param interactor [RecentTabInteractor] which will have delegated to all user interactions.
 */
class RecentTabsHeaderViewHolder(
    composeView: ComposeView,
    viewLifecycleOwner: LifecycleOwner,
    private val interactor: RecentTabInteractor,

    ) : ComposeViewHolder(composeView, viewLifecycleOwner) {

    init {
        val horizontalPadding =
            composeView.resources.getDimensionPixelSize(R.dimen.home_item_horizontal_margin)
        composeView.setPadding(horizontalPadding, 0, horizontalPadding, 0)
    }

    @Composable
    override fun Content() {
        Column {
            Spacer(modifier = Modifier.height(6.dp))
            HomeSectionHeader(

                onShowAllClick = {
                    interactor.onRecentTabShowAllClicked()
                },
                onShowAllLongPress = {
                   // interactor.onShowAllBookmarksClicked()
                   interactor.openCustomizeHomePage()
                }

            ) {
               // interactor.openCustomizeHomePage()
               interactor.onShowAllBookmarksClicked()
            }

            Spacer(Modifier.height(0.dp))
        }
    }

    companion object {
        val LAYOUT_ID = View.generateViewId()
    }
}
