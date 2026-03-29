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

package com.hardbacknutter.nevertoomanybooks.localsearch;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.database.dao.FtsSearchResult;
import com.hardbacknutter.nevertoomanybooks.databinding.RowSearchResultBinding;

public class SearchAdapter
        extends RecyclerView.Adapter<SearchAdapter.Holder> {

    @NonNull
    private final List<FtsSearchResult> list;
    @NonNull
    private final Consumer<Long> displayBook;

    SearchAdapter(@NonNull final List<FtsSearchResult> list,
                  @NonNull final Consumer<Long> displayBook) {
        this.list = list;
        this.displayBook = displayBook;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {
        final RowSearchResultBinding vb = RowSearchResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(vb, displayBook);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {
        holder.onBind(list.get(position));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowSearchResultBinding vb;
        private long id;

        Holder(@NonNull final RowSearchResultBinding vb,
               @NonNull final Consumer<Long> displayBook) {
            super(vb.getRoot());
            this.vb = vb;
            this.vb.getRoot().setOnClickListener(v -> displayBook.accept(id));
        }

        void onBind(@NonNull final FtsSearchResult result) {
            id = result.id;
            vb.line1.setText(result.line1);
            vb.line2.setText(result.line2);
        }
    }
}
