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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Used by adapters which have different {@link RecyclerView.ViewHolder} types,
 * and delegate the actual binding to individual {@link RecyclerView.ViewHolder}s.
 *
 * @param <T> type of data to bind
 */
@FunctionalInterface
public interface BindableViewHolder<T> {

    /**
     * Bind the given data to the views in the holder.
     *
     * @param data to bind
     */
    void onBind(@NonNull T data);
}
