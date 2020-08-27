/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material

import androidx.compose.animation.asDisposableClock
import androidx.compose.animation.core.AnimatedFloat
import androidx.compose.animation.core.AnimationClockObservable
import androidx.compose.animation.core.AnimationClockObserver
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.InteractionState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.material.SwipeableConstants.DefaultAnimationSpec
import androidx.compose.material.SwipeableConstants.DefaultVelocityThreshold
import androidx.compose.material.SwipeableConstants.StandardResistanceFactor
import androidx.compose.material.SwipeableConstants.StiffResistanceFactor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.onCommit
import androidx.compose.runtime.remember
import androidx.compose.runtime.savedinstancestate.Saver
import androidx.compose.runtime.savedinstancestate.rememberSavedInstanceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.gesture.scrollorientationlocking.Orientation
import androidx.compose.ui.onPositioned
import androidx.compose.ui.platform.AnimationClockAmbient
import androidx.compose.ui.platform.DensityAmbient
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.annotation.FloatRange
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.sign
import kotlin.math.sin

/**
 * State of the [swipeable] modifier.
 *
 * This contains necessary information about any ongoing swipe or animation and provides methods
 * to change the state either immediately or by starting an animation. To create and remember a
 * [SwipeableState] with the default animation clock, use [rememberSwipeableState].
 *
 * @param initialValue The initial value of the state.
 * @param clock The animation clock that will be used to drive the animations.
 * @param animationSpec The default animation that will be used to animate to a new state.
 * @param confirmStateChange Optional callback invoked to confirm or veto a pending state change.
 */
