/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.goodreads.taskqueue;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertomanybooks.database.DAO;

public class BindableItemCursorAdapter
        extends CursorAdapter {

    @NonNull
    private final Context mContext;

    @NonNull
    private final LayoutInflater mInflater;

    @NonNull
    private final BindableItemBinder mBinder;

    /** hash of class names and values used to dynamically allocate layout numbers. */
    private final Map<String, Integer> mItemTypeLookups = new HashMap<>();
    /** The position passed to the last call of {@link #getItemViewType}. */
    private int mLastItemViewTypePos = -1;
    /** The item type returned by the last call of {@link #getItemViewType}. */
    private int mLastItemViewType = -1;
    /** The Event used in the last call of {@link #getItemViewType}. */
    private BindableItem mLastItemViewTypeEvent;
    private int mItemTypeCount;

    /**
     * Constructor; calls superclass and allocates an Inflater for later use.
     *
     * @param context Current context
     * @param cursor  Cursor to use as source
     */
    BindableItemCursorAdapter(@NonNull final BindableItemBinder binder,
                              @NonNull final Context context,
                              @NonNull final Cursor cursor) {
        super(context, cursor);
        mInflater = LayoutInflater.from(context);
        mContext = context;
        mBinder = binder;
    }

    @NonNull
    @Override
    public View getView(final int position,
                        @Nullable View convertView,
                        @NonNull final ViewGroup parent) {
        BindableItemCursor cursor = (BindableItemCursor) getCursor();
        cursor.moveToPosition(position);

        BindableItem item;
        // Optimization to avoid unnecessary de-serializations.
        if (mLastItemViewTypePos == position) {
            item = mLastItemViewTypeEvent;
        } else {
            item = cursor.getBindableItem();
        }

        if (convertView == null) {
            convertView = item.getView(mInflater, cursor, parent);
        }

        // Bind it, and we are done!
        mBinder.bindView(mContext, cursor, convertView, item);

        return convertView;
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
        mLastItemViewTypePos = -1;
        mLastItemViewType = -1;
        mLastItemViewTypeEvent = null;
    }

    /**
     * Uses the actual class name of the Event object to dynamically allocate layout numbers,
     * and returns the layout number corresponding to the Event at the specified position.
     * <p>
     * The values are cached in member variables because the usual process is to call
     * getView() almost directly after calling getItemViewType().
     *
     * @param position Cursor position to check.
     */
    @Override
    public int getItemViewType(final int position) {
        // If it's the same as the last call, just return.
        if (position == mLastItemViewTypePos) {
            return mLastItemViewType;
        }

        BindableItemCursor cursor = (BindableItemCursor) getCursor();
        cursor.moveToPosition(position);
        BindableItem item = cursor.getBindableItem();

        // Use the class name to generate a layout number
        String s = item.getClass().toString();
        int resType;
        if (mItemTypeLookups.containsKey(s)) {
            //noinspection ConstantConditions
            resType = mItemTypeLookups.get(s);
        } else {
            mItemTypeCount++;
            mItemTypeLookups.put(s, mItemTypeCount);
            resType = mItemTypeCount;
        }

        //
        // Cache the recent results; this is a optimization kludge based on the fact
        // that current code always call this method before getView(...)
        // so we can avoid one deserialization by caching here.
        // If this assumption fails, the code still works. It just runs slower.
        mLastItemViewTypePos = position;
        mLastItemViewType = resType;
        mLastItemViewTypeEvent = item;

        // And return
        return resType;
    }

    /**
     * Get the (approximate, overestimate) of the number of Event subclasses
     * in use in this list.
     */
    @Override
    public int getViewTypeCount() {
        // Add 1 to allow for the Legacy object.
        return mBinder.getBindableItemTypeCount() + 1;
    }

    public interface BindableItemBinder {

        /**
         * Return the number of different Event types and event that the list will display.
         * An approximation is fine, but overestimating will be more efficient than underestimating.
         * This is analogous to the CursorAdapter.getViewTypeCount() method but needs to be set
         * to the minimum of the total number of Event subclasses that may be returned.
         *
         * @return number of view types that events will return.
         */
        int getBindableItemTypeCount();

        /**
         * Called to bind a specific event to a View in the list. It is passed to the subclass
         * so that any application-specific context can be added, or it can just be passed off
         * to the Event object itself.
         *
         * @param context     Current context
         * @param cursor      Cursor, positions at the relevant row
         * @param convertView View to populate
         * @param item        item to bin
         */
        void bindView(@NonNull Context context,
                      @NonNull BindableItemCursor cursor,
                      @NonNull View convertView,
                      @NonNull BindableItem item);
    }

    public interface BindableItem {

        /**
         * Get a new View object suitable for displaying this type of event.
         * <p>
         * <b>Note:</b> A single event subclass should NOT RETURN MORE THAN ONE TYPE OF VIEW.
         * If it needs to do this, create a new Event subclass or use a more complex view.
         *
         * @param inflater that can be used to create the view.
         * @param cursor   EventsCursor for this event, positioned at its row.
         * @param parent   ViewGroup that will contain the new View.
         *
         * @return a new view
         */
        @NonNull
        View getView(@NonNull LayoutInflater inflater,
                     @NonNull BindableItemCursor cursor,
                     @NonNull ViewGroup parent);

        /**
         * Bind this Event to the passed view. The view will be one created by a call
         * to getView(...).
         *
         * @param view    View to populate
         * @param context Context using view
         * @param cursor  EventsCursor for this event, positioned at its row.
         * @param db      Database Access
         */
        void bindView(@NonNull View view,
                      @NonNull Context context,
                      @NonNull BindableItemCursor cursor,
                      @NonNull DAO db);

        /**
         * Called when an item in a list has been clicked, this method should populate the passed
         * 'items' parameter with one {@link ContextDialogItem} per operation that can be
         * performed on this object.
         *
         * @param context  Context resulting in Click() event
         * @param parent   that contained the item
         * @param view     View that was clicked
         * @param position position in cursor of item
         * @param id       row id of item
         * @param items    items collection to fill
         * @param db       Database Access
         */
        void addContextMenuItems(@NonNull Context context,
                                 @NonNull AdapterView<?> parent,
                                 @NonNull View view,
                                 int position,
                                 long id,
                                 @NonNull List<ContextDialogItem> items,
                                 @NonNull DAO db);
    }
}
