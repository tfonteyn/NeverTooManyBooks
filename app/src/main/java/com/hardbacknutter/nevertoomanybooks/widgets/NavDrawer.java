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

import android.app.Activity;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.Side;

public final class NavDrawer {

    @NonNull
    private final DrawerLayout drawerLayout;
    @NonNull
    private final NavigationView navigationView;

    @Px
    private final int navViewItemPaddingInPx;

    /**
     * Constructor.
     *
     * @param drawerLayout the top-level layout of the Activity
     * @param listener     for menu selection
     */
    private NavDrawer(@NonNull final DrawerLayout drawerLayout,
                      @NonNull final NavigationView.OnNavigationItemSelectedListener listener) {
        this.drawerLayout = drawerLayout;
        navigationView = drawerLayout.findViewById(R.id.nav_view);
        // Sanity check
        Objects.requireNonNull(navigationView);

        navigationView.setItemMaxLines(2);
        navigationView.setNavigationItemSelectedListener(listener);

        // edg2edge: Do NOT set a WindowInsetListener on the drawerlayout.
        // Note that the internal (single) child of the navigationView is a RecyclerView
        // with the header being a child inside of the row==0 View,
        // and the other rows being the menu items.
        // So we need to do two adjustments:

        // Row 0: the header.
        // We need the padding INSIDE the actual header and NOT on the container that holds
        // the header.
        InsetsListenerBuilder.create(navigationView.getHeaderView(0))
                             .padding()
                             .sides(Side.Left)
                             .apply();

        // Row 1+: the MenuItems
        // Precalculate the default of "28dp" which we have to add to the inset
        final DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        navViewItemPaddingInPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 28, metrics);
        // The insets will be applied to the horizontal padding of the menu-items.
        // This will add padding on BOTH sides due to the API limitation.
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());

            ((NavigationView) v).setItemHorizontalPadding(
                    navViewItemPaddingInPx + insets.left);

            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * Constructor.
     * <p>
     * Should be called from {@code Activity#onCreate}.
     *
     * @param activity the hosting Activity
     * @param listener selection listener
     *
     * @return new instance
     */
    @Nullable
    public static NavDrawer create(@NonNull final Activity activity,
                                   @NonNull final NavigationView.OnNavigationItemSelectedListener
                                           listener) {
        final DrawerLayout drawerLayout = activity.findViewById(R.id.drawer_layout);
        if (drawerLayout == null) {
            return null;
        } else {
            return new NavDrawer(drawerLayout, listener);
        }
    }

    /**
     * Get the {@link MenuItem} for the given id.
     *
     * @param menuItemId to get
     *
     * @return item
     */
    @Nullable
    public MenuItem getMenuItem(@IdRes final int menuItemId) {
        return navigationView.getMenu().findItem(menuItemId);
    }

    /**
     * Get the <strong>View</strong> of the {@link MenuItem} for the given id.
     * Note this is NOT the 'action-view'.
     *
     * @param menuItemId to get
     *
     * @return view
     */
    @NonNull
    public View getMenuItemView(@IdRes final int menuItemId) {
        final View anchor = navigationView.findViewById(menuItemId);
        // Not 100% we are using a legal way of getting the View...
        Objects.requireNonNull(anchor, () -> "navigationView.findViewById(" + menuItemId + ")");
        return anchor;
    }

    /**
     * Open the navigation drawer.
     */
    public void open() {
        drawerLayout.openDrawer(GravityCompat.START);
    }

    /**
     * If the drawerLayout is showing, close it.
     *
     * @return {@code true} if it closed;
     *         {@code false} if this was a no-operation.
     */
    public boolean close() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        return false;
    }
}
