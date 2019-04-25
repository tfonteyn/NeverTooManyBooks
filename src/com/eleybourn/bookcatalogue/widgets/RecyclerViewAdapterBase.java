package com.eleybourn.bookcatalogue.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import com.eleybourn.bookcatalogue.widgets.ddsupport.ItemTouchHelperAdapter;
import com.eleybourn.bookcatalogue.widgets.ddsupport.OnStartDragListener;

public abstract class RecyclerViewAdapterBase<IT, VHT extends RecyclerViewViewHolderBase<IT>>
        extends RecyclerView.Adapter<VHT>
        implements ItemTouchHelperAdapter {

    @NonNull
    private final List<IT> mItems;
    /** Optional. */
    @Nullable
    private final OnStartDragListener mDragStartListener;
    @NonNull
    private final LayoutInflater mInflater;

    /**
     * Constructor.
     *
     * @param context caller context
     * @param items   the list
     */
    protected RecyclerViewAdapterBase(@NonNull final Context context,
                                      @NonNull final List<IT> items,
                                      @Nullable final OnStartDragListener dragStartListener) {

        mInflater = LayoutInflater.from(context);
        mDragStartListener = dragStartListener;
        mItems = items;
    }

    @NonNull
    public LayoutInflater getLayoutInflater() {
        return mInflater;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @CallSuper
    public void onBindViewHolder(@NonNull final VHT holder,
                                 final int position) {
        holder.setItem(mItems.get(position));

        // If we support drag drop re-ordering,
        if (mDragStartListener != null && holder.mDragHandleView != null) {
            // Start a drag whenever the handle view is touched
            holder.mDragHandleView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    mDragStartListener.onStartDrag(holder);
                }
                return false;
            });
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    /**
     * It’s very important to call notifyItemRemoved() so the Adapter is aware of the changes.
     *
     * @param position The position of the item removed.
     */
    @Override
    public void onItemRemoved(final int position) {
        mItems.remove(position);
        notifyItemRemoved(position);
    }

    /**
     * It’s very important to call notifyItemMoved() so the Adapter is aware of the changes.
     * It’s also important to note that we’re changing the position of the item every time the
     * view is shifted to a new index, and not at the end of a “drop” event.
     *
     * @param fromPosition The start position of the moved item.
     * @param toPosition   Then resolved position of the moved item.
     *
     * @return {@code true} if a move was done, {@code false} if not.
     */
    @Override
    public boolean onItemMove(final int fromPosition,
                              final int toPosition) {
        Collections.swap(mItems, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        return true;
    }
}
