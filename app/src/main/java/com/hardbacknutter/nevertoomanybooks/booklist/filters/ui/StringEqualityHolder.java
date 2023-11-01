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
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.FilterFactory;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PStringEqualityFilter;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ExtTextWatcher;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterStringEqualityBinding;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;

public class StringEqualityHolder
        extends PFilterHolder
        implements BindableViewHolder<PStringEqualityFilter> {

    @NonNull
    private final RowEditBookshelfFilterStringEqualityBinding vb;

    /**
     * Constructor.
     *
     * @param itemView the view specific for this holder
     * @param listener for update events
     */
    public StringEqualityHolder(@NonNull final View itemView,
                                @NonNull final ModificationListener listener) {
        super(itemView, listener);
        vb = RowEditBookshelfFilterStringEqualityBinding.bind(itemView);
    }

    public void onBind(@NonNull final PStringEqualityFilter filter) {
        final Context context = itemView.getContext();
        vb.lblFilter.setText(filter.getLabel(context));
        vb.filter.setText(filter.getValueText(context));

        // We cannot share this adapter/formatter between multiple Holder instances
        // as they depends on the DBKey of the filter.
        @Nullable
        final ExtArrayAdapter<String> adapter = FilterFactory
                .createAdapter(context, filter.getDBKey());
        // likewise, always set the adapter even when null
        vb.filter.setAdapter(adapter);
        vb.filter.addTextChangedListener((ExtTextWatcher) s -> {
            filter.setValueText(context, s.toString());
            listener.onModified(getBindingAdapterPosition());
        });
    }
}
