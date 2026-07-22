/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix

//import android.net.Uri
//import android.os.SystemClock
//import kotlinx.coroutines.Deferred
//import kotlinx.coroutines.async
//import mozilla.components.concept.storage.FrecencyThresholdOption
//import mozilla.components.feature.top.sites.TopSitesFrecencyConfig
//import mozilla.components.lib.crash.CrashReporter
//import mozilla.components.service.glean.Glean
//import mozilla.components.service.glean.config.Configuration
//import mozilla.components.service.glean.net.ConceptFetchHttpUploader
//import mozilla.components.support.rusterrors.initializeRustErrors
//import mozilla.components.support.rusthttp.RustHttpConfig
//import mozilla.components.support.rustlog.RustLog
//import org.mozilla.fenix.GleanMetrics.CustomizeHome
//import org.mozilla.fenix.GleanMetrics.GleanBuildInfo
//import org.mozilla.fenix.GleanMetrics.PerfStartup
//import org.mozilla.fenix.experiments.maybeFetchExperiments
//import org.mozilla.fenix.ext.containsQueryParameters
//import org.mozilla.fenix.ext.getCustomGleanServerUrlIfAvailable
//import org.mozilla.fenix.ext.setCustomEndpointIfAvailable
//import org.mozilla.fenix.nimbus.FxNimbus
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.StrictMode
import android.util.Log.INFO
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration.Builder
import androidx.work.Configuration.Provider
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
//import mozilla.appservices.Megazord
import mozilla.components.browser.state.action.SystemAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.storage.sync.GlobalPlacesDependencyProvider
//import mozilla.components.concept.base.crash.Breadcrumb
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.concept.engine.webextension.isUnsupported
import mozilla.components.concept.push.PushProcessor
import mozilla.components.feature.addons.migration.DefaultSupportedAddonsChecker
import mozilla.components.feature.addons.update.GlobalAddonDependencyProvider
//import mozilla.components.feature.fxsuggest.GlobalFxSuggestDependencyProvider
import mozilla.components.feature.top.sites.TopSitesProviderConfig
import mozilla.components.support.base.facts.register
//import mozilla.components.support.base.log.Log
//import mozilla.components.support.base.log.logger.Logger
//import mozilla.components.support.base.log.sink.AndroidLogSink
import mozilla.components.support.ktx.android.arch.lifecycle.addObservers
import mozilla.components.support.ktx.android.content.isMainProcess
import mozilla.components.support.ktx.android.content.runOnlyInMainProcess
import mozilla.components.support.locale.LocaleAwareApplication
import mozilla.components.support.utils.BrowsersCache
//import mozilla.components.support.utils.logElapsedTime
import mozilla.components.support.webextensions.WebExtensionSupport
import org.mozilla.fenix.components.Components
import org.mozilla.fenix.components.Core
//import org.mozilla.fenix.components.appstate.AppAction
//import org.mozilla.fenix.components.metrics.MetricServiceType
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.lifecycle.StoreLifecycleObserver
import org.mozilla.fenix.perf.MarkersActivityLifecycleCallbacks
import org.mozilla.fenix.perf.ProfilerMarkerFactProcessor
import org.mozilla.fenix.perf.StartupTimeline
//import org.mozilla.fenix.perf.StorageStatsMetrics
import org.mozilla.fenix.push.PushFxaIntegration
import org.mozilla.fenix.push.WebPushEngineIntegration
import org.mozilla.fenix.session.PerformanceActivityLifecycleCallbacks
import org.mozilla.fenix.session.VisibilityLifecycleCallback
import org.mozilla.fenix.utils.Settings.Companion.TOP_SITES_PROVIDER_MAX_THRESHOLD
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

//private const val RAM_THRESHOLD_MEGABYTES = 1000024
//private const val BYTES_TO_MEGABYTES_CONVERSION = 1024.0 * 1024.0


@Suppress("Registered", "TooManyFunctions", "LargeClass")
open class FenixApplication : LocaleAwareApplication(), Provider {
    init {
        recordOnInit()
    }


   // internal val isDeviceRamAboveThreshold: Boolean by lazy {
   // false
//}


    open val components by lazy { Components(this) }

