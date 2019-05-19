package com.eleybourn.bookcatalogue.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.entities.Entity;

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
    private final Context mContext;
    @NonNull
    private final List<T> mItems;
    @Nullable
    private final View.OnClickListener mOnSelectionListener;
    /** The (pre-)selected item. */
    private T mSelectedItem;

    /**
     * @param context Current context
     * @param items the list
     */
    public RadioGroupRecyclerAdapter(@NonNull final Context context,
                                     @NonNull final List<T> items,
                                     @Nullable final T selectedItem,
                                     @Nullable final View.OnClickListener listener) {

        mContext = context;
        mInflater = LayoutInflater.from(context);
        mItems = items;
        mSelectedItem = selectedItem;
        mOnSelectionListener = listener;
    }

    @Override
    @NonNull
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {
        View view = mInflater.inflate(R.layout.row_radiobutton,
                                      parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {

        T item = mItems.get(position);
        holder.buttonView.setTag(R.id.TAG_ITEM, item);

        holder.buttonView.setText(item.getLabel(mContext.getResources()));
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
            // use a post allowing the UI to update the radio buttons first (yes, purely for visuals)
            v.post(() -> mOnSelectionListener.onClick(v));
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

    static class Holder
            extends RecyclerView.ViewHolder {

        final CompoundButton buttonView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            buttonView = itemView.findViewById(R.id.button);
        }
    }
}
