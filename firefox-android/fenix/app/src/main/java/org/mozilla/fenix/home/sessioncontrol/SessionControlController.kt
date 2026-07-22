/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.sessioncontrol
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.widget.EditText
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.state.availableSearchEngines
import mozilla.components.browser.state.state.searchEngines
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.prompt.ShareData
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tab.collections.TabCollection
import mozilla.components.feature.tab.collections.ext.invoke
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.feature.top.sites.TopSite
import mozilla.components.service.nimbus.messaging.Message
import mozilla.components.support.ktx.android.view.showKeyboard
import mozilla.components.ui.widgets.withCenterAlignedButtons
import org.mozilla.fenix.BrowserDirection
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.collections.SaveCollectionStep
import org.mozilla.fenix.components.AppStore
import org.mozilla.fenix.components.appstate.AppAction

import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.nav
import org.mozilla.fenix.home.HomeFragment
import org.mozilla.fenix.home.HomeFragmentDirections
import org.mozilla.fenix.messaging.MessageController
import org.mozilla.fenix.onboarding.WallpaperOnboardingDialogFragment.Companion.THUMBNAILS_SELECTION_COUNT
//import org.mozilla.fenix.settings.SettingsFragmentDirections
import org.mozilla.fenix.settings.SupportUtils
import org.mozilla.fenix.utils.Settings
import org.mozilla.fenix.wallpapers.Wallpaper
import org.mozilla.fenix.wallpapers.WallpaperState
import mozilla.components.feature.tab.collections.Tab as ComponentTab

/**
 * [HomeFragment] controller. An interface that handles the view manipulation of the Tabs triggered
 * by the Interactor.
 */
@Suppress("TooManyFunctions")
interface SessionControlController {
    /**
     * @see [CollectionInteractor.onCollectionAddTabTapped]
     */
    fun handleCollectionAddTabTapped(collection: TabCollection)

    /**
     * @see [CollectionInteractor.onCollectionOpenTabClicked]
     */
    fun handleCollectionOpenTabClicked(tab: ComponentTab)

    /**
     * @see [CollectionInteractor.onCollectionOpenTabsTapped]
     */
    fun handleCollectionOpenTabsTapped(collection: TabCollection)

    /**
     * @see [CollectionInteractor.onCollectionRemoveTab]
     */
    fun handleCollectionRemoveTab(collection: TabCollection, tab: ComponentTab)

    /**
     * @see [CollectionInteractor.onCollectionShareTabsClicked]
     */
    fun handleCollectionShareTabsClicked(collection: TabCollection)

    /**
     * @see [CollectionInteractor.onDeleteCollectionTapped]
     */
    fun handleDeleteCollectionTapped(collection: TabCollection)

    /**
     * @see [TopSiteInteractor.onOpenInPrivateTabClicked]
     */
    fun handleOpenInPrivateTabClicked(topSite: TopSite)

    /**
     * @see [TopSiteInteractor.onRenameTopSiteClicked]
     */
    fun handleRenameTopSiteClicked(topSite: TopSite)

    /**
     * @see [TopSiteInteractor.onRemoveTopSiteClicked]
     */
    fun handleRemoveTopSiteClicked(topSite: TopSite)

    /**
     * @see [CollectionInteractor.onRenameCollectionTapped]
     */
    fun handleRenameCollectionTapped(collection: TabCollection)

    /**
     * @see [TopSiteInteractor.onSelectTopSite]
     */
    fun handleSelectTopSite(topSite: TopSite, position: Int)

    /**
     * @see [TopSiteInteractor.onSettingsClicked]
     */
    fun handleTopSiteSettingsClicked()

    /**
     * @see [TopSiteInteractor.onSponsorPrivacyClicked]
     */
    fun handleSponsorPrivacyClicked()

    /**
     * @see [TopSiteInteractor.onTopSiteLongClicked]
     */
    fun handleTopSiteLongClicked(topSite: TopSite)

    /**
     * @see [CollectionInteractor.onToggleCollectionExpanded]
     */
    fun handleToggleCollectionExpanded(collection: TabCollection, expand: Boolean)

    /**
     * @see [CollectionInteractor.onAddTabsToCollectionTapped]
     */
    fun handleCreateCollection()

    /**
     * @see [CollectionInteractor.onRemoveCollectionsPlaceholder]
     */
    fun handleRemoveCollectionsPlaceholder()

    /**
     * @see [MessageCardInteractor.onMessageClicked]
     */
    fun handleMessageClicked(message: Message)

    /**
     * @see [MessageCardInteractor.onMessageClosedClicked]
     */
    fun handleMessageClosed(message: Message)


    fun handleCustomizeHomeTapped()