@Stable
@ExperimentalMaterialApi
open class SwipeableState<T>(
    initialValue: T,
    clock: AnimationClockObservable,
    internal val animationSpec: AnimationSpec<Float> = DefaultAnimationSpec,
    internal val confirmStateChange: (newValue: T) -> Boolean = { true }
) {
    /**
     * The current value of the state.
     *
     * If no swipe or animation is in progress, this corresponds to the anchor at which the
     * [swipeable] is currently settled. If a swipe or animation is in progress, this corresponds
     * the last anchor at which the [swipeable] was settled before the swipe or animation started.
     */
    var value: T by mutableStateOf(initialValue)
        private set

    /**
     * Whether the state is currently animating.
     */
    var isAnimationRunning: Boolean by mutableStateOf(false)
        private set

    private val offsetState = mutableStateOf(0f)
    private val overflowState = mutableStateOf(0f)

    /**
     * The current position (in pixels) of the [swipeable].
     *
     * You should use this state to offset your content accordingly. The recommended way is to
     * use `Modifier.offsetPx`. This includes the resistance by default, if resistance is enabled.
     */
    val offset: State<Float> get() = offsetState

    /**
     * The amount by which the [swipeable] has been swiped past its bounds.
     */
    val overflow: State<Float> get() = overflowState

    private var minBound = 0f
    private var maxBound = 0f

    private val anchorsState = mutableStateOf(emptyMap<Float, T>())

    // TODO(calintat): Remove this when b/151158070 is fixed.
    private val animationClockProxy: AnimationClockObservable = object : AnimationClockObservable {
        override fun subscribe(observer: AnimationClockObserver) {
            isAnimationRunning = true
            clock.subscribe(observer)
        }

        override fun unsubscribe(observer: AnimationClockObserver) {
            isAnimationRunning = false
            clock.unsubscribe(observer)
        }
    }

    internal var anchors: Map<Float, T>
        get() {
            return anchorsState.value
        }
        set(anchors) {
            if (anchors != anchorsState.value) {
                anchorsState.value = anchors
                minBound = anchors.keys.minOrNull()!!
                maxBound = anchors.keys.maxOrNull()!!
                anchors.getOffset(value)?.let { holder.snapTo(it) }
            }
        }

    internal var thresholds: (Float, Float) -> Float by mutableStateOf({ _, _ -> 0f })

    internal var resistanceBasis = 0f
    internal var resistanceFactorAtMin = 0f
    internal var resistanceFactorAtMax = 0f

    internal val holder: AnimatedFloat = NotificationBasedAnimatedFloat(0f, animationClockProxy) {
        if (it in minBound..maxBound) {
            offsetState.value = it
            overflowState.value = 0f
        } else if (it < minBound) {
            val resistance = computeResistance(
                basis = resistanceBasis,
                factor = resistanceFactorAtMin,
                overflow = it - minBound
            )
            offsetState.value = minBound + resistance
            overflowState.value = it - minBound
        } else if (it > maxBound) {
            val resistance = computeResistance(
                basis = resistanceBasis,
                factor = resistanceFactorAtMax,
                overflow = it - maxBound
            )
            offsetState.value = maxBound + resistance
            overflowState.value = it - maxBound
        }
    }

    /**
     * The target value of the state.
     *
     * If a swipe is in progress, this is the value that the [swipeable] would animate to if the
     * swipe finished. If an animation is running, this is the target value of that animation.
     * Finally, if no swipe or animation is in progress, this is the same as the [value].
     */
    @ExperimentalMaterialApi
    val targetValue: T
        get() {
            val target = if (isAnimationRunning) {
                holder.targetValue
            } else {
                // TODO(calintat): Track current velocity (b/149549482) and use that here.
                computeTarget(
                    offset = offset.value,
                    lastValue = anchors.getOffset(value) ?: offset.value,
                    anchors = anchors.keys,
                    thresholds = thresholds,
                    velocity = 0f,
                    velocityThreshold = Float.POSITIVE_INFINITY
                )
            }
            return anchors[target] ?: value
        }

    /**
     * Information about the ongoing swipe or animation, if any. See [SwipeProgress] for details.
     *
     * If no swipe or animation is in progress, this returns `SwipeProgress(value, value, 1f)`.
     */
    @ExperimentalMaterialApi
    val progress: SwipeProgress<T>
        get() {
            val bounds = findBounds(offset.value, anchors.keys)
            val from: T
            val to: T
            val fraction: Float
            when (bounds.size) {
                0 -> {
                    from = value
                    to = value
                    fraction = 1f
                }
                1 -> {
                    from = anchors.getValue(bounds[0])
                    to = anchors.getValue(bounds[0])
                    fraction = 1f
                }
                else -> {
                    val (a, b) =
                        if (direction > 0f) {
                            bounds[0] to bounds[1]
                        } else {
                            bounds[1] to bounds[0]
                        }
                    from = anchors.getValue(a)
                    to = anchors.getValue(b)
                    fraction = (offset.value - a) / (b - a)
                }
            }
            return SwipeProgress(from, to, fraction)
        }

    /**
     * The direction in which the [swipeable] is moving, relative to the current [value].
     *
     * This will be either 1f if it is is moving from left to right or top to bottom, -1f if it is
     * moving from right to left or bottom to top, or 0f if no swipe or animation is in progress.
     */
    @ExperimentalMaterialApi
    val direction: Float
        get() = anchors.getOffset(value)?.let { sign(offset.value - it) } ?: 0f

    /**
     * Set the state to the target value immediately, without any animation.
     *
     * @param targetValue The new target value to set [value] to.
     */
    @ExperimentalMaterialApi
    fun snapTo(targetValue: T) {
        val targetOffset = anchors.getOffset(targetValue)
        requireNotNull(targetOffset) {
            "The target value must have an associated anchor."
        }
        value = targetValue
        holder.snapTo(targetOffset)
    }

    /**
     * Set the state to the target value by starting an animation.
     *
     * @param targetValue The new value to animate to.
     * @param anim The animation that will be used to animate to the new value.
     * @param onEnd Optional callback that will be invoked when the animation ended for any reason.
     */
    @ExperimentalMaterialApi
    fun animateTo(
        targetValue: T,
        anim: AnimationSpec<Float> = animationSpec,
        onEnd: ((AnimationEndReason, T) -> Unit)? = null
    ) {
        val targetOffset = anchors.getOffset(targetValue)
        requireNotNull(targetOffset) {
            "The target value must have an associated anchor."
        }
        holder.animateTo(targetOffset, anim) { endReason, endOffset ->
            anchors[endOffset]?.let {
                value = it
                onEnd?.invoke(endReason, it)
            }
        }
    }

    companion object {
        /**
         * The default [Saver] implementation for [SwipeableState].
         */
        fun <T : Any> Saver(
            clock: AnimationClockObservable,
            animationSpec: AnimationSpec<Float>,
            confirmStateChange: (T) -> Boolean
        ) = Saver<SwipeableState<T>, T>(
            save = { it.value },
            restore = { SwipeableState(it, clock, animationSpec, confirmStateChange) }
        )
    }
}

