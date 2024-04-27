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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookshelfFiltersContentBinding;

public class BookshelfFiltersBottomSheet
        extends BottomSheetDialogFragment {

    private DialogEditBookshelfFiltersContentBinding vb;
    private BookshelfFiltersDelegate delegate;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        delegate = new BookshelfFiltersDelegate(this, requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = DialogEditBookshelfFiltersContentBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        delegate.initToolbarActionButtons(vb.dialogToolbar, R.menu.edit_filters, delegate);
        vb.dragHandle.setVisibility(View.VISIBLE);
        vb.buttonPanelLayout.setVisibility(View.GONE);

        vb.dialogToolbar.setSubtitle(delegate.getToolbarSubtitle());

        delegate.onViewCreated(vb);
    }

    @Override
    public void onStart() {
        super.onStart();
        delegate.onStart();
    }
}
