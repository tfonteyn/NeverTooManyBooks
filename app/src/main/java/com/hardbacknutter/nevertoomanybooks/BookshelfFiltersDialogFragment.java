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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.EnumSet;

import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookshelfFiltersContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

public class BookshelfFiltersDialogFragment
        extends FFBaseDialogFragment {

    private BookshelfFiltersDelegate delegate;

    /**
     * No-arg constructor for OS use.
     */
    public BookshelfFiltersDialogFragment() {
        super(R.layout.dialog_edit_bookshelf_filters,
              R.layout.dialog_edit_bookshelf_filters_content,
              // Fullscreen on Medium screens
              // to avoid 3 buttons overlapping text on a UI in e.g. german
              EnumSet.of(WindowSizeClass.Medium),
              EnumSet.of(WindowSizeClass.Medium));
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        delegate = new BookshelfFiltersDelegate(this, requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getToolbar() != null) {
            delegate.initToolbarActionButtons(getToolbar(), delegate);
        }
        final DialogEditBookshelfFiltersContentBinding vb = DialogEditBookshelfFiltersContentBinding.bind(
                view.findViewById(R.id.dialog_content));
        setSubtitle(delegate.getToolbarSubtitle());

        delegate.onViewCreated(vb);

        adjustWindowSize(vb.filterList, 0.33f);
    }

    @Override
    public void onStart() {
        super.onStart();
        delegate.onStart();
    }
}
