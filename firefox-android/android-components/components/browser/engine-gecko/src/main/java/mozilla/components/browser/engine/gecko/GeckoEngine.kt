/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.engine.gecko

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.util.JsonReader
import androidx.annotation.VisibleForTesting
import mozilla.components.browser.engine.gecko.activity.GeckoActivityDelegate
import mozilla.components.browser.engine.gecko.activity.GeckoScreenOrientationDelegate
import mozilla.components.browser.engine.gecko.ext.getAntiTrackingPolicy
import mozilla.components.browser.engine.gecko.ext.getEtpLevel
import mozilla.components.browser.engine.gecko.ext.getStrictSocialTrackingProtection
import mozilla.components.browser.engine.gecko.integration.LocaleSettingUpdater
import mozilla.components.browser.engine.gecko.mediaquery.from
import mozilla.components.browser.engine.gecko.mediaquery.toGeckoValue
import mozilla.components.browser.engine.gecko.profiler.Profiler
import mozilla.components.browser.engine.gecko.serviceworker.GeckoServiceWorkerDelegate
import mozilla.components.browser.engine.gecko.translate.GeckoTranslationUtils.intoTranslationError
import mozilla.components.browser.engine.gecko.util.SpeculativeSessionFactory
import mozilla.components.browser.engine.gecko.webextension.GeckoWebExtension
import mozilla.components.browser.engine.gecko.webextension.GeckoWebExtensionException
import mozilla.components.browser.engine.gecko.webnotifications.GeckoWebNotificationDelegate
import mozilla.components.browser.engine.gecko.webpush.GeckoWebPushDelegate
import mozilla.components.browser.engine.gecko.webpush.GeckoWebPushHandler
import mozilla.components.concept.engine.CancellableOperation
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineSession.CookieBannerHandlingMode
import mozilla.components.concept.engine.EngineSession.SafeBrowsingPolicy
import mozilla.components.concept.engine.EngineSession.TrackingProtectionPolicy
import mozilla.components.concept.engine.EngineSession.TrackingProtectionPolicy.TrackingCategory
import mozilla.components.concept.engine.EngineSessionState
import mozilla.components.concept.engine.EngineView
import mozilla.components.concept.engine.Settings
import mozilla.components.concept.engine.activity.ActivityDelegate
import mozilla.components.concept.engine.activity.OrientationDelegate
import mozilla.components.concept.engine.content.blocking.TrackerLog
import mozilla.components.concept.engine.content.blocking.TrackingProtectionExceptionStorage
import mozilla.components.concept.engine.history.HistoryTrackingDelegate
import mozilla.components.concept.engine.mediaquery.PreferredColorScheme
import mozilla.components.concept.engine.serviceworker.ServiceWorkerDelegate
import mozilla.components.concept.engine.translate.Language
import mozilla.components.concept.engine.translate.LanguageModel
import mozilla.components.concept.engine.translate.LanguageSetting
import mozilla.components.concept.engine.translate.ModelManagementOptions
import mozilla.components.concept.engine.translate.TranslationError
import mozilla.components.concept.engine.translate.TranslationSupport
import mozilla.components.concept.engine.translate.TranslationsRuntime
import mozilla.components.concept.engine.utils.EngineVersion
import mozilla.components.concept.engine.webextension.Action
import mozilla.components.concept.engine.webextension.ActionHandler
import mozilla.components.concept.engine.webextension.EnableSource
import mozilla.components.concept.engine.webextension.InstallationMethod
import mozilla.components.concept.engine.webextension.TabHandler
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.concept.engine.webextension.WebExtensionDelegate
import mozilla.components.concept.engine.webextension.WebExtensionInstallException
import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import mozilla.components.concept.engine.webnotifications.WebNotificationDelegate
import mozilla.components.concept.engine.webpush.WebPushDelegate
import mozilla.components.concept.engine.webpush.WebPushHandler
import mozilla.components.support.ktx.kotlin.isResourceUrl
import mozilla.components.support.utils.ThreadUtils
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.ContentBlockingController
import org.mozilla.geckoview.ContentBlockingController.Event
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.TranslationsController
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.WebNotification
import java.lang.ref.WeakReference

typealias NativePermissionPromptResponse = org.mozilla.geckoview.WebExtension.PermissionPromptResponse


