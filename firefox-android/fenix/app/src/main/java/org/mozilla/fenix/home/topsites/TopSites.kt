package org.mozilla.fenix.home.topsites
import androidx.compose.foundation.ExperimentalFoundationApi
//import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.offset
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.offset
//import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.alpha
//import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
//import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
import mozilla.components.feature.top.sites.TopSite
import org.mozilla.fenix.R
import org.mozilla.fenix.compose.ContextualMenu
import org.mozilla.fenix.compose.Favicon
import org.mozilla.fenix.compose.MenuItem
//import org.mozilla.fenix.compose.PagerIndicator
import org.mozilla.fenix.compose.annotation.LightDarkPreview
import org.mozilla.fenix.theme.FirefoxTheme
import kotlin.math.ceil

private const val TOP_SITES_PER_PAGE = 64
private const val TOP_SITES_PER_ROW = 8

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongParameterList")
fun TopSites(
    topSites: List<TopSite>,
    onTopSiteClick: (TopSite) -> Unit,
    onTopSiteLongClick: (TopSite) -> Unit,
    onOpenInPrivateTabClicked: (topSite: TopSite) -> Unit,
    onRenameTopSiteClicked: (topSite: TopSite) -> Unit,
    onRemoveTopSiteClicked: (topSite: TopSite) -> Unit,
    onSettingsClicked: () -> Unit,
    onSponsorPrivacyClicked: () -> Unit,
    //onTopSiteDoubleClick: (TopSite) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        //verticalArrangement = Arrangement.spacedBy((-20).dp)
    ) {
        val pagerState = rememberPagerState(
            pageCount = { ceil((topSites.size.toDouble() / TOP_SITES_PER_PAGE)).toInt() },
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalPager(
                state = pagerState,
            ) { page ->
                Column {
                    val topSitesWindows = topSites.windowed(
                        size = TOP_SITES_PER_PAGE,
                        step = TOP_SITES_PER_PAGE,
                        partialWindows = true,
                    )[page].chunked(TOP_SITES_PER_ROW)

                    topSitesWindows.forEachIndexed { _, items ->
                        // if (index > 0) {
                        Spacer(modifier = Modifier.height(5.dp))
                        // }
                        Row(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 448.dp)
                                .fillMaxWidth(),
                            //.offset(y = (-6).dp),
                            //verticalArrangement = Arrangement.spacedBy((-20).dp),
                            horizontalArrangement = Arrangement.Center
                        )
                        {
                            items.forEach { topSite ->
                                TopSiteItem(
                                    topSite = topSite,
                                    menuItems = getMenuItems(
                                        topSite = topSite,
                                        onOpenInPrivateTabClicked = onOpenInPrivateTabClicked,
                                        onRenameTopSiteClicked = onRenameTopSiteClicked,
                                        onRemoveTopSiteClicked = onRemoveTopSiteClicked,
                                        onSettingsClicked = onSettingsClicked,
                                        onSponsorPrivacyClicked = onSponsorPrivacyClicked,
                                    ),
                                    onTopSiteClick = { item -> onTopSiteClick(item) },
                                    onTopSiteLongClick = onTopSiteLongClick,
                                    //onTopSiteDoubleClick = { onTopSiteDoubleClick(topSite) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopSiteItem(
    topSite: TopSite,
    menuItems: List<MenuItem>,
    onTopSiteClick: (TopSite) -> Unit,
    onTopSiteLongClick: (TopSite) -> Unit,
    //onTopSiteDoubleClick: (TopSite) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Column(
            modifier = Modifier
                // .offset(y = (-6).dp)
                .combinedClickable(
                    // onDoubleClick = { onTopSiteDoubleClick(topSite) },
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onTopSiteClick(topSite) },
                    onLongClick = {
                        onTopSiteLongClick(topSite)
                        menuExpanded = true
                    },
                )
                .width(54.75.dp),

            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(2.dp))
            TopSiteFaviconCard(topSite = topSite)
            //Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier
                    .width(54.75.dp),
                //.offset(y = (-6).dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = topSite.title ?: topSite.url,
                    color = FirefoxTheme.colors.textWarningButton,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = FirefoxTheme.typography.caption,
                    modifier = Modifier
                    //.padding(top = 0.dp, bottom = 0.dp)
                )
            }


        }

        ContextualMenu(
            menuItems = menuItems,
            showMenu = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        )
    }
}
@Composable
private fun TopSiteFaviconCard(topSite: TopSite) {
    Card(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(0.dp),
        backgroundColor = FirefoxTheme.colors.layer2,
        elevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(0.dp),
                color = FirefoxTheme.colors.layer2,
            ) {
                Favicon(
                    url = topSite.url,
                    size = 40.dp,
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun getMenuItems(
    topSite: TopSite,
    onOpenInPrivateTabClicked: (topSite: TopSite) -> Unit,
    onRenameTopSiteClicked: (topSite: TopSite) -> Unit,
    onRemoveTopSiteClicked: (topSite: TopSite) -> Unit,
    onSettingsClicked: () -> Unit,
    onSponsorPrivacyClicked: () -> Unit,
): List<MenuItem> {
    val isPinnedSite = topSite is TopSite.Pinned
    val isProvidedSite = topSite is TopSite.Provided
    val result = mutableListOf<MenuItem>()

    result.add(
        MenuItem(
            title = stringResource(id = R.string.bookmark_menu_open_in_private_tab_button),
            onClick = { onOpenInPrivateTabClicked(topSite) },
        ),
    )

    if (isPinnedSite) {
        result.add(
            MenuItem(
                title = stringResource(id = R.string.rename_top_site),
                onClick = { onRenameTopSiteClicked(topSite) },
            ),
        )
    }

    if (!isProvidedSite) {
        result.add(
            MenuItem(
                title = stringResource(
                    id = if (isPinnedSite) {
                        R.string.remove_top_site
                    } else {
                        R.string.delete_from_history
                    },
                ),
                onClick = { onRemoveTopSiteClicked(topSite) },
            ),
        )
    }

    if (isProvidedSite) {
        result.add(
            MenuItem(
                title = stringResource(id = R.string.delete_from_history),
                onClick = { onRemoveTopSiteClicked(topSite) },
            ),
        )
    }

    if (!isProvidedSite) {
        result.add(
            MenuItem(
                title = "Logins \uD83D\uDC64",
                onClick = onSettingsClicked,
            ),
        )
    }

    if (!isProvidedSite) {
        result.add(
            MenuItem(
                title = "History \uD83D\uDD51",
                onClick = onSponsorPrivacyClicked,
            ),
        )
    }

    return result
}

@Composable
@LightDarkPreview
private fun TopSitesPreview() {
    FirefoxTheme {
        Box(
            modifier = Modifier.background(color = FirefoxTheme.colors.layer1)
        ) {
            TopSites(
                topSites = mutableListOf(),
                onTopSiteClick = {},
                onTopSiteLongClick = {},
                onOpenInPrivateTabClicked = {},
                onRenameTopSiteClicked = {},
                onRemoveTopSiteClicked = {},
                onSettingsClicked = {},
                onSponsorPrivacyClicked = {},
                //onTopSiteDoubleClick = {},
            )
        }
    }
}
