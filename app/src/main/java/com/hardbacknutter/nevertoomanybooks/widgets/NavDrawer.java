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
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;

public final class NavDrawer {

    @NonNull
    private final DrawerLayout drawerLayout;
    @NonNull
    private final NavigationView navigationView;

    private NavDrawer(@NonNull final DrawerLayout drawerLayout,
                      @NonNull final NavigationView.OnNavigationItemSelectedListener listener) {
        this.drawerLayout = drawerLayout;
        navigationView = drawerLayout.findViewById(R.id.nav_view);
        // Sanity check
        Objects.requireNonNull(navigationView);

        navigationView.setItemMaxLines(2);
        navigationView.setNavigationItemSelectedListener(listener);
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
