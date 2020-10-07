/*
 * @Copyright 2020 HardBackNutter
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

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;

/**
 * Trivial encapsulation of a Menu using an ActionView with a Button for a dynamic action.
 * The button text is initially set to the title of the MenuItem.
 * The action should be hooked up with {@link #setOnClickListener}.
 * The button text can optionally be changed with {@link #setText}.
 */
public class ToolbarMenuActionButton {

    @NonNull
    private final MenuItem mMenuItem;
    @NonNull
    private final Button mButton;

    /**
     * Convenience Constructor.
     *
     * @param toolbar    to modify
     * @param menuItemId to hook up
     * @param buttonId   to hook up
     */
    public ToolbarMenuActionButton(@NonNull final Toolbar toolbar,
                                   @IdRes final int menuItemId,
                                   @IdRes final int buttonId) {
        this(toolbar.getMenu(), menuItemId, buttonId);
    }

    /**
     * Constructor.
     *
     * @param menu       to modify
     * @param menuItemId to hook up
     * @param buttonId   to hook up
     */
    public ToolbarMenuActionButton(@NonNull final Menu menu,
                                   @IdRes final int menuItemId,
                                   @IdRes final int buttonId) {
        mMenuItem = menu.findItem(menuItemId);
        mButton = mMenuItem.getActionView().findViewById(buttonId);
        mButton.setText(mMenuItem.getTitle());
    }

    /**
     * Set the {@link MenuItem.OnMenuItemClickListener} on the button.
     *
     * @param listener to hook up
     */
    public void setOnClickListener(@NonNull final MenuItem.OnMenuItemClickListener listener) {
        mButton.setOnClickListener(v -> listener.onMenuItemClick(mMenuItem));
    }

    /**
     * Set the button text.
     *
     * @param textId resource for the text
     */
    public void setText(@StringRes final int textId) {
        mButton.setText(textId);
    }
}
