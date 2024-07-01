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

package com.hardbacknutter.nevertoomanybooks.booklist.filters.ui;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.PBooleanFilter;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterBooleanBinding;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;

public class BooleanHolder
        extends PFilterViewHolder
        implements BindableViewHolder<PBooleanFilter> {

    @NonNull
    private final RowEditBookshelfFilterBooleanBinding vb;

    /**
     * Constructor.
     *
     * @param vb       view-binding
     * @param listener for update events
     */
    public BooleanHolder(@NonNull final RowEditBookshelfFilterBooleanBinding vb,
                         @NonNull final ModificationListener listener) {
        super(vb.getRoot(), listener);
        this.vb = vb;
    }

    public void onBind(@NonNull final PBooleanFilter filter) {
        final Context context = itemView.getContext();
        vb.lblFilter.setText(filter.getLabel(context));
        vb.valueTrue.setText(filter.getValueText(context, true));
        vb.valueFalse.setText(filter.getValueText(context, false));

        // Before we bind the initial value, remove the listener from the RadioGroup
        vb.filter.setOnCheckedChangeListener(null);
        final Boolean value = filter.getValue();
        if (value == null) {
            vb.filter.clearCheck();
        } else {
            vb.valueTrue.setChecked(value);
            vb.valueFalse.setChecked(!value);
        }

        // Now that the value has been set, enable a listener to detect user made changes.
        vb.filter.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == -1) {
                filter.setValue(context, null);
            } else {
                filter.setValue(context, checkedId == vb.valueTrue.getId());
            }
            listener.onModified(getBindingAdapterPosition());
        });
    }
}
