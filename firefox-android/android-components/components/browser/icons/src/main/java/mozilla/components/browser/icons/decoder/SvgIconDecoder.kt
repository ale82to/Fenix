/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.icons.decoder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Canvas
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
//import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.images.DesiredSize
import mozilla.components.support.images.decoder.ImageDecoder

/**
 * [ImageDecoder] that will use the AndroidSVG in order to decode the byte data.
 *
 */
class SvgIconDecoder(val context: Context) : ImageDecoder {
    //private val logger = Logger("SvgIconDecoder")

    override fun decode(data: ByteArray, desiredSize: DesiredSize): Bitmap? {
        return try {
            val svg = SVG.getFromInputStream(data.inputStream())

            val svgWidth: Float
            val svgHeight: Float
            val viewBox: RectF? = svg.documentViewBox
            if (viewBox != null) {
                svgWidth = viewBox.width()
                svgHeight = viewBox.height()
            } else {
                svgWidth = svg.documentWidth
                svgHeight = svg.documentHeight
            }

            var bitmapWidth = desiredSize.targetSize
            var bitmapHeight = desiredSize.targetSize

            if (svgWidth > 0 && svgHeight > 0) {
                val widthPercent = bitmapWidth / svgWidth.toDouble()
                val heightPercent = bitmapHeight / svgHeight.toDouble()
                val multiplier = minOf(widthPercent, heightPercent)

                bitmapWidth = (multiplier * svgWidth).toInt()
                bitmapHeight = (multiplier * svgHeight).toInt()
            }

            // Set the SVG's view box to enable scaling if it is not set.
            if (viewBox == null && svgWidth > 0 && svgHeight > 0) {
                svg.setDocumentViewBox(0f, 0f, svgWidth, svgHeight)
            }

            svg.setDocumentWidth("100%")
            svg.setDocumentHeight("100%")

            val bitmap = createBitmap(bitmapWidth, bitmapHeight, ARGB_8888)
            svg.renderToCanvas(Canvas(bitmap))

            bitmap
        } catch (e: SVGParseException) {
            // logger.error("Failed to parse the byte data to SVG")
            null
        } catch (e: OutOfMemoryError) {
            // logger.error("Failed to decode the byte data due to OutOfMemoryError")
            null
        } catch (e: Exception) {
            // logger.error("Unexpected error during SVG decoding", e)
            null
        }
    }
}
