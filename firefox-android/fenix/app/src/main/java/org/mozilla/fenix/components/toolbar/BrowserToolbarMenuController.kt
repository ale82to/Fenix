/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar
import android.view.ViewGroup
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.navigation.NavController
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.appservices.places.BookmarkRoot
import mozilla.components.browser.state.action.TabListAction

import mozilla.components.browser.state.selector.findCustomTabOrSelectedTab
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.prompt.ShareData
import mozilla.components.feature.session.SessionFeature
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.feature.top.sites.DefaultTopSitesStorage
import mozilla.components.feature.top.sites.TopSite
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import mozilla.components.ui.widgets.withCenterAlignedButtons
import org.mozilla.fenix.ext.navigateSafe
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.BrowserFragmentDirections
import org.mozilla.fenix.browser.readermode.ReaderModeController
import org.mozilla.fenix.components.FenixSnackbar
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.nav
import org.mozilla.fenix.utils.Settings
import mozilla.components.concept.engine.EngineSession.LoadUrlFlags
import mozilla.components.feature.top.sites.PinnedSiteStorage
/**
 * An interface that handles events from the BrowserToolbar menu, triggered by the Interactor
 */
interface BrowserToolbarMenuController {
    fun handleToolbarItemInteraction(item: ToolbarMenu.Item)
}
@Suppress("LargeClass", "ForbiddenComment", "LongParameterList")
class DefaultBrowserToolbarMenuController(
    private val store: BrowserStore,
    private val activity: HomeActivity,
    private val navController: NavController,
    private val settings: Settings,
    private val readerModeController: ReaderModeController,
    private val sessionFeature: ViewBoundFeatureWrapper<SessionFeature>,
    private val findInPageLauncher: () -> Unit,
    //private val browserAnimator: BrowserAnimator,
    private val snackbarParent: ViewGroup,
    private val customTabSessionId: String?,
    private val openInFenixIntent: Intent,
    private val bookmarkTapped: (String, String) -> Unit,
    private val scope: CoroutineScope,
    private val topSitesStorage: DefaultTopSitesStorage,
    private val browserStore: BrowserStore,
    //private val pinnedSiteStorage: PinnedSiteStorage,
) : BrowserToolbarMenuController {

    private val currentSession
        get() = store.state.findCustomTabOrSelectedTab(customTabSessionId)

    @VisibleForTesting
    internal var ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    @Suppress("ComplexMethod", "LongMethod")
    override fun handleToolbarItemInteraction(item: ToolbarMenu.Item) {
        val sessionUseCases = activity.components.useCases.sessionUseCases
        val tabsUseCases = activity.components.useCases.tabsUseCases

        when (item) {

            is ToolbarMenu.Item.InstallPwaToHomeScreen -> {
                navController.nav(
                    R.id.browserFragment,
                    BrowserFragmentDirections.actionGlobalAddonsManagementFragment(),
                )
            }
            is ToolbarMenu.Item.OpenInFenix -> {
                customTabSessionId?.let {
                    sessionFeature.get()?.release()
                    activity.startActivity(
                        openInFenixIntent.apply {
                            flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
                        },
                    )
                    activity.finishAndRemoveTask()
                }
            }
            is ToolbarMenu.Item.OpenInApp -> {
                invoke(isSelectNextTab = true)

            }
            is ToolbarMenu.Item.Quit -> {
                val tabList = store.state.tabs.toMutableList()
                val selectedTabIndex = tabList.indexOf(store.state.selectedTab)
                val tabsToClose = tabList.subList(selectedTabIndex + 1, tabList.size)
                val idList = tabsToClose.map { it.id }
                if (idList.isNotEmpty()) {
                    activity.components.useCases.tabsUseCases.removeTabs.invoke(idList)
                } else {
                    invoke(isSelectNextTab = true)

                }
            }
            is ToolbarMenu.Item.CustomizeReaderView -> {
                readerModeController.showControls()
            }
            is ToolbarMenu.Item.Back -> {
                if (item.viewHistory) {
                    navController.navigate(
                        BrowserFragmentDirections.actionGlobalTabHistoryDialogFragment(
                            activeSessionId = customTabSessionId,
                        ),
                    )
                } else {
                    currentSession?.let {
                        sessionUseCases.goBack.invoke(it.id)
                    }
                }
            }
            is ToolbarMenu.Item.Forward -> {
                if (item.viewHistory) {
                    navController.navigate(
                        BrowserFragmentDirections.actionGlobalTabHistoryDialogFragment(
                            activeSessionId = customTabSessionId,
                        ),
                    )
                } else {
                    currentSession?.let {
                        sessionUseCases.goForward.invoke(it.id)
                    }
                }
            }
            is ToolbarMenu.Item.RemoveFromTopSites -> {
                val tabs = store.state.tabs.sortedByDescending { it.lastAccess }

                if (tabs.size <= 1) {
                    val firstTabId = tabs[0].id
                    store.dispatch(TabListAction.SelectTabAction(firstTabId))
                } else {
                    val recentTabId1 = tabs[0].id
                    val recentTabId2 = tabs[1].id

                    val selectedTabId = store.state.selectedTabId

                    if (selectedTabId == recentTabId1) {

                        store.dispatch(TabListAction.SelectTabAction(recentTabId2))
                    } else {
                        store.dispatch(TabListAction.SelectTabAction(recentTabId1))
                    }}
            }
            is ToolbarMenu.Item.Stop -> {
                val tabs = store.state.tabs.sortedByDescending { it.lastAccess }
                if (tabs.size <= 1) {
                    val firstTabId = tabs[0].id
                    store.dispatch(TabListAction.SelectTabAction(firstTabId))
                } else {
                    val recentTabId1 = tabs[0].id
                    val recentTabId2 = tabs[1].id

                    val selectedTabId = store.state.selectedTabId

                    if (selectedTabId == recentTabId1) {

                        store.dispatch(TabListAction.SelectTabAction(recentTabId2))
                    } else {

                        store.dispatch(TabListAction.SelectTabAction(recentTabId1))
                    }
                }
            }
            is ToolbarMenu.Item.Share -> {
                val directions = NavGraphDirections.actionGlobalShareFragment(
                    sessionId = currentSession?.id,
                    data = arrayOf(
                        ShareData(
                            url = getProperUrl(currentSession),
                            title = currentSession?.content?.title,
                        ),
                    ),
                    showPage = true,
                )
                navController.navigate(directions)
            }

            is ToolbarMenu.Item.SyncAccount ->  {
                currentSession?.let {activity.components.useCases.tabsUseCases.removeTab.invoke(it.id)

                }
            }
            is ToolbarMenu.Item.RequestDesktop -> {
                currentSession?.let {
                    sessionUseCases.requestDesktopSite.invoke(
                        item.isChecked,
                        it.id,
                    )
                }
            }
            is ToolbarMenu.Item.AddToTopSites -> {
                scope.launch {
                    val context = snackbarParent.context
                    val numPinnedSites = topSitesStorage.cachedTopSites
                        .filter { it is TopSite.Default || it is TopSite.Pinned }.size

                    if (numPinnedSites >= settings.topSitesMaxLimit) {
                        AlertDialog.Builder(snackbarParent.context).apply {
                            setTitle(R.string.shortcut_max_limit_title)
                            setMessage(R.string.shortcut_max_limit_content)
                            setPositiveButton(R.string.top_sites_max_limit_confirmation_button) { dialog, _ ->
                                dialog.dismiss()
                            }
                            create().withCenterAlignedButtons()
                        }.show()
                    } else {
                        ioScope.launch {
                            currentSession?.let {
                                with(activity.components.useCases.topSitesUseCase) {
                                    addPinnedSites(it.content.title, it.content.url)
                                }
                            }
                        }.join()

                        FenixSnackbar.make(
                            view = snackbarParent,
                            duration = Snackbar.LENGTH_SHORT,
                            isDisplayedWithBrowserToolbar = true,
                        )
                            .setText(
                                context.getString(R.string.snackbar_added_to_shortcuts),
                            )
                            .show()
                    }
                }
            }
            is ToolbarMenu.Item.AddToHomeScreen -> {
                val directionsToLogin = NavGraphDirections.actionLoginDetailFragmentToSavedLogins()
                navController.navigate(directionsToLogin)
            }
            is ToolbarMenu.Item.FindInPage -> {
                findInPageLauncher()
            }
            is ToolbarMenu.Item.AddonsManager -> {
                invoke(isSelectNextTab = false)
            }

            is ToolbarMenu.Item.SaveToCollection -> {

                val tabList = store.state.tabs.toMutableList()
                tabList.remove(store.state.selectedTab)
                val idList: MutableList<String> =
                    emptyList<String>().toMutableList()
                for (i in tabList) idList.add(i.id)
                activity.components.useCases.tabsUseCases.removeTabs.invoke(idList.toList())
            }
            is ToolbarMenu.Item.Settings ->  {
                activity.components.useCases.tabsUseCases.removeAllTabs.invoke()
                navController.navigate(
                    BrowserFragmentDirections.actionGlobalHome(focusOnAddressBar = true),
                ) }
            is ToolbarMenu.Item.Bookmark -> {
                store.state.selectedTab?.let {
                    getProperUrl(it)?.let { url -> bookmarkTapped(url, it.content.title) }
                }
            }
            is ToolbarMenu.Item.Bookmarks ->  {
                navController.nav(
                    R.id.browserFragment,
                    BrowserFragmentDirections.actionGlobalBookmarkFragment(BookmarkRoot.Mobile.id),
                )
            }
            is ToolbarMenu.Item.History ->  {
                navController.nav(
                    R.id.browserFragment,
                    BrowserFragmentDirections.actionGlobalHistoryFragment(),
                )
            }

            ToolbarMenu.Item.Downloads -> {
            	
            	
            tabsUseCases.addTab.invoke(
                startLoading = false,
                //selectTab = true,
                //private = currentSession?.content?.private ?: false,
            )
        
        navController.navigate(
            BrowserFragmentDirections.actionGlobalHome(focusOnAddressBar = true),
        )
    
            	
               // currentSession?.let {
                  //  activity.components.useCases.tabsUseCases.removeTab.invoke(it.id, selectParentIfExists = true)
                  //  navController.navigate(
                       // BrowserFragmentDirections.actionGlobalHome(focusOnAddressBar = true)
                   // )
               // }
            }
            is ToolbarMenu.Item.OpenInRegularTab -> {
                currentSession?.let { session ->
                    getProperUrl(session)?.let { url ->
                        tabsUseCases.migratePrivateTabUseCase.invoke(session.id, url)
                    }
                }
            }
            is ToolbarMenu.Item.NewTab -> {
                store.state.selectedTab?.let {
                    getProperUrl(it)?.let { url -> activity.components.useCases.tabsUseCases.addTab(url ,private=true) }
                }
            }
            is ToolbarMenu.Item.SetDefaultBrowser -> {
                val undoTabRemovalUseCase = TabsUseCases.UndoTabRemovalUseCase(browserStore)
                undoTabRemovalUseCase.invoke()
            }
            is ToolbarMenu.Item.Reload -> {
                val flags = if (item.bypassCache) {
                    LoadUrlFlags.select(LoadUrlFlags.BYPASS_CACHE)
                } else {
                    LoadUrlFlags.none()
                }
                currentSession?.let {
                    sessionUseCases.reload.invoke(it.id, flags = flags)
                }
            }


            ToolbarMenu.Item.PrintContent -> {
                val directions = BrowserFragmentDirections.actionBrowserFragmentToSettingsFragment()
                navController.nav(R.id.browserFragment, directions)
            }
            ToolbarMenu.Item.Translate -> {
                val directions =
                    BrowserFragmentDirections.actionBrowserFragmentToTranslationsDialogFragment(
                        sessionId = currentSession?.id,
                    )
                navController.navigateSafe(R.id.browserFragment, directions)
            }
        }
    }
    private fun invoke(isSelectNextTab: Boolean) {
        val tabs = store.state.tabs

        if (isSelectNextTab) {
            if (tabs.isNotEmpty()) {
                val currentTabId = store.state.selectedTabId
                val firstTabId = tabs[0].id
                val lastTabId = tabs[tabs.size - 1].id
                val nextTabId = if (currentTabId == firstTabId) lastTabId else firstTabId
                store.dispatch(TabListAction.SelectTabAction(nextTabId))
            }
        }  else {
            val activeTabId = store.state.selectedTabId
            val moveTabsAction = TabListAction.MoveTabsAction(
                tabIds = listOfNotNull(activeTabId),
                targetTabId = store.state.tabs[0].id,
                placeAfter = false
            )
            store.dispatch(moveTabsAction)
        }
    }

    private fun getProperUrl(currentSession: SessionState?): String? {
        return currentSession?.id?.let {
            val currentTab = browserStore.state.findTab(it)
            if (currentTab?.readerState?.active == true) {
                currentTab.readerState.activeUrl
            } else {
                currentSession.content.url
            }
        }
    }
}