/**
 * Collects information about the ongoing swipe or animation in [swipeable].
 *
 * To access this information, use [SwipeableState.progress].
 *
 * @param from The state corresponding to the anchor we are moving away from.
 * @param to The state corresponding to the anchor we are moving towards.
 * @param fraction The fraction that the current position represents between [from] and [to].
 */
@Immutable
@ExperimentalMaterialApi
data class SwipeProgress<T>(
    val from: T,
    val to: T,
    @FloatRange(from = 0.0, to = 1.0) val fraction: Float
)

/**
 * Create and [remember] a [SwipeableState] with the default animation clock.
 *
 * @param initialValue The initial value of the state.
 * @param animationSpec The default animation that will be used to animate to a new state.
 * @param confirmStateChange Optional callback invoked to confirm or veto a pending state change.
 */
@Composable
@ExperimentalMaterialApi
fun <T : Any> rememberSwipeableState(
    initialValue: T,
    animationSpec: AnimationSpec<Float> = DefaultAnimationSpec,
    confirmStateChange: (newValue: T) -> Boolean = { true }
): SwipeableState<T> {
    val clock = AnimationClockAmbient.current.asDisposableClock()
    return rememberSavedInstanceState(
        clock,
        saver = SwipeableState.Saver(
            clock = clock,
            animationSpec = animationSpec,
            confirmStateChange = confirmStateChange
        )
    ) {
        SwipeableState(
            initialValue = initialValue,
            clock = clock,
            animationSpec = animationSpec,
            confirmStateChange = confirmStateChange
        )
    }
}

/**
 * Create and [remember] a [SwipeableState] which is kept in sync with another state, i.e:
 *  1. Whenever the [value] changes, the [SwipeableState] will be animated to that new value.
 *  2. Whenever the value of the [SwipeableState] changes (e.g. after a swipe), the owner of the
 *  [value] will be notified to update their state to the new value of the [SwipeableState] by
 *  invoking [onValueChange]. If the owner does not update their state to the provided value for
 *  some reason, then the [SwipeableState] will perform a rollback to the previous, correct value.
 */
@Composable
@ExperimentalMaterialApi
internal fun <T : Any> rememberSwipeableStateFor(
    value: T,
    onValueChange: (T) -> Unit,
    animationSpec: AnimationSpec<Float> = DefaultAnimationSpec
): SwipeableState<T> {
    val swipeableState = rememberSwipeableState(
        initialValue = value,
        animationSpec = animationSpec
    )
    val forceAnimationCheck = remember { mutableStateOf(false) }
    onCommit(value, forceAnimationCheck.value) {
        if (value != swipeableState.value) {
            swipeableState.animateTo(value)
        }
    }
    onCommit(swipeableState.value) {
        if (value != swipeableState.value) {
            onValueChange(swipeableState.value)
            forceAnimationCheck.value = !forceAnimationCheck.value
        }
    }
    return swipeableState
}

