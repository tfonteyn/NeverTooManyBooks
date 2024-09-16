/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.Discouraged;
import androidx.annotation.NonNull;
import androidx.window.layout.WindowMetrics;
import androidx.window.layout.WindowMetricsCalculator;

import java.util.Objects;

/**
 * Screen size support.
 * <p>
 * <a href="https://developer.android.com/guide/topics/large-screens/support-different-screen-sizes">
 * support-different-screen-sizes</a>
 * <p>
 * This class is basically a rewrite of using androidx.window.core.*;
 * The advantage (IMHO) is that we get to use an {@code enum} instead
 * of a set of class singletons.
 * <pre>
 *     {@code
 *         WindowMetrics windowMetrics =
 *                 WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity);
 *         int width = windowMetrics.getBounds().width();
 *         int height = windowMetrics.getBounds().height();
 *         float density = activity.getResources().getDisplayMetrics().density;
 *         WindowSizeClass windowSizeClass = WindowSizeClass.compute(width / density,
 *                                                                   height / density);
 *         final WindowHeightSizeClass windowHeightSizeClass =
 *              windowSizeClass.getWindowHeightSizeClass();
 *         final WindowWidthSizeClass windowWidthSizeClass =
 *              windowSizeClass.getWindowWidthSizeClass();
 *     }
 * </pre>
 */
public final class ScreenSize {

    @NonNull
    private final Value width;

    @NonNull
    private final Value height;

    private ScreenSize(@NonNull final Value width,
                       @NonNull final Value height) {
        this.width = width;
        this.height = height;
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
    public static ScreenSize compute(@NonNull final Context context) {
        return compute(getActivity(context));
    }

    /**
     * Constructor.
     *
     * @param activity to use
     *
     * @return window size definition
     */
    @NonNull
    public static ScreenSize compute(@NonNull final Activity activity) {
        final WindowMetrics metrics = WindowMetricsCalculator
                .getOrCreate().computeCurrentWindowMetrics(activity);

        final float density = activity.getResources().getDisplayMetrics().density;
        final Rect bounds = metrics.getBounds();
        final float widthDp = bounds.width() / density;
        final Value width;
        if (widthDp < 600f) {
            width = Value.Compact;
        } else if (widthDp < 840f) {
            width = Value.Medium;
        } else {
            width = Value.Expanded;
        }

        final float heightDp = bounds.height() / density;
        final Value height;
        if (heightDp < 480f) {
            height = Value.Compact;
        } else if (heightDp < 900f) {
            height = Value.Medium;
        } else {
            height = Value.Expanded;
        }

        return new ScreenSize(width, height);
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
    private static Activity getActivity(@NonNull final Context context)
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

    /**
     * Large screen definition.
     * The WIDTH/HEIGHT is:
     * <ul>
     *     <li>Expanded/Expanded</li>
     *     <li>Expanded/Medium</li>
     *     <li>Medium/Expanded</li>
     * </ul>
     *
     * @return {@code true} when large
     */
    public boolean isLargeScreen() {
        return width == Value.Expanded && height != Value.Compact
               ||
               height == Value.Expanded && width != Value.Compact;
    }

    /**
     * Small screen definition.
     * The WIDTH/HEIGHT is:
     * <ul>
     *     <li>Medium/Compact</li>
     *     <li>Compact/Medium</li>
     *     <li>Compact/Compact</li>
     * </ul>
     *
     * @return {@code true} when small
     */
    public boolean isSmallScreen() {
        return width == Value.Compact && height != Value.Expanded
               ||
               height == Value.Compact && width != Value.Expanded;
    }

    @Override
    public boolean equals(final Object o) {
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
        Compact,
        Medium,
        Expanded
    }
}
