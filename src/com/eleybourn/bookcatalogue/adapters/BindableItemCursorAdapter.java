/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * TaskQueue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TaskQueue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.adapters;

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

import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.cursors.BindableItemCursor;
import com.eleybourn.bookcatalogue.dialogs.ContextDialogItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BindableItemCursorAdapter
        extends CursorAdapter {

    /** A local Inflater for convenience. */
    @NonNull
    private final LayoutInflater mInflater;
    @NonNull
    private final Context mContext;
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
     * @param context Context of call
     * @param cursor  Cursor to use as source
     */
    public BindableItemCursorAdapter(@NonNull final BindableItemBinder binder,
                                     @NonNull final Context context,
                                     @NonNull final Cursor cursor) {
        super(context, cursor);
        //noinspection ConstantConditions
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContext = context;
        mBinder = binder;
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

        // Get the Event object
        BindableItemCursor cursor = (BindableItemCursor) getCursor();
        cursor.moveToPosition(position);
        BindableItem bindable = cursor.getBindableItem();

        // Use the class name to generate a layout number
        String s = bindable.getClass().toString();
        int resType;
        if (mItemTypeLookups.containsKey(s)) {
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
        //
        // NOTE: if this assumption fails, the code still works. It just runs slower.
        //
        mLastItemViewTypePos = position;
        mLastItemViewType = resType;
        mLastItemViewTypeEvent = bindable;

        // And return
        return resType;
    }

    /**
     * Get the (approximate, overestimate) of the number of Event subclasses
     * in use in this list.
     */
    @Override
    public int getViewTypeCount() {
        // Add 1 to allow for the LegacyEvent object.
        return mBinder.getBindableItemTypeCount() + 1;
    }

    @Nullable
    @Override
    public View getView(final int position,
                        @Nullable View convertView,
                        @NonNull final ViewGroup parent) {
        BindableItemCursor cursor = (BindableItemCursor) this.getCursor();
        cursor.moveToPosition(position);

        BindableItem bindable;
        // Optimization to avoid unnecessary de-serializations.
        if (mLastItemViewTypePos == position) {
            bindable = mLastItemViewTypeEvent;
        } else {
            bindable = cursor.getBindableItem();
        }

        if (convertView == null) {
            convertView = bindable.newListItemView(mInflater, mContext, cursor, parent);
        }

        // Bind it, and we are done!
        mBinder.bindViewToItem(mContext, convertView, cursor, bindable);

        return convertView;
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
         * @param context     Context of request
         * @param convertView View to populate
         * @param cursor      Cursor, positions at the relevant row
         */
        void bindViewToItem(@NonNull Context context,
                            @NonNull View convertView,
                            @NonNull BindableItemCursor cursor,
                            @NonNull BindableItem item);
    }

    public interface BindableItem {

        /**
         * Get a new View object suitable for displaying this type of event.
         * <p>
         * NOTE: A single event subclass should NOT RETURN MORE THAN ONE TYPE OF VIEW. If it needs
         * to do this, create a new Event subclass or use a more complex view.
         *
         * @param inflater LayoutInflater for use in expanding XML resources
         * @param context  Context that requires the view
         * @param cursor   EventsCursor for this event, positioned at its row.
         * @param parent   ViewGroup that will contain the new View.
         *
         * @return a new view
         */
        View newListItemView(@NonNull LayoutInflater inflater,
                             @NonNull Context context,
                             @NonNull BindableItemCursor cursor,
                             @NonNull ViewGroup parent);

        /**
         * Bind this Event to the passed view. The view will be one created by a call
         * to newListItemView(...).
         *
         * @param view    View to populate
         * @param context Context using view
         * @param cursor  EventsCursor for this event, positioned at its row.
         * @param db      database adapter.
         */
        void bindView(@NonNull View view,
                      @NonNull Context context,
                      @NonNull BindableItemCursor cursor,
                      @NonNull DBA db);

        /**
         * Called when an item in a list has been clicked, this method should populate the passed
         * 'items' parameter with one {@link ContextDialogItem} per operation that can be
         * performed on this object.
         *
         * @param context  Context resulting in Click() event
         * @param parent   ListView (or other) that contained the item
         * @param view     View that was clicked
         * @param position position in cursor of item
         * @param id       row id of item
         * @param items    items collection to fill
         * @param db       database adapter.
         */
        void addContextMenuItems(@NonNull Context context,
                                 @NonNull AdapterView<?> parent,
                                 @NonNull View view,
                                 int position,
                                 long id,
                                 @NonNull List<ContextDialogItem> items,
                                 @NonNull DBA db);
    }
}
