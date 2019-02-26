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

package com.eleybourn.bookcatalogue.goodreads.taskqueue;

import android.annotation.SuppressLint;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Cursor subclass used to make accessing TaskExceptions a little easier.
 *
 * @author Philip Warner
 */
public class EventsCursor
        extends SQLiteCursor
        implements BindableItemCursor {

    /** Column number of ID column. */
    private static int mIdCol = -2;
    /** Column number of date column. */
    private static int mDateCol = -2;
    /** Column number of Exception column. */
    private static int mEventCol = -2;

    @SuppressLint("UseSparseArrays")
    private final Map<Long, Boolean> mSelections =
            Collections.synchronizedMap(new HashMap<Long, Boolean>());

    /**
     * Constructor, based on SQLiteCursor constructor.
     */
    EventsCursor(@NonNull final SQLiteCursorDriver driver,
                 @NonNull final String editTable,
                 @NonNull final SQLiteQuery query) {
        super(driver, editTable, query);
    }

    /**
     * Accessor for ID field.
     *
     * @return row id
     */
    public long getId() {
        if (mIdCol < 0) {
            mIdCol = getColumnIndex(TaskQueueDBHelper.DOM_ID);
        }
        return getLong(mIdCol);
    }

    /**
     * Accessor for Exception date field.
     *
     * @return Exception date
     */
    @NonNull
    public Date getEventDate() {
        if (mDateCol < 0) {
            mDateCol = getColumnIndex(TaskQueueDBHelper.DOM_EVENT_DATE);
        }
        Date date = DateUtils.parseDate(getString(mDateCol));
        if (date == null) {
            date = new Date();
        }
        return date;
    }

    /**
     * Fake attribute to handle multi-select ListViews. if we ever do them.
     *
     * @return Flag indicating if current row has been 'selected'.
     */
    public boolean isSelected() {
        if (mSelections.containsKey(getId())) {
            //noinspection ConstantConditions
            return mSelections.get(getId());
        } else {
            return false;
        }
    }

    public void setSelected(final long id,
                            final boolean selected) {
        mSelections.put(id, selected);
    }

    @Override
    @NonNull
    public BindableItemCursorAdapter.BindableItem getBindableItem() {
        if (mEventCol < 0) {
            mEventCol = getColumnIndex(TaskQueueDBHelper.DOM_EVENT);
        }
        byte[] blob = getBlob(mEventCol);
        Event event;
        try {
            event = SerializationUtils.deserializeObject(blob);
        } catch (SerializationUtils.DeserializationException de) {
            event = new LegacyEvent();
        }
        event.setId(getId());
        return event;
    }
}
