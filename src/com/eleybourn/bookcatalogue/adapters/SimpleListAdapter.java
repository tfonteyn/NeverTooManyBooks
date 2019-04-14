/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.eleybourn.bookcatalogue.R;

/**
 * TODO: RecyclerView.
 * If you are considering using array adapter with a ListView, consider using
 * {@link RecyclerView} instead.
 * RecyclerView offers similar features with better performance and more flexibility than
 * ListView provides.
 * See the
 * <a href="https://developer.android.com/guide/topics/ui/layout/recyclerview.html">
 * Recycler View</a> guide.
 * <p>
 * <p>
 * <p>
 * <p>
 * {@link ArrayAdapter} to manage rows of an arbitrary type with row movement via clicking
 * on predefined sub-views, if present.
 * <p>
 * The layout must have the top id of:
 * <pre>
 *    SLA_ROW  {@link #onRowClick}, unless SLA_ROW_DETAILS is defined.
 * </pre>
 * <p>
 * The layout can optionally contain these "@+id/"  which will trigger the listed methods
 * <pre>
 *    SLA_ROW_DETAILS     {@link #onRowClick}
 *    SLA_ROW_DELETE      deletes that row.
 * </pre>
 * <p>
 * SLA_ROW is the complete row, SLA_ROW_DETAIL is a child of SLA_ROW.
 * So you should never have a SLA_ROW_DETAIL without an enclosing SLA_ROW element
 * <p>
 * ids.xml has these predefined:
 * <pre>
 *     <item name="SLA_ROW" type="id" />
 *     <item name="SLA_ROW_DETAILS" type="id" />
 *     <item name="SLA_ROW_DELETE" type="id"/>
 *     <item name="SLA_ROW_TAG" type="id"/>
 *  </pre>
 * <p>
 * SLA_ROW_TAG is used to store our t.
 *
 * @author Philip Warner
 */
public abstract class SimpleListAdapter<T>
        extends ArrayAdapter<T> {

    @LayoutRes
    private final int mRowLayoutId;

    protected SimpleListAdapter(@NonNull final Context context,
                                @LayoutRes final int rowLayoutId,
                                @NonNull final List<T> list) {
        super(context, rowLayoutId, list);
        mRowLayoutId = rowLayoutId;
    }

    @NonNull
    @Override
    public View getView(final int position,
                        @Nullable View convertView,
                        @NonNull final ViewGroup parent) {
        final T item = getItem(position);

        SimpleHolder holder;

        if (convertView != null) {
            // Recycling: just get the holder
            holder = (SimpleHolder) convertView.getTag(R.id.TLV_ROW_TAG);
        } else {
            // Not recycling, get a new View and make the holder for it.
            convertView = LayoutInflater.from(getContext()).inflate(mRowLayoutId, parent, false);
            holder = new SimpleHolder(convertView);
        }

        if (holder.rowDetailsView != null) {
            holder.rowDetailsView.setOnClickListener(v -> {
                if (item != null) {
                    onRowClick(item, position);
                }
            });
            //FIXME: this is forced onto the layout; caused (me) confusion as a click worked
            // without realising why it worked.
            holder.rowDetailsView.setFocusable(false);
        }

        // If the object is not null, do some processing
        if (item != null) {
            // Try to set the DELETE handler
            if (holder.deleteButton != null) {
                holder.deleteButton.setOnClickListener(v -> {
                    remove(item);
                    onListChanged();
                });
            }

            // Ask the subclass to set other fields.
            onGetView(convertView, item);
        }
        return convertView;
    }

    /**
     * Called by {@link #getView} to allow children to setup extra fields.
     */
    protected void onGetView(@NonNull final View convertView,
                             final T item) {
    }

    /**
     * Called when an otherwise inactive part of the row is clicked.
     *
     * @param item The object associated with this row
     */
    protected void onRowClick(@NonNull final T item,
                              final int position) {
    }

    /**
     * Called when the list had been modified in some way.
     * <p>
     * Child classes should override when needed.
     */
    protected void onListChanged() {
    }

    /**
     * Basic info for each row, stored with t id: R.id.SLA_ROW_TAG
     */
    static class SimpleHolder {

        /** optional row delete button. */
        @Nullable
        final View deleteButton;
        /** the details part of the row (or the row itself). */
        @Nullable
        View rowDetailsView;

        /**
         * Constructor.
         *
         * @param rowView to set the holder on.
         */
        SimpleHolder(@NonNull final View rowView) {
            // If we use a TouchListView, then don't enable the whole row, so buttons keep working
            rowDetailsView = rowView.findViewById(R.id.TLV_ROW_DETAILS);
            if (rowDetailsView == null) {
                // but if we did not define a details row subview, try row anyhow
                rowDetailsView = rowView.findViewById(R.id.TLV_ROW);
            }
            // optional
            deleteButton = rowView.findViewById(R.id.TLV_ROW_DELETE);

            rowView.setTag(R.id.TLV_ROW_TAG, this);
        }
    }
}
