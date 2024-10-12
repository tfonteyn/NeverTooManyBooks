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

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterBitmaskBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterBooleanBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterEntityListBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfFilterStringEqualityBinding;

public final class PFilterViewHolderFactory {

    private PFilterViewHolderFactory() {
    }

    /**
     * Create a ViewHolder to display the given filter type.
     *
     * @param inflater   The LayoutInflater object that can be used to inflate any views
     * @param parent     The ViewGroup into which the new View will be added after it is bound to
     *                   an adapter position.
     * @param filterType The view type of the filter (a layout id)
     * @param listener   to receive update notifications
     *
     * @return holder
     *
     * @throws IllegalArgumentException for an unsupported filter type
     */
    @NonNull
    public static PFilterViewHolder create(@NonNull final LayoutInflater inflater,
                                           @NonNull final ViewGroup parent,
                                           @LayoutRes final int filterType,
                                           @NonNull final ModificationListener listener) {
        if (filterType == R.layout.row_edit_bookshelf_filter_bitmask) {
            final RowEditBookshelfFilterBitmaskBinding vb =
                    RowEditBookshelfFilterBitmaskBinding.inflate(inflater, parent, false);
            return new BitmaskHolder(vb, listener);

        } else if (filterType == R.layout.row_edit_bookshelf_filter_boolean) {
            final RowEditBookshelfFilterBooleanBinding vb =
                    RowEditBookshelfFilterBooleanBinding.inflate(inflater, parent, false);
            return new BooleanHolder(vb, listener);

        } else if (filterType == R.layout.row_edit_bookshelf_filter_entity_list) {
            final RowEditBookshelfFilterEntityListBinding vb =
                    RowEditBookshelfFilterEntityListBinding.inflate(inflater, parent, false);
            return new EntityListHolder<>(vb, listener);

        } else if (filterType == R.layout.row_edit_bookshelf_filter_string_equality) {
            final RowEditBookshelfFilterStringEqualityBinding vb =
                    RowEditBookshelfFilterStringEqualityBinding.inflate(inflater, parent, false);
            return new StringEqualityHolder(vb, listener);

        } else {
            throw new IllegalArgumentException("Unsupported filter type");
        }
    }
}
