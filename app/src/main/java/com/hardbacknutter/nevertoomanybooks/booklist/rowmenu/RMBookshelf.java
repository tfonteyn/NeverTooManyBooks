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

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelfViewModel;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.bookshelf.EditBookshelfBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.bookshelf.EditBookshelfDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;

public class RMBookshelf
        implements RowMenu {

    /** Edit a {@link Bookshelf} which appears as a {@link BooklistGroup} (node). */
    private final EditParcelableLauncher<Bookshelf> editBookshelfLauncher;
    @NonNull
    private final BooksOnBookshelfViewModel vm;

    public RMBookshelf(@NonNull final BooksOnBookshelfViewModel vm) {
        this.vm = vm;
        editBookshelfLauncher = new EditParcelableLauncher<>(
                DBKey.FK_BOOKSHELF,
                EditBookshelfDialogFragment::new,
                EditBookshelfBottomSheet::new);
        editBookshelfLauncher.setOnEditInPlaceListener(
                bookshelf -> vm.onRowGroupEntityUpdate(BooklistGroup.BOOKSHELF, bookshelf));
    }

    @Override
    public void registerForFragmentResult(@NonNull final FragmentManager fm,
                                          @NonNull final LifecycleOwner lifecycleOwner) {
        editBookshelfLauncher.registerForFragmentResult(fm, lifecycleOwner);
    }

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final MenuInflater menuInflater,
                             @NonNull final Menu menu,
                             @NonNull final DataHolder rowData) {
        if (!rowData.getString(DBKey.FK_BOOKSHELF).isEmpty()) {
            menuInflater.inflate(R.menu.bl_group_bookshelf, menu);
        }
        // Note that a "(No Bookshelf)" does NOT exist.
        // Books are always on a shelf.
    }

    @Override
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      final int menuItemId,
                                      @NonNull final DataHolder rowData,
                                      final int adapterPosition) {
        if (menuItemId == R.id.MENU_BOOKSHELF_EDIT) {
            final Bookshelf bookshelf = DataHolderUtils.requireBookshelf(rowData);
            editBookshelfLauncher.editInPlace(context, bookshelf);
            return true;

        } else if (menuItemId == R.id.MENU_BOOKSHELF_DELETE) {
            final Bookshelf bookshelf = DataHolderUtils.requireBookshelf(rowData);
            // We're handling default/only bookshelf situations in the dialog method
            StandardDialogs.deleteBookshelf(context, bookshelf, () ->
                    vm.delete(context, bookshelf));
            return true;
        }
        return false;
    }
}
