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

package com.hardbacknutter.nevertoomanybooks.settings;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.Menu;

import androidx.annotation.Discouraged;
import androidx.annotation.NonNull;

import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

public enum DialogAndMenuMode {
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

    private static final int UI_DIALOGS_MODE_CLASSIC = 0;
    private static final int UI_DIALOGS_MODE_BOTTOM_SHEET = 1;
    private static final int UI_DIALOGS_MODE_BY_SCREEN_SIZE = 2;

    private static final int UI_CONTEXT_MENUS_CLASSIC = 0;
    private static final int UI_CONTEXT_MENUS_BOTTOM_SHEET = 1;
    private static final int UI_CONTEXT_MENUS_BY_MENU_SIZE = 2;

    private static final Set<DialogAndMenuMode> POPUPS = Set.of(Start, End, Center, Anchored);

    /**
     * Determine how to show a <strong>dialog</strong>.
     * Either as a true Dialog, or as a BottomSheet.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     *
     * @return location
     */
    @NonNull
    public static DialogAndMenuMode getDialogMode(@NonNull final Context context) {
        final WindowSizeClass height = WindowSizeClass.getHeight(context);
        final WindowSizeClass width = WindowSizeClass.getWidth(context);

        final int mode = IntListPref.getInt(context, Prefs.PK_UI_DIALOGS_MODE,
                                            UI_DIALOGS_MODE_BY_SCREEN_SIZE);
        switch (mode) {
            case UI_DIALOGS_MODE_CLASSIC: {
                return Dialog;
            }
            case UI_DIALOGS_MODE_BOTTOM_SHEET: {
                return BottomSheet;
            }
            case UI_DIALOGS_MODE_BY_SCREEN_SIZE: {
                // Expanded/Expanded.
                // Expanded/Medium
                if (isLargeScreen(height, width)) {
                    // Large screen like tablets or desktops always use true dialog.
                    return Dialog;
                }

                // Expanded/Compact
                // Medium/Medium
                // Medium/Compact
                // Compact/Compact
                return BottomSheet;
            }
            default:
                throw new IllegalArgumentException("mode=" + mode);
        }
    }

    /**
     * Determine where to show a <strong>menu</strong>.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     * @param menu    to check
     *
     * @return menu location
     */
    @Discouraged(message = "use getLocation(Activity,Menu) if possible")
    @NonNull
    public static DialogAndMenuMode getMenuMode(@NonNull final Context context,
                                                @NonNull final Menu menu) {
        return getMenuMode(context,
                           WindowSizeClass.getHeight(context),
                           WindowSizeClass.getWidth(context),
                           menu);
    }

    /**
     * Determine where to show a <strong>menu</strong>.
     *
     * @param activity hosting Activity
     * @param menu     to check
     *
     * @return menu location
     */
    @NonNull
    public static DialogAndMenuMode getMenuMode(@NonNull final Activity activity,
                                                @NonNull final Menu menu) {
        return getMenuMode(activity,
                           WindowSizeClass.getHeight(activity),
                           WindowSizeClass.getWidth(activity),
                           menu);
    }

    @NonNull
    private static DialogAndMenuMode getMenuMode(@NonNull final Context context,
                                                 @NonNull final WindowSizeClass height,
                                                 @NonNull final WindowSizeClass width,
                                                 @NonNull final Menu menu) {
        final int mode = IntListPref.getInt(context, Prefs.PK_UI_CONTEXT_MENUS,
                                            UI_CONTEXT_MENUS_BY_MENU_SIZE);

        switch (mode) {
            case UI_CONTEXT_MENUS_CLASSIC: {
                return Anchored;
            }
            case UI_CONTEXT_MENUS_BOTTOM_SHEET: {
                return BottomSheet;
            }
            case UI_CONTEXT_MENUS_BY_MENU_SIZE: {
                // Expanded/Expanded.
                // Expanded/Medium
                if (isLargeScreen(height, width)) {
                    // Large screen like tablets or desktops always use a popup.
                    return Anchored;
                }

                // Expanded/Compact
                // Medium/Medium
                // Medium/Compact
                // Compact/Compact

                // Small menus are served as popup menus
                // anchored to the view to minimalize eye-movement.
                if (menu.size() < 5) {
                    return Anchored;
                }

                // menu with 5 or more items are large enough to warranty extra eye-movement
                return BottomSheet;
            }
            default:
                throw new IllegalArgumentException("mode=" + mode);
        }
    }

    /**
     * A large screen is defined as being
     * <ul>
     *     <li>Expanded/Expanded</li>
     *     <li>Expanded/Medium</li>
     * </ul>
     *
     * @param height size
     * @param width  size
     *
     * @return {@code true} when large
     */
    private static boolean isLargeScreen(@NonNull final WindowSizeClass height,
                                         @NonNull final WindowSizeClass width) {
        return ((height == WindowSizeClass.Medium || height == WindowSizeClass.Expanded)
                && width == WindowSizeClass.Expanded)
               ||
               ((width == WindowSizeClass.Medium || width == WindowSizeClass.Expanded)
                && height == WindowSizeClass.Expanded);
    }

    /**
     * Check if this is a popup mode.
     *
     * @return flag
     */
    public boolean isPopup() {
        return POPUPS.contains(this);
    }
}
