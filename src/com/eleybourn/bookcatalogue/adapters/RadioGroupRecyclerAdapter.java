package com.eleybourn.bookcatalogue.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;

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
        extends RecyclerView.Adapter<RadioGroupRecyclerAdapter.RecyclerViewHolder> {

    @NonNull
    private final LayoutInflater mInflater;
    @NonNull
    private final Context mContext;
    @NonNull
    private final List<T> mList;
    @Nullable
    private final View.OnClickListener mOnSelectionListener;
    private T mSelectedItem;

    /**
     * @param context caller context
     * @param objects the list
     */
    public RadioGroupRecyclerAdapter(@NonNull final Context context,
                                     @NonNull final List<T> objects,
                                     @Nullable final T selectedItem,
                                     @Nullable final View.OnClickListener listener) {

        mContext = context;
        mInflater = LayoutInflater.from(context);
        mList = objects;
        mSelectedItem = selectedItem;
        mOnSelectionListener = listener;
    }

    @Override
    @NonNull
    public RecyclerViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                 final int viewType) {
        RadioButton button = (RadioButton) mInflater.inflate(R.layout.row_radiobutton,
                                                             parent, false);
        return new RecyclerViewHolder<T>(button);
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerViewHolder holder,
                                 final int position) {

        holder.item = mList.get(position);
        holder.buttonView.setTag(holder.item);

        holder.buttonView.setText(holder.item.getLabel(mContext));
        holder.buttonView.setChecked(holder.item.getId() == mSelectedItem.getId());
        holder.buttonView.setOnClickListener(this::itemCheckChanged);
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    /**
     * On selecting any view set the current position to mSelectedPosition and notify adapter.
     */
    private void itemCheckChanged(@NonNull final View v) {
        //noinspection unchecked
        mSelectedItem = (T) v.getTag();
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
            mList.remove(mSelectedItem);
            mSelectedItem = null;
            notifyDataSetChanged();
        }
    }

    static class RecyclerViewHolder<T extends Entity>
            extends RecyclerView.ViewHolder {

        /** For non-casting convenience. */
        final RadioButton buttonView;
        T item;

        RecyclerViewHolder(@NonNull final RadioButton buttonView) {
            super(buttonView);
            this.buttonView = buttonView;
        }
    }
}
