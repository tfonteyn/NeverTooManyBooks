/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.view.View;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.PBooleanFilter;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterBooleanBinding;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;

public class BooleanHolder
        extends PFilterHolder
        implements BindableViewHolder<PBooleanFilter> {

    @NonNull
    private final RowEditBookshelfFilterBooleanBinding vb;

    /**
     * Constructor.
     *
     * @param itemView the view specific for this holder
     * @param listener for update events
     */
    public BooleanHolder(@NonNull final View itemView,
                         @NonNull final ModificationListener listener) {
        super(itemView, listener);
        vb = RowEditBookshelfFilterBooleanBinding.bind(itemView);
    }

    public void onBind(@NonNull final PBooleanFilter filter) {
        final Context context = itemView.getContext();
        vb.lblFilter.setText(filter.getLabel(context));
        vb.valueTrue.setText(filter.getValueText(context, true));
        vb.valueFalse.setText(filter.getValueText(context, false));

        vb.filter.setOnCheckedChangeListener(null);
        final Boolean value = filter.getValue();
        if (value == null) {
            vb.filter.clearCheck();
        } else {
            vb.valueTrue.setChecked(value);
            vb.valueFalse.setChecked(!value);
        }

        vb.filter.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == -1) {
                filter.setValue(null);
            } else {
                filter.setValue(checkedId == vb.valueTrue.getId());
            }
            listener.onModified(getBindingAdapterPosition());
        });
    }
}
