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

package com.hardbacknutter.nevertoomanybooks.widgets.popupmenu;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.Menu;

import androidx.annotation.Discouraged;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

public enum ExtMenuLocation {
    /** Show a popup window with {@link Gravity#START}. */
    Start,
    /** Show a popup window with {@link Gravity#END}. */
    End,
    /** Show a popup window with {@link Gravity#CENTER} */
    Center,
    /** Show a popup window anchored to a given view. */
    Anchored,
    /** Show a Dialog; Fullscreen or Floating. */
    Dialog,
    /** Show a BottomSheet. */
    BottomSheet;

    private static final Set<ExtMenuLocation> POPUPS = Set.of(Start, End, Center, Anchored);

    /**
     * Determine where to show menus.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     * @param menu    to check
     *
     * @return menu location
     */
    @Discouraged(message = "use getLocation(Activity,Menu) if possible")
    @NonNull
    public static ExtMenuLocation getLocation(@NonNull final Context context,
                                              @Nullable final Menu menu) {
        return getLocation(WindowSizeClass.getWidth(context), menu);
    }

    /**
     * Determine where to show menus.
     *
     * @param activity hosting Activity
     * @param menu     to check
     *
     * @return menu location
     */
    @NonNull
    public static ExtMenuLocation getLocation(@NonNull final Activity activity,
                                              @Nullable final Menu menu) {
        return getLocation(WindowSizeClass.getWidth(activity), menu);
    }

    @NonNull
    private static ExtMenuLocation getLocation(@NonNull final WindowSizeClass windowSize,
                                               @Nullable final Menu menu) {
        // The decision process code is written for clarity, NOT for compactness or optimized

        // If we have no menu, then we predetermined that we're using
        // either a Dialog or a BottomSheet, and never a PopupWindow.
        if (menu == null) {
            switch (windowSize) {
                case Expanded: {
                    // Tablets always use a Dialog
                    return Dialog;
                }
                case Medium:
                case Compact: {
                    return BottomSheet;
                }
                default:
                    throw new IllegalArgumentException("menu==null, windowSize=" + windowSize);
            }
        }

        // regardless of menu size, tablets always use a Dialog
        if (windowSize == WindowSizeClass.Expanded) {
            return Dialog;
        }

        // Small menus are best served as popup menus
        // anchored to the view to minimalize eye-movement.
        if (menu.size() < 5 && windowSize == WindowSizeClass.Medium) {
            return ExtMenuLocation.Anchored;
        }

        // We eiter have a Compact screen, or a menu with 5 or more items.
        return ExtMenuLocation.BottomSheet;
    }

    public boolean isPopup() {
        return POPUPS.contains(this);
    }
}
