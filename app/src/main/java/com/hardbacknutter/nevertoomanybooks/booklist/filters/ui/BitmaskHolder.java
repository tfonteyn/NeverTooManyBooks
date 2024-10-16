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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PBitmaskFilter;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterBitmaskBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.MultiChoiceAlertDialogBuilder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;

public class BitmaskHolder
        extends PFilterViewHolder
        implements BindableViewHolder<PBitmaskFilter> {

    @NonNull
    private final RowEditBookshelfFilterBitmaskBinding vb;

    /**
     * Constructor.
     *
     * @param vb       view-binding
     * @param listener for update events
     */
    public BitmaskHolder(@NonNull final RowEditBookshelfFilterBitmaskBinding vb,
                         @NonNull final ModificationListener listener) {
        super(vb.getRoot(), listener);
        this.vb = vb;
    }

    @Override
    public void onBind(@NonNull final PBitmaskFilter filter) {
        final Context context = itemView.getContext();
        vb.lblFilter.setText(filter.getLabel(context));
        vb.filter.setText(filter.getValueText(context));

        vb.ROWONCLICKTARGET.setOnClickListener(v -> {
            final Map<Integer, String> bitsAndLabels = filter.getBitsAndLabels(context);
            final List<Integer> ids = new ArrayList<>(bitsAndLabels.keySet());
            final List<String> labels = new ArrayList<>(bitsAndLabels.values());

            // This will potentially be called upon from inside a BottomSheet.
            // Hence we stick with using a Dialog.
            // - Note this is NOT rotation-safe
            new MultiChoiceAlertDialogBuilder<Integer>(context)
                    .setTitle(R.string.lbl_edition)
                    .setItems(ids, labels)
                    .setSelectedItems(filter.getValue())
                    .setPositiveButton(R.string.ok, value -> {
                        filter.setValue(context, value);
                        vb.filter.setText(filter.getValueText(context));
                        listener.onModified(getBindingAdapterPosition());
                    })
                    .build()
                    .show();
        });
    }
}
