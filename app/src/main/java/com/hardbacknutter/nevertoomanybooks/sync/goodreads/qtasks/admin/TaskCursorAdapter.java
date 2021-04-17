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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.admin;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.GrBaseTask;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.LegacyTask;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.TQCursorAdapter;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.TQItem;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.TQTaskCursorRow;

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
    TaskCursorAdapter(@NonNull final Context context,
                      @NonNull final Cursor cursor) {
        super(context, cursor);
        mLayoutInflater = LayoutInflater.from(context);
    }

    @Override
    @NonNull
    public TQTaskCursorRow getItem(final int position) {
        final Cursor cursor = Objects.requireNonNull((Cursor) super.getItem(position),
                                                     String.valueOf(position));
        return new TQTaskCursorRow(cursor);
    }

    @NonNull
    @Override
    public View newView(@NonNull final Context context,
                        @NonNull final Cursor cursor,
                        @NonNull final ViewGroup parent) {
        final TQTaskCursorRow row = new TQTaskCursorRow(cursor);
        final Object task = row.getTask(context);
        if (task instanceof GrBaseTask) {
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
        if (task instanceof GrBaseTask) {
            final TaskViewHolder holder = new TaskViewHolder(view);
            holder.bind(row, (GrBaseTask) task);
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
