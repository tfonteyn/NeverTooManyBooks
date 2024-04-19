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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.view.Menu;
import android.view.MenuInflater;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public interface MenuHandler {

    /**
     * Inflate/create the menu.
     *
     * @param context  Current context
     * @param menu     The Menu to inflate into
     * @param inflater to use
     */
    void onCreateMenu(@NonNull Context context,
                      @NonNull Menu menu,
                      @NonNull MenuInflater inflater);

    /**
     * Prepare the menu before opening it.
     *
     * @param context Current context
     * @param menu    to prepare
     * @param rowData the row data
     */
    void onPrepareMenu(@NonNull Context context,
                       @NonNull Menu menu,
                       @NonNull DataHolder rowData);

    /**
     * Called after the user selected a menu item.
     *
     * @param context  Current context
     * @param menuItemId The menu item that was invoked.
     * @param rowData  the row data
     *
     * @return {@code true} if the event was handled, {@code false} otherwise.
     */
    boolean onMenuItemSelected(@NonNull Context context,
                               @IdRes int menuItemId,
                               @NonNull DataHolder rowData);
}
