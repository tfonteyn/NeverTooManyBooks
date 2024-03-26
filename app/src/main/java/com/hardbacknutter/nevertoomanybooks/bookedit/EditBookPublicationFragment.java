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

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.bookreadstatus.ReadStatusFragmentFactory;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookPublicationBinding;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FieldGroup;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;

public class EditBookPublicationFragment
        extends EditBookBaseFragment {

    /** View Binding. */
    private FragmentEditBookPublicationBinding vb;

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
        vb = FragmentEditBookPublicationBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = getContext();

        //noinspection DataFlowIssue
        vm.initFields(context, FragmentId.Publication, FieldGroup.Publication);
        // On tablets the notes fields (notes, read-flag, read-dates...)
        // are incorporated in the publication fragment
        // On small screens (i.e. phones) they get their own tab
        if (vb.notes != null) {
            vm.initFields(context, FragmentId.Publication, FieldGroup.Notes);

            ReadStatusFragmentFactory.bind(getChildFragmentManager(), R.id.fragment_read,
                                           vm.getStyle(),
                                           ReadStatusFragmentFactory.VIEWMODEL_EDIT);

            vm.onReadStatusChanged()
              .observe(getViewLifecycleOwner(), aVoid -> onReadStatusChanged());
        }

        // Publisher editor (screen)
        // no listener/callback. We share the book view model in the Activity scope
        vb.lblPublisher.setEndIconOnClickListener(v -> editPublisher());
        vb.publisher.setOnClickListener(v -> editPublisher());
    }

    private void editPublisher() {
        EditBookPublisherListDialogFragment.launch(getChildFragmentManager());
    }


    @Override
    void onPopulateViews(@NonNull final List<Field<?, ? extends View>> fields,
                         @NonNull final Book book) {
        //noinspection DataFlowIssue
        vm.getBook().prunePublishers(getContext());

        super.onPopulateViews(fields, book);

        getFab().setVisibility(View.INVISIBLE);

        //noinspection DataFlowIssue
        fields.forEach(field -> field.setVisibility(getView(), false, false));
    }
}
