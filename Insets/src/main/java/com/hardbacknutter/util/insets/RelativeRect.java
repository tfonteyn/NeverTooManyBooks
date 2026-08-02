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

package com.hardbacknutter.util.insets;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;

import java.util.Set;

class RelativeRect {

    private static final String TAG = "RelativeRect";

    public final int start;
    public final int top;
    public final int end;
    public final int bottom;

    @NonNull
    private final Set<Side> sides;

    RelativeRect(final int start,
                 final int top,
                 final int end,
                 final int bottom,
                 @NonNull final Set<Side> sides) {
        this.start = start;
        this.top = top;
        this.end = end;
        this.bottom = bottom;
        this.sides = sides;
    }

    @SuppressWarnings("NestedConditionalExpression")
    @NonNull
    RelativeRect transform(@NonNull final View view,
                           @NonNull final Insets insets) {
        final boolean rtl = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        final int nTop = top + (sides.contains(Side.Top) ? insets.top : 0);
        final int nBottom = bottom + (sides.contains(Side.Bottom) ? insets.bottom : 0);

        final int nStart = start + (sides.contains(Side.Start)
                                    ? (rtl ? insets.right : insets.left)
                                    : 0);
        final int nEnd = end + (sides.contains(Side.End)
                                ? (rtl ? insets.left : insets.right)
                                : 0);

//        if (BuildConfig.DEBUG) {
//            dumpDebug(view, insets);
//        }

        return new RelativeRect(nStart, nTop, nEnd, nBottom, sides);
    }

    @SuppressLint("LogConditional")
    private void dumpDebug(@NonNull final View view,
                           @NonNull final Insets insets) {
        final int id = view.getId();
        String resourceEntryName;
        try {
            resourceEntryName = view.getResources().getResourceEntryName(id);
        } catch (@NonNull final Resources.NotFoundException ignore) {
            resourceEntryName = String.valueOf(id);
        }
        Log.d(TAG, "sides=" + sides
                   + "; resName=" + resourceEntryName
                   + "; viewClass=" + view.getClass().getName()
                   + "; insets=" + insets);
    }
}
