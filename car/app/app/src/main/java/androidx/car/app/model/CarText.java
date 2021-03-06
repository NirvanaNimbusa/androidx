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

package androidx.car.app.model;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;

import android.text.SpannableString;
import android.text.Spanned;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.car.app.utils.CollectionUtils;
import androidx.car.app.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A model that represents text to display in the car screen.
 *
 * <h2>Text handling in the library</h2>
 *
 * Models that consume text strings take a {@link CharSequence} type as the parameter type. These
 * strings can contain spans that are applied to the text and allow, for example, changing the
 * color of the text, introducing inline images, or displaying a time duration.
 * As described in
 * <a href="https://developer.android.com/guide/topics/text/spans">the span documentation</a>,
 * you can use types such as {@link SpannableString} or {@link android.text.SpannedString} to
 * create the strings with the spans
 *
 * <p>The Car App Library only supports a specific set of spans of type {@link CarSpan}. Further,
 * individual APIs in the library that take text as input may only support a certain subset of
 * {@link CarSpan}s. Spans that are not supported will be simply ignored by the host.
 *
 * <p>By default and unless explicitly documented in the individual APIs that take a text
 * parameter as input, spans for that API are not supported and will be ignored.
 *
 * <p>For example, the {@link Row.Builder#addText(CharSequence)} API documents that
 * {@link ForegroundCarColorSpan} instances can be used to color the text of the row. This means any
 * other types of spans except {@link ForegroundCarColorSpan} will be ignored.
 *
 * <p>{@link CarText} instances represent the text that was passed by the app through a
 * {@link CharSequence}, with the non-{@link CarSpan} spans removed.
 *
 * <p>The {@link CarText#toString} method can be used to get a string representation of the string,
 * whereas the {@link CarText#toCharSequence()} method returns the reconstructed
 * {@link CharSequence}, with the non{@link CarSpan} spans removed.
 *
 */
public final class CarText {
    @Keep
    private final String mText;
    @Keep
    private final List<SpanWrapper> mSpans;

    /**
     * Returns {@code true} if the {@code carText} is {@code null} or an empty string, {@code
     * false} otherwise.
     */
    public static boolean isNullOrEmpty(@Nullable CarText carText) {
        return carText == null || carText.isEmpty();
    }

    /**
     * Returns a {@link CarText} instance for the given {@link CharSequence}.
     *
     * <p>Only {@link CarSpan} type spans are allowed in a {@link CarText}, other spans will be
     * removed from the provided {@link CharSequence}.
     */
    @NonNull
    public static CarText create(@NonNull CharSequence text) {
        return new CarText(text);
    }

    /** Returns whether the text string is empty. */
    public boolean isEmpty() {
        return mText.isEmpty();
    }

    @NonNull
    @Override
    public String toString() {
        return mText;
    }

    /**
     * Returns the {@link CharSequence} corresponding to this text.
     *
     * <p>Spans that are not of type {@link CarSpan} that were passed when creating the
     * {@link CarText} instance will not be present in the returned {@link CharSequence}.
     *
     * @see CarText#create(CharSequence)
     */
    @NonNull
    public CharSequence toCharSequence() {
        SpannableString spannableString = new SpannableString(mText == null ? "" : mText);
        for (SpanWrapper spanWrapper : CollectionUtils.emptyIfNull(mSpans)) {
            spannableString.setSpan(
                    spanWrapper.getCarSpan(),
                    spanWrapper.getStart(),
                    spanWrapper.getEnd(),
                    spanWrapper.getFlags());
        }
        return spannableString;
    }

    /**
     * Returns a shortened string from the input {@code text}.
     *
     * @hide
     */
    @RestrictTo(LIBRARY)
    @Nullable
    public static String toShortString(@Nullable CarText text) {
        return text == null ? null : StringUtils.shortenString(text.toString());
    }

    private CarText() {
        mText = "";
        mSpans = Collections.emptyList();
    }

    private CarText(CharSequence text) {
        mText = text.toString();

        List<SpanWrapper> spans = new ArrayList<>();
        if (text instanceof Spanned) {
            Spanned spanned = (Spanned) text;

            for (Object span : spanned.getSpans(0, text.length(), Object.class)) {
                if (span instanceof CarSpan) {
                    spans.add(new SpanWrapper(spanned, (CarSpan) span));
                }
            }
        }
        mSpans = CollectionUtils.unmodifiableCopy(spans);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CarText)) {
            return false;
        }
        CarText otherText = (CarText) other;
        return Objects.equals(mText, otherText.mText) && Objects.equals(mSpans, otherText.mSpans);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mText, mSpans);
    }

    /**
     * Wraps a span to send it to the host.
     */
    private static class SpanWrapper {
        @Keep
        private final int mStart;
        @Keep
        private final int mEnd;
        @Keep
        private final int mFlags;
        @Keep
        @NonNull
        private final CarSpan mCarSpan;

        SpanWrapper(@NonNull Spanned spanned, @NonNull CarSpan carSpan) {
            mStart = spanned.getSpanStart(carSpan);
            mEnd = spanned.getSpanEnd(carSpan);
            mFlags = spanned.getSpanFlags(carSpan);
            mCarSpan = carSpan;
        }

        SpanWrapper() {
            mStart = 0;
            mEnd = 0;
            mFlags = 0;
            mCarSpan = new CarSpan();
        }

        public int getStart() {
            return mStart;
        }

        public int getEnd() {
            return mEnd;
        }

        public int getFlags() {
            return mFlags;
        }

        @NonNull
        public CarSpan getCarSpan() {
            return mCarSpan;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SpanWrapper)) {
                return false;
            }
            SpanWrapper wrapper = (SpanWrapper) other;
            return mStart == wrapper.mStart
                    && mEnd == wrapper.mEnd
                    && mFlags == wrapper.mFlags
                    && Objects.equals(mCarSpan, wrapper.mCarSpan);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mStart, mEnd, mFlags, mCarSpan);
        }

        @NonNull
        @Override
        public String toString() {
            return "[" + mCarSpan + ": " + mStart + ", " + mEnd + ", flags: " + mFlags + "]";
        }
    }
}
