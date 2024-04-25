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
package com.hardbacknutter.nevertoomanybooks.widgets.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.databinding.RowChoiceSingleBinding;

/**
 * Add a list of RadioButtons to a RecyclerView.
 * <p>
 * Row layout: {@code R.layout.row_choice_single}
 *
 * @param <T> type of the item
 */
public class RadioGroupRecyclerAdapter<T>
        extends RecyclerView.Adapter<RadioGroupRecyclerAdapter.Holder> {

    /** Cached inflater. */
    @NonNull
    private final LayoutInflater inflater;
    @NonNull
    private final List<T> items;
    @NonNull
    private final Function<Integer, CharSequence> labelSupplier;
    @Nullable
    private final SelectionListener<T> selectionListener;
    /** The (pre-)selected item. */
    @Nullable
    private T selection;

    /**
     * Constructor.
     *
     * @param context       Current context
     * @param items         List of items
     * @param labelSupplier given the position in the list, supply a label for the item
     * @param selection     (optional) the pre-selected item
     * @param listener      (optional) when provided, the user selection will be send
     *                      to this listener each time it changes;
     *                      Alternatively use {@link #getSelection()} when done.
     */
    public RadioGroupRecyclerAdapter(@NonNull final Context context,
                                     @NonNull final List<T> items,
                                     @NonNull final Function<Integer, CharSequence> labelSupplier,
                                     @Nullable final T selection,
                                     @Nullable final SelectionListener<T> listener) {

        inflater = LayoutInflater.from(context);
        this.items = items;
        this.labelSupplier = labelSupplier;
        this.selection = selection;
        selectionListener = listener;
    }

    @Override
    @NonNull
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {
        final RowChoiceSingleBinding vb = RowChoiceSingleBinding.inflate(inflater, parent, false);
        final Holder holder = new Holder(vb);
        holder.vb.btnOption.setOnClickListener(v -> onItemCheckChanged(holder));
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {
        final boolean checked = selection != null && selection.equals(items.get(position));
        holder.vb.btnOption.setChecked(checked);
        holder.vb.btnOption.setText(labelSupplier.apply(position));
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onItemCheckChanged(@NonNull final Holder holder) {
        final int position = holder.getAbsoluteAdapterPosition();

        selection = items.get(position);
        // this triggers a bind call for all rows, which in turn (un)sets the checked row.
        notifyDataSetChanged();

        if (selectionListener != null) {
            // use a post allowing the UI to update the view first
            holder.vb.btnOption.post(() -> selectionListener.onSelected(selection));
        }
    }

    /**
     * Get the selected item.
     *
     * @return item selected, can be {@code null} if none selected.
     */
    @Nullable
    public T getSelection() {
        return selection;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @FunctionalInterface
    public interface SelectionListener<T> {

        /**
         * Called after the user selected an item.
         *
         * @param item selected, can be {@code null} if none selected.
         */
        void onSelected(@Nullable T item);
    }

    /**
     * Row ViewHolder for {@link RadioGroupRecyclerAdapter}.
     */
    public static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowChoiceSingleBinding vb;

        Holder(@NonNull final RowChoiceSingleBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }
    }
}
