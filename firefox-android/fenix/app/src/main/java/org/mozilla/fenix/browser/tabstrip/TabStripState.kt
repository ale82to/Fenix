/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser.tabstrip

import android.graphics.Bitmap
import mozilla.components.browser.state.state.BrowserState


data class TabsStripState(
    val tabs: List<TabUIState>,
) {
    companion object {
        val initial = TabsStripState(tabs = emptyList())
    }
}

data class TabUIState(
    val id: String,
    val title: String,
    val icon: Bitmap? = null,
    val isPrivate: Boolean,
    val isSelected: Boolean,
)


internal fun BrowserState.toTabsStripState(
    onHome: Boolean,
    isPrivateMode: Boolean,
): TabsStripState {
    return TabsStripState(
        tabs = tabs
            .filter {
                if (isPrivateMode) {
                    it.content.private
                } else {
                    !it.content.private
                }
            }
            .map {
                TabUIState(
                    id = it.id,
                    title = it.content.title,
                    icon = it.content.icon,
                    isPrivate = it.content.private,
                    isSelected = !onHome && it.id == selectedTabId,
                )
            },
    )
}
