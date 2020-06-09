/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * Add a list of RadioButtons to a RecyclerView.
 * <p>
 * Row layout: {@code R.layout.row_choice_single}
 *
 * @param <T> type of the {@link Entity} represented by each RadioButton.
 */
public class RadioGroupRecyclerAdapter<T extends Entity>
        extends RecyclerView.Adapter<RadioGroupRecyclerAdapter.Holder> {

    @NonNull
    private final Context mContext;

    @NonNull
    private final LayoutInflater mInflater;
    @NonNull
    private final List<T> mItems;
    @Nullable
    private final SelectionListener<T> mOnSelectionListener;
    /** The (pre-)selected item. */
    @Nullable
    private T mSelection;

    /**
     * Constructor.
     *
     * @param context   Current context
     * @param items     List of items
     * @param selection (optional) the pre-selected item
     * @param listener  (optional) to send a selection to
     */
    public RadioGroupRecyclerAdapter(@NonNull final Context context,
                                     @NonNull final List<T> items,
                                     @Nullable final T selection,
                                     @Nullable final SelectionListener<T> listener) {

        mContext = context;
        mInflater = LayoutInflater.from(mContext);
        mItems = items;
        mSelection = selection;
        mOnSelectionListener = listener;
    }

    @Override
    @NonNull
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {
        final View view = mInflater.inflate(R.layout.row_choice_single, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {

        final T item = mItems.get(position);
        // store the item on a tag for easy retrieval
        holder.buttonView.setTag(R.id.TAG_ITEM, item);

        holder.buttonView.setText(item.getLabel(mContext));
        final boolean checked = mSelection != null && item.getId() == mSelection.getId();
        holder.buttonView.setChecked(checked);
        holder.buttonView.setOnClickListener(this::itemCheckChanged);
    }


    private void itemCheckChanged(@NonNull final View v) {
        //noinspection unchecked
        mSelection = (T) v.getTag(R.id.TAG_ITEM);
        // this triggers a bind calls for the rows, which in turn set the checked status.
        notifyDataSetChanged();
        if (mOnSelectionListener != null) {
            // use a post allowing the UI to update the view first
            v.post(() -> mOnSelectionListener.onSelected(mSelection));
        }
    }

    /**
     * Get the selected item.
     *
     * @return item, can be {@code null} if no item is selected.
     */
    @Nullable
    public T getSelection() {
        return mSelection;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public interface SelectionListener<T> {

        void onSelected(T o);
    }

    /**
     * Holder pattern for {@link RadioGroupRecyclerAdapter}.
     */
    static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final CompoundButton buttonView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            buttonView = itemView.findViewById(R.id.btn_option);
        }
    }
}