@Suppress("LargeClass", "TooManyFunctions")
class GeckoEngine(
    context: Context,
    private val defaultSettings: Settings? = null,
    private val runtime: GeckoRuntime = GeckoRuntime.getDefault(context),
    executorProvider: () -> GeckoWebExecutor = { GeckoWebExecutor(runtime) },
    override val trackingProtectionExceptionStore: TrackingProtectionExceptionStorage =
        GeckoTrackingProtectionExceptionStorage(runtime),
) : Engine, WebExtensionRuntime, TranslationsRuntime {
    private val executor by lazy { executorProvider.invoke() }
    private val localeUpdater = LocaleSettingUpdater(context, runtime)

    @VisibleForTesting internal val speculativeConnectionFactory = SpeculativeSessionFactory()
    private var webExtensionDelegate: WebExtensionDelegate? = null
    private val webExtensionActionHandler = object : ActionHandler {
        override fun onBrowserAction(extension: WebExtension, session: EngineSession?, action: Action) {
            webExtensionDelegate?.onBrowserActionDefined(extension, action)
        }

        override fun onPageAction(extension: WebExtension, session: EngineSession?, action: Action) {
            webExtensionDelegate?.onPageActionDefined(extension, action)
        }

        override fun onToggleActionPopup(extension: WebExtension, action: Action): EngineSession? {
            return webExtensionDelegate?.onToggleActionPopup(
                extension,
                GeckoEngineSession(
                    runtime,
                    defaultSettings = defaultSettings,
                ),
                action,
            )
        }
    }
    private val webExtensionTabHandler = object : TabHandler {
        override fun onNewTab(webExtension: WebExtension, engineSession: EngineSession, active: Boolean, url: String) {
            webExtensionDelegate?.onNewTab(webExtension, engineSession, active, url)
        }
    }

    private var webPushHandler: WebPushHandler? = null

    init {
        runtime.delegate = GeckoRuntime.Delegate {
            
            @Suppress("TooGenericExceptionThrown")
            throw RuntimeException("GeckoRuntime is shutting down")
        }
    }

       override fun getTrackersLog(
        session: EngineSession,
        onSuccess: (List<TrackerLog>) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val geckoSession = (session as GeckoEngineSession).geckoSession
        runtime.contentBlockingController.getLog(geckoSession).then(
            { contentLogList ->
                val list = contentLogList ?: emptyList()
                val logs = list.map { logEntry ->
                    logEntry.toTrackerLog()
                }.filterNot {
                    !it.cookiesHasBeenBlocked &&
                        it.blockedCategories.isEmpty() &&
                        it.loadedCategories.isEmpty()
                }

                onSuccess(logs)
                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable)
                GeckoResult<Void>()
            },
        )
    }

   
    override fun createView(context: Context, attrs: AttributeSet?): EngineView {
        return GeckoEngineView(context, attrs).apply {
            setColorScheme(settings.preferredColorScheme)
        }
    }

    
    override fun createSession(private: Boolean, contextId: String?): EngineSession {
        ThreadUtils.assertOnUiThread()
        val speculativeSession = speculativeConnectionFactory.get(private, contextId)
        return speculativeSession ?: GeckoEngineSession(runtime, private, defaultSettings, contextId)
    }

   
    override fun createSessionState(json: JSONObject): EngineSessionState {
        return GeckoEngineSessionState.fromJSON(json)
    }

   
    override fun createSessionStateFrom(reader: JsonReader): EngineSessionState {
        return GeckoEngineSessionState.from(reader)
    }

    
    override fun speculativeCreateSession(private: Boolean, contextId: String?) {
        ThreadUtils.assertOnUiThread()
        speculativeConnectionFactory.create(runtime, private, contextId, defaultSettings)
    }

   
    override fun clearSpeculativeSession() {
        speculativeConnectionFactory.clear()
    }

   
    override fun speculativeConnect(url: String) {
        executor.speculativeConnect(url)
    }

    
    override fun installBuiltInWebExtension(
        id: String,
        url: String,
        onSuccess: ((WebExtension) -> Unit),
        onError: ((Throwable) -> Unit),
    ): CancellableOperation {
        require(url.isResourceUrl()) { "url should be a resource url" }

        val geckoResult = runtime.webExtensionController.ensureBuiltIn(url, id).apply {
            then(
                {
                    onExtensionInstalled(it!!, onSuccess)
                    GeckoResult<Void>()
                },
                { throwable ->
                    onError(GeckoWebExtensionException.createWebExtensionException(throwable))
                    GeckoResult<Void>()
                },
            )
        }
        return geckoResult.asCancellableOperation()
    }

    
    override fun installWebExtension(
        url: String,
        installationMethod: InstallationMethod?,
        onSuccess: ((WebExtension) -> Unit),
        onError: ((Throwable) -> Unit),
    ): CancellableOperation {
        require(!url.isResourceUrl()) { "url shouldn't be a resource url" }

        val geckoResult = runtime.webExtensionController.install(
            url,
            installationMethod?.toGeckoInstallationMethod(),
        ).apply {
            then(
                {
                    onExtensionInstalled(it!!, onSuccess)
                    GeckoResult<Void>()
                },
                { throwable ->
                    onError(GeckoWebExtensionException.createWebExtensionException(throwable))
                    GeckoResult<Void>()
                },
            )
        }
        return geckoResult.asCancellableOperation()
    }

    
    override fun uninstallWebExtension(
        ext: WebExtension,
        onSuccess: () -> Unit,
        onError: (String, Throwable) -> Unit,
    ) {
        runtime.webExtensionController.uninstall((ext as GeckoWebExtension).nativeExtension).then(
            {
                onSuccess()
                GeckoResult<Void>()
            },
            { throwable ->
                onError(ext.id, throwable)
                GeckoResult<Void>()
            },
        )
    }

   
    override fun updateWebExtension(
        extension: WebExtension,
        onSuccess: (WebExtension?) -> Unit,
        onError: (String, Throwable) -> Unit,
    ) {
        runtime.webExtensionController.update((extension as GeckoWebExtension).nativeExtension).then(
            { geckoExtension ->
                val updatedExtension = if (geckoExtension != null) {
                    GeckoWebExtension(geckoExtension, runtime).also {
                        it.registerActionHandler(webExtensionActionHandler)
                        it.registerTabHandler(webExtensionTabHandler, defaultSettings)
                    }
                } else {
                    null
                }
                onSuccess(updatedExtension)
                GeckoResult<Void>()
            },
            { throwable ->
                onError(extension.id, GeckoWebExtensionException(throwable))
                GeckoResult<Void>()
            },
        )
    }

    
   @Suppress("Deprecation")
    override fun registerWebExtensionDelegate(
        webExtensionDelegate: WebExtensionDelegate,
    ) {
        this.webExtensionDelegate = webExtensionDelegate

        val promptDelegate = object : WebExtensionController.PromptDelegate {

            override fun onInstallPromptRequest(
                ext: org.mozilla.geckoview.WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
            ): GeckoResult<NativePermissionPromptResponse>? {
                val result = GeckoResult<NativePermissionPromptResponse>()

                webExtensionDelegate.onInstallPermissionRequest(
                    GeckoWebExtension(ext, runtime),
                    // We pass both permissions and origins as a single list of
                    // permissions to be shown to the user.
                    permissions.toList() + origins.toList(),
                ) { data ->
                    result.complete(
                        NativePermissionPromptResponse(
                           // data.isPermissionsGranted,
                           // data.isPrivateModeGranted,
                           true,
                           true,
                        ),
                    )
                }

                return result
            }


            override fun onUpdatePrompt(
                current: org.mozilla.geckoview.WebExtension,
                updated: org.mozilla.geckoview.WebExtension,
                newPermissions: Array<out String>,
                newOrigins: Array<out String>,
            ): GeckoResult<AllowOrDeny>? {
                val result = GeckoResult<AllowOrDeny>()
                webExtensionDelegate.onUpdatePermissionRequest(
                    GeckoWebExtension(current, runtime),
                    GeckoWebExtension(updated, runtime),
                    newPermissions.toList() + newOrigins.toList(),
                ) { result.complete(AllowOrDeny.ALLOW)
                }
                return result
            }

            override fun onOptionalPrompt(
                extension: org.mozilla.geckoview.WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
            ): GeckoResult<AllowOrDeny>? {
                val result = GeckoResult<AllowOrDeny>()
                webExtensionDelegate.onOptionalPermissionsRequest(
                    GeckoWebExtension(extension, runtime),
                    permissions.toList() + origins.toList(),
                ){ result.complete(AllowOrDeny.ALLOW)
                }
                
                return result
            }
        }

        val debuggerDelegate = object : WebExtensionController.DebuggerDelegate {
            override fun onExtensionListUpdated() {
                webExtensionDelegate.onExtensionListUpdated()
            }
        }

        val addonManagerDelegate = object : WebExtensionController.AddonManagerDelegate {
            override fun onDisabled(extension: org.mozilla.geckoview.WebExtension) {
                webExtensionDelegate.onDisabled(GeckoWebExtension(extension, runtime))
            }

            override fun onEnabled(extension: org.mozilla.geckoview.WebExtension) {
                webExtensionDelegate.onEnabled(GeckoWebExtension(extension, runtime))
            }

            override fun onReady(extension: org.mozilla.geckoview.WebExtension) {
                webExtensionDelegate.onReady(GeckoWebExtension(extension, runtime))
            }

            override fun onUninstalled(extension: org.mozilla.geckoview.WebExtension) {
                webExtensionDelegate.onUninstalled(GeckoWebExtension(extension, runtime))
            }

            override fun onInstalled(extension: org.mozilla.geckoview.WebExtension) {
                val installedExtension = GeckoWebExtension(extension, runtime)
                webExtensionDelegate.onInstalled(installedExtension)
                installedExtension.registerActionHandler(webExtensionActionHandler)
                installedExtension.registerTabHandler(webExtensionTabHandler, defaultSettings)
            }

             fun onInstallationFailed(
              url: String,
        installationMethod: InstallationMethod?,
        onSuccess: ((WebExtension) -> Unit),
       onError: ((Throwable) -> Unit),
    ): CancellableOperation {
        require(!url.isResourceUrl()) { "url shouldn't be a resource url" }

        val geckoResult = runtime.webExtensionController.install(
            url,
            installationMethod?.toGeckoInstallationMethod(),
        ).apply {
            then(
                {
                    onExtensionInstalled(it!!, onSuccess)
                    GeckoResult<Void>()
                },
                { throwable ->
                    onError(GeckoWebExtensionException.createWebExtensionException(throwable))
                    GeckoResult<Void>()
                },
            )
        }
        return geckoResult.asCancellableOperation()
    }
    
    
}

        val extensionProcessDelegate = object : WebExtensionController.ExtensionProcessDelegate {
            override fun onDisabledProcessSpawning() {
                webExtensionDelegate.onDisabledExtensionProcessSpawning()
            }
        }

        runtime.webExtensionController.setPromptDelegate(promptDelegate)
        runtime.webExtensionController.setDebuggerDelegate(debuggerDelegate)
        runtime.webExtensionController.setAddonManagerDelegate(addonManagerDelegate)
        runtime.webExtensionController.setExtensionProcessDelegate(extensionProcessDelegate)
    }

   
    override fun listInstalledWebExtensions(onSuccess: (List<WebExtension>) -> Unit, onError: (Throwable) -> Unit) {
        runtime.webExtensionController.list().then(
            {
                val extensions = it?.map {
                        extension ->
                    GeckoWebExtension(extension, runtime)
                } ?: emptyList()

                extensions.forEach { extension ->
                    extension.registerActionHandler(webExtensionActionHandler)
                    extension.registerTabHandler(webExtensionTabHandler, defaultSettings)
                }

                onSuccess(extensions)
                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable)
                GeckoResult<Void>()
            },
        )
    }

   
    override fun enableWebExtension(
        extension: WebExtension,
        source: EnableSource,
        onSuccess: (WebExtension) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        runtime.webExtensionController.enable((extension as GeckoWebExtension).nativeExtension, source.id).then(
            {
                val enabledExtension = GeckoWebExtension(it!!, runtime)
                onSuccess(enabledExtension)
                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable)
                GeckoResult<Void>()
            },
        )
    }

   
    override fun disableWebExtension(
        extension: WebExtension,
        source: EnableSource,
        onSuccess: (WebExtension) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        runtime.webExtensionController.disable((extension as GeckoWebExtension).nativeExtension, source.id).then(
            {
                val disabledExtension = GeckoWebExtension(it!!, runtime)
                onSuccess(disabledExtension)
                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable)
                GeckoResult<Void>()
            },
        )
    }

   
    override fun setAllowedInPrivateBrowsing(
        extension: WebExtension,
        allowed: Boolean,
        onSuccess: (WebExtension) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        runtime.webExtensionController.setAllowedInPrivateBrowsing(
            (extension as GeckoWebExtension).nativeExtension,
            allowed,
        ).then(
            { geckoExtension ->
                if (geckoExtension == null) {
                    onError(
                        Exception(
                            "Gecko extension was not returned after trying to" +
                                " setAllowedInPrivateBrowsing with value $allowed",
                        ),
                    )
                } else {
                    val ext = GeckoWebExtension(geckoExtension, runtime)
                    webExtensionDelegate?.onAllowedInPrivateBrowsingChanged(ext)
                    onSuccess(ext)
                }
                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable)
                GeckoResult<Void>()
            },
        )
    }

    
    override fun enableExtensionProcessSpawning() {
        runtime.webExtensionController.enableExtensionProcessSpawning()
    }

    
    override fun disableExtensionProcessSpawning() {
        runtime.webExtensionController.disableExtensionProcessSpawning()
    }

    
    override fun registerWebNotificationDelegate(
        webNotificationDelegate: WebNotificationDelegate,
    ) {
        runtime.webNotificationDelegate = GeckoWebNotificationDelegate(webNotificationDelegate)
    }

    
    override fun registerWebPushDelegate(
        webPushDelegate: WebPushDelegate,
    ): WebPushHandler {
        runtime.webPushController.setDelegate(GeckoWebPushDelegate(webPushDelegate))

        if (webPushHandler == null) {
            webPushHandler = GeckoWebPushHandler(runtime)
        }

        return requireNotNull(webPushHandler)
    }

    
    override fun registerActivityDelegate(
        activityDelegate: ActivityDelegate,
    ) {
       
        runtime.activityDelegate = GeckoActivityDelegate(WeakReference(activityDelegate))
    }

    
    override fun unregisterActivityDelegate() {
        runtime.activityDelegate = null
    }

   
    override fun registerScreenOrientationDelegate(
        delegate: OrientationDelegate,
    ) {
        runtime.orientationController.delegate = GeckoScreenOrientationDelegate(delegate)
    }

   
    override fun unregisterScreenOrientationDelegate() {
        runtime.orientationController.delegate = null
    }

    override fun registerServiceWorkerDelegate(serviceWorkerDelegate: ServiceWorkerDelegate) {
        runtime.serviceWorkerDelegate = GeckoServiceWorkerDelegate(
            delegate = serviceWorkerDelegate,
            runtime = runtime,
            engineSettings = defaultSettings,
        )
    }

    override fun unregisterServiceWorkerDelegate() {
        runtime.serviceWorkerDelegate = null
    }

    override fun handleWebNotificationClick(webNotification: Parcelable) {
        (webNotification as? WebNotification)?.click()
    }

   
    override fun clearData(
        data: Engine.BrowsingData,
        host: String?,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val flags = data.types.toLong()
        if (host != null) {
            runtime.storageController.clearDataFromBaseDomain(host, flags)
        } else {
            runtime.storageController.clearData(flags)
        }.then(
            {
                onSuccess()
                GeckoResult<Void>()
            },
            {
                    throwable ->
                onError(throwable)
                GeckoResult<Void>()
            },
        )
    }

   
    override fun isTranslationsEngineSupported(
        onSuccess: (Boolean) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        TranslationsController.RuntimeTranslation.isTranslationsEngineSupported().then(
            {
                if (it != null) {
                    onSuccess(it)
                } else {
                    onError(TranslationError.UnexpectedNull())
                }
                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable.intoTranslationError())
                GeckoResult<Void>()
            },
        )
    }

   
    override fun getTranslationsPairDownloadSize(
        fromLanguage: String,
        toLanguage: String,
        onSuccess: (Long) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        TranslationsController.RuntimeTranslation.checkPairDownloadSize(fromLanguage, toLanguage).then(
            {
                if (it != null) {
                    onSuccess(it)
                } else {
                    onError(TranslationError.UnexpectedNull())
                }
                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable.intoTranslationError())
                GeckoResult<Void>()
            },
        )
    }

   
    override fun getTranslationsModelDownloadStates(
        onSuccess: (List<LanguageModel>) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        TranslationsController.RuntimeTranslation.listModelDownloadStates().then(
            {
                if (it != null) {
                    var listOfModels = mutableListOf<LanguageModel>()
                    for (each in it) {
                        var language = each.language?.let {
                                language ->
                            Language(language.code, each.language?.localizedDisplayName)
                        }
                        var model = LanguageModel(language, each.isDownloaded, each.size)
                        listOfModels.add(model)
                    }
                    onSuccess(listOfModels)
                } else {
                    onError(TranslationError.UnexpectedNull())
                }
                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable.intoTranslationError())
                GeckoResult<Void>()
            },
        )
    }

    
    override fun getSupportedTranslationLanguages(
        onSuccess: (TranslationSupport) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        TranslationsController.RuntimeTranslation.listSupportedLanguages().then(
            {
                if (it != null) {
                    var listOfFromLanguages = mutableListOf<Language>()
                    var listOfToLanguages = mutableListOf<Language>()

                    if (it.fromLanguages != null) {
                        for (each in it.fromLanguages!!) {
                            listOfFromLanguages.add(Language(each.code, each.localizedDisplayName))
                        }
                    }

                    if (it.toLanguages != null) {
                        for (each in it.toLanguages!!) {
                            listOfToLanguages.add(Language(each.code, each.localizedDisplayName))
                        }
                    }

                    onSuccess(TranslationSupport(listOfFromLanguages, listOfToLanguages))
                } else {
                    onError(TranslationError.UnexpectedNull())
                }
                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable.intoTranslationError())
                GeckoResult<Void>()
            },
        )
    }

    
    override fun manageTranslationsLanguageModel(
        options: ModelManagementOptions,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val geckoOptions =
            TranslationsController.RuntimeTranslation.ModelManagementOptions.Builder()
                .operation(options.operation.toString())
                .operationLevel(options.operationLevel.toString())

        options.languageToManage?.let { geckoOptions.languageToManage(it) }

        TranslationsController.RuntimeTranslation.manageLanguageModel(geckoOptions.build()).then(
            {
                onSuccess()
                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable.intoTranslationError())
                GeckoResult<Void>()
            },
        )
    }

   
    override fun getUserPreferredLanguages(
        onSuccess: (List<String>) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        TranslationsController.RuntimeTranslation.preferredLanguages().then(
            {
                if (it != null) {
                    onSuccess(it)
                } else {
                    onError(TranslationError.UnexpectedNull())
                }

                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable.intoTranslationError())
                GeckoResult<Void>()
            },
        )
    }

    
    override fun getTranslationsOfferPopup(): Boolean {
        return runtime.settings.translationsOfferPopup
    }

   
    override fun setTranslationsOfferPopup(offer: Boolean) {
        runtime.settings.translationsOfferPopup = offer
    }

   
    override fun getLanguageSetting(
        languageCode: String,
        onSuccess: (LanguageSetting) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        TranslationsController.RuntimeTranslation.getLanguageSetting(languageCode).then(
            {
                if (it != null) {
                    try {
                        onSuccess(LanguageSetting.fromValue(it))
                    } catch (e: IllegalArgumentException) {
                        onError(e.intoTranslationError())
                    }
                } else {
                    onError(TranslationError.UnexpectedNull())
                }

                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable.intoTranslationError())
                GeckoResult<Void>()
            },
        )
    }

  
    override fun setLanguageSetting(
        languageCode: String,
        languageSetting: LanguageSetting,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        TranslationsController.RuntimeTranslation.setLanguageSettings(languageCode, languageSetting.toString()).then(
            {
                onSuccess()
                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable.intoTranslationError())
                GeckoResult<Void>()
            },
        )
    }

    
    override fun getLanguageSettings(
        onSuccess: (Map<String, LanguageSetting>) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        TranslationsController.RuntimeTranslation.getLanguageSettings().then(
            {
                if (it != null) {
                    try {
                        val result = mutableMapOf<String, LanguageSetting>()
                        it.forEach { item ->
                            result[item.key] = LanguageSetting.fromValue(item.value)
                        }
                        onSuccess(result)
                    } catch (e: IllegalArgumentException) {
                        onError(e.intoTranslationError())
                    }
                } else {
                    onError(TranslationError.UnexpectedNull())
                }
                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable.intoTranslationError())
                GeckoResult<Void>()
            },
        )
    }

   
    override fun getNeverTranslateSiteList(
        onSuccess: (List<String>) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        TranslationsController.RuntimeTranslation.getNeverTranslateSiteList().then(
            {
                if (it != null) {
                    try {
                        onSuccess(it)
                    } catch (e: IllegalArgumentException) {
                        onError(e.intoTranslationError())
                    }
                } else {
                    onError(TranslationError.UnexpectedNull())
                }
                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable.intoTranslationError())
                GeckoResult<Void>()
            },
        )
    }

  
    override fun setNeverTranslateSpecifiedSite(
        origin: String,
        setting: Boolean,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        TranslationsController.RuntimeTranslation.setNeverTranslateSpecifiedSite(setting, origin).then(
            {
                onSuccess()
                GeckoResult<Void>()
            },
            { throwable ->
                onError(throwable.intoTranslationError())
                GeckoResult<Void>()
            },
        )
    }

   
    override val profiler: Profiler? = Profiler(runtime)

    override fun name(): String = "Gecko"

    override val version: EngineVersion = EngineVersion.parse(
        org.mozilla.geckoview.BuildConfig.MOZILLA_VERSION,
        org.mozilla.geckoview.BuildConfig.MOZ_UPDATE_CHANNEL,
    ) ?: throw IllegalStateException("Could not determine engine version")

   
    override val settings: Settings = object : Settings() {
        override var javascriptEnabled: Boolean
            get() = runtime.settings.javaScriptEnabled
            set(value) { runtime.settings.javaScriptEnabled = value }

        override var webFontsEnabled: Boolean
            get() = runtime.settings.webFontsEnabled
            set(value) { runtime.settings.webFontsEnabled = value }

        override var automaticFontSizeAdjustment: Boolean
            get() = runtime.settings.automaticFontSizeAdjustment
            set(value) { runtime.settings.automaticFontSizeAdjustment = value }

        override var automaticLanguageAdjustment: Boolean
            get() = localeUpdater.enabled
            set(value) {
                localeUpdater.enabled = value
                defaultSettings?.automaticLanguageAdjustment = value
            }

        override var safeBrowsingPolicy: Array<SafeBrowsingPolicy> =
            arrayOf(SafeBrowsingPolicy.RECOMMENDED)
            set(value) {
                val policy = value.sumOf { it.id }
                runtime.settings.contentBlocking.setSafeBrowsing(policy)
                field = value
            }

        override var trackingProtectionPolicy: TrackingProtectionPolicy? = null
            set(value) {
                value?.let { policy ->
                    with(runtime.settings.contentBlocking) {
                        if (enhancedTrackingProtectionLevel != value.getEtpLevel()) {
                            enhancedTrackingProtectionLevel = value.getEtpLevel()
                        }

                        if (strictSocialTrackingProtection != value.getStrictSocialTrackingProtection()) {
                            strictSocialTrackingProtection = policy.getStrictSocialTrackingProtection()
                        }

                        if (antiTrackingCategories != value.getAntiTrackingPolicy()) {
                            setAntiTracking(policy.getAntiTrackingPolicy())
                        }

                        if (cookieBehavior != value.cookiePolicy.id) {
                            cookieBehavior = value.cookiePolicy.id
                        }

                        if (cookieBehaviorPrivateMode != value.cookiePolicyPrivateMode.id) {
                            cookieBehaviorPrivateMode = value.cookiePolicyPrivateMode.id
                        }

                        if (cookiePurging != value.cookiePurging) {
                            setCookiePurging(value.cookiePurging)
                        }
                    }

                    defaultSettings?.trackingProtectionPolicy = value
                    field = value
                }
            }

        override var cookieBannerHandlingMode: CookieBannerHandlingMode = CookieBannerHandlingMode.DISABLED
            set(value) {
                with(runtime.settings.contentBlocking) {
                    if (this.cookieBannerMode != value.mode) {
                        this.cookieBannerMode = value.mode
                    }
                }
                field = value
            }

        override var cookieBannerHandlingModePrivateBrowsing: CookieBannerHandlingMode =
            CookieBannerHandlingMode.REJECT_ALL
            set(value) {
                with(runtime.settings.contentBlocking) {
                    if (this.cookieBannerModePrivateBrowsing != value.mode) {
                        this.cookieBannerModePrivateBrowsing = value.mode
                    }
                }
                field = value
            }

        override var emailTrackerBlockingPrivateBrowsing: Boolean = false
            set(value) {
                with(runtime.settings.contentBlocking) {
                    if (this.emailTrackerBlockingPrivateBrowsingEnabled != value) {
                        this.setEmailTrackerBlockingPrivateBrowsing(value)
                    }
                }
                field = value
            }

        override var cookieBannerHandlingDetectOnlyMode: Boolean = false
            set(value) {
                with(runtime.settings.contentBlocking) {
                    if (this.cookieBannerDetectOnlyMode != value) {
                        this.cookieBannerDetectOnlyMode = value
                    }
                }
                field = value
            }

        override var cookieBannerHandlingGlobalRules: Boolean = false
            set(value) {
                with(runtime.settings.contentBlocking) {
                    if (this.cookieBannerGlobalRulesEnabled != value) {
                        this.cookieBannerGlobalRulesEnabled = value
                    }
                }
                field = value
            }

        override var cookieBannerHandlingGlobalRulesSubFrames: Boolean = false
            set(value) {
                with(runtime.settings.contentBlocking) {
                    if (this.cookieBannerGlobalRulesSubFramesEnabled != value) {
                        this.cookieBannerGlobalRulesSubFramesEnabled = value
                    }
                }
                field = value
            }

        override var queryParameterStripping: Boolean = false
            set(value) {
                with(runtime.settings.contentBlocking) {
                    if (this.queryParameterStrippingEnabled != value) {
                        this.queryParameterStrippingEnabled = value
                    }
                }
                field = value
            }

        override var queryParameterStrippingPrivateBrowsing: Boolean = false
            set(value) {
                with(runtime.settings.contentBlocking) {
                    if (this.queryParameterStrippingPrivateBrowsingEnabled != value) {
                        this.queryParameterStrippingPrivateBrowsingEnabled = value
                    }
                }
                field = value
            }

        @Suppress("SpreadOperator")
        override var queryParameterStrippingAllowList: String = ""
            set(value) {
                with(runtime.settings.contentBlocking) {
                    if (this.queryParameterStrippingAllowList.joinToString() != value) {
                        this.setQueryParameterStrippingAllowList(
                            *value.split(",")
                                .toTypedArray(),
                        )
                    }
                }
                field = value
            }

        @Suppress("SpreadOperator")
        override var queryParameterStrippingStripList: String = ""
            set(value) {
                with(runtime.settings.contentBlocking) {
                    if (this.queryParameterStrippingStripList.joinToString() != value) {
                        this.setQueryParameterStrippingStripList(
                            *value.split(",").toTypedArray(),
                        )
                    }
                }
                field = value
            }

        override var remoteDebuggingEnabled: Boolean
            get() = runtime.settings.remoteDebuggingEnabled
            set(value) { runtime.settings.remoteDebuggingEnabled = value }

        override var historyTrackingDelegate: HistoryTrackingDelegate?
            get() = defaultSettings?.historyTrackingDelegate
            set(value) { defaultSettings?.historyTrackingDelegate = value }

        override var testingModeEnabled: Boolean
            get() = defaultSettings?.testingModeEnabled ?: false
            set(value) { defaultSettings?.testingModeEnabled = value }

        override var userAgentString: String?
            get() = defaultSettings?.userAgentString ?: GeckoSession.getDefaultUserAgent()
            set(value) { defaultSettings?.userAgentString = value }

        override var preferredColorScheme: PreferredColorScheme
            get() = PreferredColorScheme.from(runtime.settings.preferredColorScheme)
            set(value) { runtime.settings.preferredColorScheme = value.toGeckoValue() }

        override var suspendMediaWhenInactive: Boolean
            get() = defaultSettings?.suspendMediaWhenInactive ?: false
            set(value) { defaultSettings?.suspendMediaWhenInactive = value }

        override var clearColor: Int?
            get() = defaultSettings?.clearColor
            set(value) { defaultSettings?.clearColor = value }

        override var fontInflationEnabled: Boolean?
            get() = runtime.settings.fontInflationEnabled
            set(value) {
                
                value?.let {
                    runtime.settings.fontInflationEnabled = it
                }
            }

        override var fontSizeFactor: Float?
            get() = runtime.settings.fontSizeFactor
            set(value) {
               
                value?.let {
                    runtime.settings.fontSizeFactor = it
                }
            }

        override var loginAutofillEnabled: Boolean
            get() = runtime.settings.loginAutofillEnabled
            set(value) { runtime.settings.loginAutofillEnabled = value }

        override var forceUserScalableContent: Boolean
            get() = runtime.settings.forceUserScalableEnabled
            set(value) { runtime.settings.forceUserScalableEnabled = value }

        override var enterpriseRootsEnabled: Boolean
            get() = runtime.settings.enterpriseRootsEnabled
            set(value) { runtime.settings.enterpriseRootsEnabled = value }

        override var httpsOnlyMode: Engine.HttpsOnlyMode
            get() = when (runtime.settings.allowInsecureConnections) {
                GeckoRuntimeSettings.ALLOW_ALL -> Engine.HttpsOnlyMode.DISABLED
                GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE -> Engine.HttpsOnlyMode.ENABLED_PRIVATE_ONLY
                GeckoRuntimeSettings.HTTPS_ONLY -> Engine.HttpsOnlyMode.ENABLED
                else -> throw java.lang.IllegalStateException("Unknown HTTPS-Only mode returned by GeckoView")
            }
            set(value) {
                runtime.settings.allowInsecureConnections = when (value) {
                    Engine.HttpsOnlyMode.DISABLED -> GeckoRuntimeSettings.ALLOW_ALL
                    Engine.HttpsOnlyMode.ENABLED_PRIVATE_ONLY -> GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE
                    Engine.HttpsOnlyMode.ENABLED -> GeckoRuntimeSettings.HTTPS_ONLY
                }
            }
        override var globalPrivacyControlEnabled: Boolean
            get() = runtime.settings.globalPrivacyControl
            set(value) { runtime.settings.setGlobalPrivacyControl(value) }
    }.apply {
        defaultSettings?.let {
            this.javascriptEnabled = it.javascriptEnabled
            this.webFontsEnabled = it.webFontsEnabled
            this.automaticFontSizeAdjustment = it.automaticFontSizeAdjustment
            this.automaticLanguageAdjustment = it.automaticLanguageAdjustment
            this.trackingProtectionPolicy = it.trackingProtectionPolicy
            this.safeBrowsingPolicy = arrayOf(SafeBrowsingPolicy.RECOMMENDED)
            this.remoteDebuggingEnabled = it.remoteDebuggingEnabled
            this.testingModeEnabled = it.testingModeEnabled
            this.userAgentString = it.userAgentString
            this.preferredColorScheme = it.preferredColorScheme
            this.fontInflationEnabled = it.fontInflationEnabled
            this.fontSizeFactor = it.fontSizeFactor
            this.forceUserScalableContent = it.forceUserScalableContent
            this.clearColor = it.clearColor
            this.loginAutofillEnabled = it.loginAutofillEnabled
            this.enterpriseRootsEnabled = it.enterpriseRootsEnabled
            this.httpsOnlyMode = it.httpsOnlyMode
            this.cookieBannerHandlingMode = it.cookieBannerHandlingMode
            this.cookieBannerHandlingModePrivateBrowsing = it.cookieBannerHandlingModePrivateBrowsing
            this.cookieBannerHandlingDetectOnlyMode = it.cookieBannerHandlingDetectOnlyMode
            this.cookieBannerHandlingGlobalRules = it.cookieBannerHandlingGlobalRules
            this.cookieBannerHandlingGlobalRulesSubFrames = it.cookieBannerHandlingGlobalRulesSubFrames
            this.globalPrivacyControlEnabled = it.globalPrivacyControlEnabled
            this.emailTrackerBlockingPrivateBrowsing = it.emailTrackerBlockingPrivateBrowsing
        }
    }

    @Suppress("ComplexMethod")
    internal fun ContentBlockingController.LogEntry.BlockingData.getLoadedCategory(): TrackingCategory {
        val socialTrackingProtectionEnabled = settings.trackingProtectionPolicy?.strictSocialTrackingProtection
            ?: false

        return when (category) {
            Event.LOADED_FINGERPRINTING_CONTENT -> TrackingCategory.FINGERPRINTING
            Event.LOADED_CRYPTOMINING_CONTENT -> TrackingCategory.CRYPTOMINING
            Event.LOADED_SOCIALTRACKING_CONTENT -> {
                if (socialTrackingProtectionEnabled) TrackingCategory.MOZILLA_SOCIAL else TrackingCategory.NONE
            }
            Event.COOKIES_LOADED_SOCIALTRACKER -> {
                if (!socialTrackingProtectionEnabled) TrackingCategory.MOZILLA_SOCIAL else TrackingCategory.NONE
            }
            Event.LOADED_LEVEL_1_TRACKING_CONTENT -> TrackingCategory.SCRIPTS_AND_SUB_RESOURCES
            Event.LOADED_LEVEL_2_TRACKING_CONTENT -> {
               
                val isContentListActive =
                    settings.trackingProtectionPolicy?.contains(TrackingCategory.CONTENT)
                        ?: false
                val isStrictLevelActive =
                    runtime.settings
                        .contentBlocking
                        .getEnhancedTrackingProtectionLevel() == ContentBlocking.EtpLevel.STRICT

                if (isStrictLevelActive && isContentListActive) {
                    TrackingCategory.SCRIPTS_AND_SUB_RESOURCES
                } else {
                    TrackingCategory.NONE
                }
            }
            else -> TrackingCategory.NONE
        }
    }

    private fun isCategoryActive(category: TrackingCategory) = settings.trackingProtectionPolicy?.contains(category)
        ?: false

  
    internal fun ContentBlockingController.LogEntry.toTrackerLog(): TrackerLog {
        val cookiesHasBeenBlocked = this.blockingData.any { it.hasBlockedCookies() }
        val blockedCategories = blockingData.map { it.getBlockedCategory() }
            .filterNot { it == TrackingCategory.NONE }
            .distinct()
        val loadedCategories = blockingData.map { it.getLoadedCategory() }
            .filterNot { it == TrackingCategory.NONE }
            .distinct()

               val shimmedCount = blockingData.find {
            it.category == Event.REPLACED_TRACKING_CONTENT
        }?.count ?: 0

        val shimmedCategories = loadedCategories.filter { isCategoryActive(it) }
            .take(shimmedCount)

       
        return TrackerLog(
            url = origin,
            loadedCategories = loadedCategories.filterNot { it in shimmedCategories },
            blockedCategories = (blockedCategories + shimmedCategories).distinct(),
            cookiesHasBeenBlocked = cookiesHasBeenBlocked,
            unBlockedBySmartBlock = this.blockingData.any { it.unBlockedBySmartBlock() },
        )
    }

    internal fun org.mozilla.geckoview.WebExtension?.toSafeWebExtension(): GeckoWebExtension? {
        return if (this != null) {
            GeckoWebExtension(
                this,
                runtime,
            )
        } else {
            null
        }
    }

    private fun onExtensionInstalled(
        ext: org.mozilla.geckoview.WebExtension,
        onSuccess: ((WebExtension) -> Unit),
    ) {
        val installedExtension = GeckoWebExtension(ext, runtime)
        webExtensionDelegate?.onInstalled(installedExtension)
        installedExtension.registerActionHandler(webExtensionActionHandler)
        installedExtension.registerTabHandler(webExtensionTabHandler, defaultSettings)
        onSuccess(installedExtension)
    }
}

