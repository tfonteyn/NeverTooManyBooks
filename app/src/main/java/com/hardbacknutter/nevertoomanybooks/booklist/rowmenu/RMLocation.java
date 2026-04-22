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
import android.content.res.Resources;
import android.view.Menu;
import android.view.MenuInflater;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelfViewModel;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.inlinestring.EditInLineStringLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.inlinestring.EditLocationBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.inlinestring.EditLocationDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public class RMLocation
        implements RowMenu {

    /** Edit a {@code Book Location} which appears as a {@link BooklistGroup} (node). */
    private final EditInLineStringLauncher editLocationLauncher;

    public RMLocation(@NonNull final BooksOnBookshelfViewModel vm) {
        editLocationLauncher = new EditInLineStringLauncher(
                DBKey.LOCATION,
                EditLocationDialogFragment::new,
                EditLocationBottomSheet::new);
        editLocationLauncher.setOnEditListener(
                (original, modified)
                        -> vm.onRowGroupUpdate(BooklistGroup.LOCATION, original, modified));
    }

    @Override
    public void registerForFragmentResult(@NonNull final FragmentManager fm,
                                          @NonNull final LifecycleOwner lifecycleOwner) {
        editLocationLauncher.registerForFragmentResult(fm, lifecycleOwner);
    }

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final MenuInflater menuInflater,
                             @NonNull final Menu menu,
                             @NonNull final DataHolder rowData) {
        if (!rowData.getString(DBKey.LOCATION).isEmpty()) {
            final Resources res = context.getResources();
            menu.add(Menu.NONE, R.id.MENU_LOCATION_EDIT,
                     res.getInteger(R.integer.MENU_ORDER_EDIT),
                     R.string.action_edit_ellipsis)
                .setIcon(R.drawable.edit_24px);

            menu.add(Menu.NONE, R.id.MENU_SET_BOOKSHELVES,
                     res.getInteger(R.integer.MENU_ORDER_SET_BOOKSHELVES),
                     R.string.lbl_assign_bookshelves)
                .setIcon(R.drawable.library_books_24px);
            menu.add(Menu.NONE, R.id.MENU_SET_LOCATION,
                     res.getInteger(R.integer.MENU_ORDER_SET_LOCATION),
                     R.string.lbl_assign_location)
                .setIcon(R.drawable.edit_location_24px);
        }

    }

    @Override
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      final int menuItemId,
                                      @NonNull final DataHolder rowData,
                                      final int adapterPosition) {
        if (menuItemId == R.id.MENU_LOCATION_EDIT) {
            editLocationLauncher.edit(context, rowData.getString(DBKey.LOCATION));
            return true;
        }
        return false;
    }
}
