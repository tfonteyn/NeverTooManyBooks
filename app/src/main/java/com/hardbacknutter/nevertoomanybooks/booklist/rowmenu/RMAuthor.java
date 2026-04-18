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

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelfViewModel;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.AuthorWorksContract;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.author.EditAuthorBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.author.EditAuthorDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;

public class RMAuthor
        implements RowMenu {

    /** Edit an {@link Author} which appears as a {@link BooklistGroup} (node). */
    private final EditParcelableLauncher<Author> editAuthorLauncher;
    @NonNull
    private final BooksOnBookshelfViewModel vm;
    @NonNull
    private final ActivityResultLauncher<AuthorWorksContract.Input> authorWorksLauncher;

    public RMAuthor(@NonNull final BooksOnBookshelfViewModel vm,
                    @NonNull final ActivityResultLauncher<AuthorWorksContract.Input> authorWorksLauncher) {
        this.vm = vm;
        this.authorWorksLauncher = authorWorksLauncher;
        editAuthorLauncher = new EditParcelableLauncher<>(
                DBKey.FK_AUTHOR,
                EditAuthorDialogFragment::new,
                EditAuthorBottomSheet::new);
        editAuthorLauncher.setOnEditInPlaceListener(
                author -> vm.onRowGroupEntityUpdate(BooklistGroup.AUTHOR, author));
    }

    @Override
    public void registerForFragmentResult(@NonNull final FragmentManager fm,
                                          @NonNull final LifecycleOwner lifecycleOwner) {
        editAuthorLauncher.registerForFragmentResult(fm, lifecycleOwner);
    }

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final MenuInflater menuInflater,
                             @NonNull final Menu menu,
                             @NonNull final DataHolder rowData) {
        menuInflater.inflate(R.menu.bl_group_author, menu);
        vm.getMenuHandlers().forEach(h -> h.onCreateMenu(context, menu, menuInflater, rowData));

        final boolean complete = rowData.getBoolean(DBKey.AUTHOR.COMPLETE);
        menu.findItem(R.id.MENU_AUTHOR_SET_COMPLETE).setVisible(!complete);
        menu.findItem(R.id.MENU_AUTHOR_SET_INCOMPLETE).setVisible(complete);

        vm.getMenuHandlers().forEach(h -> h.onPrepareMenu(context, menu, rowData));

    }

    @Override
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      final int menuItemId,
                                      @NonNull final DataHolder rowData,
                                      final int adapterPosition) {
        if (menuItemId == R.id.MENU_AUTHOR_WORKS_LIST) {
            authorWorksLauncher.launch(new AuthorWorksContract.Input(
                    rowData.getLong(DBKey.FK_AUTHOR),
                    vm.getBookshelf()));
            return true;

        } else if (menuItemId == R.id.MENU_AUTHOR_SET_COMPLETE
                   || menuItemId == R.id.MENU_AUTHOR_SET_INCOMPLETE) {
            final Author author = DataHolderUtils.requireAuthor(rowData);
            // toggle the complete status
            final boolean status = !rowData.getBoolean(DBKey.AUTHOR.COMPLETE);
            vm.setAuthorComplete(author, status);
            return true;

        } else if (menuItemId == R.id.MENU_AUTHOR_EDIT) {
            final Author author = DataHolderUtils.requireAuthor(rowData);
            editAuthorLauncher.editInPlace(context, author);
            return true;
        }
        return false;
    }
}
