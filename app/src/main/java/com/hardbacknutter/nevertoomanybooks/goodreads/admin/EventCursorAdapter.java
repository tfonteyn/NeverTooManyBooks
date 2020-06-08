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
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.goodreads.events.SendBookEvent;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.LegacyEvent;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQCursorAdapter;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQEventCursorRow;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQItem;

public class EventCursorAdapter
        extends TQCursorAdapter {

    @NonNull
    private final DAO mDb;

    @NonNull
    private final LayoutInflater mLayoutInflater;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param cursor  Cursor to use as source
     * @param db      Database Access
     */
    public EventCursorAdapter(@NonNull final Context context,
                              @NonNull final Cursor cursor,
                              @NonNull final DAO db) {
        super(context, cursor);
        mLayoutInflater = LayoutInflater.from(context);
        mDb = db;
    }

    @Override
    public TQEventCursorRow getItem(final int position) {
        final Cursor cursor = (Cursor) super.getItem(position);
        Objects.requireNonNull(cursor, ErrorMsg.NULL_CURSOR);
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
