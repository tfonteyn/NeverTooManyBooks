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
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;

import java.util.Date;

import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;

/**
 * Cursor subclass used to make accessing Events a little easier.
 */
public class EventsCursor
        extends SQLiteCursor
        implements BindableItemCursor<Event> {

    /** Column number of id column. */
    private static int sIdCol = -2;
    /** Column number of date column. */
    private static int sDateCol = -2;
    /** Column number of Exception column. */
    private static int sEventCol = -2;

    private final LongSparseArray<Boolean> mSelections = new LongSparseArray<>();

    /**
     * Constructor, based on SQLiteCursor constructor.
     */
    EventsCursor(@NonNull final SQLiteCursorDriver driver,
                 @NonNull final String editTable,
                 @NonNull final SQLiteQuery query) {
        super(driver, editTable, query);
    }

    /**
     * Accessor for Event date field.
     *
     * @return Exception date
     */
    @NonNull
    public Date getEventDate() {
        if (sDateCol < 0) {
            sDateCol = getColumnIndex(QueueDBHelper.KEY_EVENT_DATE);
        }
        Date date = DateUtils.parseSqlDateTime(getString(sDateCol));
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
        synchronized (mSelections) {
            Boolean isSelected = mSelections.get(getId());
            if (isSelected != null) {
                return isSelected;
            } else {
                return false;
            }
        }
    }

    public void setSelected(final long id,
                            final boolean selected) {
        synchronized (mSelections) {
            mSelections.put(id, selected);
        }
    }

    @Override
    @NonNull
    public Event getBindableItem(@NonNull final Context context) {
        if (sEventCol < 0) {
            sEventCol = getColumnIndex(QueueDBHelper.KEY_EVENT);
        }
        byte[] blob = getBlob(sEventCol);
        Event event;
        try {
            event = SerializationUtils.deserializeObject(blob);
        } catch (@NonNull final SerializationUtils.DeserializationException de) {
            event = new LegacyEvent(context);
        }
        event.setId(getId());
        return event;
    }

    @Override
    public long getId() {
        if (sIdCol < 0) {
            sIdCol = getColumnIndex(QueueDBHelper.KEY_PK_ID);
        }
        return getLong(sIdCol);
    }
}
