/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.hardbacknutter.nevertoomanybooks.R;

public class FabMenu {

    /** The normal FAB button; opens or closes the FAB menu. */
    private final FloatingActionButton mFab;
    /** Overlay enabled while the FAB menu is shown to intercept clicks and close the FAB menu. */
    @NonNull
    private final View mFabOverlay;
    /** Array with the submenu FAB buttons. Element 0 shows at the bottom. */
    private ExtendedFloatingActionButton[] mFabMenuItems;
    /** Define a scroller to show, or collapse/hide the FAB. */
    private final RecyclerView.OnScrollListener mUpdateFABVisibility =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull final RecyclerView recyclerView,
                                       final int dx,
                                       final int dy) {
                    if (dy > 0 || dy < 0 && mFab.isShown()) {
                        hideMenu();
                        mFab.hide();
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
                        mFab.show();
                    }
                    super.onScrollStateChanged(recyclerView, newState);
                }
            };

    /**
     * Constructor.
     *
     * @param fab        the standard FAB button
     * @param fabOverlay the overlay view
     * @param items      the fab menu items
     */
    public FabMenu(@NonNull final FloatingActionButton fab,
                   @NonNull final View fabOverlay,
                   @Nullable final ExtendedFloatingActionButton... items) {
        mFab = fab;
        mFab.setOnClickListener(v -> show(!mFabMenuItems[0].isShown()));
        mFabOverlay = fabOverlay;

        if (items != null && items.length > 0) {
            mFabMenuItems = new ExtendedFloatingActionButton[items.length];
            System.arraycopy(items, 0, mFabMenuItems, 0, items.length);
        }
    }

    public void setOnClickListener(@NonNull final View.OnClickListener listener) {
        for (final ExtendedFloatingActionButton fabMenuItem : mFabMenuItems) {
            fabMenuItem.setOnClickListener(listener);
        }
    }

    @NonNull
    public ExtendedFloatingActionButton getItem(final int index) {
        return mFabMenuItems[index];
    }

    public void attach(@NonNull final RecyclerView recyclerView) {
        recyclerView.addOnScrollListener(mUpdateFABVisibility);
    }

    public boolean isShown() {
        return mFabMenuItems[0].isShown();
    }

    /**
     * Hide the FAB menu if it's showing. Does not affect the FAB button itself.
     *
     * @return {@code true} if it was visible before. {@code false} if this was a no-operation.
     */
    public boolean hideMenu() {
        if (mFabMenuItems[0].isShown()) {
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
            mFab.setImageResource(R.drawable.ic_baseline_close_24);
            // mFabOverlay overlaps the whole screen and intercepts clicks.
            // This does not include the ToolBar.
            mFabOverlay.setVisibility(View.VISIBLE);
            mFabOverlay.setOnClickListener(v -> hideMenu());
        } else {
            mFab.setImageResource(R.drawable.ic_baseline_add_24);
            mFabOverlay.setVisibility(View.GONE);
            mFabOverlay.setOnClickListener(null);
        }

        final Resources res = mFab.getResources();

        final float baseY = res.getDimension(R.dimen.fab_menu_translationY_base);
        final float deltaY = res.getDimension(R.dimen.fab_menu_translationY_delta);

        final float baseX = res.getDimension(R.dimen.fab_menu_translationX);
        final float deltaX = res.getDimension(R.dimen.fab_menu_translationX_delta);

        // Test for split-screen layouts (or really small devices?)
        // Having more than 4 FAB buttons is not really a good UI design
        // But this just about fits our 5...
        // It's a fringe case... resource qualifiers would be overkill.
        final boolean smallScreen = res.getConfiguration().screenHeightDp < 400;

        int i = 0;
        for (final ExtendedFloatingActionButton fab : mFabMenuItems) {
            // allow for null items
            if (fab != null && fab.isEnabled()) {
                if (show) {
                    fab.show();
                    if (smallScreen) {
                        // to the left of FAB and up
                        fab.animate().translationX(baseX + deltaX);
                        fab.animate().translationY(i * deltaY);
                    } else {
                        // on top of base FAB
                        fab.animate().translationX(baseX);
                        fab.animate().translationY(baseY + ((i + 1) * deltaY));
                    }
                } else {
                    fab.animate().translationX(0);
                    fab.animate().translationY(0);
                    fab.hide();
                }
                i++;
            }
        }
    }
}
