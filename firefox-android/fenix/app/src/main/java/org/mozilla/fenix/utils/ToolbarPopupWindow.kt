/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.utils
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.annotation.VisibleForTesting
import androidx.core.view.isVisible

import mozilla.components.browser.state.selector.findCustomTab
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore




import org.mozilla.fenix.R

import org.mozilla.fenix.databinding.BrowserToolbarPopupWindowBinding
import org.mozilla.fenix.ext.components
import java.lang.ref.WeakReference
import android.os.Handler
import android.os.Looper



object ToolbarPopupWindow {
    private fun handleShare(context: Context, url: String) {
        // Launch an intent to share the URL
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }
    fun show(

        view: WeakReference<View>,
        customTabId: String? = null,
        handlePasteAndGo: (String) -> Unit,
        handlePaste: (String) -> Unit,
        copyVisible: Boolean = true,

        ) {

        val context = view.get()?.context ?: return
        val clipboard = context.components.clipboardHandler
        val clipboardUrl = clipboard.getUrl()
        val clipboardText = clipboard.text
        if (!copyVisible && clipboardUrl == null) return
        //val isCustomTabSession = customTabId != null
        val binding = BrowserToolbarPopupWindowBinding.inflate(LayoutInflater.from(context))
        val popupWindow = PopupWindow(
            binding.root,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            context.resources.getDimensionPixelSize(R.dimen.context_menu_height),
            true,
        )

        popupWindow.elevation =
            context.resources.getDimension(R.dimen.mozac_browser_menu_elevation)
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        binding.copy.isVisible = copyVisible
        binding.paste.isVisible = copyVisible
        binding.pasteAndGo.isVisible = copyVisible
        val urlForClipboard = getUrlForClipboard(context.components.core.store, customTabId)
        if (copyVisible) {
            clipboard.text = urlForClipboard
            binding.copy.setOnClickListener {
                handleShare(context, urlForClipboard!!)
            }

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                popupWindow.dismiss()

            }, 2500)
        }

        clipboardText?.let { text ->
            binding.paste.setOnClickListener {
                popupWindow.dismiss()
                handlePaste(text)
            }
        }

        clipboardUrl?.let { url ->
            binding.pasteAndGo.setOnClickListener {
                popupWindow.dismiss()
                handlePasteAndGo(url)
            }
        }

        view.get()?.let {
            popupWindow.showAsDropDown(
                it,
                context.resources.getDimensionPixelSize(R.dimen.context_menu_x_offset),
                0,
                Gravity.START,
            )
        }
    }

    @VisibleForTesting
    internal fun getUrlForClipboard(
        store: BrowserStore,
        customTabId: String? = null,
    ): String? {
        return if (customTabId != null) {
            val customTab = store.state.findCustomTab(customTabId)
            customTab?.content?.url
        } else {
            val selectedTab = store.state.selectedTab
            selectedTab?.readerState?.activeUrl ?: selectedTab?.content?.url
        }
    }

    private fun ClipboardHandler.getUrl(): String? {
        if (containsURL()) {
            text?.let { return it }
            // Logger("ToolbarPopupWindow").error("Clipboard contains URL but unable to read text")
        }
        return null
    }
}
