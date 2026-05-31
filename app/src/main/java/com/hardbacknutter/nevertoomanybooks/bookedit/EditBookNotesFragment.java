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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.bookreadstatus.ReadStatusFragmentFactory;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;

public class EditBookNotesFragment
        extends EditBookBaseFragment {

    @NonNull
    @Override
    public FragmentId getFragmentId() {
        return FragmentId.Notes;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        vm.initFieldsNotes(FragmentId.Notes);

        return inflater.inflate(R.layout.fragment_edit_book_notes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Always active as fields other than the read/readProgress fragment depend on it
        vm.onUpdateReadStatus().observe(getViewLifecycleOwner(), this::onReadStatusUpdate);

        if (ServiceLocator.getInstance().isFieldEnabled(DBKey.READ__BOOL)
            || ServiceLocator.getInstance().isFieldEnabled(DBKey.READ_PROGRESS)) {
            ReadStatusFragmentFactory.createEditor(getChildFragmentManager(),
                                                   R.id.fragment_read,
                                                   vm.getStyle());
        }
        // Update *this* fragment + the ReadStatusFragment
        vm.updateReadStatus(false);
    }

    @CallSuper
    @Override
    public void onResume() {
        super.onResume();
        getFab().setVisibility(View.INVISIBLE);
    }

    @Override
    void onPopulateViews(@NonNull final List<Field<?, ? extends View>> fields,
                         @NonNull final Book book) {

        super.onPopulateViews(fields, book);

        //noinspection DataFlowIssue
        fields.forEach(field -> field.setVisibility(getView(), false, false));
    }
}
