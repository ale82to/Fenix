/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser.tabstrip

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import mozilla.components.lib.state.ext.observeAsState
import org.mozilla.fenix.R
import org.mozilla.fenix.components.components
import org.mozilla.fenix.theme.FirefoxTheme
import org.mozilla.fenix.theme.Theme
import androidx.compose.ui.unit.sp


// import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.feature.tabs.TabsUseCases
import androidx.compose.ui.res.dimensionResource

import androidx.compose.foundation.combinedClickable



import org.mozilla.fenix.components.AppStore
import mozilla.components.browser.state.store.BrowserStore

private val minTabStripItemWidth = 44.dp
private val maxTabStripItemWidth = 45.dp
private val tabStripIconSize = 24.dp


@Composable
fun TabStrip(
    onHome: Boolean = false,
    onSelectedTabClick: () -> Unit = {},
    onSelectedTabLongPress: () -> Unit = {}
) {
    val browserStore = components.core.store    
    val tabsUseCases = components.useCases.tabsUseCases
    val appStore = components.appStore
    
    val store = browserStore // Initialize store

    val state = browserStore.observeAsState(TabsStripState.initial) {
        it.toTabsStripState(onHome = onHome, isPrivateMode = appStore.state.mode.isPrivate)
    }

    TabStripContent(
        state = state.value,
        onSelectedTabClick = {
            tabsUseCases.selectTab(it)
            onSelectedTabClick()
        },   
        onSelectedTabLongPress = {
            handleTabAction(it,store)
            tabsUseCases.removeTab(it)
            onSelectedTabLongPress()
        },
        store = store 
    )
}

private fun handleTabAction(tabId: String, store: BrowserStore) {
    val tabs = store.state.tabs

    if (tabs.isNotEmpty()) {
        val moveTabsAction = TabListAction.MoveTabsAction(
            tabIds = listOfNotNull(tabId), // Use the provided tabId
            targetTabId = tabs[0].id, // Target the first tab position
            placeAfter = false
        )
        store.dispatch(moveTabsAction)
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabStripContent(
    state: TabsStripState,
    onSelectedTabClick: (id: String) -> Unit,
    onSelectedTabLongPress: (id: String) -> Unit,
    store: BrowserStore // Add store parameter here
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(FirefoxTheme.colors.layer3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        val listState = rememberLazyListState()

        LazyRow(
            modifier = Modifier.weight(1f, fill = false),
            state = listState,
        ) {
            items(
                items = state.tabs,
                key = { it.id },
            ) { itemState ->
                TabItem(
                    state = itemState,
                    onSelectedTabClick = onSelectedTabClick,
                    onSelectedTabLongPress = onSelectedTabLongPress,
                    modifier = Modifier.animateItemPlacement(),
                    store = store // Pass the store parameter here
                )
            }
        }

        if (state.tabs.isNotEmpty()) {
            LaunchedEffect(state.tabs.last().id) {
                listState.animateScrollToItem(state.tabs.size)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabItem(
    state: TabUIState,
    modifier: Modifier = Modifier,
    onSelectedTabClick: (id: String) -> Unit,
    onSelectedTabLongPress: (id: String) -> Unit,
    store: BrowserStore
) {
    TabStripCard(
        modifier = modifier.fillMaxSize(),
        backgroundColor =
        if (state.isPrivate) {
            if (state.isSelected) {
                FirefoxTheme.colors.layer4Center
            } else {
                FirefoxTheme.colors.layer3
            }
        } else {
            if (state.isSelected) {
                FirefoxTheme.colors.layer4Center
            } else {
                FirefoxTheme.colors.layer3
            }
        },
        elevation = if (state.isSelected) {
            selectedTabStripCardElevation
        } else {
            defaultTabStripCardElevation
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .combinedClickable(
                        onClick = {
                            onSelectedTabClick(state.id)
                        },
                        onLongClick = {
                            onSelectedTabLongPress(state.id)
                        }
                    )
            ) {
                TabStripIcon(state.icon)
            }
            Spacer(modifier = Modifier.size(3.dp))
            Text(
                text = state.title,
                color = FirefoxTheme.colors.layer4Start,
                maxLines = 1,
                fontSize = 10.sp,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .widthIn(
                        min = minTabStripItemWidth,
                        max = maxTabStripItemWidth,
                    )
                    .combinedClickable(
                        onClick = {
                            onSelectedTabClick(state.id)
                        },
                        onLongClick = {
                            handleTabAction(state.id, store) 
                        }
                    ),
                style = FirefoxTheme.typography.subtitle2,
            )
        }
    }
}





@Composable
private fun TabStripIcon(icon: Bitmap?) {
    if (icon != null) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(tabStripIconSize)
                .clip(CircleShape),
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.mozac_ic_globe_24),
            modifier = Modifier.size(tabStripIconSize),
            tint = FirefoxTheme.colors.iconDisabled,
            contentDescription = null,
        )
    }
}

@Preview(device = Devices.TABLET)
@Composable
private fun TabsStripPreview(
    @PreviewParameter(TabUIStateParameterProvider::class) tabsStripState: TabsStripState,
    store: BrowserStore // Add store parameter here
) {
    FirefoxTheme {
        TabsStripContentPreview(tabsStripState.tabs.filter { !it.isPrivate }, store = store)
    }
}


private class TabUIStateParameterProvider :
    PreviewParameterProvider<TabsStripState> {
    override val values: Sequence<TabsStripState>
        get() = sequenceOf(
            TabsStripState(
                listOf(
                    TabUIState(
                        id = "1",
                        title = "Tab 1",
                        isPrivate = false,
                        isSelected = false,
                    ),
                    TabUIState(
                        id = "2",
                        title = "Tab 2 with a very long title that should be truncated",
                        isPrivate = false,
                        isSelected = false,
                    ),
                    TabUIState(
                        id = "3",
                        title = "Selected tab",
                        isPrivate = false,
                        isSelected = true,
                    ),
                    TabUIState(
                        id = "p1",
                        title = "Private tab 1",
                        isPrivate = true,
                        isSelected = false,
                    ),
                    TabUIState(
                        id = "p2",
                        title = "Private selected tab",
                        isPrivate = true,
                        isSelected = true,
                    ),
                ),
            ),
        )
}

@Preview(device = Devices.TABLET)
@Composable
private fun TabsStripPreviewDarkMode(
    @PreviewParameter(TabUIStateParameterProvider::class) tabsStripState: TabsStripState,
    store: BrowserStore // Add store parameter here
) {
    FirefoxTheme {
        TabsStripContentPreview(tabsStripState.tabs.filter { !it.isPrivate }, store)
    }
}

@Preview(device = Devices.TABLET)
@Composable
private fun TabsStripPreviewPrivateMode(
    @PreviewParameter(TabUIStateParameterProvider::class) tabsStripState: TabsStripState,
    store: BrowserStore // Add store parameter here
) {
    FirefoxTheme(theme = Theme.Private) {
        TabsStripContentPreview(tabsStripState.tabs.filter { it.isPrivate }, store)
    }
}

@Composable
private fun TabsStripContentPreview(
    tabs: List<TabUIState>,
    store: BrowserStore // Define store parameter here
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        TabStripContent(
            state = TabsStripState(
                tabs = tabs,
            ),
            onSelectedTabClick = {},
            onSelectedTabLongPress = {},
            store = store // Pass store parameter here
        )
    }
}

