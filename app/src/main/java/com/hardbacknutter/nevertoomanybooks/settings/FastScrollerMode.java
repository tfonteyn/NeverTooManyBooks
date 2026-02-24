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

package com.hardbacknutter.nevertoomanybooks.settings;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.fastscroller.FastScrollerBuilder;
import com.hardbacknutter.fastscroller.OverlayProviderFactory;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

public final class FastScrollerMode {
    /**
     * Whether and how a Text Bubble with row information is shown
     * while fast-scrolling through a list.
     * <p>
     * {@code int}
     *
     * @see OverlayProviderFactory
     */
    public static final String PK_OVERLAY = "booklist.fastscroller.overlay";

    /**
     * {@code int}
     */
    public static final String PK_DRAG_HANDLE = "ui.fastscroller.draghandle";
    /** default. */
    private static final int DRAG_HANDLE_SYSTEM = 0;
    /** Thicker and longer. */
    private static final int DRAG_HANDLE_LARGE = 1;

    private FastScrollerMode() {
    }

    /**
     * Create a new {@link FastScrollerBuilder} with the desired user options applied.
     * The caller should just add a call to {@link FastScrollerBuilder#attach(RecyclerView)}.
     *
     * @param context Current context
     *
     * @return new unattached {@link FastScrollerBuilder} instance
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    @NonNull
    public static FastScrollerBuilder create(@NonNull final Context context) {
        final Prefs prefs = ServiceLocator.getInstance().getSharedPreferences();
        final int overlayType = prefs.getIntFromString(PK_OVERLAY, OverlayProviderFactory.TYPE_MD2);
        final int mode = prefs.getIntFromString(PK_DRAG_HANDLE, DRAG_HANDLE_SYSTEM);

        switch (mode) {
            case DRAG_HANDLE_LARGE: {
                return new FastScrollerBuilder(context)
                        .setExpandedTouchArea(24)
                        .setOverlayType(overlayType)
                        // default: 8
                        .setThumbThickness(12)
                        // default: 48
                        .setThumbMinSize(96);
            }
            case DRAG_HANDLE_SYSTEM:
            default: {
                return new FastScrollerBuilder(context)
                        .setExpandedTouchArea(24)
                        .setOverlayType(overlayType);
            }
        }
    }
}
