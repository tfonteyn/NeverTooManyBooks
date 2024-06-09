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

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

public enum DialogMode {
    /** Show a Dialog; Fullscreen or Floating. */
    Dialog,
    /** Show a BottomSheet (Menus and BottomSheets). */
    BottomSheet;

    /**
     * How dialogs will be shown.
     * <p>
     * {@code int}
     */
    public static final String PK_UI_DIALOGS_MODE = "ui.dialog.mode";

    /** Always show as traditional Dialog windows. */
    private static final int UI_DIALOGS_MODE_CLASSIC = 0;
    /** Always show as a BottomSheet. */
    private static final int UI_DIALOGS_MODE_BOTTOM_SHEET = 1;
    /** The default; either as Dialog or as BottomSheet depending on screen size. */
    private static final int UI_DIALOGS_MODE_BY_SCREEN_SIZE = 2;

    /**
     * Determine how to show a <strong>dialog</strong>.
     * Either as a true Dialog, or as a BottomSheet.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     *
     * @return location: either {@link #Dialog} or {@link #BottomSheet}
     */
    @NonNull
    public static DialogMode getMode(@NonNull final Context context) {
        final int mode = IntListPref.getInt(context, PK_UI_DIALOGS_MODE,
                                            UI_DIALOGS_MODE_BY_SCREEN_SIZE);
        switch (mode) {
            case UI_DIALOGS_MODE_CLASSIC: {
                return Dialog;
            }
            case UI_DIALOGS_MODE_BOTTOM_SHEET: {
                return BottomSheet;
            }
            case UI_DIALOGS_MODE_BY_SCREEN_SIZE: {
                if (WindowSizeClass.isLargeScreen(context)) {
                    // Expanded/Expanded.
                    // Expanded/Medium
                    return Dialog;
                } else {
                    // Expanded/Compact
                    // Medium/Medium
                    // Medium/Compact
                    // Compact/Compact
                    return BottomSheet;
                }
            }
            default:
                throw new IllegalArgumentException("mode=" + mode);
        }
    }
}
