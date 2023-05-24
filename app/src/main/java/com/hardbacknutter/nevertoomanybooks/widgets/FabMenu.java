/*
 * @Copyright 2018-2023 HardBackNutter
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

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.Arrays;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;

public class FabMenu {

    /** The normal FAB button; opens or closes the FAB menu. */
    @NonNull
    private final ExtendedFloatingActionButton fabButton;

    /** Overlay enabled while the FAB menu is shown to intercept clicks and close the FAB menu. */
    @NonNull
    private final View fabOverlay;

    /** Array with the submenu FAB buttons. Element {@code 0} shows at the bottom. */
    private ExtendedFloatingActionButton[] fabMenuItems;

    /** Define a scroller to show, or collapse/hide the FAB. */
    private final RecyclerView.OnScrollListener updateFabVisibility =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull final RecyclerView recyclerView,
                                       final int dx,
                                       final int dy) {
                    if (dy > 0 || dy < 0 && fabButton.isShown()) {
                        hideMenu();
                        fabButton.hide();
                    }
                }

                @Override
                public void onScrollStateChanged(@NonNull final RecyclerView recyclerView,
                                                 final int newState) {
                    // This method is not called when the fast scroller stops scrolling but
                    // we can ignore that as in practice a minuscule swipe brings the FAB back.
                    if (newState == RecyclerView.SCROLL_STATE_IDLE
                        || newState == RecyclerView.SCROLL_STATE_SETTLING) {
                        hideMenu();
                        fabButton.show();
                    }
                    super.onScrollStateChanged(recyclerView, newState);
                }
            };

    /**
     * Constructor.
     *
     * @param fabButton  the standard FAB button
     * @param fabOverlay the overlay view
     * @param items      the fab menu items
     */
    public FabMenu(@NonNull final ExtendedFloatingActionButton fabButton,
                   @NonNull final View fabOverlay,
                   @Nullable final ExtendedFloatingActionButton... items) {
        this.fabButton = fabButton;
        this.fabButton.setOnClickListener(v -> show(!fabMenuItems[0].isShown()));
        this.fabOverlay = fabOverlay;

        if (items != null && items.length > 0) {
            // Create a new empty array and copy the actual item references to that.
            fabMenuItems = new ExtendedFloatingActionButton[items.length];
            System.arraycopy(items, 0, fabMenuItems, 0, items.length);
        }
    }

    /**
     * Set a listener on all the fab-menu items.
     *
     * @param listener to set
     */
    public void setOnClickListener(@NonNull final View.OnClickListener listener) {
        for (final ExtendedFloatingActionButton fabMenuItem : fabMenuItems) {
            fabMenuItem.setOnClickListener(listener);
        }
    }

    /**
     * Get the fab-menu item with the given id.
     *
     * @param id to lookup
     *
     * @return the fab-menu item
     */
    @NonNull
    public Optional<ExtendedFloatingActionButton> getItem(@IdRes final int id) {
        return Arrays.stream(fabMenuItems).filter(item -> item.getId() == id).findFirst();
    }

    /**
     * Hook up the {@code RecyclerView} to update the FAB as scrolling takes place.
     *
     * @param recyclerView to hookup
     */
    public void attach(@NonNull final RecyclerView recyclerView) {
        recyclerView.addOnScrollListener(updateFabVisibility);
    }

    /**
     * Check if the menu is showing.
     *
     * @return {code true} if it is.
     */
    public boolean isShown() {
        return fabMenuItems[0].isShown();
    }

    /**
     * Hide the FAB menu if it's showing. Does not affect the FAB button itself.
     *
     * @return {@code true} if it was visible before. {@code false} if this was a no-operation.
     */
    public boolean hideMenu() {
        if (fabMenuItems[0].isShown()) {
            show(false);
            return true;
        }
        return false;
    }

    /**
     * When the user clicks the FAB button, we open/close the FAB menu and change the FAB icon.
     *
     * @param show {@code true} to show the menu.
     */
    public void show(final boolean show) {
        if (show) {
            fabButton.setIconResource(R.drawable.ic_baseline_close_24);
            // mFabOverlay overlaps the whole screen and intercepts clicks.
            // This does not include the ToolBar.
            fabOverlay.setVisibility(View.VISIBLE);
            fabOverlay.setOnClickListener(v -> hideMenu());
        } else {
            fabButton.setIconResource(R.drawable.ic_baseline_add_24);
            fabOverlay.setVisibility(View.GONE);
            fabOverlay.setOnClickListener(null);
        }

        final Resources res = fabButton.getResources();

        // try-with-res requires Android 13
        final TypedArray baseX = res.obtainTypedArray(R.array.fab_menu_translationX_all);
        final TypedArray baseY = res.obtainTypedArray(R.array.fab_menu_translationY_all);
        try {
            for (int i = 0; i < fabMenuItems.length; i++) {
                final ExtendedFloatingActionButton fab = fabMenuItems[i];
                // allow for null items
                if (fab != null && fab.isEnabled()) {
                    if (show) {
                        fab.show();
                        fab.animate().translationX(baseX.getDimensionPixelSize(i, 0));
                        fab.animate().translationY(baseY.getDimensionPixelSize(i, 0));
                    } else {
                        fab.animate().translationX(0);
                        fab.animate().translationY(0);
                        fab.hide();
                    }
                }
            }
        } finally {
            baseX.recycle();
            baseY.recycle();
        }
    }
}
