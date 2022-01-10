/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.databinding.RowChoiceMultiBinding;

/**
 * Add a list of CheckBox options to a RecyclerView.
 * <p>
 * Row layout: {@code R.layout.row_choice_multi}
 *
 * @param <ID> first element of a {@link Pair} - the id for the item
 * @param <CS> second element of a {@link Pair} - the CharSequence to display
 */
public class ChecklistRecyclerAdapter<ID, CS extends CharSequence>
        extends RecyclerView.Adapter<ChecklistRecyclerAdapter.Holder> {

    /** Cached inflater. */
    @NonNull
    private final LayoutInflater mInflater;
    @NonNull
    private final List<Pair<ID, CS>> mItems;
    @Nullable
    private final SelectionListener<ID> mOnSelectionListener;
    /** The (pre-)selected items. */
    @NonNull
    private final Set<ID> mSelection;

    /**
     * Constructor.
     *
     * @param context   Current context
     * @param items     List of items
     * @param selection (optional) the pre-selected items
     * @param listener  (optional) to send a selection to
     */
    ChecklistRecyclerAdapter(@NonNull final Context context,
                             @NonNull final List<Pair<ID, CS>> items,
                             @Nullable final Set<ID> selection,
                             @Nullable final SelectionListener<ID> listener) {

        mInflater = LayoutInflater.from(context);
        mItems = items;
        mSelection = selection != null ? selection : new HashSet<>();
        mOnSelectionListener = listener;
    }

    @Override
    @NonNull
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {
        final RowChoiceMultiBinding vb = RowChoiceMultiBinding.inflate(mInflater, parent, false);
        final Holder holder = new Holder(vb);
        holder.vb.btnOption.setOnClickListener(v -> onItemCheckChanged(holder));
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {
        final Pair<ID, CS> item = mItems.get(position);

        final boolean checked = mSelection.contains(item.first);
        holder.vb.btnOption.setChecked(checked);
        holder.vb.btnOption.setText(item.second);
    }

    private void onItemCheckChanged(@NonNull final Holder holder) {

        final int position = holder.getAbsoluteAdapterPosition();

        final boolean selected = holder.vb.btnOption.isChecked();

        final ID itemId = mItems.get(position).first;
        if (selected) {
            mSelection.add(itemId);
        } else {
            mSelection.remove(itemId);
        }

        if (mOnSelectionListener != null) {
            // use a post allowing the UI to update view first
            holder.vb.btnOption.post(() -> mOnSelectionListener.onSelected(itemId, selected));
        }
    }

    /**
     * Get the set with the selected item ID's.
     *
     * @return set of ID's
     */
    @NonNull
    public Set<ID> getSelection() {
        return mSelection;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public interface SelectionListener<ID> {

        void onSelected(@NonNull ID id,
                        boolean checked);
    }

    /**
     * Row ViewHolder for {@link ChecklistRecyclerAdapter}.
     */
    static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowChoiceMultiBinding vb;

        Holder(@NonNull final RowChoiceMultiBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }
    }
}
