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

package androidx.compose.ui.test

import androidx.compose.animation.core.ManualFrameClock
import androidx.compose.animation.core.advanceClockMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/**
 * Runs a new coroutine and blocks the current thread interruptibly until it completes, passing a
 * new [ManualFrameClock] to the code [block]. This is intended to be used by tests instead of
 * [runBlocking] if they want to use a [ManualFrameClock].
 *
 * The clock will start at time 0L and should be driven manually from your test, from the
 * [main dispatcher][Dispatchers.Main]. Pass the clock to the animation that you want to
 * control in your test, and then [advance][advanceClockMillis] it as necessary. After the block
 * has completed, the clock will be forwarded with 10 second increments until it has drained all
 * work that took frames from that clock. If the work never ends, this function never ends, so
 * make sure that all animations driven by this clock are finite.
 *
 * For example:
 * ```
 * @Test
 * fun myTest() = runWithManualClock { clock ->
 *     // set some compose content
 *     testRule.setContent {
 *         MyAnimation(animationClock = clock)
 *     }
 *     // advance the clock by 1 second
 *     withContext(TestUiDispatcher.Main) {
 *         clock.advanceClock(1000L)
 *     }
 *     // await composition(s)
 *     waitForIdle()
 *     // check if the animation is finished or not
 *     if (clock.hasAwaiters) {
 *         println("The animation is still running")
 *     } else {
 *         println("The animation is done")
 *     }
 * }
 * ```
 * Here, `MyAnimation` is an animation that takes frames from the `animationClock` passed to it.
 */
@ExperimentalTestApi
fun <R> runBlockingWithManualClock(
    block: suspend CoroutineScope.(clock: ManualFrameClock) -> R
) {
    val clock = ManualFrameClock(0L)
    return runBlocking(clock) {
        block(clock)
        while (clock.hasAwaiters) {
            clock.advanceClockMillis(10_000L)
            // Give awaiters the chance to await again
            yield()
        }
    }
}