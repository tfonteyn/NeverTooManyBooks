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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;

public class PFilterListAdapter
        extends RecyclerView.Adapter<PFilterHolder> {

    @NonNull
    private final List<PFilter<?>> filters;
    @NonNull
    private final ModificationListener listener;
    private final LayoutInflater layoutInflater;

    /**
     * Constructor.
     *
     * @param context  Current context
     * @param filters  List of items
     * @param listener listener which will be notified if a filter is modified
     */
    public PFilterListAdapter(@NonNull final Context context,
                              @NonNull final List<PFilter<?>> filters,
                              @NonNull final ModificationListener listener) {
        layoutInflater = LayoutInflater.from(context);
        this.filters = filters;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PFilterHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                            final int viewType) {
        final View view = layoutInflater.inflate(viewType, parent, false);

        return filters
                .stream()
                .filter(pFilter -> pFilter.getPrefLayoutId() == viewType)
                .map(pFilter -> pFilter.createHolder(view, listener))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown viewType"));
    }

    @Override
    public void onBindViewHolder(@NonNull final PFilterHolder holder,
                                 final int position) {
        final PFilter<?> pFilter = filters.get(position);
        //noinspection unchecked
        ((BindableViewHolder<PFilter<?>>) holder).onBind(pFilter);
    }

    @Override
    public int getItemViewType(final int position) {
        return filters.get(position).getPrefLayoutId();
    }

    @Override
    public int getItemCount() {
        return filters.size();
    }
}
