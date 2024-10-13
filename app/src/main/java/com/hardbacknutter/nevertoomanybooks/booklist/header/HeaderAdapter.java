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

package com.hardbacknutter.nevertoomanybooks.booklist.header;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfHeaderBinding;

public class HeaderAdapter
        extends RecyclerView.Adapter<HeaderAdapter.HeaderViewHolder> {

    @NonNull
    private final LayoutInflater inflater;
    @NonNull
    private final Supplier<BooklistHeader> headerSupplier;

    /**
     * Constructor.
     *
     * @param context        Current context
     * @param headerSupplier a supplier to get the current header values from
     */
    public HeaderAdapter(@NonNull final Context context,
                         @NonNull final Supplier<BooklistHeader> headerSupplier) {
        inflater = LayoutInflater.from(context);
        this.headerSupplier = headerSupplier;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public HeaderViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                               final int viewType) {
        final BooksonbookshelfHeaderBinding vb = BooksonbookshelfHeaderBinding
                .inflate(inflater, parent, false);
        return new HeaderViewHolder(vb);
    }

    @Override
    public void onBindViewHolder(@NonNull final HeaderViewHolder holder,
                                 final int position) {
        holder.onBind(headerSupplier.get());
    }

    @Override
    public int getItemCount() {
        // Even if NO headers are displayed, we still have a row with height 0px
        return 1;
    }

    @Override
    public long getItemId(final int position) {
        // we only have one row; return a dummy value which is not a row-id in the list-table
        return Integer.MAX_VALUE;
    }

    public static class HeaderViewHolder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final BooksonbookshelfHeaderBinding vb;

        HeaderViewHolder(@NonNull final BooksonbookshelfHeaderBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        void onBind(@NonNull final BooklistHeader headerContent) {
            String header;
            header = headerContent.getStyleName();
            vb.styleName.setText(header);
            vb.styleName.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = headerContent.getFilterText();
            vb.filterText.setText(header);
            vb.filterText.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = headerContent.getSearchText();
            vb.searchText.setText(header);
            vb.searchText.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = headerContent.getBookCount();
            vb.bookCount.setText(header);
            vb.bookCount.setVisibility(header != null ? View.VISIBLE : View.GONE);
        }
    }
}
