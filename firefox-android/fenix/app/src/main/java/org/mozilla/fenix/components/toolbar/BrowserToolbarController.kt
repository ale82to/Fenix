/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar

import androidx.navigation.NavController
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.selector.findCustomTabOrSelectedTab
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.tabs.TabsUseCases
//import mozilla.components.service.glean.private.NoExtras
import mozilla.components.support.ktx.kotlin.isUrl
import mozilla.components.ui.tabcounter.TabCounterMenu
//import org.mozilla.fenix.GleanMetrics.Events
//import org.mozilla.fenix.GleanMetrics.ReaderMode
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
//import org.mozilla.fenix.browser.BrowserAnimator
//import org.mozilla.fenix.browser.BrowserAnimator.Companion.getToolbarNavOptions
import org.mozilla.fenix.browser.BrowserFragmentDirections
//import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.browser.readermode.ReaderModeController
import org.mozilla.fenix.components.toolbar.interactor.BrowserToolbarInteractor
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.nav
import org.mozilla.fenix.ext.navigateSafe
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.home.HomeFragment
import org.mozilla.fenix.home.HomeScreenViewModel
import org.mozilla.fenix.components.AppStore
import org.mozilla.fenix.components.appstate.AppAction


/**
 * An interface that handles the view manipulation of the BrowserToolbar, triggered by the Interactor
 */
interface BrowserToolbarController {
    fun handleScroll(offset: Int)
    fun handleToolbarPaste(text: String)
    fun handleToolbarPasteAndGo(text: String)
    fun handleToolbarClick()
    fun handleTabCounterClick()
    fun handleTabCounterItemInteraction(item: TabCounterMenu.Item)
    fun handleReaderModePressed(enabled: Boolean)

    /**
     * @see [BrowserToolbarInteractor.onHomeButtonClicked]
     */
    fun handleHomeButtonClick()

    /**
     * @see [BrowserToolbarInteractor.onEraseButtonClicked]
     */
    fun handleEraseButtonClick()

    /**
     * @see [BrowserToolbarInteractor.onShoppingCfrActionClicked]
     */
    fun handleShoppingCfrActionClick()

    /**
     * @see [BrowserToolbarInteractor.onShoppingCfrDisplayed]
     */
    fun handleShoppingCfrDisplayed()

    /**
     * @see [BrowserToolbarInteractor.onTranslationsButtonClicked]
     */
    fun handleTranslationsButtonClick()
}

private const val MAX_DISPLAY_NUMBER_SHOPPING_CFR = 0

