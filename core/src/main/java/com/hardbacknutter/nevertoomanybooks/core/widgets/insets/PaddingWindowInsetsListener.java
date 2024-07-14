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

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.WindowInsetsCompat;

public class PaddingWindowInsetsListener
        implements OnApplyWindowInsetsListener {

    private static final int TYPE_MASK = WindowInsetsCompat.Type.systemBars()
                                         | WindowInsetsCompat.Type.displayCutout();
    private final Insets base;
    private final boolean left;
    private final boolean top;
    private final boolean right;
    private final boolean bottom;

    /**
     * Constructor.
     *
     * @param view   to add the listener to
     * @param left   {@code true} to apply insets on the left border
     * @param top    {@code true} to apply insets on the left top
     * @param right  {@code true} to apply insets on the left right
     * @param bottom {@code true} to apply insets on the left bottom
     */
    @SuppressWarnings("SameParameterValue")
    public PaddingWindowInsetsListener(@NonNull final View view,
                                       final boolean left,
                                       final boolean top,
                                       final boolean right,
                                       final boolean bottom) {
        base = Insets.of(view.getPaddingLeft(), view.getPaddingTop(),
                         view.getPaddingRight(), view.getPaddingBottom());
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    @NonNull
    @Override
    public WindowInsetsCompat onApplyWindowInsets(@NonNull final View v,
                                                  @NonNull final WindowInsetsCompat windowInsets) {
        final Insets insets = windowInsets.getInsets(TYPE_MASK);

        v.setPadding(base.left + (left ? insets.left : 0),
                     base.top + (top ? insets.top : 0),
                     base.right + (right ? insets.right : 0),
                     base.bottom + (bottom ? insets.bottom : 0));

        return WindowInsetsCompat.CONSUMED;
    }
}
