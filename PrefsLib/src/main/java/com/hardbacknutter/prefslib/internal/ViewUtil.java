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

package com.hardbacknutter.prefslib.internal;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

// ok, bad practice... a util bin...
final class ViewUtil {
    private ViewUtil() {
    }

    /**
     * Recursively enable/disabled the given view and all its children.
     *
     * @param view    to change
     * @param enabled flag
     */
    static void setViewAndChildrenEnabled(@NonNull final View view,
                                          final boolean enabled) {
        // Dim when disabled
        view.setAlpha(enabled ? 1.0f : 0.5f);
        recurseSetViewAndChildrenEnabled(view, enabled);
    }

    private static void recurseSetViewAndChildrenEnabled(@NonNull final View view,
                                                         final boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                recurseSetViewAndChildrenEnabled(group.getChildAt(i), enabled);
            }
        }
    }
}
