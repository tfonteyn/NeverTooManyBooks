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

package com.hardbacknutter.nevertoomanybooks.widgets.popupmenu;

import android.content.Context;
import android.view.Menu;

import androidx.annotation.IdRes;

@FunctionalInterface
public interface ExtMenuResultListener {
    /**
     * Callback handler.
     *
     * @param menuOwner  The  value which was passed into
     *                   {@link ExtMenuLauncher#launch(Context, CharSequence,
     *                   CharSequence, int, Menu, boolean)}
     * @param menuItemId The menu item that was invoked.
     *
     * @return Return true to consume this click and prevent others from executing.
     */
    boolean onMenuItemClick(int menuOwner,
                            @IdRes int menuItemId);
}
