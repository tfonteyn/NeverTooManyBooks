/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.entities.Entity;

/**
 * Add a list of RadioButtons to a RecyclerView.
 * <p>
 * Handles that only one RadioButton is selected at any time.
 *
 * @param <T> type of the {@link Entity} represented by each RadioButton.
 */
public class RadioGroupRecyclerAdapter<T extends Entity>
        extends RecyclerView.Adapter<RadioGroupRecyclerAdapter.Holder> {

    @NonNull
    private final LayoutInflater mInflater;
    @NonNull
    private final List<T> mItems;
    @Nullable
    private final SelectionListener<T> mOnSelectionListener;
    /** The (pre-)selected item. */
    private T mSelectedItem;

    /**
     * @param items the list
     */
    public RadioGroupRecyclerAdapter(@NonNull final LayoutInflater layoutInflater,
                                     @NonNull final List<T> items,
                                     @Nullable final T selectedItem,
                                     @Nullable final SelectionListener<T> listener) {

        mInflater = layoutInflater;
        mItems = items;
        mSelectedItem = selectedItem;
        mOnSelectionListener = listener;
    }

    @Override
    @NonNull
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {
        View view = mInflater.inflate(R.layout.row_radiobutton, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {

        T item = mItems.get(position);
        holder.buttonView.setTag(R.id.TAG_ITEM, item);

        holder.buttonView.setText(item.getLabel(mInflater.getContext()));
        // only 'check' the pre-selected item.
        holder.buttonView.setChecked(item.getId() == mSelectedItem.getId());
        holder.buttonView.setOnClickListener(this::itemCheckChanged);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    /**
     * On selecting any view set the current position to mSelectedPosition and notify adapter.
     */
    private void itemCheckChanged(@NonNull final View v) {
        //noinspection unchecked
        mSelectedItem = (T) v.getTag(R.id.TAG_ITEM);
        // this triggers a bind calls for the rows, which in turn set the checked status.
        notifyDataSetChanged();
        if (mOnSelectionListener != null) {
            // use a post allowing the UI to update the radio buttons first (purely for visuals)
            v.post(() -> mOnSelectionListener.onSelected(mSelectedItem));
        }
    }

    /**
     * @return the selected item.
     */
    @SuppressWarnings("unused")
    @Nullable
    public T getSelectedItem() {
        if (mSelectedItem != null) {
            return mSelectedItem;
        }
        return null;
    }

    /** Delete the selected position from the List. */
    @SuppressWarnings("unused")
    public void deleteSelectedPosition() {
        if (mSelectedItem != null) {
            mItems.remove(mSelectedItem);
            mSelectedItem = null;
            notifyDataSetChanged();
        }
    }

    public interface SelectionListener<T> {

        void onSelected(T o);
    }

    static class Holder
            extends RecyclerView.ViewHolder {

        final CompoundButton buttonView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            buttonView = itemView.findViewById(R.id.button);
        }
    }
}