    var visibilityLifecycleCallback: VisibilityLifecycleCallback? = null
        private set

    override fun onCreate() {
        super.onCreate()

        initialize()
    }

    fun initialize() {


       // setupInAllProcesses()

        if (!isMainProcess()) {

            return
        }

        setupInMainProcessOnly()

    }

  //  @VisibleForTesting
  //  protected open fun setupInAllProcesses() {
        // setupCrashReporting()

      //  Log.addSink(FenixLogSink(logsDebug = Config.channel.isDebug, AndroidLogSink()))
   // }

    @VisibleForTesting
    protected open fun setupInMainProcessOnly() {


        ProfilerMarkerFactProcessor.create { components.core.engine.profiler }.register()

        run {
            components.strictMode.resetAfter(StrictMode.allowThreadDiskReads()) {
                components.core.engine.warmUp()
            }



            setDayNightTheme()
            components.strictMode.enableStrictMode(true)
            warmBrowsersCache()

            initializeWebExtensionSupport()


            GlobalPlacesDependencyProvider.initialize(components.core.historyStorage)

            restoreBrowserState()


        }

       // setupLeakCanary()
       // startMetricsIfEnabled()
        setupPush()

       // GlobalFxSuggestDependencyProvider.initialize(components.fxSuggest.storage)

        visibilityLifecycleCallback = VisibilityLifecycleCallback(getSystemService())
        registerActivityLifecycleCallbacks(visibilityLifecycleCallback)
        registerActivityLifecycleCallbacks(MarkersActivityLifecycleCallbacks(components.core.engine))

        components.appStartReasonProvider.registerInAppOnCreate(this)
        //components.startupActivityLog.registerInAppOnCreate(this)
        initVisualCompletenessQueueAndQueueTasks()

        ProcessLifecycleOwner.get().lifecycle.addObservers(
            StoreLifecycleObserver(
                appStore = components.appStore,
                browserStore = components.core.store,
            ),
        )

        //components.analytics.metricsStorage.tryRegisterAsUsageRecorder(this)

      //  downloadWallpapers()
    }

    @OptIn(DelicateCoroutinesApi::class) // GlobalScope usage
    private fun restoreBrowserState() = GlobalScope.launch(Dispatchers.Main) {
        val store = components.core.store
        val sessionStorage = components.core.sessionStorage

        components.useCases.tabsUseCases.restore(sessionStorage, settings().getTabTimeout())
        sessionStorage.autoSave(store)
            .periodicallyInForeground(interval = 30, unit = TimeUnit.SECONDS)
            .whenGoingToBackground()
            .whenSessionsChange()
    }

    private fun initVisualCompletenessQueueAndQueueTasks() {
        val queue = components.performance.visualCompletenessQueue.queue

        fun initQueue() {
            registerActivityLifecycleCallbacks(PerformanceActivityLifecycleCallbacks(queue))
        }

        @OptIn(DelicateCoroutinesApi::class) // GlobalScope usage
        fun queueInitStorageAndServices() {
            components.performance.visualCompletenessQueue.queue.runIfReadyOrQueue {
                GlobalScope.launch(IO) {

                    components.core.historyStorage.warmUp()
                    components.core.bookmarksStorage.warmUp()
                    components.core.passwordsStorage.warmUp()
                    components.core.autofillStorage.warmUp()
                    components.core.topSitesStorage.getTopSites(
                        totalSites = components.settings.topSitesMaxLimit,

                        providerConfig = TopSitesProviderConfig(
                            showProviderTopSites = components.settings.showContileFeature,
                            maxThreshold = TOP_SITES_PROVIDER_MAX_THRESHOLD,
                        ),
                    )
                    components.core.historyMetadataService.cleanup(
                        System.currentTimeMillis() - Core.HISTORY_METADATA_MAX_AGE_IN_MS,
                    )
                    

                }
                GlobalScope.launch(Dispatchers.Main) {
                    components.backgroundServices.accountManager
                }
            }
        }


        @OptIn(DelicateCoroutinesApi::class) // GlobalScope usage
        fun queueReviewPrompt() {
            GlobalScope.launch(IO) {
                components.reviewPromptController.trackApplicationLaunch()
            }
        }

        @OptIn(DelicateCoroutinesApi::class)
        fun queueRestoreLocale() {
            components.performance.visualCompletenessQueue.queue.runIfReadyOrQueue {
                GlobalScope.launch(IO) {
                    components.useCases.localeUseCases.restore()
                }
            }
        }

        fun queueStorageMaintenance() {
            queue.runIfReadyOrQueue {
                components.core.historyStorage.registerStorageMaintenanceWorker()
            }
        }

        initQueue()
        queueInitStorageAndServices()
        queueReviewPrompt()
        queueRestoreLocale()
        queueStorageMaintenance()
    }

   

