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
import com.eleybourn.bookcatalogue.widgets.ddsupport.StartDragListener;

/**
 * Base class for implementing a RecyclerView with Drag&Drop support for re-arranging rows.
 * Supports an optional 'delete' button as well.
 * <p>
 * See {@link RecyclerViewViewHolderBase} for the matching base class for ViewHolder.
 *
 * @param <Item> list item type
 * @param <VHT>  ViewHolder type
 */
public abstract class RecyclerViewAdapterBase<Item, VHT extends RecyclerViewViewHolderBase>
        extends RecyclerView.Adapter<VHT>
        implements ItemTouchHelperAdapter {

    @NonNull
    private final List<Item> mItems;
    /** Optional. */
    @Nullable
    private final StartDragListener mDragStartListener;
    @NonNull
    private final LayoutInflater mInflater;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param items   the list
     */
    protected RecyclerViewAdapterBase(@NonNull final Context context,
                                      @NonNull final List<Item> items,
                                      @Nullable final StartDragListener dragStartListener) {

        mInflater = LayoutInflater.from(context);
        mDragStartListener = dragStartListener;
        mItems = items;
    }

    @NonNull
    public LayoutInflater getLayoutInflater() {
        return mInflater;
    }

    @NonNull
    public Context getContext() {
        return mInflater.getContext();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @CallSuper
    public void onBindViewHolder(@NonNull final VHT holder,
                                 final int position) {

        if (holder.mDeleteButton != null) {
            holder.mDeleteButton.setOnClickListener(v -> {
                mItems.remove(getItem(position));
                notifyItemRemoved(position);
            });
        }

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

    public Item getItem(final int position) {
        return mItems.get(position);
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
    public void onItemSwiped(final int position) {
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