@Suppress("LongParameterList")
class DefaultBrowserToolbarController(
    private val appStore: AppStore,
    private val store: BrowserStore,
    private val tabsUseCases: TabsUseCases,
    private val activity: HomeActivity,
    private val navController: NavController,
    private val readerModeController: ReaderModeController,
    private val engineView: EngineView,
    private val homeViewModel: HomeScreenViewModel,
    private val customTabSessionId: String?,
    //private val browserAnimator: BrowserAnimator,
    private val onTabCounterClicked: () -> Unit,
    private val onCloseTab: (SessionState) -> Unit,
) : BrowserToolbarController {

    private val currentSession
        get() = store.state.findCustomTabOrSelectedTab(customTabSessionId)

    override fun handleToolbarPaste(text: String) {

        navController.nav(
            R.id.browserFragment,
            BrowserFragmentDirections.actionGlobalSearchDialog(
                sessionId = currentSession?.id,
                //pastedText = text,
            ),
            //getToolbarNavOptions(activity),
        )
    }


    override fun handleToolbarPasteAndGo(text: String) {
        if (text.isUrl()) {
            store.updateSearchTermsOfSelectedSession("")
            activity.components.useCases.sessionUseCases.loadUrl.invoke(text)
            return
        }

        store.updateSearchTermsOfSelectedSession(text)
        activity.components.useCases.searchUseCases.defaultSearch.invoke(
            text,
            sessionId = store.state.selectedTabId,
        )
    }

    override fun handleToolbarClick() {
        if (currentSession?.content?.searchTerms.isNullOrBlank()) {
            navController.navigate(
                BrowserFragmentDirections.actionGlobalHome(),
            )
            navController.navigate(
                BrowserFragmentDirections.actionGlobalSearchDialog(
                    currentSession?.id,
                ),
            )

        } else {
            navController.navigate(
                BrowserFragmentDirections.actionGlobalSearchDialog(
                    currentSession?.id,
                ),
            )
        }
    }

    override fun handleTabCounterClick() {
        onTabCounterClicked.invoke()
    }

    override fun handleReaderModePressed(enabled: Boolean) {
        if (enabled) {
            readerModeController.showReaderView()

        } else {
            readerModeController.hideReaderView()

        }
    }

    override fun handleTabCounterItemInteraction(item: TabCounterMenu.Item) {
        when (item) {
            is TabCounterMenu.Item.CloseTab -> {
                store.state.selectedTab?.let {
                    if (store.state.getNormalOrPrivateTabs(it.content.private).count() == 1) {
                        homeViewModel.sessionToDelete = it.id
                        navController.navigate(
                            BrowserFragmentDirections.actionGlobalHome(),
                        )
                    } else {
                        onCloseTab.invoke(it)
                        tabsUseCases.removeTab(it.id, selectParentIfExists = true)
                    }
                }
            }
            is TabCounterMenu.Item.DuplicateTab -> {
                store.state.selectedTab?.let {

                        tabsUseCases.addTab.invoke(it.content.url, true)


                }
            }


            is TabCounterMenu.Item.NewTab -> {
                tabsUseCases.removeAllTabs.invoke()
                navController.navigate(
                    BrowserFragmentDirections.actionGlobalHome(focusOnAddressBar = true),
                )

            }
            is TabCounterMenu.Item.NewPrivateTab -> {
                val tabList = store.state.tabs.toMutableList()
                tabList.remove(store.state.selectedTab)
                val idList: MutableList<String> =
                    emptyList<String>().toMutableList()
                for (i in tabList) idList.add(i.id)
                tabsUseCases.removeTabs.invoke(idList.toList())
            }
        }
    }

    override fun handleScroll(offset: Int) {
        if (activity.settings().isDynamicToolbarEnabled) {
            engineView.setVerticalClipping(offset)
        }
    }

    override fun handleHomeButtonClick() {


        tabsUseCases.addTab.invoke(
                startLoading = false,
                //private = currentSession?.content?.private ?: false,
            )
            navController.navigate(
            BrowserFragmentDirections.actionGlobalHome(focusOnAddressBar = true),
        )

    }

    override fun handleEraseButtonClick() {
        // Events.browserToolbarEraseTapped.record(NoExtras())
        homeViewModel.sessionToDelete = HomeFragment.ALL_PRIVATE_TABS
        val directions = BrowserFragmentDirections.actionGlobalHome()
        navController.navigate(directions)
    }

    override fun handleShoppingCfrActionClick() {
        navController.navigate(
            BrowserFragmentDirections.actionBrowserFragmentToReviewQualityCheckDialogFragment(),
        )
    }

    override fun handleShoppingCfrDisplayed() {
        updateShoppingCfrSettings()
    }

    override fun handleTranslationsButtonClick() {
        val directions =
            BrowserFragmentDirections.actionBrowserFragmentToTranslationsDialogFragment(
                sessionId = currentSession?.id,
            )
        navController.navigateSafe(R.id.browserFragment, directions)
    }

  
    private fun updateShoppingCfrSettings() = with(activity.settings()) {
        reviewQualityCheckCFRClosedCounter++
        if (reviewQualityCheckCfrDisplayTimeInMillis != 0L &&
            reviewQualityCheckCFRClosedCounter >= MAX_DISPLAY_NUMBER_SHOPPING_CFR
        ) {
            shouldShowReviewQualityCheckCFR = false
        } else {
            reviewQualityCheckCfrDisplayTimeInMillis = System.currentTimeMillis()
        }
    }
}

private fun BrowserStore.updateSearchTermsOfSelectedSession(
    searchTerms: String,
) {
    val selectedTabId = state.selectedTabId ?: return

    dispatch(
        ContentAction.UpdateSearchTermsAction(
            selectedTabId,
            searchTerms,
        ),
    )
}
