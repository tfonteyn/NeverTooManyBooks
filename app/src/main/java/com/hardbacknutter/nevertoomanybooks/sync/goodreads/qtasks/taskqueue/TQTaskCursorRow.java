/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;

public class TQTaskCursorRow
        extends CursorRow {

    /**
     * Constructor.
     *
     * @param cursor to read from
     */
    public TQTaskCursorRow(@NonNull final Cursor cursor) {
        super(cursor);
    }

    /**
     * Get the id of the Task.
     *
     * @return id
     */
    public long getId() {
        return getLong(TaskQueueDBHelper.KEY_PK_ID);
    }

    /**
     * Get the date of when the Task was queued.
     *
     * @return LocalDateTime(ZoneOffset.UTC)
     */
    @NonNull
    public LocalDateTime getQueuedUtcDate() {

        LocalDateTime utcDate = mDateParser.parse(getString(
                TaskQueueDBHelper.KEY_TASK_QUEUED_UTC_DATETIME));
        if (utcDate == null) {
            utcDate = LocalDateTime.now(ZoneOffset.UTC);
        }
        return utcDate;
    }

    /**
     * Get the date of when the Task was last retried.
     *
     * @return LocalDateTime(ZoneOffset.UTC)
     */
    @NonNull
    public LocalDateTime getRetryUtcDate() {
        LocalDateTime utcDate = mDateParser.parse(getString(
                TaskQueueDBHelper.KEY_TASK_RETRY_UTC_DATETIME));
        if (utcDate == null) {
            utcDate = LocalDateTime.now(ZoneOffset.UTC);
        }
        return utcDate;
    }

    /**
     * Get the status code.
     *
     * @return status
     */
    @NonNull
    public String getStatusCode() {
        return getString(TaskQueueDBHelper.KEY_TASK_STATUS_CODE);
    }

    /**
     * Get the number of Events linked to this Task.
     *
     * @return count
     */
    public int getEventCount() {
        return getInt(TaskQueueDBHelper.KEY_EVENT_COUNT);
    }

    /**
     * Get the actual Task object.
     *
     * @param context Current context
     *
     * @return (subclass of) TQTask; or {@code null} on deserialize failure
     */
    @NonNull
    public TQTask getTask(@NonNull final Context context) {
        TQTask task;
        final byte[] blob = getBlob(TaskQueueDBHelper.KEY_TASK);
        try {
            task = SerializationUtils.deserializeObject(blob);
        } catch (@NonNull final SerializationUtils.DeserializationException de) {
            task = new LegacyTask(context.getString(R.string.legacy_record, getId()));
        }
        task.setId(getId());
        return task;
    }
}
