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
package com.hardbacknutter.nevertoomanybooks.goodreads.admin;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.LegacyTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQCursorAdapter;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQItem;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQTaskCursorRow;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.BaseTQTask;

public class TaskCursorAdapter
        extends TQCursorAdapter {

    @NonNull
    private final LayoutInflater mLayoutInflater;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param cursor  Cursor to use as source
     */
    public TaskCursorAdapter(@NonNull final Context context,
                             @NonNull final Cursor cursor) {
        super(context, cursor);
        mLayoutInflater = LayoutInflater.from(context);
    }

    @Override
    public TQTaskCursorRow getItem(final int position) {
        final Cursor cursor = (Cursor) super.getItem(position);
        Objects.requireNonNull(cursor, ErrorMsg.NULL_CURSOR);
        return new TQTaskCursorRow(cursor);
    }

    @NonNull
    @Override
    public View newView(@NonNull final Context context,
                        @NonNull final Cursor cursor,
                        @NonNull final ViewGroup parent) {
        final TQTaskCursorRow row = new TQTaskCursorRow(cursor);
        final Object task = row.getTask(context);
        if (task instanceof BaseTQTask) {
            // same layout for all subclasses
            return mLayoutInflater.inflate(R.layout.row_task_info, parent, false);
        } else {
            return LegacyViewHolder.createView(context);
        }
    }

    @Override
    public void bindView(@NonNull final View view,
                         @NonNull final Context context,
                         @NonNull final Cursor cursor) {
        final TQTaskCursorRow row = new TQTaskCursorRow(cursor);
        final Object task = row.getTask(context);
        if (task instanceof BaseTQTask) {
            final TaskViewHolder holder = new TaskViewHolder(view);
            holder.bind(row, (BaseTQTask) task);
        } else {
            final LegacyViewHolder holder = new LegacyViewHolder(view);
            holder.bind((LegacyTask) task);
        }
    }

    @NonNull
    @Override
    public TQItem getTQItem(@NonNull final Context context,
                            final int position) {
        return getItem(position).getTask(context);
    }
}
