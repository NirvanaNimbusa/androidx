/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ui.layout

import androidx.compose.Stable
import androidx.ui.core.Constraints
import androidx.ui.core.IntrinsicMeasurable
import androidx.ui.core.IntrinsicMeasureScope
import androidx.ui.core.LayoutDirection
import androidx.ui.core.LayoutModifier
import androidx.ui.core.Measurable
import androidx.ui.core.MeasureScope
import androidx.ui.core.Modifier
import androidx.ui.core.satisfiedBy
import androidx.ui.unit.IntSize
import androidx.ui.util.annotation.FloatRange
import kotlin.math.roundToInt

/**
 * Attempts to size the content to match a specified aspect ratio by trying to match one of the
 * incoming constraints in the following order:
 * [Constraints.maxWidth], [Constraints.maxHeight], [Constraints.minWidth], [Constraints.minHeight].
 * The size in the other dimension is determined by the aspect ratio.
 *
 * Example usage:
 * @sample androidx.ui.layout.samples.SimpleAspectRatio
 *
 * @param ratio the desired width/height positive ratio
 */
@Stable
fun Modifier.aspectRatio(
    @FloatRange(from = 0.0, to = 3.4e38 /* POSITIVE_INFINITY */, fromInclusive = false)
    ratio: Float
) = this + AspectRatioModifier(ratio)

private data class AspectRatioModifier(val aspectRatio: Float) : LayoutModifier {
    init {
        require(aspectRatio > 0) { "aspectRatio $aspectRatio must be > 0" }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
        layoutDirection: LayoutDirection
    ): MeasureScope.MeasureResult {
        val size = constraints.findSizeWith(aspectRatio)
        val wrappedConstraints = if (size != null) {
            Constraints.fixed(size.width, size.height)
        } else {
            constraints
        }
        val placeable = measurable.measure(wrappedConstraints)
        return layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
        layoutDirection: LayoutDirection
    ) = if (height != Constraints.Infinity) {
        (height * aspectRatio).roundToInt()
    } else {
        measurable.minIntrinsicWidth(height)
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
        layoutDirection: LayoutDirection
    ) = if (height != Constraints.Infinity) {
        (height * aspectRatio).roundToInt()
    } else {
        measurable.maxIntrinsicWidth(height)
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
        layoutDirection: LayoutDirection
    ) = if (width != Constraints.Infinity) {
        (width / aspectRatio).roundToInt()
    } else {
        measurable.minIntrinsicHeight(width)
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
        layoutDirection: LayoutDirection
    ) = if (width != Constraints.Infinity) {
        (width / aspectRatio).roundToInt()
    } else {
        measurable.maxIntrinsicHeight(width)
    }

    private fun Constraints.findSizeWith(aspectRatio: Float): IntSize? {
        val maxWidth = this.maxWidth
        if (maxWidth != Constraints.Infinity) {
            val height = (maxWidth / aspectRatio).roundToInt()
            if (height > 0) {
                val size = IntSize(maxWidth, height)
                if (satisfiedBy(size)) {
                    return size
                }
            }
        }
        val maxHeight = this.maxHeight
        if (maxHeight != Constraints.Infinity) {
            val width = (maxHeight * aspectRatio).roundToInt()
            if (width > 0) {
                val size = IntSize(width, maxHeight)
                if (satisfiedBy(size)) {
                    return size
                }
            }
        }
        val minWidth = this.minWidth
        val height = (minWidth / aspectRatio).roundToInt()
        if (height > 0) {
            val size = IntSize(minWidth, height)
            if (satisfiedBy(size)) {
                return size
            }
        }
        val minHeight = this.minHeight
        val width = (minHeight * aspectRatio).roundToInt()
        if (width > 0) {
            val size = IntSize(width, minHeight)
            if (satisfiedBy(size)) {
                return size
            }
        }
        return null
    }
}