internal fun ContentBlockingController.LogEntry.BlockingData.hasBlockedCookies(): Boolean {
    return category == Event.COOKIES_BLOCKED_BY_PERMISSION ||
        category == Event.COOKIES_BLOCKED_TRACKER ||
        category == Event.COOKIES_BLOCKED_ALL ||
        category == Event.COOKIES_PARTITIONED_FOREIGN ||
        category == Event.COOKIES_BLOCKED_FOREIGN ||
        category == Event.COOKIES_BLOCKED_SOCIALTRACKER
}

internal fun ContentBlockingController.LogEntry.BlockingData.unBlockedBySmartBlock(): Boolean {
    return category == Event.ALLOWED_TRACKING_CONTENT
}

internal fun ContentBlockingController.LogEntry.BlockingData.getBlockedCategory(): TrackingCategory {
    return when (category) {
        Event.BLOCKED_FINGERPRINTING_CONTENT -> TrackingCategory.FINGERPRINTING
        Event.BLOCKED_CRYPTOMINING_CONTENT -> TrackingCategory.CRYPTOMINING
        Event.BLOCKED_SOCIALTRACKING_CONTENT, Event.COOKIES_BLOCKED_SOCIALTRACKER -> TrackingCategory.MOZILLA_SOCIAL
        Event.BLOCKED_TRACKING_CONTENT -> TrackingCategory.SCRIPTS_AND_SUB_RESOURCES
        else -> TrackingCategory.NONE
    }
}

internal fun InstallationMethod.toGeckoInstallationMethod(): String? {
    return when (this) {
        InstallationMethod.MANAGER -> WebExtensionController.INSTALLATION_METHOD_MANAGER
        InstallationMethod.FROM_FILE -> WebExtensionController.INSTALLATION_METHOD_FROM_FILE
        //else -> null
    }
}
