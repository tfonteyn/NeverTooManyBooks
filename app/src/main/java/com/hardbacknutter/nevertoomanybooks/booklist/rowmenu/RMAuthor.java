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

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelfViewModel;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.AuthorWorksInput;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditInPlaceParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.author.EditAuthorBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.author.EditAuthorDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.menus.MenuHandler;
import com.hardbacknutter.nevertoomanybooks.menus.SiteSearchMenuHandler;
import com.hardbacknutter.nevertoomanybooks.menus.ViewAuthorOnSiteMenuHandler;

public class RMAuthor
        implements RowMenu {

    /** Edit an {@link Author} which appears as a {@link BooklistGroup} (node). */
    private final EditInPlaceParcelableLauncher<Author> launcher;
    @NonNull
    private final BooksOnBookshelfViewModel vm;
    @NonNull
    private final ActivityResultLauncher<AuthorWorksInput> authorWorksLauncher;
    private final List<MenuHandler<DataHolder>> menuHandlers;

    public RMAuthor(@NonNull final BooksOnBookshelfViewModel vm,
                    @NonNull final ActivityResultLauncher<AuthorWorksInput>
                            authorWorksLauncher) {
        this.vm = vm;
        this.authorWorksLauncher = authorWorksLauncher;

        menuHandlers = List.of(new ViewAuthorOnSiteMenuHandler(),
                               new SiteSearchMenuHandler());

        launcher = new EditInPlaceParcelableLauncher<>(
                DBKey.FK_AUTHOR,
                EditAuthorDialogFragment::new,
                EditAuthorBottomSheet::new);
        launcher.setListener(author -> vm.onRowGroupUpdate(BooklistGroup.AUTHOR, author));
    }

    @Override
    public void registerForFragmentResult(@NonNull final FragmentManager fm,
                                          @NonNull final LifecycleOwner lifecycleOwner) {
        launcher.registerForFragmentResult(fm, lifecycleOwner);
    }

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final MenuInflater menuInflater,
                             @NonNull final Menu menu,
                             @NonNull final DataHolder rowData) {
        final Resources res = context.getResources();

        menu.add(Menu.NONE, R.id.MENU_AUTHOR_WORKS_LIST,
                 0,
                 R.string.option_author_details)
            .setIcon(R.drawable.person_24px);

        final boolean complete = rowData.getBoolean(DBKey.AUTHOR.COMPLETE);

        menu.add(Menu.NONE, R.id.MENU_AUTHOR_SET_COMPLETE,
                 res.getInteger(R.integer.MENU_ORDER_COMPLETE),
                 R.string.option_set_complete)
            .setIcon(R.drawable.check_box_24px)
            .setVisible(!complete);

        menu.add(Menu.NONE, R.id.MENU_AUTHOR_SET_INCOMPLETE,
                 res.getInteger(R.integer.MENU_ORDER_COMPLETE),
                 R.string.option_set_incomplete)
            .setIcon(R.drawable.check_box_outline_blank_24px)
            .setVisible(complete);

        menu.add(Menu.NONE, R.id.MENU_AUTHOR_EDIT,
                 res.getInteger(R.integer.MENU_ORDER_EDIT),
                 R.string.action_edit_ellipsis)
            .setIcon(R.drawable.edit_24px);

        menuHandlers.forEach(h -> h.onCreateMenu(context, menuInflater, menu, rowData));
        menuHandlers.forEach(h -> h.onPrepareMenu(context, menu, rowData));

        menu.add(Menu.NONE, R.id.MENU_SET_BOOKSHELVES,
                 res.getInteger(R.integer.MENU_ORDER_SET_BOOKSHELVES),
                 R.string.lbl_assign_bookshelves)
            .setIcon(R.drawable.library_books_24px);

        menu.add(Menu.NONE, R.id.MENU_SET_LOCATION,
                 res.getInteger(R.integer.MENU_ORDER_SET_LOCATION),
                 R.string.lbl_assign_location)
            .setIcon(R.drawable.edit_location_24px);

        menu.add(Menu.NONE, R.id.MENU_UPDATE_BOOKS_BY_SEARCH,
                 res.getInteger(R.integer.MENU_ORDER_UPDATE_FIELDS),
                 R.string.menu_update_books)
            .setIcon(R.drawable.cloud_download_24px);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      final int menuItemId,
                                      @NonNull final DataHolder rowData,
                                      final int adapterPosition) {
        if (menuItemId == R.id.MENU_AUTHOR_WORKS_LIST) {
            authorWorksLauncher.launch(new AuthorWorksInput(
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
            launcher.edit(context, author);
            return true;
        }
        return menuHandlers.stream().anyMatch(
                h -> h.onMenuItemSelected(context, menuItemId, rowData));
    }
}
