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
package com.hardbacknutter.nevertoomanybooks.widgets.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.databinding.RowChoiceMultiBinding;

/**
 * Add a list of CheckBox options to a RecyclerView.
 * <p>
 * Row layout: {@code R.layout.row_choice_multi}
 *
 * @param <T> type of the item
 */
public class ChecklistRecyclerAdapter<T>
        extends RecyclerView.Adapter<ChecklistRecyclerAdapter.Holder> {

    @NonNull
    private final List<T> items;
    @NonNull
    private final Function<Integer, CharSequence> labelSupplier;
    /** The (pre-)selected items. */
    @NonNull
    private final Set<T> selection;
    @Nullable
    private final SelectionListener<T> selectionListener;

    /**
     * Constructor.
     *
     * @param items         List of items
     * @param labelSupplier given the position in the list, supply a label for the item
     * @param selection     (optional) the pre-selected item ids
     * @param listener      (optional) to send a selection to as the user changes them;
     *                      alternatively use {@link #getSelection()} when done.
     */
    public ChecklistRecyclerAdapter(@NonNull final List<T> items,
                                    @NonNull final Function<Integer, CharSequence> labelSupplier,
                                    @Nullable final Set<T> selection,
                                    @Nullable final SelectionListener<T> listener) {
        this.items = items;
        this.labelSupplier = labelSupplier;
        this.selection = selection != null ? selection : new HashSet<>();
        selectionListener = listener;
    }

    @Override
    @NonNull
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {
        final RowChoiceMultiBinding vb = RowChoiceMultiBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        final Holder holder = new Holder(vb);
        holder.vb.btnOption.setOnClickListener(v -> onItemCheckChanged(holder));
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {
        final boolean checked = selection.contains(items.get(position));
        holder.vb.btnOption.setChecked(checked);
        holder.vb.btnOption.setText(labelSupplier.apply(position));
    }

    private void onItemCheckChanged(@NonNull final Holder holder) {
        final int position = holder.getAbsoluteAdapterPosition();

        final boolean selected = holder.vb.btnOption.isChecked();

        final T item = items.get(position);

        if (selectionListener != null) {
            // It's the listeners responsibility to update the Set (or not)
            // use a post allowing the UI to update view first
            holder.vb.btnOption.post(() -> selectionListener.onSelected(item, selected));
        } else {
            // There was no listener, we'll update it now/here
            if (selected) {
                selection.add(item);
            } else {
                selection.remove(item);
            }
        }
    }

    /**
     * Get the set with the selected items.
     *
     * @return set of selected items, can be empty of none selected.
     */
    @NonNull
    public Set<T> getSelection() {
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
         * <p>
         * <strong>It's the listeners responsibility to update the backing Set.</strong>
         *
         * @param item    selected
         * @param checked state of the item
         */
        void onSelected(@NonNull T item,
                        boolean checked);
    }

    /**
     * Row ViewHolder for {@link ChecklistRecyclerAdapter}.
     */
    public static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowChoiceMultiBinding vb;

        Holder(@NonNull final RowChoiceMultiBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }
    }
}