    /**
     * @see [WallpaperInteractor.showWallpapersOnboardingDialog]
     */
    fun handleShowWallpapersOnboardingDialog(state: WallpaperState): Boolean


}

@Suppress("TooManyFunctions", "LargeClass", "LongParameterList")
class DefaultSessionControlController(
    private val activity: HomeActivity,
    private val settings: Settings,
    private val engine: Engine,
    private val messageController: MessageController,
    private val store: BrowserStore,
    // private val tabCollectionStorage: TabCollectionStorage,
    private val addTabUseCase: TabsUseCases.AddNewTabUseCase,
    private val restoreUseCase: TabsUseCases.RestoreUseCase,
    private val reloadUrlUseCase: SessionUseCases.ReloadUrlUseCase,
    private val selectTabUseCase: TabsUseCases.SelectTabUseCase,
    private val appStore: AppStore,
    private val navController: NavController,
    private val viewLifecycleScope: CoroutineScope,
    private val removeCollectionWithUndo: (tabCollection: TabCollection) -> Unit,
    private val showUndoSnackbarForTopSite: (topSite: TopSite) -> Unit,
    private val showTabTray: () -> Unit,
) : SessionControlController {

    override fun handleCollectionAddTabTapped(collection: TabCollection) {

        showCollectionCreationFragment(
            step = SaveCollectionStep.SelectTabs,
            selectedTabCollectionId = collection.id,
        )
    }

    override fun handleCollectionOpenTabClicked(tab: ComponentTab) {
        restoreUseCase.invoke(
            activity.filesDir,
            engine,
            tab,
            onTabRestored = {
                activity.openToBrowser(BrowserDirection.FromHome)
                selectTabUseCase.invoke(it)
                reloadUrlUseCase.invoke(it)
            },
            onFailure = {
                activity.openToBrowserAndLoad(
                    searchTermOrURL = tab.url,
                    newTab = true,
                    from = BrowserDirection.FromHome,
                )
            },
        )


    }

    override fun handleCollectionOpenTabsTapped(collection: TabCollection) {
        restoreUseCase.invoke(
            activity.filesDir,
            engine,
            collection,
            onFailure = { url ->
                addTabUseCase.invoke(url)
            },
        )

        showTabTray()

    }

    override fun handleCollectionRemoveTab(
        collection: TabCollection,
        tab: ComponentTab,
    ) {


        if (collection.tabs.size == 1) {
            removeCollectionWithUndo(collection)
        } else {
            viewLifecycleScope.launch {
                // tabCollectionStorage.removeTabFromCollection(collection, tab)
            }
        }
    }

    override fun handleCollectionShareTabsClicked(collection: TabCollection) {
        showShareFragment(
            collection.title,
            collection.tabs.map { ShareData(url = it.url, title = it.title) },
        )
    }

    override fun handleDeleteCollectionTapped(collection: TabCollection) {
        removeCollectionWithUndo(collection)
    }

   override fun handleOpenInPrivateTabClicked(topSite: TopSite) {
     activity.openToBrowserAndLoad(
        searchTermOrURL = topSite.url,
        newTab = true,
        from = BrowserDirection.FromHome,
    )
}

    @SuppressLint("InflateParams")
    override fun handleRenameTopSiteClicked(topSite: TopSite) {
        activity.let {
            val customLayout =
                LayoutInflater.from(it).inflate(R.layout.top_sites_rename_dialog, null)
            val topSiteLabelEditText: EditText =
                customLayout.findViewById(R.id.top_site_title)
            topSiteLabelEditText.setText(topSite.title)

            AlertDialog.Builder(it).apply {
                setTitle(R.string.rename_top_site)
                setView(customLayout)
                setPositiveButton(R.string.top_sites_rename_dialog_ok) { dialog, _ ->
                    viewLifecycleScope.launch(Dispatchers.IO) {
                        with(activity.components.useCases.topSitesUseCase) {
                            updateTopSites(
                                topSite,
                                topSiteLabelEditText.text.toString(),
                                topSite.url,
                            )
                        }
                    }
                    dialog.dismiss()
                }
                setNegativeButton(R.string.top_sites_rename_dialog_cancel) { dialog, _ ->
                    dialog.cancel()
                }
            }.show().withCenterAlignedButtons().also {
                topSiteLabelEditText.setSelection(0, topSiteLabelEditText.text.length)
                topSiteLabelEditText.showKeyboard()
            }
        }
    }

    override fun handleRemoveTopSiteClicked(topSite: TopSite) {


        viewLifecycleScope.launch(Dispatchers.IO) {
            with(activity.components.useCases.topSitesUseCase) {
                removeTopSites(topSite)
            }
        }

        showUndoSnackbarForTopSite(topSite)
    }

    override fun handleRenameCollectionTapped(collection: TabCollection) {
        showCollectionCreationFragment(
            step = SaveCollectionStep.RenameCollection,
            selectedTabCollectionId = collection.id,
        )
    }

    override fun handleSelectTopSite(topSite: TopSite, position: Int) {

      //  val tabId = addTabUseCase.invoke(
         //   url = (topSite.url),
          //  selectTab = true,
           // startLoading = true,
       // )
        
        activity.openToBrowserAndLoad(
                searchTermOrURL = (topSite.url),
                newTab = false,
                from = BrowserDirection.FromHome,
            )
        
        if (settings.openNextTabInDesktopMode) {
        	val tabId = addTabUseCase.invoke(
         url = (topSite.url),
          selectTab = true,
            startLoading = true,
       )
        	
            activity.handleRequestDesktopMode(tabId)
        }
        // activity.openToBrowser(BrowserDirection.FromHome)

    }

    override fun handleTopSiteSettingsClicked() {
        val directions = HomeFragmentDirections.actionLoginDetailFragmentToSavedLogins()
        navController.nav(navController.currentDestination?.id, directions)
    }

    override fun handleSponsorPrivacyClicked() {
        activity.openToBrowserAndLoad(
            searchTermOrURL = SupportUtils.getGenericSumoURLForTopic(SupportUtils.SumoTopic.SPONSOR_PRIVACY),
            newTab = false,
            from = BrowserDirection.FromHome,
        )
    }

    override fun handleTopSiteLongClicked(topSite: TopSite) {
    }

    @VisibleForTesting
    internal fun getAvailableSearchEngines() =
        activity.components.core.store.state.search.searchEngines +
                activity.components.core.store.state.search.availableSearchEngines

    /**
     * Append a search attribution query to any provided search engine URL based on the
     * user's current region.
     */


    override fun handleCustomizeHomeTapped() {
        val directions = HomeFragmentDirections.actionGlobalSettingsFragment()
        navController.nav(navController.currentDestination?.id, directions)
    }

    override fun handleShowWallpapersOnboardingDialog(state: WallpaperState): Boolean {
        return if (appStore.state.mode.isPrivate) {
            false
        } else {
            state.availableWallpapers.filter { wallpaper ->
                wallpaper.thumbnailFileState == Wallpaper.ImageFileState.Downloaded
            }.size.let { downloadedCount ->
                downloadedCount >= THUMBNAILS_SELECTION_COUNT
            }.also { showOnboarding ->
                if (showOnboarding) {
                    navController.nav(
                        R.id.homeFragment,
                        HomeFragmentDirections.actionGlobalWallpaperOnboardingDialog(),
                    )
                }
            }
        }
    }
    override fun handleToggleCollectionExpanded(collection: TabCollection, expand: Boolean) {
        appStore.dispatch(AppAction.CollectionExpanded(collection, expand))
    }

    private fun showTabTrayCollectionCreation() {
        val directions = HomeFragmentDirections.actionGlobalTabsTrayFragment(
            enterMultiselect = true,
        )
        navController.nav(R.id.homeFragment, directions)
    }

    private fun showCollectionCreationFragment(
        step: SaveCollectionStep,
        selectedTabIds: Array<String>? = null,
        selectedTabCollectionId: Long? = null,
    ) {
        if (navController.currentDestination?.id == R.id.collectionCreationFragment) return

        val tabIds = store.state
            .getNormalOrPrivateTabs(private = appStore.state.mode.isPrivate)
            .map { session -> session.id }
            .toList()
            .toTypedArray()
        val directions = HomeFragmentDirections.actionGlobalCollectionCreationFragment(
            tabIds = tabIds,
            saveCollectionStep = step,
            selectedTabIds = selectedTabIds,
            selectedTabCollectionId = selectedTabCollectionId ?: -1,
        )
        navController.nav(R.id.homeFragment, directions)
    }

    override fun handleCreateCollection() {
        showTabTrayCollectionCreation()
    }

    override fun handleRemoveCollectionsPlaceholder() {
        settings.showCollectionsPlaceholderOnHome = false
        appStore.dispatch(AppAction.RemoveCollectionsPlaceholder)
    }

    private fun showShareFragment(shareSubject: String, data: List<ShareData>) {
        val directions = HomeFragmentDirections.actionGlobalShareFragment(
            sessionId = store.state.selectedTabId,
            shareSubject = shareSubject,
            data = data.toTypedArray(),
        )
        navController.nav(R.id.homeFragment, directions)
    }

    override fun handleMessageClicked(message: Message) {
        messageController.onMessagePressed(message)
    }

    override fun handleMessageClosed(message: Message) {
        messageController.onMessageDismissed(message)
    }

    //override fun handleReportSessionMetrics(state: AppState) {
    // }
}
