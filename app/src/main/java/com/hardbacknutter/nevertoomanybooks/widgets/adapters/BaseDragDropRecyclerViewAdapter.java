/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.view.MotionEvent;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.booklist.ShowContextMenu;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.ItemTouchHelperAdapter;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.StartDragListener;

/**
 * Base class for implementing a RecyclerView with Drag&Drop support for re-arranging rows.
 * <p>
 * See {@link CheckableDragDropViewHolder} for the matching base class for the ViewHolder.
 *
 * @param <Item> list item type
 * @param <VHT>  ViewHolder type
 */
public abstract class BaseDragDropRecyclerViewAdapter<Item, VHT extends CheckableDragDropViewHolder>
        extends RecyclerView.Adapter<VHT>
        implements ItemTouchHelperAdapter {

    @NonNull
    private final List<Item> items;
    /** Optional. */
    @Nullable
    private final StartDragListener dragStartListener;
    /** Cached inflater. */
    @NonNull
    private final LayoutInflater inflater;

    @Nullable
    protected OnRowClickListener rowClickListener;
    @Nullable
    protected OnRowClickListener rowShowMenuListener;
    @Nullable
    protected ShowContextMenu contextMenuMode;

    /**
     * Constructor.
     *
     * @param context           Current context
     * @param items             List of items
     * @param dragStartListener Listener to handle the user moving rows up and down
     */
    protected BaseDragDropRecyclerViewAdapter(@NonNull final Context context,
                                              @NonNull final List<Item> items,
                                              @Nullable final StartDragListener dragStartListener) {
        inflater = LayoutInflater.from(context);
        this.dragStartListener = dragStartListener;
        this.items = items;
    }

    /**
     * Set the {@link OnRowClickListener} for a click on a row.
     *
     * @param listener to set
     */
    public void setOnRowClickListener(@Nullable final OnRowClickListener listener) {
        this.rowClickListener = listener;
    }

    /**
     * Set the {@link OnRowClickListener} for showing the context menu on a row.
     *
     * @param contextMenuMode how to show context menus
     * @param listener        to receive clicks
     */
    public void setOnRowShowMenuListener(@NonNull final ShowContextMenu contextMenuMode,
                                         @Nullable final OnRowClickListener listener) {
        this.rowShowMenuListener = listener;
        this.contextMenuMode = contextMenuMode;
    }

    @NonNull
    protected LayoutInflater getLayoutInflater() {
        return inflater;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @CallSuper
    public void onBindViewHolder(@NonNull final VHT holder,
                                 final int position) {
        // Enable drag-drop re-ordering if supported
        if (dragStartListener != null && holder.isDraggable()) {
            // Start a drag whenever the handle view is touched
            holder.setOnDragListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    dragStartListener.onStartDrag(holder);
                }
                return false;
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    public Item getItem(final int position) {
        return items.get(position);
    }

    /**
     * Note that we're changing the position of the item every time the
     * view is shifted to a new index, and not at the end of a “drop” event.
     *
     * @param fromPosition The start position of the moved item.
     * @param toPosition   The resolved position of the moved item.
     *
     * @return {@code true} if a move was done, {@code false} if not.
     */
    @Override
    @CallSuper
    public boolean onItemMove(final int fromPosition,
                              final int toPosition) {
        Collections.swap(items, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        return true;
    }
}
