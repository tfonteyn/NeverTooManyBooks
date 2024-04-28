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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookTocContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link TocEntry}.
 */
public class EditTocEntryDialogFragment
        extends FFBaseDialogFragment {

    private EditTocEntryDelegate delegate;

    /**
     * No-arg constructor for OS use.
     */
    public EditTocEntryDialogFragment() {
        super(R.layout.dialog_edit_book_toc, R.layout.dialog_edit_book_toc_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        delegate = new EditTocEntryDelegate(this, requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getToolbar() != null) {
            delegate.initToolbarActionButtons(getToolbar(), delegate);
        }

        final DialogEditBookTocContentBinding vb = DialogEditBookTocContentBinding
                .bind(view.findViewById(R.id.dialog_content));
        setTitle(delegate.getToolbarTitle());

        delegate.onViewCreated(vb);
    }

    @Override
    public void onPause() {
        delegate.viewToModel();
        super.onPause();
    }
}
