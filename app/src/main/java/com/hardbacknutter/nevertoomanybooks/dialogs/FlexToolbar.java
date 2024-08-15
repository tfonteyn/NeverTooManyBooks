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

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

public interface FlexToolbar {

    /**
     * Called when the user clicks the Navigation icon on the toolbar.
     * The default action should dismiss the dialog.
     *
     * @param v view
     */
    void onToolbarNavigationClick(@NonNull View v);

    /**
     * Called when the user selects a menu item from the toolbar menu.
     *
     * @param menuItem The menu item that was invoked.
     *
     * @return {@code true} if the event was handled, {@code false} otherwise.
     */
    default boolean onToolbarMenuItemClick(@Nullable final MenuItem menuItem) {
        return false;
    }

    /**
     * Called when the user clicks a button on the toolbar or the dialog (bottom) button-bar.
     *
     * @param button the toolbar action-view-button or button-bar button
     *
     * @return {@code true} if the event was handled, {@code false} otherwise.
     */
    boolean onToolbarButtonClick(@Nullable View button);

    /**
     * Setup the Toolbar listeners.
     * <p>
     * Dev. Note: If we want an outline to be drawn AROUND the icon, then we seem
     * forced to use an "actionLayout" with an icon-Button using the outline style.
     * Only alternative is to use an icon with outline builtin...
     * which makes the actual icon to small.
     *
     * @param toolbar to process
     */
    default void initToolbar(@NonNull final Toolbar toolbar) {
        // The (optional) navigation/home icon
        toolbar.setNavigationOnClickListener(this::onToolbarNavigationClick);
        // The (optional) menu items; i.e. non-action view.
        toolbar.setOnMenuItemClickListener(this::onToolbarMenuItemClick);

        // Hookup all menu items with action views to use #onToolbarButtonClick
        final Menu menu = toolbar.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            final View actionView = menu.getItem(i).getActionView();
            if (actionView != null) {
                if (actionView instanceof Button) {
                    // Single button as top-level view
                    actionView.setOnClickListener(this::onToolbarButtonClick);

                } else if (actionView instanceof ViewGroup) {
                    // A ViewGroup with multiple Buttons.
                    final ViewGroup av = (ViewGroup) actionView;
                    for (int c = 0; c < av.getChildCount(); c++) {
                        final View child = av.getChildAt(c);
                        if (child instanceof Button) {
                            child.setOnClickListener(this::onToolbarButtonClick);
                        }
                    }
                }
            }
        }
    }
}
