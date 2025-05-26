/*
 * @Copyright 2018-2025 HardBackNutter
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

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.fastscroller.OverlayProviderFactory;
import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;

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
     * Create a new {@link FastScroller} with the desired user options applied.
     * The caller should just add a call to {@link FastScroller#attach(RecyclerView)}.
     *
     * @param context Current context
     *
     * @return new unattached {@link FastScroller} instance
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    @NonNull
    public static FastScroller create(@NonNull final Context context) {
        final int overlayType = IntListPref.getInt(context, PK_OVERLAY,
                                                   OverlayProviderFactory.TYPE_MD2);

        final int mode = IntListPref.getInt(context, PK_DRAG_HANDLE, DRAG_HANDLE_SYSTEM);

        switch (mode) {
            case DRAG_HANDLE_LARGE: {
                return new FastScroller(context)
                        .setExpandedTouchArea(24)
                        .setOverlayType(overlayType)
                        // default: 8
                        .setThumbThickness(12)
                        // default: 48
                        .setThumbMinSize(96);
            }
            case DRAG_HANDLE_SYSTEM:
            default: {
                return new FastScroller(context)
                        .setExpandedTouchArea(24)
                        .setOverlayType(overlayType);
            }
        }
    }
}
