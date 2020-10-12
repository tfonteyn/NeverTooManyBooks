/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;

public class TQEventCursorRow
        extends CursorRow {

    /**
     * Constructor.
     *
     * @param cursor to read from
     */
    public TQEventCursorRow(@NonNull final Cursor cursor) {
        super(cursor);
    }

    /**
     * Get the id of the Event.
     *
     * @return id
     */
    public long getId() {
        return getLong(QueueDBHelper.KEY_PK_ID);
    }

    /**
     * Get the date of when the Event occurred.
     *
     * @param context Current context
     *
     * @return date; UTC based
     */
    @NonNull
    public LocalDateTime getEventDate(@NonNull final Context context) {
        LocalDateTime utcDate = DateParser.getInstance(context).parseISO(
                getString(QueueDBHelper.KEY_EVENT_UTC_DATETIME));
        if (utcDate == null) {
            utcDate = LocalDateTime.now(ZoneOffset.UTC);
        }
        return utcDate;
    }

    /**
     * Get the actual Event object.
     *
     * @param context Current context
     *
     * @return (subclass of) TQEvent
     */
    @NonNull
    public TQEvent getEvent(@NonNull final Context context) {
        TQEvent event;
        final byte[] blob = getBlob(QueueDBHelper.KEY_EVENT);
        try {
            event = SerializationUtils.deserializeObject(blob);
        } catch (@NonNull final SerializationUtils.DeserializationException de) {
            event = new LegacyEvent(context.getString(R.string.legacy_record, getId()));
        }
        event.setId(getId());
        return event;
    }
}
