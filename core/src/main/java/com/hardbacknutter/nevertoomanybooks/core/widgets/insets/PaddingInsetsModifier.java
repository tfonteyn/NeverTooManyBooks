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

package com.hardbacknutter.nevertoomanybooks.core.widgets.insets;

import android.content.res.Resources;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;

import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.DEBUG_FLAGS;

class PaddingInsetsModifier
        implements InsetsModifier {

    private static final String TAG = "PaddingInsetsModifier";

    @NonNull
    private final Insets padding;
    @NonNull
    private final Set<Side> sides;

    PaddingInsetsModifier(@NonNull final View view,
                          @NonNull final Set<Side> sides) {
        this.padding = Insets.of(view.getPaddingLeft(),
                                 view.getPaddingTop(),
                                 view.getPaddingRight(),
                                 view.getPaddingBottom());
        this.sides = sides;
    }

    @Override
    public void apply(@NonNull final View view,
                      @NonNull final Insets insets) {
        final int left = padding.left + (sides.contains(Side.Left) ? insets.left : 0);
        final int top = padding.top + (sides.contains(Side.Top) ? insets.top : 0);
        final int right = padding.right + (sides.contains(Side.Right) ? insets.right : 0);
        final int bottom = padding.bottom + (sides.contains(Side.Bottom) ? insets.bottom : 0);

        view.setPadding(left, top, right, bottom);

        if (BuildConfig.DEBUG && DEBUG_FLAGS.INSETS) {
            dumpDebug(view, insets);
        }
    }

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
