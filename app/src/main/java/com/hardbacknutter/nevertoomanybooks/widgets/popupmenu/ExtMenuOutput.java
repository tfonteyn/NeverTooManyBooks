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

package com.hardbacknutter.nevertoomanybooks.widgets.popupmenu;

import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;

public class ExtMenuOutput
        implements LauncherOutput {

    private static final String TAG = "ExtMenuOutput";
    private static final String RESULT_MENU_ITEM = TAG + ":mi";
    private static final String RESULT_MENU_OWNER = TAG + ":owner";

    private final int menuOwner;
    @IdRes
    private final int menuItemId;

    /**
     * Constructor.
     *
     * @param menuOwner  as was passed into {@link ExtMenuLauncher#launch}
     * @param menuItemId The menu item that was invoked.
     */
    ExtMenuOutput(final int menuOwner,
                  @IdRes final int menuItemId) {
        this.menuOwner = menuOwner;
        this.menuItemId = menuItemId;
    }

    @NonNull
    static ExtMenuOutput fromBundle(@NonNull final Bundle args) {
        final int menuOwner = args.getInt(RESULT_MENU_OWNER);
        @IdRes
        final int menuItemId = args.getInt(RESULT_MENU_ITEM);

        return new ExtMenuOutput(menuOwner, menuItemId);
    }

    @NonNull
    @Override
    public Bundle toBundle() {
        final Bundle args = new Bundle(2);
        args.putInt(RESULT_MENU_OWNER, menuOwner);
        args.putInt(RESULT_MENU_ITEM, menuItemId);

        return args;
    }

    int getMenuOwner() {
        return menuOwner;
    }

    @IdRes
    int getMenuItemId() {
        return menuItemId;
    }

    @Override
    @NonNull
    public String toString() {
        return "ExtMenuOutput{"
               + "menuOwner=" + menuOwner
               + ", menuItemId=" + menuItemId
               + '}';
    }
}
