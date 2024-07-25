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

import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.navigation.NavigationView;

/**
 * The internal (single) child of the navigationView is a RecyclerView
 * with the header being a child inside of the row {@code 0} ViewHolder,
 * and the other rows being the menu items.
 */
class NavigationViewWindowInsetsListener
        implements OnApplyWindowInsetsListener {

    /** Default horizontal padding for the menu items. Taken from Material3 resource definition. */
    private static final int DEFAULT_PADDING = 28;

    private static final int INSETS_TYPE_MASK = WindowInsetsCompat.Type.systemBars()
                                                | WindowInsetsCompat.Type.displayCutout();
    @Px
    private final int navViewItemPadding;
    @Px
    private final int headerPaddingLeft;

    NavigationViewWindowInsetsListener(@NonNull final NavigationView view) {
        // Row 1+: the MenuItems
        final DisplayMetrics metrics = view.getResources().getDisplayMetrics();
        navViewItemPadding = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, DEFAULT_PADDING, metrics);

        headerPaddingLeft = view.getHeaderView(0).getPaddingLeft();
    }

    @Override
    @NonNull
    public WindowInsetsCompat onApplyWindowInsets(@NonNull final View v,
                                                  @NonNull final WindowInsetsCompat wic) {
        final Insets insets = wic.getInsets(INSETS_TYPE_MASK);

        final NavigationView navigationView = (NavigationView) v;

        // Row 0: the header.
        // We need the padding INSIDE the actual header and NOT on the container that holds
        // the header.
        final View headerView = navigationView.getHeaderView(0);
        headerView.setPadding(headerPaddingLeft + insets.left,
                              headerView.getPaddingTop(),
                              headerView.getPaddingRight(),
                              headerView.getPaddingBottom());

        // Row 1+: the MenuItems. Just add horizontal padding as needed.
        navigationView.setItemHorizontalPadding(navViewItemPadding + insets.left);

        return WindowInsetsCompat.CONSUMED;
    }
}
