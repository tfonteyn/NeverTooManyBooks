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

    private static final String ERROR_NO_LIST_INDEX_FOR_POSITION = "No ListIndex for position=";

    private final int columnCount;

    /**
     * Constructor.
     *
     * @param columnCount the number of columns to be used
     */
    protected MultiColumnRecyclerViewAdapter(final int columnCount) {
        this.columnCount = columnCount;
    }

    protected void requireValidOrThrow(final int position,
                                       final int gridPosition) {
        if (position == RecyclerView.NO_POSITION) {
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
     * Convert the given grid-position to the list-index (position).
     *
     * @param gridPosition to convert
     *
     * @return the index (position) into the item-list
     */
    protected int gridToListPosition(final int gridPosition) {
        final int listSize = getListSize();
        final int rowCount = getRowCount(listSize);

        final int column = gridPosition % columnCount;
        final int row = gridPosition / columnCount;

        final int listIndex = row + (column * rowCount);

        if (listIndex < listSize) {
            return listIndex;
        }
        return RecyclerView.NO_POSITION;
    }

    /**
     * Convert the given list-index (position) to the grid-position.
     *
     * @param listIndex to convert
     *
     * @return grid-position
     */
    protected int listToGridPosition(final int listIndex) {
        final int listSize = getListSize();
        final int rowCount = getRowCount(listSize);

        final int column = listIndex / rowCount;
        final int row = listIndex % rowCount;

        return (row * columnCount) + column;
    }

    @SuppressWarnings("WeakerAccess")
    protected int getRowCount(final int listSize) {
        return (int) Math.ceil((double) listSize / columnCount);
    }

    /**
     * Acts like the original getItemCount() method.
     *
     * @return the actual item count in the list of items
     */
    protected abstract int getListSize();

    /**
     * Return the <strong>CELL COUNT</strong> for the grid.
     *
     * @return cell count
     */
    @Override
    public int getItemCount() {
        final int listSize = getListSize();
        final int rowCount = getRowCount(listSize);
        return rowCount * columnCount;
    }
}
