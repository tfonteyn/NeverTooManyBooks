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
package com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;

public class BindableItemCursorAdapter<
        BI extends BindableItem,
        BICursor extends BindableItemCursor<BI>>
        extends CursorAdapter {

    @NonNull
    private final DAO mDb;

    /** hash of class names and values used to dynamically allocate layout numbers. */
    private final Map<String, Integer> mItemTypeLookups = new HashMap<>();
    /** The position passed to the last call of {@link #getItemViewType}. */
    private int mLastItemViewTypePosition = -1;
    /** The item type returned by the last call of {@link #getItemViewType}. */
    private int mLastItemViewTypeType = -1;
    /** The Event used in the last call of {@link #getItemViewType}. */
    private BI mLastItemViewTypeItem;
    private int mItemTypeCount;

    private final LayoutInflater mLayoutInflater;

    /**
     * Constructor; calls superclass and allocates an Inflater for later use.
     *
     * @param context Current context
     * @param cursor  Cursor to use as source
     * @param db      Database Access
     */
    BindableItemCursorAdapter(@NonNull final Context context,
                              @NonNull final Cursor cursor,
                              @NonNull final DAO db) {
        super(context, cursor);
        mDb = db;
        mLayoutInflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(final int position,
                        @Nullable View convertView,
                        @NonNull final ViewGroup parent) {

        BindableItemCursor<BI> cursor = getItem(position);

        BI item;
        // Optimization to avoid unnecessary de-serializations.
        if (mLastItemViewTypePosition == position) {
            item = mLastItemViewTypeItem;
        } else {
            item = cursor.getBindableItem();
        }

        BindableItemViewHolder holder;
        if (convertView == null) {
            holder = item.onCreateViewHolder(mLayoutInflater, parent);
        } else {
            holder = (BindableItemViewHolder) convertView.getTag(R.id.TAG_VIEW_HOLDER);
        }

        // Bind it, and we are done!
        //noinspection unchecked
        item.onBindViewHolder(holder, cursor, mDb);

        return holder.itemView;
    }

    /**
     * NOT USED. Should never be called.
     */
    @NonNull
    @Override
    public View newView(@NonNull final Context context,
                        @NonNull final Cursor cursor,
                        @NonNull final ViewGroup parent) {
        throw new UnsupportedOperationException();
    }

    /**
     * NOT USED. Should never be called.
     */
    @Override
    public void bindView(@NonNull final View view,
                         @NonNull final Context context,
                         @NonNull final Cursor cursor) {
        throw new UnsupportedOperationException();
    }

    @Override
    @CallSuper
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        // Clear cached stuff
        mLastItemViewTypePosition = -1;
        mLastItemViewTypeType = -1;
        mLastItemViewTypeItem = null;
    }

    @Override
    public BICursor getItem(int position) {
        //noinspection unchecked
        return (BICursor) super.getItem(position);
    }

    /**
     * Uses the actual class name of the Event object to dynamically allocate layout numbers,
     * and returns the layout number corresponding to the Event at the specified position.
     * <p>
     * The values are cached in member variables because the usual process is to call
     * onCreateViewHolder() almost directly after calling getItemViewType().
     *
     * @param position Cursor position to check.
     */
    @Override
    public int getItemViewType(final int position) {
        // If it's the same as the last call, just return.
        if (position == mLastItemViewTypePosition) {
            return mLastItemViewTypeType;
        }

        BICursor cursor = getItem(position);
        BI item = cursor.getBindableItem();

        // Use the class name to generate a layout number
        String s = item.getClass().toString();
        int type;
        if (mItemTypeLookups.containsKey(s)) {
            //noinspection ConstantConditions
            type = mItemTypeLookups.get(s);
        } else {
            mItemTypeCount++;
            mItemTypeLookups.put(s, mItemTypeCount);
            type = mItemTypeCount;
        }

        //
        // Cache the recent results; this is a optimization kludge based on the fact
        // that current code always call this method before onCreateViewHolder(...)
        // so we can avoid one deserialization by caching here.
        // If this assumption fails, the code still works. It just runs slower.
        mLastItemViewTypePosition = position;
        mLastItemViewTypeType = type;
        mLastItemViewTypeItem = item;

        // And return
        return type;
    }

    /**
     * Return the number of different item types that the list will display.
     * An approximation is fine, but overestimating will be more efficient than underestimating.
     * This is analogous to the CursorAdapter.getViewTypeCount() method but needs to be set
     * to the minimum of the total number of item subclasses that may be returned.
     * <p>
     * The default return a paranoid overestimate of the number of types we use.
     *
     * @return number of view types that items will use.
     */
    @Override
    public int getViewTypeCount() {
        return 50;
    }

}
