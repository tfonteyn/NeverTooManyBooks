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

package com.hardbacknutter.nevertoomanybooks.widgets;

import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.navigation.NavigationView;

import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.Side;

final class NavigationViewWindowInsetsListener
        implements OnApplyWindowInsetsListener {

    /** Default horizontal padding for the menu items. Taken from Material3 resource definition. */
    private static final int DEFAULT_PADDING = 28;
    @Px
    private final int navViewItemPaddingInPx;

    private NavigationViewWindowInsetsListener(@NonNull final NavigationView view) {
        // Row 1+: the MenuItems
        final DisplayMetrics metrics = view.getResources().getDisplayMetrics();
        navViewItemPaddingInPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, DEFAULT_PADDING, metrics);
    }

    /**
     * Construct and attach.
     *
     * @param view to handle
     */
    static void apply(@NonNull final NavigationView view) {
        // The internal (single) child of the navigationView is a RecyclerView
        // with the header being a child inside of the row==0 ViewHolder,
        // and the other rows being the menu items.
        // So we need to setup two listeners:

        // Row 0: the header.
        // We need the padding INSIDE the actual header and NOT on the container that holds
        // the header.
        InsetsListenerBuilder.create()
                             .padding(Side.Left)
                             .applyTo(view.getHeaderView(0));

        // Row 1+: the MenuItems. Just add horizontal padding as needed.
        final OnApplyWindowInsetsListener listener =
                new NavigationViewWindowInsetsListener(view);
        ViewCompat.setOnApplyWindowInsetsListener(view, listener);
    }

    @NonNull
    @Override
    public WindowInsetsCompat onApplyWindowInsets(
            @NonNull final View v,
            @NonNull final WindowInsetsCompat windowInsets) {
        final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                                                     | WindowInsetsCompat.Type.displayCutout());

        ((NavigationView) v).setItemHorizontalPadding(navViewItemPaddingInPx + insets.left);

        return WindowInsetsCompat.CONSUMED;
    }
}
