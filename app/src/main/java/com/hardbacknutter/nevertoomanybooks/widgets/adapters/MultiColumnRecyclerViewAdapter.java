/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.content.res.Resources;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertoomanybooks.R;

public abstract class MultiColumnRecyclerViewAdapter<HOLDER extends RecyclerView.ViewHolder>
        extends RecyclerView.Adapter<HOLDER> {

    private static final String ERROR_NO_LIST_INDEX_FOR_POSITION =
            "No listIndex for gridPosition=";

    private final int columnCount;

    /**
     * Constructor.
     *
     * @param columnCount the number of columns to be used
     */
    protected MultiColumnRecyclerViewAdapter(final int columnCount) {
        this.columnCount = columnCount;
    }

    protected void requireValidOrThrow(final int listIndex,
                                       final int gridPosition) {
        if (listIndex == RecyclerView.NO_POSITION) {
            // Should never get here
            throw new IllegalStateException(ERROR_NO_LIST_INDEX_FOR_POSITION + gridPosition);
        }
    }

    /**
     * Optionally adjust the margins of the columns if there is more than 1.
     * i.e. on larger displays.
     * <p>
     * Call from {@link #onCreateViewHolder(ViewGroup, int)}
     *
     * @param columnView to adjust
     */
    protected void adjustColumns(@NonNull final ViewGroup columnView) {
        if (columnCount > 1) {
            final GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams)
                    columnView.getLayoutParams();
            final Resources res = columnView.getContext().getResources();
            lp.setMarginStart(res.getDimensionPixelSize(R.dimen.field_margin_start));
            lp.setMarginEnd(res.getDimensionPixelSize(R.dimen.field_margin_end));
            columnView.setLayoutParams(lp);
        }
    }

    /**
     * Convert the given grid-position to the list-index.
     *
     * @param gridPosition to convert
     *
     * @return the index of the item in the list
     */
    protected int gridPositionToListIndex(final int gridPosition) {
        final int listSize = getItemCount();
        // Paranoia
        if (listSize <= 0 || gridPosition < 0) {
            return RecyclerView.NO_POSITION;
        }

        final int maxRowCount = getRowCount(listSize);
        final int remainder = listSize % columnCount;

        final int column = gridPosition % columnCount;
        final int row = gridPosition / columnCount;

        // Determine exact height of *this* specific column
        final int thisColHeight = (remainder == 0 || column < remainder)
                                  ? maxRowCount
                                  : maxRowCount - 1;

        // Paranoia: Check if it is outside the populated rows of this column
        // This should never be the case
        if (row >= thisColHeight) {
            return RecyclerView.NO_POSITION;
        }

        final int listIndex;
        if (remainder == 0 || column < remainder) {
            listIndex = row + (column * maxRowCount);
        } else {
            final int fullColsItems = remainder * maxRowCount;
            final int shortColsItems = (column - remainder) * (maxRowCount - 1);
            listIndex = row + fullColsItems + shortColsItems;
        }

        return (listIndex < listSize) ? listIndex : RecyclerView.NO_POSITION;
    }

    /**
     * Convert the given list-index to the grid-position.
     *
     * @param listIndex to convert
     *
     * @return grid-position
     */
    public int listIndexToGridPosition(final int listIndex) {
        final int listSize = getItemCount();
        // Paranoia
        if (listIndex < 0 || listIndex >= listSize || columnCount <= 0) {
            return RecyclerView.NO_POSITION;
        }

        final int maxRowCount = getRowCount(listSize);
        final int remainder = listSize % columnCount;

        // Total items stored in the taller columns on the left
        final int fullColsCapacity = (remainder == 0)
                                     ? listSize
                                     : remainder * maxRowCount;

        final int column;
        final int row;

        if (remainder == 0 || listIndex < fullColsCapacity) {
            // 'Full' column
            column = listIndex / maxRowCount;
            row = listIndex % maxRowCount;
        } else {
            // Shorter column
            final int shortRowCount = maxRowCount - 1;
            final int offsetIndex = listIndex - fullColsCapacity;

            column = remainder + (offsetIndex / shortRowCount);
            row = offsetIndex % shortRowCount;
        }

        return (row * columnCount) + column;
    }

    @SuppressWarnings("WeakerAccess")
    protected int getRowCount(final int listSize) {
        // Paranoia checks
        if (listSize <= 0 || columnCount <= 0) {
            return 0;
        }
        // Math.ceil((double) listSize / columnCount)
        return (listSize + columnCount - 1) / columnCount;
    }
}
