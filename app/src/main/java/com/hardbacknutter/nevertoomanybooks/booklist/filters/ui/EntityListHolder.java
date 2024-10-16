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

import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PEntityListFilter;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterEntityListBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.MultiChoiceAlertDialogBuilder;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;

public class EntityListHolder<T extends Entity>
        extends PFilterViewHolder
        implements BindableViewHolder<PEntityListFilter<T>> {

    @NonNull
    private final RowEditBookshelfFilterEntityListBinding vb;

    /**
     * Constructor.
     *
     * @param vb       view-binding
     * @param listener for update events
     */
    public EntityListHolder(@NonNull final RowEditBookshelfFilterEntityListBinding vb,
                            @NonNull final ModificationListener listener) {
        super(vb.getRoot(), listener);
        this.vb = vb;
    }

    public void onBind(@NonNull final PEntityListFilter<T> filter) {
        final Context context = itemView.getContext();
        vb.lblFilter.setText(filter.getLabel(context));
        vb.filter.setText(filter.getValueText(context));

        vb.ROWONCLICKTARGET.setOnClickListener(v -> {
            final List<T> entities = filter.getEntities();
            final List<Long> ids = entities.stream()
                                           .map(Entity::getId)
                                           .collect(Collectors.toList());
            final List<String> labels = entities.stream()
                                                .map(entity -> entity.getLabel(context))
                                                .collect(Collectors.toList());

            // This will potentially be called upon from inside a BottomSheet.
            // Hence we stick with using a Dialog.
            // - Note this is NOT rotation-safe
            new MultiChoiceAlertDialogBuilder<Long>(context)
                    .setTitle(filter.getLabel(context))
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