/**
 * Enable swipe gestures between a set of predefined states.
 *
 * To use this, you must provide a map of anchors (in pixels) to states (of type [T]).
 * Note that this map cannot be empty and cannot have two anchors mapped to the same state.
 *
 * When a swipe is detected, the offset of the [SwipeableState] will be updated with the swipe
 * delta. You should use this offset to move your content accordingly (see `Modifier.offsetPx`).
 * When the swipe ends, the offset will be animated to one of the anchors and when that anchor is
 * reached, the value of the [SwipeableState] will also be updated to the state corresponding to
 * the new anchor. The target anchor is calculated based on the provided positional [thresholds].
 *
 * Swiping is constrained between the min and max anchors. If the user attempts to swipe past
 * those bounds, a resistance effect will be applied. The amount of resistance at each edge can
 * be customised using the two resistance factor parameters, and you can disable resistance by
 * setting them to zero. Two default resistance factors are provided in [SwipeableConstants]:
 * - [StiffResistanceFactor] which conveys to the user that swiping is not available right now, and
 * - [StandardResistanceFactor] which conveys to the user that they have run out of things to see.
 *
 * For an example of a [swipeable] with three states, see:
 *
 * @sample androidx.compose.material.samples.SwipeableSample
 *
 * @param T The type of the state.
 * @param state The state of the [swipeable].
 * @param anchors Pairs of anchors and states, used to map anchors to states and vice versa.
 * @param thresholds Specifies where the thresholds between the states are. The thresholds will be
 * used to determine which state to animate to when swiping stops. This is represented as a lambda
 * that takes two states and returns the threshold between them in the form of a [ThresholdConfig].
 * Note that the order of the states corresponds to the swipe direction.
 * @param orientation The orientation in which the [swipeable] can be swiped.
 * @param enabled Whether this [swipeable] is enabled and should react to the user's input.
 * @param reverseDirection Whether to reverse the direction of the swipe, so a top to bottom
 * swipe will behave like bottom to top, and a left to right swipe will behave like right to left.
 * @param interactionState Optional [InteractionState] that will passed on to [Modifier.draggable].
 * @param velocityThreshold The threshold (in dp per second) that the end velocity has to exceed
 * in order to animate to the next state, even if the positional [thresholds] have not been reached.
 * @param resistanceFactorAtMin The resistance factor to be applied when swiping past the min bound.
 * @param resistanceFactorAtMax The resistance factor to be applied when swiping past the max bound.
 */
@ExperimentalMaterialApi
fun <T> Modifier.swipeable(
    state: SwipeableState<T>,
    anchors: Map<Float, T>,
    thresholds: (from: T, to: T) -> ThresholdConfig,
    orientation: Orientation,
    enabled: Boolean = true,
    reverseDirection: Boolean = false,
    interactionState: InteractionState? = null,
    velocityThreshold: Dp = DefaultVelocityThreshold,
    resistanceFactorAtMin: Float = StandardResistanceFactor,
    resistanceFactorAtMax: Float = StandardResistanceFactor
) = composed {
    require(anchors.isNotEmpty()) {
        "You must have at least one anchor."
    }
    require(anchors.values.distinct().count() == anchors.size) {
        "You cannot have two anchors mapped to the same state."
    }
    val density = DensityAmbient.current
    state.anchors = anchors
    state.thresholds = { a, b ->
        val from = anchors.getValue(a)
        val to = anchors.getValue(b)
        with(thresholds(from, to)) { density.computeThreshold(a, b) }
    }
    state.resistanceFactorAtMin = resistanceFactorAtMin
    state.resistanceFactorAtMax = resistanceFactorAtMax

    val draggable = Modifier.draggable(
        orientation = orientation,
        enabled = enabled,
        reverseDirection = reverseDirection,
        interactionState = interactionState,
        startDragImmediately = state.isAnimationRunning,
        onDragStopped = { velocity ->
            val lastAnchor = anchors.getOffset(state.value)!!
            val targetValue = computeTarget(
                offset = state.offset.value,
                lastValue = lastAnchor,
                anchors = anchors.keys,
                thresholds = state.thresholds,
                velocity = velocity,
                velocityThreshold = with(density) { velocityThreshold.toPx() }
            )
            val targetState = anchors[targetValue]
            if (targetState != null && state.confirmStateChange(targetState)) {
                state.animateTo(targetState)
            } else {
                // If the user vetoed the state change, rollback to the previous state.
                state.holder.animateTo(lastAnchor, state.animationSpec)
            }
        }
    ) { delta ->
        state.holder.snapTo(state.holder.value + delta)
    }
    draggable.onPositioned {
        state.resistanceBasis = when (orientation) {
            Orientation.Vertical -> it.size.height.toFloat()
            Orientation.Horizontal -> it.size.width.toFloat()
        }
    }
}

/**
 * Interface to compute a threshold between two anchors/states in a [swipeable].
 *
 * To define a [ThresholdConfig], consider using [FixedThreshold] and [FractionalThreshold].
 */
@Stable
@ExperimentalMaterialApi
interface ThresholdConfig {
    /**
     * Compute the value of the threshold (in pixels), once the values of the anchors are known.
     */
    fun Density.computeThreshold(fromValue: Float, toValue: Float): Float
}

