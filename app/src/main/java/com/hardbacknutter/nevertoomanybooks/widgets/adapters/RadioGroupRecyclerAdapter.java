/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Add a list of RadioButtons to a RecyclerView.
 * <p>
 * Row layout: {@code R.layout.row_choice_single}
 *
 * @param <ID> the id for the item
 * @param <CS> the CharSequence to display
 */
public class RadioGroupRecyclerAdapter<ID, CS extends CharSequence>
        extends RecyclerView.Adapter<RadioGroupRecyclerAdapter.Holder> {

    /** Cached inflater. */
    @NonNull
    private final LayoutInflater inflater;
    @NonNull
    private final List<ID> itemIds;
    @NonNull
    private final List<CS> itemLabels;
    @Nullable
    private final SelectionListener<ID> selectionListener;
    /** The (pre-)selected item. */
    @Nullable
    private ID selection;

    /**
     * Constructor.
     *
     * @param context   Current context
     * @param ids       List of items; their ids
     * @param labels    List of items; their labels to display
     * @param selection (optional) the pre-selected item
     * @param listener  (optional) to send a selection to as the user changes them;
     *                  alternatively use {@link #getSelection()} when done.
     */
    public RadioGroupRecyclerAdapter(@NonNull final Context context,
                                     @NonNull final List<ID> ids,
                                     @NonNull final List<CS> labels,
                                     @Nullable final ID selection,
                                     @Nullable final SelectionListener<ID> listener) {

        inflater = LayoutInflater.from(context);
        itemIds = ids;
        itemLabels = labels;
        this.selection = selection;
        selectionListener = listener;
    }

    @Override
    @NonNull
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {
        final View view = inflater.inflate(R.layout.row_choice_single, parent, false);
        final Holder holder = new Holder(view);
        holder.btnOption.setOnClickListener(v -> onItemCheckChanged(holder));
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {
        final boolean checked = selection != null && selection == itemIds.get(position);
        holder.btnOption.setChecked(checked);
        holder.btnOption.setText(itemLabels.get(position));
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onItemCheckChanged(@NonNull final Holder holder) {
        final int position = holder.getAbsoluteAdapterPosition();

        selection = itemIds.get(position);
        // this triggers a bind call for all rows, which in turn (un)sets the checked row.
        notifyDataSetChanged();

        if (selectionListener != null) {
            // use a post allowing the UI to update the view first
            holder.btnOption.post(() -> selectionListener.onSelected(selection));
        }
    }

    /**
     * Get the selected item.
     *
     * @return item id, can be {@code null} if no item is selected.
     */
    @Nullable
    public ID getSelection() {
        return selection;
    }

    @Override
    public int getItemCount() {
        return itemIds.size();
    }

    @FunctionalInterface
    public interface SelectionListener<ID> {

        void onSelected(@Nullable ID id);
    }

    /**
     * Row ViewHolder for {@link RadioGroupRecyclerAdapter}.
     */
    static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RadioButton btnOption;

        Holder(@NonNull final View itemView) {
            super(itemView);
            btnOption = itemView.findViewById(R.id.btn_option);
        }
    }
}
