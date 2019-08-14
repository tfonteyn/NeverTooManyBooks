/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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

import android.annotation.SuppressLint;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.SerializationUtils;

/**
 * Cursor subclass used to make accessing TaskExceptions a little easier.
 */
public class EventsCursor
        extends SQLiteCursor
        implements BindableItemCursor {

    /** Column number of ID column. */
    private static int sIdCol = -2;
    /** Column number of date column. */
    private static int sDateCol = -2;
    /** Column number of Exception column. */
    private static int sEventCol = -2;

    @SuppressLint("UseSparseArrays")
    private final Map<Long, Boolean> mSelections = Collections.synchronizedMap(new HashMap<>());

    /**
     * Constructor, based on SQLiteCursor constructor.
     */
    EventsCursor(@NonNull final SQLiteCursorDriver driver,
                 @NonNull final String editTable,
                 @NonNull final SQLiteQuery query) {
        super(driver, editTable, query);
    }

    /**
     * Accessor for Exception date field.
     *
     * @return Exception date
     */
    @NonNull
    public Date getEventDate() {
        if (sDateCol < 0) {
            sDateCol = getColumnIndex(TaskQueueDBHelper.DOM_EVENT_DATE);
        }
        Date date = DateUtils.parseDate(getString(sDateCol));
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
        if (sEventCol < 0) {
            sEventCol = getColumnIndex(TaskQueueDBHelper.DOM_EVENT);
        }
        byte[] blob = getBlob(sEventCol);
        Event event;
        try {
            event = SerializationUtils.deserializeObject(blob);
        } catch (@NonNull final SerializationUtils.DeserializationException de) {
            event = new LegacyEvent();
        }
        event.setId(getId());
        return event;
    }

    /**
     * Accessor for ID field.
     *
     * @return row id
     */
    public long getId() {
        if (sIdCol < 0) {
            sIdCol = getColumnIndex(TaskQueueDBHelper.DOM_ID);
        }
        return getLong(sIdCol);
    }
}