/**
 * A fixed threshold will be at an [offset] away from the first anchor.
 *
 * @param offset The offset (in dp) that the threshold will be at.
 */
@Immutable
@ExperimentalMaterialApi
data class FixedThreshold(private val offset: Dp) : ThresholdConfig {
    override fun Density.computeThreshold(fromValue: Float, toValue: Float): Float {
        return fromValue + offset.toPx() * sign(toValue - fromValue)
    }
}

/**
 * A fractional threshold will be at a [fraction] of the way between the two anchors.
 *
 * @param fraction The fraction (between 0 and 1) that the threshold will be at.
 */
@Immutable
@ExperimentalMaterialApi
data class FractionalThreshold(
    @FloatRange(from = 0.0, to = 1.0) private val fraction: Float
) : ThresholdConfig {
    override fun Density.computeThreshold(fromValue: Float, toValue: Float): Float {
        return lerp(fromValue, toValue, fraction)
    }
}

private class NotificationBasedAnimatedFloat(
    initialValue: Float,
    clock: AnimationClockObservable,
    val onNewValue: (Float) -> Unit
) : AnimatedFloat(clock) {
    override var value = initialValue
        set(value) {
            field = value
            onNewValue(value)
        }
}

/**
 *  Given an offset x and a set of anchors, return a list of anchors:
 *   1. [ ] if the set of anchors is empty,
 *   2. [ x ] if x is equal to one of the anchors,
 *   3. [ min ] if min is the minimum anchor and x < min,
 *   4. [ max ] if max is the maximum anchor and x > max, or
 *   5. [ a , b ] if a and b are anchors such that a < x < b and b - a is minimal.
 */
private fun findBounds(
    offset: Float,
    anchors: Set<Float>
): List<Float> {
    // Find the anchors the target lies between.
    val a = anchors.filter { it <= offset }.maxOrNull()
    val b = anchors.filter { it >= offset }.minOrNull()

    return when {
        a == null ->
            // case 1 or 3
            listOfNotNull(b)
        b == null ->
            // case 4
            listOf(a)
        a == b ->
            // case 2
            listOf(offset)
        else ->
            // case 5
            listOf(a, b)
    }
}

private fun computeTarget(
    offset: Float,
    lastValue: Float,
    anchors: Set<Float>,
    thresholds: (Float, Float) -> Float,
    velocity: Float,
    velocityThreshold: Float
): Float {
    val bounds = findBounds(offset, anchors)
    return when (bounds.size) {
        0 -> lastValue
        1 -> bounds[0]
        else -> {
            val lower = bounds[0]
            val upper = bounds[1]
            if (lastValue <= offset) {
                // Swiping from lower to upper (positive).
                if (velocity >= velocityThreshold) {
                    return upper
                } else {
                    val threshold = thresholds(lower, upper)
                    if (offset < threshold) lower else upper
                }
            } else {
                // Swiping from upper to lower (negative).
                if (velocity <= -velocityThreshold) {
                    return lower
                } else {
                    val threshold = thresholds(upper, lower)
                    if (offset > threshold) upper else lower
                }
            }
        }
    }
}

internal fun computeResistance(
    basis: Float,
    factor: Float,
    overflow: Float
): Float {
    return if (basis == 0f || factor == 0f) {
        0f
    } else {
        val progress = (overflow / basis).coerceIn(-1f, 1f)
        basis / factor * sin(progress * PI.toFloat() / 2)
    }
}

private fun <T> Map<Float, T>.getOffset(state: T): Float? {
    return entries.firstOrNull { it.value == state }?.key
}

/**
 * Contains useful constants for [swipeable] and [SwipeableState].
 */
object SwipeableConstants {
    /**
     * The default animation used by [SwipeableState].
     */
    val DefaultAnimationSpec = SpringSpec<Float>()

    /**
     * The default velocity threshold (1.8 dp per millisecond) used by [swipeable].
     */
    val DefaultVelocityThreshold = 125.dp

    /**
     * A stiff resistance factor which indicates that swiping isn't available right now.
     */
    const val StiffResistanceFactor = 20f

    /**
     * A standard resistance factor which indicates that the user has run out of things to see.
     */
    const val StandardResistanceFactor = 10f
}