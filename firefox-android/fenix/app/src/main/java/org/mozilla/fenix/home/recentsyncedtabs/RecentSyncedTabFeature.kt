/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.recentsyncedtabs

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mozilla.components.browser.storage.sync.Tab
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.concept.sync.Device
import mozilla.components.concept.sync.DeviceType
import mozilla.components.feature.syncedtabs.storage.SyncedTabsStorage
import mozilla.components.lib.state.ext.flow
import mozilla.components.service.fxa.SyncEngine
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.service.fxa.manager.SyncEnginesStorage
import mozilla.components.service.fxa.manager.ext.withConstellation
import mozilla.components.service.fxa.store.SyncStatus
import mozilla.components.service.fxa.store.SyncStore
import mozilla.components.service.fxa.sync.SyncReason
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl
//import mozilla.telemetry.glean.GleanTimerId
//import org.mozilla.fenix.GleanMetrics.RecentSyncedTabs
import org.mozilla.fenix.components.AppStore
import org.mozilla.fenix.components.appstate.AppAction
import java.util.concurrent.TimeUnit


@Suppress("LongParameterList")
class RecentSyncedTabFeature(
    private val context: Context,
    private val appStore: AppStore,
    private val syncStore: SyncStore,
    private val storage: SyncedTabsStorage,
    private val accountManager: FxaAccountManager,
    private val historyStorage: HistoryStorage,
    private val coroutineScope: CoroutineScope,
) : LifecycleAwareFeature {

    private var lastSyncedTabs: List<RecentSyncedTab>? = null

    override fun start() {
        collectAccountUpdates()
        collectStatusUpdates()
    }

    override fun stop() = Unit

    private fun collectAccountUpdates() {
        syncStore.flow()
            .distinctUntilChangedBy { state ->
                state.account != null
            }.onEach { state ->
                if (state.account != null) {
                    dispatchLoading()
                  
                    accountManager.withConstellation { refreshDevices() }
                    accountManager.syncNow(
                        reason = SyncReason.User,
                        debounce = true,
                        customEngineSubset = listOf(SyncEngine.Tabs),
                    )
                }
            }.launchIn(coroutineScope)
    }

    private fun collectStatusUpdates() {
        syncStore.flow()
            .distinctUntilChangedBy { state ->
                state.status
            }.onEach { state ->
                when (state.status) {
                    SyncStatus.Idle -> dispatchSyncedTabs()
                    SyncStatus.Error -> onError()
                    SyncStatus.LoggedOut -> appStore.dispatch(
                        AppAction.RecentSyncedTabStateChange(RecentSyncedTabState.None),
                    )
                    else -> Unit
                }
            }.launchIn(coroutineScope)
    }

    private fun dispatchLoading() {
        if (appStore.state.recentSyncedTabState == RecentSyncedTabState.None) {
            appStore.dispatch(AppAction.RecentSyncedTabStateChange(RecentSyncedTabState.Loading))
        }
    }

    private suspend fun dispatchSyncedTabs() {
        if (!isSyncedTabsEngineEnabled()) {
            appStore.dispatch(
                AppAction.RecentSyncedTabStateChange(RecentSyncedTabState.None),
            )
            return
        }

        val syncedTabs = storage.getSyncedDeviceTabs()
            .filterNot { it.device.isCurrentDevice || it.tabs.isEmpty() }
            .flatMap {
                it.tabs.map { tab ->
                    SyncedDeviceTab(it.device, tab)
                }
            }
            .ifEmpty { return }
            .sortedByDescending { deviceTab -> deviceTab.tab.lastUsed }
            .take(MAX_RECENT_SYNCED_TABS)
            .map { deviceTab ->
                val activeTabEntry = deviceTab.tab.active()

                val currentTime = System.currentTimeMillis()
                val maxAgeInMs = TimeUnit.DAYS.toMillis(DAYS_HISTORY_FOR_PREVIEW_IMAGE)
                val history = historyStorage.getDetailedVisits(
                    start = currentTime - maxAgeInMs,
                    end = currentTime,
                )

                val previewImageUrl = history.find { entry ->
                    entry.url.contains(activeTabEntry.url.tryGetHostFromUrl()) && entry.previewImageUrl != null
                }?.previewImageUrl

                RecentSyncedTab(
                    deviceDisplayName = deviceTab.device.displayName,
                    deviceType = deviceTab.device.deviceType,
                    title = activeTabEntry.title,
                    url = activeTabEntry.url,
                    previewImageUrl = previewImageUrl,
                )
            }

        if (syncedTabs.isEmpty()) {
            appStore.dispatch(
                AppAction.RecentSyncedTabStateChange(RecentSyncedTabState.None),
            )
        } else {
            appStore.dispatch(
                AppAction.RecentSyncedTabStateChange(RecentSyncedTabState.Success(syncedTabs)),
            )
            lastSyncedTabs = syncedTabs
        }
    }

    private fun onError() {
        if (appStore.state.recentSyncedTabState == RecentSyncedTabState.Loading) {
            appStore.dispatch(AppAction.RecentSyncedTabStateChange(RecentSyncedTabState.None))
        }
    }

    private fun isSyncedTabsEngineEnabled(): Boolean {
        return SyncEnginesStorage(context).getStatus()[SyncEngine.Tabs] ?: true
    }

    companion object {
        const val DAYS_HISTORY_FOR_PREVIEW_IMAGE = 3L
        const val MAX_RECENT_SYNCED_TABS = 8
    }
}

sealed class RecentSyncedTabState {
    object None : RecentSyncedTabState()
    object Loading : RecentSyncedTabState()
    data class Success(val tabs: List<RecentSyncedTab>) : RecentSyncedTabState()
}

data class RecentSyncedTab(
    val deviceDisplayName: String,
    val deviceType: DeviceType,
    val title: String,
    val url: String,
    val previewImageUrl: String?,
)

private data class SyncedDeviceTab(
    val device: Device,
    val tab: Tab,
)
