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
package com.hardbacknutter.nevertoomanybooks;

import android.os.Bundle;
import android.view.Menu;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.dialogs.BaseFFDialogFragment;

public class BookshelfFiltersDialogFragment
        extends BaseFFDialogFragment {

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        delegate = new BookshelfFiltersDelegate(this, requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final BookshelfFiltersDelegate filtersDelegate = (BookshelfFiltersDelegate) delegate;

        adjustWindowSize(filtersDelegate.getRecyclerView(), 0.33f);

        if (!isFullscreen()) {
            // The floating dialog toolbar menu must display
            //     R.id.MENU_ACTION_ADD
            // but hide these duplicate buttons in favour of the bottom button-bar
            final Menu menu = filtersDelegate.getToolbar().getMenu();
            menu.findItem(R.id.MENU_ACTION_CLEAR).setVisible(false);
            menu.findItem(R.id.MENU_ACTION_SELECT).setVisible(false);
        }
    }
}
