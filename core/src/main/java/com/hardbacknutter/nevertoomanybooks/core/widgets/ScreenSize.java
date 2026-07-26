/*
 * @Copyright 2018-2026 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */

package com.hardbacknutter.nevertoomanybooks.core.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.Discouraged;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiContext;
import androidx.window.layout.WindowMetrics;
import androidx.window.layout.WindowMetricsCalculator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;

/**
 * Screen size support.
 * <p>
 * <a href="https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes">
 * use-window-size-classes</a>
 * <p>
 * This class is basically a rewrite of the above API.
 * The advantage (IMHO) is that we get to use simple {@code enum}s instead
 * of rather convoluted and kotlin-only mess.
 */
public final class ScreenSize {

    private static final String TAG = "ScreenSize";

    /** androidx.window.core.layout.WindowSizeClass. */
    private static final int WIDTH_DP_MEDIUM_LOWER_BOUND = 600;
    private static final int WIDTH_DP_EXPANDED_LOWER_BOUND = 840;
    private static final int WIDTH_DP_LARGE_LOWER_BOUND = 1200;
    private static final int WIDTH_DP_EXTRA_LARGE_LOWER_BOUND = 1600;

    private static final int HEIGHT_DP_MEDIUM_LOWER_BOUND = 480;
    private static final int HEIGHT_DP_EXPANDED_LOWER_BOUND = 900;

    @NonNull
    private final Value width;

    @NonNull
    private final Value height;
    @NonNull
    private final WindowMetrics metrics;

    private ScreenSize(@NonNull final Value width,
                       @NonNull final Value height,
                       @NonNull final WindowMetrics metrics) {
        this.width = width;
        this.height = height;
        this.metrics = metrics;
    }

    /**
     * Constructor.
     *
     * @param context Current context - this <strong>MUST</strong> be a UI context
     *
     * @return window size definition
     */
    @Discouraged(message = "use compute(Activity) if possible")
    @NonNull
    public static ScreenSize compute(@NonNull @UiContext final Context context) {
        return compute(getActivity(context));
    }

    /**
     * Constructor.
     *
     * @param activity to use
     *
     * @return window size definition
     *
     * @see <a href="https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/window/window-core/src/commonMain/kotlin/androidx/window/core/layout/WindowSizeClass.kt#144">
     *         WindowSizeClass.kt#144</a>
     */
    @NonNull
    public static ScreenSize compute(@NonNull final Activity activity) {
        final WindowMetrics metrics = WindowMetricsCalculator
                .getOrCreate().computeCurrentWindowMetrics(activity);

        final float density = metrics.getDensity();
        final Rect bounds = metrics.getBounds();
        final float widthDp = bounds.width() / density;
        final float heightDp = bounds.height() / density;

        final List<Value> list = Arrays.asList(Value.values());
        Collections.reverse(list);

        final Predicate<Value> heightPredicate;
        final Predicate<Value> widthPredicate;
        if (widthDp > heightDp) {
            // landscape, use the opposite lowerBound values
            widthPredicate = value -> widthDp > value.heightLowerBound;
            heightPredicate = value -> heightDp > value.widthLowerBound;
        } else {
            // portrait
            widthPredicate = value -> widthDp > value.widthLowerBound;
            heightPredicate = value -> heightDp > value.heightLowerBound;
        }

        final Value width = list.stream()
                                .filter(widthPredicate)
                                .findFirst()
                                .orElse(Value.Compact);

        final Value height = list.stream()
                                 .filter(heightPredicate)
                                 .findFirst()
                                 .orElse(Value.Compact);

        final ScreenSize screenSize = new ScreenSize(width, height, metrics);

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "widthDp=" + widthDp + "|heightDp=" + heightDp
                       + "|screenSize=" + screenSize);
        }

        return screenSize;
    }

    /**
     * Unwrap the given context to get the Activity.
     * <p>
     * This code is based on {@code androidx.window.layout.util.ContextUtils.unwrapUiContext}
     *
     * @param context Current context - this <strong>MUST</strong> be a UI context
     *
     * @return Activity
     *
     * @throws IllegalArgumentException if the given Context is not a UiContext
     */
    @NonNull
    private static Activity getActivity(@NonNull @UiContext final Context context)
            throws IllegalArgumentException {
        Context iterator = context;
        while (iterator instanceof ContextWrapper) {
            if (iterator instanceof Activity) {
                return (Activity) iterator;
            }
            iterator = ((ContextWrapper) iterator).getBaseContext();
        }
        throw new IllegalArgumentException("Context is not a UiContext");
    }

    /**
     * Width.
     * <ul>
     *     <li>{@link Value#Compact} -> base; phone in portrait</li>
     *     <li>{@link Value#Medium} -> sw600; phone in landscape + tablet in portrait</li>
     *     <li>{@link Value#Expanded} -> sw800; tablet in landscape</li>
     * </ul>
     *
     * @return the Width Value
     */
    @NonNull
    public Value getWidth() {
        return width;
    }

    /**
     * Height.
     * <ul>
     *     <li>{@link Value#Compact} -> base; small phone in landscape</li>
     *     <li>{@link Value#Medium} -> sw600; phone in portrait + tablet in landscape</li>
     *     <li>{@link Value#Expanded} -> sw800; tablet in portrait</li>
     * </ul>
     *
     * @return the Height Value
     */
    @NonNull
    public Value getHeight() {
        return height;
    }

    @NonNull
    public WindowMetrics getMetrics() {
        return metrics;
    }

    /**
     * Large screen definition.
     * BOTH width and height must be at least {@link Value#Medium}.
     *
     * @return {@code true} when large
     */
    public boolean isLargeScreen() {
        return width.isAtLeast(Value.Medium) && height.isAtLeast(Value.Medium);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ScreenSize that = (ScreenSize) o;
        return width == that.width && height == that.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height);
    }

    @Override
    @NonNull
    public String toString() {
        return "ScreenSize{"
               + "width=" + width
               + ", height=" + height
               + '}';
    }

    /**
     * Screen size definitions. See {@link #width} and {@link #height} for documentation.
     * <p>
     * Never change the order!
     */
    public enum Value {
        /**
         * base; phone in portrait.
         * base; small phone in landscape.
         */
        Compact(0, 0),
        /**
         * sw600; phone in landscape + tablet in portrait.
         * sw600; phone in portrait + tablet in landscape.
         */
        Medium(WIDTH_DP_MEDIUM_LOWER_BOUND, HEIGHT_DP_MEDIUM_LOWER_BOUND),
        /**
         * sw800; tablet in landscape.
         * sw800; tablet in portrait;
         */
        Expanded(WIDTH_DP_EXPANDED_LOWER_BOUND, HEIGHT_DP_EXPANDED_LOWER_BOUND);

        private final int widthLowerBound;
        private final int heightLowerBound;

        Value(final int widthLowerBound,
              final int heightLowerBound) {
            this.widthLowerBound = widthLowerBound;
            this.heightLowerBound = heightLowerBound;
        }

        /**
         * {@code this} >= {@code that}.
         *
         * @param that arg
         *
         * @return boolean
         */
        public boolean isAtLeast(@NonNull final Value that) {
            return this.ordinal() >= that.ordinal();
        }
    }
}
