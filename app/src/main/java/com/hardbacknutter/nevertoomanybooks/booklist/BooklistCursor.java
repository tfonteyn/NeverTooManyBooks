/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.database.AbstractCursor;
import android.database.Cursor;
import android.util.LruCache;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Function;

/**
 * TODO: https://developer.android.com/topic/libraries/architecture/paging.html
 */
public class BooklistCursor
        extends AbstractCursor {

    /** Number of rows to return in each cursor. No tuning has been done to pick this number. */
    private static final int PAGE_SIZE = 32;
    /** Size of {@link CursorCache}. No tuning has been done to pick this number. */
    private static final int LRU_LIST_SIZE = 8;

    /** Back reference to the builder which produced this cursor. */
    @NonNull
    private final Booklist booklist;
    @NonNull
    private final CursorCache cursorCache;
    /** The Currently active cursor. */
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private Cursor currentCursor;
    /** Pseudo-count obtained from the {@link Booklist}. */
    @Nullable
    private Integer pseudoCount;

    /**
     * Constructor.
     *
     * @param booklist that created the table to which this cursor refers.
     */
    BooklistCursor(@NonNull final Booklist booklist) {
        this.booklist = booklist;
        cursorCache = new CursorCache(LRU_LIST_SIZE, cursorId -> {
            // Determine the actual start position offset.
            final int offset = cursorId * PAGE_SIZE;
            return this.booklist.getOffsetCursor(offset, PAGE_SIZE);
        });
    }

    @Override
    public int getCount() {
        if (pseudoCount == null) {
            pseudoCount = booklist.countVisibleRows();
        }
        return pseudoCount;
    }

    @Override
    @NonNull
    public String[] getColumnNames() {
        return booklist.getListColumnNames();
    }

    @Override
    @Nullable
    public String getString(final int column) {
        return getCurrentCursor().getString(column);
    }

    @Override
    public short getShort(final int column) {
        return getCurrentCursor().getShort(column);
    }

    @Override
    public int getInt(final int column) {
        return getCurrentCursor().getInt(column);
    }

    @Override
    public long getLong(final int column) {
        return getCurrentCursor().getLong(column);
    }

    @Override
    public float getFloat(final int column) {
        return getCurrentCursor().getFloat(column);
    }

    @Override
    public double getDouble(final int column) {
        return getCurrentCursor().getDouble(column);
    }

    @Override
    public boolean isNull(final int column) {
        return getCurrentCursor().isNull(column);
    }

    @NonNull
    private Cursor getCurrentCursor() {
        synchronized (cursorCache) {
            if (currentCursor == null) {
                currentCursor = cursorCache.get(getPosition() / PAGE_SIZE);
            }
        }
        return currentCursor;
    }

    @Override
    @CallSuper
    public boolean requery() {
        final int newPos = getPosition();
        close();
        pseudoCount = null;
        // create our new cursor, reposition, and update the super
        return onMove(newPos, newPos) && super.requery();
    }

    @Override
    public boolean onMove(final int oldPosition,
                          final int newPosition) {
        synchronized (cursorCache) {
            currentCursor = cursorCache.get(newPosition / PAGE_SIZE);
            return currentCursor.moveToPosition(newPosition % PAGE_SIZE);
        }
    }

    @Override
    @CallSuper
    public void close() {
        cursorCache.evictAll();
        super.close();
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistCursor{"
               + "booklist=" + booklist
               + ", pseudoCount=" + pseudoCount
               + ", cursorCache=" + cursorCache
               + '}';
    }

    private static class CursorCache
            extends LruCache<Integer, Cursor> {

        @NonNull
        private final Function<Integer, Cursor> cursorSupplier;

        CursorCache(final int maxSize,
                    @NonNull final Function<Integer, Cursor> cursorSupplier) {
            super(maxSize);
            this.cursorSupplier = cursorSupplier;
        }

        @Override
        protected Cursor create(@NonNull final Integer key) {
            return cursorSupplier.apply(key);
        }

        @Override
        protected void entryRemoved(final boolean evicted,
                                    @NonNull final Integer key,
                                    @NonNull final Cursor oldValue,
                                    @Nullable final Cursor newValue) {
            oldValue.close();
        }
    }
}