    private fun setupPush() {

        components.push.feature?.let {

            PushProcessor.install(it)

            WebPushEngineIntegration(components.core.engine, it).start()

            PushFxaIntegration(it, lazy { components.backgroundServices.accountManager }).launch()

            it.initialize()
        }
    }
    
    
  //  private fun setupCrashReporting() {
  
         // no-op, LeakCanary is disabled by default
   // }
    
    
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        runOnlyInMainProcess {
            components.core.icons.onTrimMemory(level)
            components.core.store.dispatch(SystemAction.LowMemoryAction(level))
        }
    }

    @SuppressLint("WrongConstant")

    private fun setDayNightTheme() {
        val settings = this.settings()
        when {
            settings.shouldUseLightTheme -> {
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_NO,
                )
            }
            settings.shouldUseDarkTheme -> {
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_YES,
                )
            }
            SDK_INT < Build.VERSION_CODES.P && settings.shouldUseAutoBatteryTheme -> {
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY,
                )
            }
            SDK_INT >= Build.VERSION_CODES.P && settings.shouldFollowDeviceTheme -> {
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                )
            }

            else -> {
                if (SDK_INT >= Build.VERSION_CODES.P) {
                    AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                    )
                    settings.shouldFollowDeviceTheme = true
                } else {
                    AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_NO,
                    )
                    settings.shouldUseLightTheme = true
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun warmBrowsersCache() {

        GlobalScope.launch(Dispatchers.Default) {
            BrowsersCache.all(this@FenixApplication)
        }
    }

    private fun initializeWebExtensionSupport() {
        try {
            GlobalAddonDependencyProvider.initialize(
                components.addonManager,
                components.addonUpdater,
               
            )
            WebExtensionSupport.initialize(
                components.core.engine,
                components.core.store,
                onNewTabOverride = { _, engineSession, url ->
                    val shouldCreatePrivateSession =
                        components.core.store.state.selectedTab?.content?.private
                            ?: components.settings.openLinksInAPrivateTab

                    components.useCases.tabsUseCases.addTab(
                        url = url,
                        selectTab = true,
                        engineSession = engineSession,
                        private = shouldCreatePrivateSession,
                    )
                },
                onCloseTabOverride = { _, sessionId ->
                    components.useCases.tabsUseCases.removeTab(sessionId)
                },
                onSelectTabOverride = { _, sessionId ->
                    components.useCases.tabsUseCases.selectTab(sessionId)
                },
                
                onUpdatePermissionRequest = components.addonUpdater::onUpdatePermissionRequest,
            )
        } catch (e: UnsupportedOperationException) {
        }
    }

   



   //private fun isDeviceRamAboveThreshold(): Boolean {
   // return false
//}


    protected fun recordOnInit() {

        StartupTimeline.onApplicationInit()
    }

    override fun onConfigurationChanged(config: android.content.res.Configuration) {

        applicationContext.resources.configuration.uiMode = config.uiMode

        if (isMainProcess()) {

            components.strictMode.resetAfter(StrictMode.allowThreadDiskReads()) {
                super.onConfigurationChanged(config)
            }
        } else {
            super.onConfigurationChanged(config)
        }
    }

    //override fun getWorkManagerConfiguration() = Builder().setMinimumLoggingLevel(INFO).build()
    override val workManagerConfiguration = Builder().setMinimumLoggingLevel(INFO).build()

    @OptIn(DelicateCoroutinesApi::class)
    open fun downloadWallpapers() {
        // no-op, LeakCanary is disabled by default
    }


}
