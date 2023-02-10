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

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Interface to listen for a move or dismissal event from a {@link ItemTouchHelper.Callback}.
 *
 * @author Paul Burke (ipaulpro)
 */
public interface ItemTouchHelperAdapter {

    /**
     * Called when an item has been dragged far enough to trigger a move. This is called every time
     * an item is shifted, and <strong>not</strong> at the end of a "drop" event.<br>
     * <br>
     * Implementations should call {@link RecyclerView.Adapter#notifyItemMoved(int, int)} after
     * adjusting the underlying data to reflect this move.
     *
     * @param fromPosition The start position of the moved item.
     * @param toPosition   The resolved position of the moved item.
     *
     * @return {@code true} if the item was moved to the new adapter position.
     */
    @SuppressWarnings("UnusedReturnValue")
    default boolean onItemMove(final int fromPosition,
                               final int toPosition) {
        return false;
    }


    /**
     * Called when an item has been dismissed by a swipe.<br>
     * <br>
     * Implementations should call {@link RecyclerView.Adapter#notifyItemRemoved(int)} after
     * adjusting the underlying data to reflect this removal.
     *
     * @param position The position of the item dismissed.
     */
    default void onItemSwiped(@NonNull final View v,
                              final int position) {

    }
}

