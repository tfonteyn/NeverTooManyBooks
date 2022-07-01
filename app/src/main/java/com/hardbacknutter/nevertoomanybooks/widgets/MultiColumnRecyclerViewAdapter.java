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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public abstract class MultiColumnRecyclerViewAdapter<HOLDER extends RecyclerView.ViewHolder>
        extends RecyclerView.Adapter<HOLDER> {


    /** Cached inflater. */
    @NonNull
    protected final LayoutInflater inflater;
    protected final int columnCount;

    public MultiColumnRecyclerViewAdapter(@NonNull final Context context,
                                          final int columnCount) {
        this.inflater = LayoutInflater.from(context);
        this.columnCount = columnCount;
    }

    protected int transpose(final int position) {
        final int rowCount = getRowCount();

        final int column = position % columnCount;
        final int row = position / columnCount;

        return (column * rowCount) + row;
    }

    protected int revert(final int listIndex) {
        final int rowCount = getRowCount();

        final int column = listIndex % rowCount;
        final int row = listIndex / rowCount;

        return (column * columnCount) + row;
    }

    private int getRowCount() {
        final int itemCount = getItemCount();
        final int rowCount;
        if (itemCount % columnCount != 0) {
            rowCount = (itemCount / columnCount) + 1;
        } else {
            rowCount = itemCount / columnCount;
        }
        return rowCount;
    }
}
