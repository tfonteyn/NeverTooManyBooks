/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;

import androidx.annotation.NonNull;
import androidx.window.layout.WindowMetrics;
import androidx.window.layout.WindowMetricsCalculator;

/**
 * Screen size support.
 * <p>
 * <a href="https://developer.android.com/guide/topics/large-screens/support-different-screen-sizes">
 * support-different-screen-sizes</a>
 * <p>
 * Width:
 * <ul>
 *     <li>{@link #Compact} -> base; phone in portrait</li>
 *     <li>{@link #Medium} -> sw600; phone in landscape + tablet in portrait</li>
 *     <li>{@link #Expanded} -> sw800; tablet in landscape</li>
 * </ul>
 * Height:
 * <ul>
 *     <li>{@link #Compact} -> base; small phone in landscape</li>
 *     <li>{@link #Medium} -> sw600; phone in portrait + tablet in landscape</li>
 *     <li>{@link #Expanded} -> sw800; tablet in portrait</li>
 * </ul>
 * <p>
 * Never change the order!
 */
public enum WindowSizeClass {
    Compact,
    Medium,
    Expanded;

    @NonNull
    public static WindowSizeClass getWidth(@NonNull final Activity activity) {
        final WindowMetrics metrics = WindowMetricsCalculator
                .getOrCreate().computeCurrentWindowMetrics(activity);

        final float widthDp = metrics.getBounds().width()
                              / activity.getResources().getDisplayMetrics().density;
        if (widthDp < 600f) {
            return Compact;
        } else if (widthDp < 840f) {
            return Medium;
        } else {
            return Expanded;
        }
    }

    @NonNull
    public static WindowSizeClass getHeight(@NonNull final Activity activity) {
        final WindowMetrics metrics = WindowMetricsCalculator
                .getOrCreate().computeCurrentWindowMetrics(activity);

        final float heightDp = metrics.getBounds().height()
                               / activity.getResources().getDisplayMetrics().density;
        if (heightDp < 480f) {
            return Compact;
        } else if (heightDp < 900f) {
            return Medium;
        } else {
            return Expanded;
        }
    }

    @NonNull
    public static WindowSizeClass getWidth(@NonNull final Context context) {
        return getWidth(getActivity(context));
    }

    @NonNull
    public static WindowSizeClass getHeight(@NonNull final Context context) {
        return getHeight(getActivity(context));
    }

    @NonNull
    private static Activity getActivity(@NonNull final Context context) {
        // Gross way of unwrapping the Activity
        Context tmp = context;
        while (tmp instanceof ContextWrapper) {
            if (tmp instanceof Activity) {
                return (Activity) tmp;
            }
            tmp = ((ContextWrapper) tmp).getBaseContext();
        }
        throw new IllegalStateException();
    }
}
