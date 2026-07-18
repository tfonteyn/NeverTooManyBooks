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
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.helper.widget.Flow;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.fields.IdentifierField;

public class EditBookExternalIdFragment
        extends EditBookBaseFragment {

    @NonNull
    @Override
    public FragmentId getFragmentId() {
        return FragmentId.ExternalId;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        final View root = inflater
                .inflate(R.layout.fragment_edit_book_external_id, container, false);

        vm.initFieldsExternalId(FragmentId.ExternalId);

        final ViewGroup content = root.findViewById(R.id.content_body);
        final Flow flow = content.findViewById(R.id.flow_site_ids);

        // collect ids to add to the Flow layout
        final int[] ids = new int[EditBookViewModel.KEYS.size()];

        for (int i = 0; i < EditBookViewModel.KEYS.size(); i++) {
            final IdentifierField<EditText> field = (IdentifierField<EditText>)
                    vm.<String, EditText>requireField(EditBookViewModel.KEYS.get(i));

            final Identifier identifier = field.getIdentifier();
            @LayoutRes
            final int layoutId;
            if (identifier.getType() == Identifier.Type.Number) {
                layoutId = R.layout.row_edit_sid_number;
            } else {
                layoutId = R.layout.row_edit_sid_text;
            }
            final View view = inflater.inflate(layoutId, content, false);

            final TextInputLayout til = view.findViewById(R.id.til);
            final int tilId = field.getTextInputLayoutId();
            til.setId(tilId);
            til.setHint(identifier.getName());
            ids[i] = tilId;

            final TextInputEditText tie = view.findViewById(R.id.tie);
            tie.setId(field.getFieldViewId());
            // last one?
            if (i == EditBookViewModel.KEYS.size() - 1) {
                tie.setImeOptions(EditorInfo.IME_ACTION_DONE);
            }

            content.addView(view);
        }
        flow.setReferencedIds(ids);

        return root;
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

        // Force hidden fields to stay hidden; this will allow us to temporarily remove
        // some sites without removing the data.
        //noinspection DataFlowIssue
        fields.forEach(field -> field.setVisibility(getView(), false, true));
    }
}
