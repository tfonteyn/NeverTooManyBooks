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

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelfViewModel;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditInPlaceParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.series.EditSeriesBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.series.EditSeriesDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.menus.MenuHandler;
import com.hardbacknutter.nevertoomanybooks.menus.SiteSearchMenuHandler;

public class RMSeries
        implements RowMenu {

    /** Edit a {@link Series} which appears as a {@link BooklistGroup} (node). */
    private final EditInPlaceParcelableLauncher<Series> launcher;
    @NonNull
    private final BooksOnBookshelfViewModel vm;
    private final List<MenuHandler<DataHolder>> menuHandlers;

    public RMSeries(@NonNull final BooksOnBookshelfViewModel vm) {
        this.vm = vm;

        menuHandlers = List.of(new SiteSearchMenuHandler());

        launcher = new EditInPlaceParcelableLauncher<>(
                DBKey.FK_SERIES,
                EditSeriesDialogFragment::new,
                EditSeriesBottomSheet::new);
        launcher.setListener(series -> vm.onRowGroupUpdate(BooklistGroup.SERIES, series));
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

        if (rowData.getLong(DBKey.FK_SERIES) != 0) {
            final boolean complete = rowData.getBoolean(DBKey.SERIES.COMPLETE);

            menu.add(Menu.NONE, R.id.MENU_SERIES_SET_COMPLETE,
                     res.getInteger(R.integer.MENU_ORDER_COMPLETE),
                     R.string.option_set_complete)
                .setIcon(R.drawable.check_box_24px)
                .setVisible(!complete);

            menu.add(Menu.NONE, R.id.MENU_SERIES_SET_INCOMPLETE,
                     res.getInteger(R.integer.MENU_ORDER_COMPLETE),
                     R.string.option_set_incomplete)
                .setIcon(R.drawable.check_box_outline_blank_24px)
                .setVisible(complete);

            menu.add(Menu.NONE, R.id.MENU_SERIES_EDIT,
                     res.getInteger(R.integer.MENU_ORDER_EDIT),
                     R.string.action_edit_ellipsis)
                .setIcon(R.drawable.edit_24px);

            menu.add(Menu.NONE, R.id.MENU_SERIES_DELETE,
                     res.getInteger(R.integer.MENU_ORDER_DELETE),
                     R.string.action_delete)
                .setIcon(R.drawable.delete_24px);


            menuHandlers.forEach(h -> h.onCreateMenu(context, menuInflater, menu, rowData));
            menuHandlers.forEach(h -> h.onPrepareMenu(context, menu, rowData));
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
        if (menuItemId == R.id.MENU_SERIES_SET_COMPLETE
            || menuItemId == R.id.MENU_SERIES_SET_INCOMPLETE) {
            final Series series = DataHolderUtils.requireSeries(rowData);
            // toggle the complete status
            final boolean status = !rowData.getBoolean(DBKey.SERIES.COMPLETE);
            vm.setSeriesComplete(series, status);
            return true;

        } else if (menuItemId == R.id.MENU_SERIES_EDIT) {
            final Series series = DataHolderUtils.requireSeries(rowData);
            launcher.edit(context, series);
            return true;

        } else if (menuItemId == R.id.MENU_SERIES_DELETE) {
            final Series series = DataHolderUtils.requireSeries(rowData);
            final int books = ServiceLocator.getInstance().getSeriesDao().countBooks(series);
            StandardDialogs.deleteSeries(context, series, books,
                                         () -> vm.delete(context, series));
            return true;
        }
        return menuHandlers.stream().anyMatch(
                h -> h.onMenuItemSelected(context, menuItemId, rowData));
    }
}
