/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.app.ActionBar;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;

public class FabMenu {

    /** The normal FAB button; opens or closes the FAB menu. */
    @NonNull
    private final FloatingActionButton fabButton;

    /** Overlay enabled while the FAB menu is shown to intercept clicks and close the FAB menu. */
    @NonNull
    private final View fabOverlay;

    /** Array with the submenu FAB buttons. Element {@code 0} shows at the bottom. */
    private ExtendedFloatingActionButton[] fabMenuItems;
    @Nullable
    private Consumer<Boolean> onOpenListener;
    /** Temp storage for the original icon while the menu is open. */
    @Nullable
    private Drawable fabDrawable;

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
                    // This method is not called when the fast scroller stops scrolling, but
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
    public FabMenu(@NonNull final FloatingActionButton fabButton,
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
     * Constructor.
     *
     * @param fabButton the FAB button to use as base
     * @param menuResId to inflate
     */
    public FabMenu(@NonNull final FloatingActionButton fabButton,
                   @MenuRes final int menuResId) {
        this.fabButton = fabButton;
        final CoordinatorLayout parent = (CoordinatorLayout) fabButton.getParent();
        final Context context = fabButton.getContext();

        fabOverlay = new View(context);
        parent.addView(fabOverlay);
        final CoordinatorLayout.LayoutParams overlayLp =
                (CoordinatorLayout.LayoutParams) fabOverlay.getLayoutParams();
        overlayLp.width = ActionBar.LayoutParams.MATCH_PARENT;
        overlayLp.height = ActionBar.LayoutParams.MATCH_PARENT;

        final Menu menu = new PopupMenu(context, null).getMenu();
        new MenuInflater(context).inflate(menuResId, menu);
        final int size = menu.size();
        if (size > 0) {
            fabMenuItems = new ExtendedFloatingActionButton[size];
            for (int i = 0; i < size; i++) {
                final MenuItem item = menu.getItem(i);
                fabMenuItems[i] = new ExtendedFloatingActionButton(context);
                fabMenuItems[i].setId(item.getItemId());
                fabMenuItems[i].setText(item.getTitle());
                fabMenuItems[i].setIcon(item.getIcon());

                parent.addView(fabMenuItems[i]);

                final CoordinatorLayout.LayoutParams lp =
                        (CoordinatorLayout.LayoutParams) fabMenuItems[i].getLayoutParams();
                lp.setAnchorId(fabButton.getId());
            }
        }

        this.fabButton.setOnClickListener(v -> show(!fabMenuItems[0].isShown()));
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
     * Set a listener to be notified of the menu being opened or closed.
     *
     * @param onOpenListener to set
     */
    public void setOnOpenListener(@Nullable final Consumer<Boolean> onOpenListener) {
        this.onOpenListener = onOpenListener;
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
     */
    public void hideMenu() {
        if (fabMenuItems[0].isShown()) {
            show(false);
        }
    }

    /**
     * When the user clicks the FAB button, we open/close the FAB menu and change the FAB icon.
     *
     * @param show {@code true} to show the menu.
     */
    public void show(final boolean show) {
        if (show) {
            fabDrawable = fabButton.getDrawable();
            fabButton.setImageResource(R.drawable.close_24px);
            // The overlay overlaps the whole screen and intercepts clicks.
            // This does not include the ToolBar.
            fabOverlay.setVisibility(View.VISIBLE);
            fabOverlay.setOnClickListener(v -> hideMenu());
        } else {
            fabButton.setImageDrawable(fabDrawable);
            fabOverlay.setVisibility(View.GONE);
            fabOverlay.setOnClickListener(null);
        }

        final Resources res = fabButton.getResources();

        // try-with-res requires Android 13
        final TypedArray baseX = res.obtainTypedArray(R.array.fab_menu_translationX);
        final TypedArray baseY = res.obtainTypedArray(R.array.fab_menu_translationY);
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

        if (onOpenListener != null) {
            onOpenListener.accept(show);
        }
    }
}
