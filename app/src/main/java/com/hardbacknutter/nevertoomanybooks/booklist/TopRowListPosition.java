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

package com.hardbacknutter.nevertoomanybooks.booklist;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

public class TopRowListPosition {
    private final int adapterPosition;
    private final int viewOffset;

    /**
     * Constructor.
     *
     * @param adapterPosition {@link LinearLayoutManager#findFirstVisibleItemPosition()}
     * @param viewOffset      {@link RecyclerView#getChildAt(int)} for value {@code 0}
     */
    public TopRowListPosition(final int adapterPosition,
                              final int viewOffset) {
        this.adapterPosition = adapterPosition;
        this.viewOffset = viewOffset;
    }

    /**
     * Get the booklist adapter position to use for re-displaying a booklist.
     * Normally in the range 0..x, but can be {@link RecyclerView#NO_POSITION}
     * if the list is empty.
     *
     * @return index value for {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}
     */
    @IntRange(from = RecyclerView.NO_POSITION)
    public int getAdapterPosition() {
        return adapterPosition;
    }

    /**
     * Get the view offset to use for re-displaying a booklist.
     * Due to CoordinatorLayout behaviour, the returned value
     * <strong>can be negative, this is NORMAL</strong>.
     *
     * @return offset value for {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}
     */
    public int getViewOffset() {
        return viewOffset;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TopRowListPosition that = (TopRowListPosition) o;
        return adapterPosition == that.adapterPosition
               && viewOffset == that.viewOffset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(adapterPosition, viewOffset);
    }

    @Override
    @NonNull
    public String toString() {
        return "TopRowListPosition{"
               + "adapterPosition=" + adapterPosition
               + ", viewOffset=" + viewOffset
               + '}';
    }
}
