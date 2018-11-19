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

package com.eleybourn.bookcatalogue.taskqueue;

import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.cursors.BindableItemCursor;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;
import com.eleybourn.bookcatalogue.adapters.BindableItemCursorAdapter;

import java.util.Date;
import java.util.Hashtable;
import java.util.Map;

import static com.eleybourn.bookcatalogue.taskqueue.DBHelper.DOM_EVENT;
import static com.eleybourn.bookcatalogue.taskqueue.DBHelper.DOM_EVENT_DATE;
import static com.eleybourn.bookcatalogue.taskqueue.DBHelper.DOM_ID;
import static com.eleybourn.bookcatalogue.taskqueue.DBHelper.DOM_TASK_ID;

/**
 * Cursor subclass used to make accessing TaskExceptions a little easier.
 *
 * @author Philip Warner
 */
public class EventsCursor extends SQLiteCursor implements BindableItemCursor {

    /** Column number of ID column. */
    private static int mIdCol = -2;
    /** Column number of date column. */
    private static int mDateCol = -2;
    /** Column number of Exception column. */
    private static int mEventCol = -2;
    /** Column number of TaskId column. */
    private static int mTaskIdCol = -2;

    private final Map<Long, Boolean> mSelections = new Hashtable<>();


    /**
     * Constructor, based on SQLiteCursor constructor
     */
    EventsCursor(final @NonNull SQLiteCursorDriver driver, final @NonNull String editTable, final @NonNull SQLiteQuery query) {
        super(driver, editTable, query);
    }

    /**
     * Accessor for ID field.
     *
     * @return row id
     */
    public long getId() {
        if (mIdCol < 0) {
            mIdCol = this.getColumnIndex(DOM_ID);
        }
        return getLong(mIdCol);
    }
    /**
     * Accessor for Task ID field. Not present in all cursors.
     *
     * @return	task id
     */
    public long getTaskId() {
        if (mTaskIdCol == -2) {
            mTaskIdCol = this.getColumnIndex(DOM_TASK_ID);
        }
        return getLong(mTaskIdCol);
    }

    /**
     * See if the optional task_id column is returned. m_taskIdCol will be initialized to -2, but if
     * getColumnIndex has been called it will be a column number, or -1.
     *
     * @return	boolean indicating if task_id column is present.
     */
    public boolean hasTaskId() {
        if (mTaskIdCol == -2) {
            mTaskIdCol = this.getColumnIndex(DOM_TASK_ID);
        }
        return mTaskIdCol >= 0;
    }

    /**
     * Accessor for Exception date field.
     *
     * @return Exception date
     */
    @NonNull
    public Date getEventDate() {
        if (mDateCol < 0) {
            mDateCol = this.getColumnIndex(DOM_EVENT_DATE);
        }
        Date date = DateUtils.parseDate(getString(mDateCol));
        if (date == null) {
            date = new Date();
        }
        return date;
    }

    /**
     * Accessor for Exception field.
     *
     * @return TaskException object
     */
    @NonNull
    private Event getEvent() {
        if (mEventCol < 0) {
            mEventCol = this.getColumnIndex(DOM_EVENT);
        }
        byte[] blob = getBlob(mEventCol);
        Event event;
        try {
            event = SerializationUtils.deserializeObject(blob);
        } catch (RTE.DeserializationException de) {
            event = QueueManager.getQueueManager().newLegacyEvent(blob);
        }
        event.setId(this.getId());
        return event;
    }

    /**
     * Fake attribute to handle multi-select ListViews. if we ever do them.
     *
     * @return Options indicating if current row has been 'selected'.
     */
    public boolean getIsSelected() {
        return getIsSelected(getId());
    }

    private boolean getIsSelected(final long id) {
        if (mSelections.containsKey(id)) {
            return mSelections.get(id);
        } else {
            return false;
        }
    }

    public void setIsSelected(final long id, final boolean selected) {
        mSelections.put(id, selected);
    }

    @Override
    @NonNull
    public BindableItemCursorAdapter.BindableItem getBindableItem() {
        return getEvent();
    }
}
