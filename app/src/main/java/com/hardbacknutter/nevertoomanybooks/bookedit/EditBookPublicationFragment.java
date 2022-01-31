/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookPublicationBinding;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FieldGroup;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.utils.WindowClass;

public class EditBookPublicationFragment
        extends EditBookBaseFragment {

    /** View Binding. */
    private FragmentEditBookPublicationBinding mVb;

    @NonNull
    @Override
    public FragmentId getFragmentId() {
        return FragmentId.Publication;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentEditBookPublicationBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = getContext();
        //noinspection ConstantConditions
        mVm.initFields(context, FragmentId.Publication, FieldGroup.Publication);
        // on EXPANDED screens the notes fields are incorporated in the publication fragment
        if (WindowClass.getCurrent(context) == WindowClass.EXPANDED) {
            mVm.initFields(context, FragmentId.Publication, FieldGroup.Notes);
        }

        mVm.onPublisherList().observe(getViewLifecycleOwner(),
                                      publishers -> mVm.requireField(R.id.publisher)
                                                       .setValue(publishers));

        // no listener/callback. We share the book view model in the Activity scope
        mVb.publisher.setOnClickListener(v -> EditBookPublisherListDialogFragment
                .launch(getChildFragmentManager()));
    }


    @Override
    void onPopulateViews(@NonNull final List<Field<?, ? extends View>> fields,
                         @NonNull final Book book) {
        //noinspection ConstantConditions
        mVm.getBook().prunePublishers(getContext(), true);

        super.onPopulateViews(fields, book);

        //noinspection ConstantConditions
        fields.forEach(field -> field.setVisibility(getView(), false, false));
    }
}
