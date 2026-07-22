/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.view.ViewConfiguration
import androidx.core.animation.doOnEnd
import androidx.core.graphics.contains
import androidx.core.graphics.toPoint
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.support.ktx.android.view.getRectWithViewLocation
import mozilla.components.support.utils.ext.bottom
import mozilla.components.support.utils.ext.mandatorySystemGestureInsets
//import mozilla.telemetry.glean.private.NoExtras
//import org.mozilla.fenix.GleanMetrics.Events
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.getRectWithScreenLocation
import org.mozilla.fenix.ext.getWindowInsets
import org.mozilla.fenix.ext.isKeyboardVisible
import org.mozilla.fenix.ext.settings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Suppress("LargeClass", "TooManyFunctions")
class ToolbarGestureHandler(
    private val activity: Activity,
    private val contentLayout: View,
    private val tabPreview: TabPreview,
    private val toolbarLayout: View,
    private val store: BrowserStore,
    private val selectTabUseCase: TabsUseCases.SelectTabUseCase,
) : SwipeGestureListener {

    private enum class GestureDirection {
        LEFT_TO_RIGHT, RIGHT_TO_LEFT
    }

    private sealed class Destination {
        data class Tab(val tab: TabSessionState) : Destination()
        object None : Destination()
    }

    private val windowWidth: Int
        get() = activity.resources.displayMetrics.widthPixels

    private val previewOffset =
        activity.resources.getDimensionPixelSize(R.dimen.browser_fragment_gesture_preview_offset)

    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
    private val minimumFlingVelocity = ViewConfiguration.get(activity).scaledMinimumFlingVelocity

    private var gestureDirection = GestureDirection.LEFT_TO_RIGHT

    override fun onSwipeStarted(start: PointF, next: PointF): Boolean {
        val dx = next.x - start.x
        val dy = next.y - start.y
        gestureDirection = if (dx < 0) {
            GestureDirection.RIGHT_TO_LEFT
        } else {
            GestureDirection.LEFT_TO_RIGHT
        }

        @Suppress("ComplexCondition")
        return if (
            start.isInToolbar() &&
            abs(dx) > touchSlop &&
            abs(dy) < abs(dx)
        ) {
            preparePreview(getDestination())
            true
        } else {
            false
        }
    }

    override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {
        when (getDestination()) {
            is Destination.Tab -> {
                tabPreview.translationX = when (gestureDirection) {
                    GestureDirection.RIGHT_TO_LEFT -> min(
                        windowWidth.toFloat() + previewOffset,
                        tabPreview.translationX - distanceX,
                    ).coerceAtLeast(0f)
                    GestureDirection.LEFT_TO_RIGHT -> max(
                        -windowWidth.toFloat() - previewOffset,
                        tabPreview.translationX - distanceX,
                    ).coerceAtMost(0f)
                }
                contentLayout.translationX = when (gestureDirection) {
                    GestureDirection.RIGHT_TO_LEFT -> min(
                        0f,
                        contentLayout.translationX - distanceX,
                    ).coerceAtLeast(-windowWidth.toFloat() - previewOffset)
                    GestureDirection.LEFT_TO_RIGHT -> max(
                        0f,
                        contentLayout.translationX - distanceX,
                    ).coerceAtMost(windowWidth.toFloat() + previewOffset)
                }
            }
            is Destination.None -> {
                val maxContentHidden = contentLayout.width * OVERSCROLL_HIDE_PERCENT
                contentLayout.translationX = when (gestureDirection) {
                    GestureDirection.RIGHT_TO_LEFT -> max(
                        -maxContentHidden.toFloat(),
                        contentLayout.translationX - distanceX,
                    ).coerceAtMost(0f)
                    GestureDirection.LEFT_TO_RIGHT -> min(
                        maxContentHidden.toFloat(),
                        contentLayout.translationX - distanceX,
                    ).coerceAtLeast(0f)
                }
            }
        }
    }

    override fun onSwipeFinished(
        velocityX: Float,
        velocityY: Float,
    ) {
        val destination = getDestination()
        if (destination is Destination.Tab && isGestureComplete(velocityX)) {
            animateToNextTab(destination.tab)
        } else {
            animateCanceledGesture(velocityX)
        }
    }

    private fun getDestination(): Destination {
        val isLtr = activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR
        val currentTab = store.state.selectedTab ?: return Destination.None
        val currentIndex =
            store.state.getNormalOrPrivateTabs(private = false).indexOfFirst {
                it.id == currentTab.id
            }

        return if (currentIndex == -1) {
            Destination.None
        } else {
            val tabs = store.state.getNormalOrPrivateTabs(private = false)
            val index = when (gestureDirection) {
                GestureDirection.RIGHT_TO_LEFT -> if (isLtr) {
                    currentIndex + 1
                } else {
                    currentIndex - 1
                }
                GestureDirection.LEFT_TO_RIGHT -> if (isLtr) {
                    currentIndex - 1
                } else {
                    currentIndex + 1
                }
            }

            if (index < tabs.count() && index >= 0) {
                Destination.Tab(tabs.elementAt(index))
            } else {
                Destination.None
            }
        }
    }

    private fun preparePreview(destination: Destination) {
        val (thumbnailId, isPrivate) = when (destination) {
            is Destination.Tab -> destination.tab.id to destination.tab.content.private
            is Destination.None -> return
        }

        tabPreview.loadPreviewThumbnail(thumbnailId, isPrivate)
        tabPreview.alpha = 1f
        tabPreview.translationX = when (gestureDirection) {
            GestureDirection.RIGHT_TO_LEFT -> windowWidth.toFloat() + previewOffset
            GestureDirection.LEFT_TO_RIGHT -> -windowWidth.toFloat() - previewOffset
        }
        tabPreview.isVisible = false
    }


    private fun isGestureComplete(velocityX: Float): Boolean {
        val previewWidth = tabPreview.getRectWithViewLocation().visibleWidth.toDouble()
        val velocityMatchesDirection = when (gestureDirection) {
            GestureDirection.RIGHT_TO_LEFT -> velocityX <= 0
            GestureDirection.LEFT_TO_RIGHT -> velocityX >= 0
        }
        val reverseFling =
            abs(velocityX) >= minimumFlingVelocity && !velocityMatchesDirection

        return !reverseFling && (
                previewWidth / windowWidth >= GESTURE_FINISH_PERCENT ||
                        abs(velocityX) >= minimumFlingVelocity
                )
    }

    private fun getAnimator(finalContextX: Float, duration: Long): ValueAnimator {
        return ValueAnimator.ofFloat(contentLayout.translationX, finalContextX).apply {
            this.duration = duration
            this.interpolator = LinearOutSlowInInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                contentLayout.translationX = value
                tabPreview.translationX = when (gestureDirection) {
                    GestureDirection.RIGHT_TO_LEFT -> value + windowWidth + previewOffset
                    GestureDirection.LEFT_TO_RIGHT -> value - windowWidth - previewOffset
                }
            }
        }
    }

    private fun animateToNextTab(tab: TabSessionState) {
        val browserFinalXCoordinate: Float = when (gestureDirection) {
            GestureDirection.RIGHT_TO_LEFT -> -windowWidth.toFloat() - previewOffset
            GestureDirection.LEFT_TO_RIGHT -> windowWidth.toFloat() + previewOffset
        }

        getAnimator(browserFinalXCoordinate, FINISHED_GESTURE_ANIMATION_DURATION).apply {
            doOnEnd {
                contentLayout.translationX = 0f
                selectTabUseCase(tab.id)

                val shortAnimationDuration =
                    activity.resources.getInteger(android.R.integer.config_shortAnimTime)
                tabPreview.animate()
                    .alpha(0f)
                    .setDuration(shortAnimationDuration.toLong())
                    .setListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                tabPreview.isVisible = false
                               // Events.toolbarTabSwipe.record(NoExtras())
                            }
                        },
                    )
            }
        }.start()
    }

    private fun animateCanceledGesture(velocityX: Float) {
        val duration = if (abs(velocityX) >= minimumFlingVelocity) {
            CANCELED_FLING_ANIMATION_DURATION
        } else {
            CANCELED_GESTURE_ANIMATION_DURATION
        }

        getAnimator(0f, duration).apply {
            doOnEnd {
                tabPreview.isVisible = false
            }
        }.start()
    }

    private fun PointF.isInToolbar(): Boolean {
        val toolbarLocation = toolbarLayout.getRectWithScreenLocation()
        activity.window.decorView.getWindowInsets()?.let { insets ->
            if (activity.settings().shouldUseBottomToolbar) {
                toolbarLocation.top -= (insets.mandatorySystemGestureInsets().bottom - insets.bottom())
            }
        }
        return toolbarLocation.contains(toPoint())
    }

    private val Rect.visibleWidth: Int
        get() = if (left < 0) {
            right
        } else {
            windowWidth - left
        }

    companion object {


        private const val GESTURE_FINISH_PERCENT = 0.25

        private const val OVERSCROLL_HIDE_PERCENT = 0.20


        private const val FINISHED_GESTURE_ANIMATION_DURATION = 0L


        private const val CANCELED_GESTURE_ANIMATION_DURATION = 10L

        private const val CANCELED_FLING_ANIMATION_DURATION = 25L
    }
}
