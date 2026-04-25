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

package com.hardbacknutter.nevertoomanybooks.booklist.rowmenu;

import android.content.Context;
import android.view.Menu;
import android.view.MenuInflater;

import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;

import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public interface RowMenu {

    void registerForFragmentResult(@NonNull FragmentManager fm,
                                   @NonNull LifecycleOwner lifecycleOwner);

    /**
     * Create the row/context menu for a {@link BooklistGroup}.
     *
     * @param context      Current context
     * @param menuInflater to use
     * @param menu         to attach to
     * @param rowData      the row data
     */
    void onCreateMenu(@NonNull Context context,
                      @NonNull MenuInflater menuInflater,
                      @NonNull Menu menu,
                      @NonNull DataHolder rowData);

    /**
     * Handle the row/context menu for a {@link BooklistGroup}.
     *
     * @param context         Current context
     * @param menuItemId      The menu item that was invoked.
     * @param rowData         the row data
     * @param adapterPosition {@code -1} if not applicable, otherwise
     *                        a valid position starting from {@code 0}
     *
     * @return {@code true} if handled.
     */
    boolean onMenuItemSelected(@NonNull Context context,
                               @IdRes int menuItemId,
                               @NonNull DataHolder rowData,
                               @IntRange(from = -1) int adapterPosition);
}
