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
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.WindowInsetsCompat;

import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;

public class PaddingWindowInsetsListener
        implements OnApplyWindowInsetsListener {

    private static final String TAG = "PaddingWindowInsetsList";
    private final Insets base;
    private final int typeMask;
    private final boolean left;
    private final boolean top;
    private final boolean right;
    private final boolean bottom;
    private final boolean consume;

    PaddingWindowInsetsListener(@NonNull final InsetsListenerBuilder builder) {
        typeMask = builder.getTypeMask();
        consume = builder.getConsume();

        final Set<Side> sides = builder.getSides();
        if (sides == null || sides.contains(Side.All)) {
            left = true;
            top = true;
            right = true;
            bottom = true;
        } else {
            left = sides.contains(Side.Left);
            top = sides.contains(Side.Top);
            right = sides.contains(Side.Right);
            bottom = sides.contains(Side.Bottom);
        }

        final View view = builder.getView();
        base = Insets.of(view.getPaddingLeft(),
                         view.getPaddingTop(),
                         view.getPaddingRight(),
                         view.getPaddingBottom());
    }

    @NonNull
    @Override
    public WindowInsetsCompat onApplyWindowInsets(@NonNull final View v,
                                                  @NonNull final WindowInsetsCompat windowInsets) {
        final Insets insets = windowInsets.getInsets(typeMask);

        // FIXME: Android API 28/29 does not always call the listener. Trying to find why...
        if (BuildConfig.DEBUG /* always */) {
            try {
                Log.d(TAG, "view=" + v.getResources().getResourceEntryName(v.getId()));
            } catch (@NonNull final Resources.NotFoundException ignore) {
                Log.d(TAG, "view=" + v.getClass().getName());
            }
        }

        v.setPadding(base.left + (left ? insets.left : 0),
                     base.top + (top ? insets.top : 0),
                     base.right + (right ? insets.right : 0),
                     base.bottom + (bottom ? insets.bottom : 0));

        if (consume) {
            return WindowInsetsCompat.CONSUMED;
        } else {
            return windowInsets;
        }
    }
}
