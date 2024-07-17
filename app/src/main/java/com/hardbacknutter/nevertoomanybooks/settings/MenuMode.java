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

import android.content.Context;
import android.view.Gravity;
import android.view.Menu;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

public enum MenuMode {
    /** Show a popup window with {@link Gravity#START}. */
    Start,
    /** Show a popup window with {@link Gravity#END}. */
    End,
    /** Show a popup window with {@link Gravity#CENTER}. */
    Center,
    /** Show a popup window anchored to a given view. */
    Anchored,
    /** Show a BottomSheet. */
    BottomSheet;

    /**
     * How context/row menus will be shown.
     * <p>
     * {@code int}
     */
    private static final String PK_UI_CONTEXT_MENUS = "ui.menu.context.mode";

    /** Always show as traditional PopupMenu. */
    private static final int UI_CONTEXT_MENUS_CLASSIC = 0;
    /** Always show as a BottomSheet. */
    private static final int UI_CONTEXT_MENUS_BOTTOM_SHEET = 1;
    /** The default; either as PopupMenu or as BottomSheet depending on screen size. */
    private static final int UI_CONTEXT_MENUS_BY_MENU_SIZE = 2;

    /** Different behavior depending on size < THRESHOLD versus size >= THRESHOLD. */
    private static final int MENU_SIZE_THRESHOLD = 5;

    /**
     * Determine where to show a <strong>menu</strong>.
     * Small popup-menus will be anchored, longer ones will be centered.
     * Depending on user-preference a BottomSheet is used instead.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     * @param menu    to check
     *
     * @return mode
     */
    @NonNull
    public static MenuMode getMode(@NonNull final Context context,
                                   @NonNull final Menu menu) {
        final int mode = IntListPref.getInt(context, PK_UI_CONTEXT_MENUS,
                                            UI_CONTEXT_MENUS_BY_MENU_SIZE);

        switch (mode) {
            case UI_CONTEXT_MENUS_CLASSIC: {
                return menu.size() < MENU_SIZE_THRESHOLD ? Anchored : Center;
            }
            case UI_CONTEXT_MENUS_BOTTOM_SHEET: {
                return BottomSheet;
            }
            case UI_CONTEXT_MENUS_BY_MENU_SIZE: {
                if (WindowSizeClass.isLargeScreen(context)) {
                    // Expanded/Expanded.
                    // Expanded/Medium
                    return menu.size() < MENU_SIZE_THRESHOLD ? Anchored : Center;
                } else {
                    // Expanded/Compact
                    // Medium/Medium
                    // Medium/Compact
                    // Compact/Compact
                    return menu.size() < MENU_SIZE_THRESHOLD ? Anchored : BottomSheet;
                }
            }
            default:
                throw new IllegalArgumentException("mode=" + mode);
        }
    }

    /**
     * Check if this is a popup mode.
     *
     * @return flag
     */
    public boolean isPopup() {
        return this != BottomSheet;
    }
}
