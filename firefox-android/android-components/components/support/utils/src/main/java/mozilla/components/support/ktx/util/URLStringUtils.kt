package mozilla.components.support.ktx.util

import android.net.Uri
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import androidx.core.text.TextDirectionHeuristicCompat
import androidx.core.text.TextDirectionHeuristicsCompat
import java.util.regex.Pattern

object URLStringUtils {

    private val isURLLenient by lazy {
        Pattern.compile(
           "^(?:(?:https?|web|m|www|org):\\/\\/|about:[\\S ]+|[\\S]+(?:\\.[a-zA-Z]{2,})(?:\\/\\S*)?(?:\\?[\\S ]+)?)$",
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        )
    }


    private const val HTTP = "http://"
    private const val HTTPS = "https://"
    private const val WWW = "www."

    fun isURLLike(string: String): Boolean {
        val trimmedInput = string.trim()
        return isURLLenient.matcher(trimmedInput).matches()
    }

    fun isSearchTerm(string: String): Boolean {
        return  !isURLLike(string)
    }

    /**
     * Normalizes a URL String.
     */
    fun toNormalizedURL(string: String): String {
        val trimmedInput = string.trim()
        var uri = Uri.parse(trimmedInput)
        uri = if (TextUtils.isEmpty(uri.scheme)) {
            Uri.parse("http://$trimmedInput")
        } else {
            uri.normalizeScheme()
        }
        return uri.toString()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal const val UNICODE_CHARACTER_CLASS: Int = 0x100

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var flags = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE

    fun toDisplayUrl(
        originalUrl: CharSequence,
        textDirectionHeuristic: TextDirectionHeuristicCompat = TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR
    ): CharSequence {
        val strippedText = maybeStripTrailingSlash(maybeStripUrlProtocol(originalUrl))

        return if (isURLLike(originalUrl.toString())) {
            strippedText
        } else {
            if (strippedText.isNotBlank() && textDirectionHeuristic.isRtl(strippedText, 0, 1)) {
                "\u200E$strippedText"
            } else {
                strippedText
            }
        }
    }

    private fun maybeStripUrlProtocol(url: CharSequence): CharSequence {
        var noPrefixUrl = url
        if (url.toString().startsWith(HTTPS)) {
            noPrefixUrl = maybeStripUrlSubDomain(url.toString().replaceFirst(HTTPS, ""))
        } else if (url.toString().startsWith(HTTP)) {
            noPrefixUrl = maybeStripUrlSubDomain(url.toString().replaceFirst(HTTP, ""))
        }
        return noPrefixUrl
    }

    private fun maybeStripUrlSubDomain(url: CharSequence): CharSequence {
        return if (url.toString().startsWith(WWW)) {
            url.toString().replaceFirst(WWW, "")
        } else {
            url
        }
    }

    private fun maybeStripTrailingSlash(url: CharSequence): CharSequence {
        return url.trimEnd('/')
    }
}
