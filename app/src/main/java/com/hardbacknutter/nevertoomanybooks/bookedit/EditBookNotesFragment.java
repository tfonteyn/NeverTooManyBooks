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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.bookreadstatus.ReadStatusFragmentFactory;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FieldGroup;
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
        return inflater.inflate(R.layout.fragment_edit_book_notes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection DataFlowIssue
        vm.initFields(getContext(), FragmentId.Notes, FieldGroup.Notes);

        ReadStatusFragmentFactory.bind(getChildFragmentManager(), R.id.fragment_read,
                                       vm.getStyle(),
                                       ReadStatusFragmentFactory.Mode.Edit);

        vm.onReadStatusChanged().observe(getViewLifecycleOwner(), aVoid -> onReadStatusChanged());
    }

    @Override
    void onPopulateViews(@NonNull final List<Field<?, ? extends View>> fields,
                         @NonNull final Book book) {

        super.onPopulateViews(fields, book);

        getFab().setVisibility(View.INVISIBLE);

        //noinspection DataFlowIssue
        fields.forEach(field -> field.setVisibility(getView(), false, false));
    }
}
