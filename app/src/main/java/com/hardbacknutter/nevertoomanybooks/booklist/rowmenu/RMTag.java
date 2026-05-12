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
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditInPlaceParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.tag.EditTagBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.tag.EditTagDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;

public class RMTag
        implements RowMenu {

    /** Edit a {@link  Tag} which appears as a {@link BooklistGroup} (node). */
    private final EditInPlaceParcelableLauncher<Tag> launcher;
    @NonNull
    private final BooksOnBookshelfViewModel vm;

    public RMTag(@NonNull final BooksOnBookshelfViewModel vm) {
        this.vm = vm;
        launcher = new EditInPlaceParcelableLauncher<>(
                DBKey.FK_TAG,
                EditTagDialogFragment::new,
                EditTagBottomSheet::new);
        launcher.setListener(tag -> vm.onRowGroupUpdate(BooklistGroup.TAGS_GENRE, tag));
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

        if (rowData.getLong(DBKey.FK_TAG) != 0) {
            menu.add(Menu.NONE, R.id.MENU_TAG_EDIT,
                     res.getInteger(R.integer.MENU_ORDER_EDIT),
                     R.string.action_edit_ellipsis)
                .setIcon(R.drawable.edit_24px);

            menu.add(Menu.NONE, R.id.MENU_TAG_DELETE,
                     res.getInteger(R.integer.MENU_ORDER_DELETE),
                     R.string.action_delete)
                .setIcon(R.drawable.delete_24px);
        }

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
        if (menuItemId == R.id.MENU_TAG_EDIT) {
            final Tag tag = DataHolderUtils.requireTag(rowData);
            launcher.edit(context, tag);
            return true;

        } else if (menuItemId == R.id.MENU_TAG_DELETE) {
            final Tag tag = DataHolderUtils.requireTag(rowData);
            final int books = ServiceLocator.getInstance().getTagDao().countBooks(tag);
            StandardDialogs.deleteTag(context, tag, books, () -> vm.delete(context, tag));
            return true;
        }
        return false;
    }
}
