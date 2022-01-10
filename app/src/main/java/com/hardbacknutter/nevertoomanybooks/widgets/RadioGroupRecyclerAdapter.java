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

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.databinding.RowChoiceSingleBinding;

/**
 * Add a list of RadioButtons to a RecyclerView.
 * <p>
 * Row layout: {@code R.layout.row_choice_single}
 *
 * @param <ID> first element of a {@link Pair} - the id for the item
 * @param <CS> second element of a {@link Pair} - the CharSequence to display
 */
public class RadioGroupRecyclerAdapter<ID, CS extends CharSequence>
        extends RecyclerView.Adapter<RadioGroupRecyclerAdapter.Holder> {

    /** Cached inflater. */
    @NonNull
    private final LayoutInflater mInflater;
    @NonNull
    private final List<Pair<ID, CS>> mItems;
    @Nullable
    private final SelectionListener<ID> mOnSelectionListener;
    /** The (pre-)selected item. */
    @Nullable
    private ID mSelection;

    /**
     * Constructor.
     *
     * @param context   Current context
     * @param items     List of items
     * @param selection (optional) the pre-selected item
     * @param listener  (optional) to send a selection to
     */
    public RadioGroupRecyclerAdapter(@NonNull final Context context,
                                     @NonNull final List<Pair<ID, CS>> items,
                                     @Nullable final ID selection,
                                     @Nullable final SelectionListener<ID> listener) {

        mInflater = LayoutInflater.from(context);
        mItems = items;
        mSelection = selection;
        mOnSelectionListener = listener;
    }

    @Override
    @NonNull
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {
        final RowChoiceSingleBinding vb = RowChoiceSingleBinding.inflate(mInflater, parent, false);
        final Holder holder = new Holder(vb);
        holder.vb.btnOption.setOnClickListener(v -> onItemCheckChanged(holder));
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {
        final Pair<ID, CS> item = mItems.get(position);

        final boolean checked = mSelection != null && mSelection == item.first;
        holder.vb.btnOption.setChecked(checked);
        holder.vb.btnOption.setText(item.second);
    }

    private void onItemCheckChanged(@NonNull final Holder holder) {

        final int position = holder.getAbsoluteAdapterPosition();

        mSelection = mItems.get(position).first;
        // this triggers a bind call for all rows, which in turn (un)sets the checked row.
        notifyDataSetChanged();
        if (mOnSelectionListener != null) {
            // use a post allowing the UI to update the view first
            holder.vb.btnOption.post(() -> mOnSelectionListener.onSelected(mSelection));
        }
    }

    /**
     * Get the selected item.
     *
     * @return item id, can be {@code null} if no item is selected.
     */
    @Nullable
    public ID getSelection() {
        return mSelection;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public interface SelectionListener<ID> {

        void onSelected(@Nullable ID id);
    }

    /**
     * Row ViewHolder for {@link RadioGroupRecyclerAdapter}.
     */
    static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowChoiceSingleBinding vb;

        Holder(@NonNull final RowChoiceSingleBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }
    }
}
