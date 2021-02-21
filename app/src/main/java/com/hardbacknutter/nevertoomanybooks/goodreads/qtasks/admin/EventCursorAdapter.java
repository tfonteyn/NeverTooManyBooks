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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.admin;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.BookDao;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.LegacyEvent;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.TQCursorAdapter;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.TQEventCursorRow;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.TQItem;

public class EventCursorAdapter
        extends TQCursorAdapter {

    /** Database Access. */
    @NonNull
    private final BookDao mDb;

    @NonNull
    private final LayoutInflater mLayoutInflater;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param cursor  Cursor to use as source
     * @param db      Database Access
     */
    EventCursorAdapter(@NonNull final Context context,
                       @NonNull final Cursor cursor,
                       @NonNull final BookDao db) {
        super(context, cursor);
        mLayoutInflater = LayoutInflater.from(context);
        mDb = db;
    }

    @Override
    @NonNull
    public TQEventCursorRow getItem(final int position) {
        final Cursor cursor = Objects.requireNonNull((Cursor) super.getItem(position),
                                                     String.valueOf(position));
        return new TQEventCursorRow(cursor);
    }

    @NonNull
    @Override
    public View newView(@NonNull final Context context,
                        @NonNull final Cursor cursor,
                        @NonNull final ViewGroup parent) {
        final TQEventCursorRow row = new TQEventCursorRow(cursor);
        final Object event = row.getEvent(context);
        if (event instanceof SendBookEvent) {
            return mLayoutInflater.inflate(R.layout.row_event_info, parent, false);
        } else {
            return LegacyViewHolder.createView(context);
        }
    }

    @Override
    public void bindView(@NonNull final View view,
                         @NonNull final Context context,
                         @NonNull final Cursor cursor) {
        final TQEventCursorRow row = new TQEventCursorRow(cursor);
        final Object event = row.getEvent(context);
        if (event instanceof SendBookEvent) {
            final BookEventViewHolder holder = new BookEventViewHolder(view);
            holder.bind(row, (SendBookEvent) event, mDb);
        } else {
            final LegacyViewHolder holder = new LegacyViewHolder(view);
            holder.bind((LegacyEvent) event);
        }
    }

    @NonNull
    @Override
    public TQItem getTQItem(@NonNull final Context context,
                            final int position) {
        return getItem(position).getEvent(context);
    }
}
