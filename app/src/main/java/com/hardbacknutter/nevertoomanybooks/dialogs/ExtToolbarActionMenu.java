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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.hardbacknutter.nevertoomanybooks.R;

public interface ExtToolbarActionMenu {

    /**
     * Called when the user clicks the Navigation icon from the toolbar menu.
     * The default action should dismiss the dialog.
     *
     * @param v view
     */
    void onToolbarNavigationClick(@NonNull View v);

    /**
     * Called when the user selects a menu item from the toolbar menu.
     * The default action ignores the selection.
     *
     * @param menuItem The menu item that was invoked.
     *
     * @return {@code true} if the event was handled, {@code false} otherwise.
     */
    default boolean onToolbarMenuItemClick(@Nullable final MenuItem menuItem) {
        return false;
    }

    /**
     * Called when the user clicks a button on the toolbar or the bottom button-bar.
     * The default action ignores the selection.
     *
     * @param button the toolbar action-view-button or button-bar button
     *
     * @return {@code true} if the event was handled, {@code false} otherwise.
     */
    default boolean onToolbarButtonClick(@Nullable final View button) {
        return false;
    }

    /**
     * Setup the Toolbar listeners.
     *
     * @param dialogToolbar to process
     * @param listener      to set
     */
    default void initToolbarActionButtons(@NonNull final Toolbar dialogToolbar,
                                          @NonNull final ExtToolbarActionMenu listener) {
        dialogToolbar.setNavigationOnClickListener(listener::onToolbarNavigationClick);
        // Simple menu items; i.e. non-action view.
        dialogToolbar.setOnMenuItemClickListener(listener::onToolbarMenuItemClick);

        // Hookup any/all buttons in the action-view to use #onToolbarButtonClick
        final MenuItem menuItem = dialogToolbar.getMenu().findItem(R.id.MENU_ACTION_CONFIRM);
        if (menuItem != null) {
            final View actionView = menuItem.getActionView();

            if (actionView instanceof Button) {
                // Single button as top-level view
                actionView.setOnClickListener(listener::onToolbarButtonClick);

            } else if (actionView instanceof ViewGroup) {
                // A ViewGroup with multiple Buttons.
                final ViewGroup av = (ViewGroup) actionView;
                for (int c = 0; c < av.getChildCount(); c++) {
                    final View child = av.getChildAt(c);
                    if (child instanceof Button) {
                        child.setOnClickListener(listener::onToolbarButtonClick);
                    }
                }
            }
        }
    }
}
