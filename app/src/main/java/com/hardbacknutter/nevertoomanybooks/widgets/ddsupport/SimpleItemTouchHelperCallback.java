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

/*
 * Copyright (C) 2015 Paul Burke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hardbacknutter.nevertoomanybooks.widgets.ddsupport;

import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * An implementation of {@link ItemTouchHelper.Callback} that enables basic drag & drop and
 * swipe-to-dismiss. Drag events are automatically started by an item long-press.<br><br>
 * Expects the {@code RecyclerView.Adapter<} to listen for {@link ItemTouchHelperAdapter}
 * callbacks and the {@code RecyclerView.ViewHolder} to implement {@link ItemTouchHelperViewHolder}.
 *
 * @author Paul Burke (ipaulpro)
 */
public class SimpleItemTouchHelperCallback
        extends ItemTouchHelper.Callback {

    private static final float ALPHA_FULL = 1.0f;

    @NonNull
    private final ItemTouchHelperAdapter mAdapter;

    private boolean mIsLongPressDragEnabled;
    private boolean mIsItemViewSwipeEnabled;

    public SimpleItemTouchHelperCallback(@NonNull final ItemTouchHelperAdapter adapter) {
        mAdapter = adapter;
    }

    @Override
    public int getMovementFlags(@NonNull final RecyclerView recyclerView,
                                @NonNull final RecyclerView.ViewHolder viewHolder) {

        // Set movement flags based on the layout manager
        if (recyclerView.getLayoutManager() instanceof GridLayoutManager) {
            final int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN
                                  | ItemTouchHelper.START | ItemTouchHelper.END;
            final int swipeFlags = 0;
            return makeMovementFlags(dragFlags, swipeFlags);

        } else {
            final int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
            final int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
            return makeMovementFlags(dragFlags, swipeFlags);
        }
    }

    @Override
    public boolean onMove(@NonNull final RecyclerView recyclerView,
                          @NonNull final RecyclerView.ViewHolder viewHolder,
                          @NonNull final RecyclerView.ViewHolder target) {

        if (viewHolder.getItemViewType() != target.getItemViewType()) {
            return false;
        }

        // Notify the adapter of the move
        mAdapter.onItemMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
        return true;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return mIsLongPressDragEnabled;
    }

    @SuppressWarnings("unused")
    public void setLongPressDragEnabled(final boolean longPressDragEnabled) {
        mIsLongPressDragEnabled = longPressDragEnabled;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return mIsItemViewSwipeEnabled;
    }

    @SuppressWarnings("unused")
    public void setItemViewSwipeEnabled(final boolean itemViewSwipeEnabled) {
        mIsItemViewSwipeEnabled = itemViewSwipeEnabled;
    }

    @Override
    public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder,
                         final int direction) {
        // Notify the adapter of the dismissal
        mAdapter.onItemSwiped(viewHolder.getAdapterPosition());
    }

    @Override
    public void onSelectedChanged(@Nullable final RecyclerView.ViewHolder viewHolder,
                                  final int actionState) {
        // We only want the active item to change
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            if (viewHolder instanceof ItemTouchHelperViewHolder) {
                // Let the view holder know that this item is being moved or dragged
                ((ItemTouchHelperViewHolder) viewHolder).onItemDragStarted();
            }
        }

        super.onSelectedChanged(viewHolder, actionState);
    }

    @Override
    public void clearView(@NonNull final RecyclerView recyclerView,
                          @NonNull final RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);

        viewHolder.itemView.setAlpha(ALPHA_FULL);

        if (viewHolder instanceof ItemTouchHelperViewHolder) {
            // Tell the view holder it's time to restore the idle state
            ((ItemTouchHelperViewHolder) viewHolder).onItemDragFinished();
        }
    }

    @Override
    public void onChildDraw(@NonNull final Canvas c,
                            @NonNull final RecyclerView recyclerView,
                            @NonNull final RecyclerView.ViewHolder viewHolder,
                            final float dX,
                            final float dY,
                            final int actionState,
                            final boolean isCurrentlyActive) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            // Fade out the view as it is swiped out of the parent's bounds
            final float alpha = ALPHA_FULL - Math.abs(dX) / (float) viewHolder.itemView.getWidth();
            viewHolder.itemView.setAlpha(alpha);
            viewHolder.itemView.setTranslationX(dX);
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }
}

