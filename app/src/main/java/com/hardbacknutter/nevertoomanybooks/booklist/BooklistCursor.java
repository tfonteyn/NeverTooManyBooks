/*
 * @Copyright 2018-2025 HardBackNutter
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

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Parcelable;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.ColumnNotPresentException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * A wrapper for a cached collection of cursors.
 * <p>
 * TODO: https://developer.android.com/topic/libraries/architecture/paging/v3-overview
 */
public class BooklistCursor
        implements DataHolder {

    public static final String PK_PAGE_SIZE = "booklist.cursor.page.size";
    public static final String PK_LRU_LIST_MULTIPLIER = "booklist.cursor.lru.size";

    /** Number of rows to return in each cursor. */
    public static final int PAGE_SIZE_MIN = 16;
    public static final int PAGE_SIZE_DEFAULT = 32;
    public static final int PAGE_SIZE_MAX = 64;

    /** Multiplier for the {@link CursorCache}; multiplies with the pageSize. */
    public static final int LRU_LIST_MULTIPLIER_MIN = 4;
    public static final int LRU_LIST_MULTIPLIER_DEFAULT = 8;
    public static final int LRU_LIST_MULTIPLIER_MAX = 16;

    /** Back reference to the builder which produced this cursor. */
    @NonNull
    private final Booklist booklist;
    @NonNull
    private final CursorCache cursorCache;
    private final int pageSize;
    /** The Currently active cursor. */
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private Cursor currentCursor;
    /** Pseudo-count obtained from the {@link Booklist}. */
    @Nullable
    private Integer pseudoCount;
    /** Absolute position in the list (as opposed to position in the {@link #currentCursor}). */
    private int absPosition = -1;


    /**
     * Constructor.
     *
     * @param booklist that created the table to which this cursor refers.
     */
    BooklistCursor(@NonNull final Booklist booklist) {
        this.booklist = booklist;

        final SharedPreferences prefs = ServiceLocator.getInstance().getSharedPreferences();
        // Protect against silly values
        pageSize = MathUtils.clamp(prefs.getInt(PK_PAGE_SIZE, PAGE_SIZE_DEFAULT),
                                   PAGE_SIZE_MIN, PAGE_SIZE_MAX);

        // Protect against silly values
        final int lruSize = pageSize * MathUtils.clamp(
                prefs.getInt(PK_LRU_LIST_MULTIPLIER, LRU_LIST_MULTIPLIER_DEFAULT),
                LRU_LIST_MULTIPLIER_MIN, LRU_LIST_MULTIPLIER_MAX);

        cursorCache = new CursorCache(lruSize, cursorId -> {
            // Determine the actual start position offset.
            final int offset = cursorId * pageSize;
            return this.booklist.getOffsetCursor(offset, pageSize);
        });
    }

    /**
     * Get the count of all visible rows.
     *
     * @return visible row count
     */
    public int getCount() {
        if (pseudoCount == null) {
            pseudoCount = booklist.countVisibleRows();
        }
        return pseudoCount;
    }

    @Override
    @NonNull
    public Set<String> keySet() {
        return Set.copyOf(Arrays.asList(getCurrentCursor().getColumnNames()));
    }

    /**
     * Returns {@code true} if the given key is contained in the mapping
     * of this DataHolder.
     *
     * @param key the domain to get
     *
     * @return {@code true} if this cursor contains the specified domain.
     */
    @Override
    public boolean contains(@NonNull final String key) {
        return getCurrentCursor().getColumnIndex(key) > -1;
    }

    @Override
    @Nullable
    public String getString(@NonNull final String key,
                            @Nullable final String defValue)
            throws ColumnNotPresentException {
        final Cursor cursor = getCurrentCursor();
        final int col = cursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        if (cursor.isNull(col)) {
            return defValue;
        }
        return cursor.getString(col);
    }

    /**
     * Returns the value associated with the given key.
     *
     * @param key to get
     *
     * @return the boolean value of the column ({@code null} comes back as false).
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @Override
    public boolean getBoolean(@NonNull final String key)
            throws ColumnNotPresentException {
        return getInt(key) == 1;
    }

    /**
     * Returns the value associated with the given key.
     *
     * @param key to get
     *
     * @return the int value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @Override
    public int getInt(@NonNull final String key)
            throws ColumnNotPresentException {

        final Cursor cursor = getCurrentCursor();
        final int col = cursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        // if (cursor.isNull(col)) {
        //     return 0;
        // }
        return cursor.getInt(col);
    }

    /**
     * Returns the value associated with the given key.
     *
     * @param key to get
     *
     * @return the long value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @Override
    public long getLong(@NonNull final String key)
            throws ColumnNotPresentException {

        final Cursor cursor = getCurrentCursor();
        final int col = cursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        // if (cursor.isNull(col)) {
        //     return 0;
        // }
        return cursor.getLong(col);
    }

    /**
     * Returns the value associated with the given key.
     *
     * @param parser to use for number parsing
     * @param key    to get
     *
     * @return the double value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     * @throws NumberFormatException     if the value could not be parsed.
     */
    @Override
    public float getFloat(@NonNull final String key,
                          @NonNull final RealNumberParser parser)
            throws NumberFormatException {

        final Cursor cursor = getCurrentCursor();
        final int col = cursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        // if (cursor.isNull(col)) {
        //     return 0;
        // }
        return cursor.getFloat(col);
    }

    /**
     * Returns the value associated with the given key.
     *
     * @param key to get
     *
     * @return the double value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     * @throws NumberFormatException     if the value could not be parsed.
     */
    @Override
    public double getDouble(@NonNull final String key,
                            @NonNull final RealNumberParser parser)
            throws NumberFormatException {

        final Cursor cursor = getCurrentCursor();
        final int col = cursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        // if (cursor.isNull(col)) {
        //     return 0;
        // }
        return cursor.getDouble(col);
    }

    @NonNull
    public <T extends Parcelable> ArrayList<T> getParcelableArrayList(@NonNull final String key)
            throws ColumnNotPresentException {
        throw new ColumnNotPresentException(key);
    }

    @NonNull
    private Cursor getCurrentCursor() {
        synchronized (cursorCache) {
            if (currentCursor == null) {
                currentCursor = cursorCache.get(absPosition / pageSize);
            }
        }
        return currentCursor;
    }

    /**
     * Move to the given absolute Booklist position.
     *
     * @param position to move to
     *
     * @return {@code true} if the move succeeded
     */
    public final boolean moveToPosition(final int position) {
        // Make sure position isn't past the end of the cursor
        final int count = getCount();
        if (position >= count) {
            absPosition = count;
            return false;
        }

        // Make sure position isn't before the beginning of the cursor
        if (position < 0) {
            absPosition = -1;
            return false;
        }

        // Check for no-op moves, and skip the rest of the work for them
        if (position == absPosition) {
            return true;
        }

        final boolean result = onMove(position);
        if (result) {
            absPosition = position;
        } else {
            absPosition = -1;
        }

        return result;
    }

    /**
     * Close all existing underlying cursors and start over fetching all data from the database.
     *
     * @return whether the requested reload succeeded to reposition
     */
    public boolean reload() {
        close();
        pseudoCount = null;
        return onMove(absPosition);
    }

    private boolean onMove(final int newPosition) {
        synchronized (cursorCache) {
            currentCursor = cursorCache.get(newPosition / pageSize);
            return currentCursor.moveToPosition(newPosition % pageSize);
        }
    }

    /**
     * Close all existing underlying cursors.
     */
    public void close() {
        cursorCache.evictAll();
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistCursor{"
               + "booklist=" + booklist
               + ", pos=" + absPosition
               + ", pseudoCount=" + pseudoCount
               + ", pageSize=" + pageSize
               + ", cursorCache=" + cursorCache
               + '}';
    }

    private static class CursorCache
            extends LruCache<Integer, Cursor> {
        private static final String TAG = "CursorCache";
        @NonNull
        private final Function<Integer, Cursor> cursorSupplier;

        CursorCache(final int maxSize,
                    @NonNull final Function<Integer, Cursor> cursorSupplier) {
            super(maxSize);
            this.cursorSupplier = cursorSupplier;
        }

        @Override
        protected Cursor create(@NonNull final Integer key) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "create: " + key + ", createCount=" + createCount());
            }
            return cursorSupplier.apply(key);
        }

        @Override
        protected void entryRemoved(final boolean evicted,
                                    @NonNull final Integer key,
                                    @NonNull final Cursor oldValue,
                                    @Nullable final Cursor newValue) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "remove: " + key + ", evictionCount=" + evictionCount());
            }
            oldValue.close();
        }
    }
}
