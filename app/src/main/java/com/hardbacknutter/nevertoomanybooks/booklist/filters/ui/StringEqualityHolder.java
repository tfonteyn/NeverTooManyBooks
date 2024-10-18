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
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.FilterFactory;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PStringEqualityFilter;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ExtTextWatcher;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterStringEqualityBinding;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;

@SuppressWarnings("WeakerAccess")
public class StringEqualityHolder
        extends PFilterViewHolder
        implements BindableViewHolder<PStringEqualityFilter> {

    @NonNull
    private final RowEditBookshelfFilterStringEqualityBinding vb;
    @Nullable
    private ExtTextWatcher currentTextWatcher;

    /**
     * Constructor.
     *
     * @param vb       view-binding
     * @param listener for update events
     */
    public StringEqualityHolder(@NonNull final RowEditBookshelfFilterStringEqualityBinding vb,
                                @NonNull final ModificationListener listener) {
        super(vb.getRoot(), listener);
        this.vb = vb;
    }

    public void onBind(@NonNull final PStringEqualityFilter filter) {
        // Remove any previous listener before modifying the field
        if (currentTextWatcher != null) {
            vb.filter.removeTextChangedListener(currentTextWatcher);
        }

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
        // Now that the value has been set, enable a listener to detect user made changes.
        currentTextWatcher = s -> {
            filter.setValue(context, s.toString());
            listener.onModified(getBindingAdapterPosition());
        };
        vb.filter.addTextChangedListener(currentTextWatcher);
    }
}
