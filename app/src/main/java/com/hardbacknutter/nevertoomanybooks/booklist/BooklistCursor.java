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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * The glue between the adapter and the OffsetCursor/DataCursor from the {@link Booklist}.
 * <ul>
 * <li>{@link #booklist}: the table structure, from which OffsetCursor/DataCursor are produced.</li>
 * <li>{@link #dataCursorCache}: the cache where the above DataCursors are held.</li>
 * <li>{@link #currentCursor}: the current active window of rows.</li>
 * </ul>
 * TODO: https://developer.android.com/topic/libraries/architecture/paging/v3-overview
 */
public class BooklistCursor
        implements DataHolder {

    /** Number of rows to return in each cursor. */
    public static final String PK_PAGE_SIZE = "booklist.cursor.page.size";
    /** Multiplier for the {@link DataCursorCache}; multiplies with the pageSize. */
    public static final String PK_LRU_LIST_MULTIPLIER = "booklist.cursor.lru.size";

    public static final int PAGE_SIZE_MIN = 16;
    public static final int PAGE_SIZE_DEFAULT = 32;
    public static final int PAGE_SIZE_MAX = 64;

    public static final int LRU_LIST_MULTIPLIER_MIN = 4;
    public static final int LRU_LIST_MULTIPLIER_DEFAULT = 8;
    public static final int LRU_LIST_MULTIPLIER_MAX = 16;

    /** Cursor position. */
    private static final int BEFORE_FIRST = -1;

    /** Back reference to the builder which produced this cursor. */
    @NonNull
    private final Booklist booklist;
    @NonNull
    private final DataCursorCache dataCursorCache;
    private final int pageSize;
    /** The Currently active cursor. */
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private DataCursor currentCursor;
    /** Pseudo-count obtained from the {@link Booklist}. */
    @Nullable
    private Integer pseudoCount;
    /** Absolute position in the list (as opposed to position in the {@link #currentCursor}). */
    private int absPosition = BEFORE_FIRST;

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

        dataCursorCache = new DataCursorCache(lruSize, pageNum -> {
            try (Cursor offsetCursor = this.booklist
                    .getOffsetCursor(pageNum * pageSize, pageSize)) {
                return new DataCursor(offsetCursor);
            }
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
        return Set.copyOf(getCurrentCursor().getRow().keySet());
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
        return getCurrentCursor().getRow().contains(key);
    }

    @Override
    @Nullable
    public String getString(@NonNull final String key,
                            @Nullable final String defValue) {
        final DataManager data = getCurrentCursor().getRow();
        if (!data.contains(key)) {
            return defValue;
        }
        return data.getString(key, defValue);
    }

    /**
     * Returns the value associated with the given key.
     *
     * @param key to get
     *
     * @return the boolean value of the column ({@code null} comes back as false).
     */
    @Override
    public boolean getBoolean(@NonNull final String key) {
        return getInt(key) == 1;
    }

    /**
     * Returns the value associated with the given key.
     *
     * @param key to get
     *
     * @return the int value of the column ({@code null} comes back as 0)
     */
    @Override
    public int getInt(@NonNull final String key) {
        final DataManager data = getCurrentCursor().getRow();
        if (!data.contains(key)) {
            return 0;
        }
        return data.getInt(key);
    }

    /**
     * Returns the value associated with the given key.
     *
     * @param key to get
     *
     * @return the long value of the column ({@code null} comes back as 0)
     */
    @Override
    public long getLong(@NonNull final String key) {
        final DataManager data = getCurrentCursor().getRow();
        if (!data.contains(key)) {
            return 0;
        }
        return data.getLong(key);
    }

    /**
     * Returns the value associated with the given key.
     *
     * @param parser to use for number parsing
     * @param key    to get
     *
     * @return the double value of the column ({@code null} comes back as 0)
     *
     * @throws NumberFormatException if the value could not be parsed.
     */
    @Override
    public float getFloat(@NonNull final String key,
                          @NonNull final RealNumberParser parser)
            throws NumberFormatException {
        final DataManager data = getCurrentCursor().getRow();
        if (!data.contains(key)) {
            return 0;
        }
        return data.getFloat(key, parser);
    }

    /**
     * Returns the value associated with the given key.
     *
     * @param key to get
     *
     * @return the double value of the column ({@code null} comes back as 0)
     *
     * @throws NumberFormatException if the value could not be parsed.
     */
    @Override
    public double getDouble(@NonNull final String key,
                            @NonNull final RealNumberParser parser)
            throws NumberFormatException {
        final DataManager data = getCurrentCursor().getRow();
        if (!data.contains(key)) {
            return 0;
        }
        return data.getDouble(key, parser);
    }

    @NonNull
    public <T extends Parcelable> ArrayList<T> getParcelableArrayList(@NonNull final String key)
            throws UnsupportedOperationException {
        throw new UnsupportedOperationException(key);
    }

    @NonNull
    private DataCursor getCurrentCursor() {
        synchronized (dataCursorCache) {
            if (currentCursor == null) {
                currentCursor = dataCursorCache.get(absPosition / pageSize);
            }
        }
        return currentCursor;
    }

    /**
     * Move to the given <strong>absolute</strong> Booklist position.
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
            absPosition = BEFORE_FIRST;
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
            absPosition = BEFORE_FIRST;
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
        synchronized (dataCursorCache) {
            currentCursor = dataCursorCache.get(newPosition / pageSize);
            return currentCursor.moveToPosition(newPosition % pageSize);
        }
    }

    /**
     * Close all existing underlying cursors.
     */
    public void close() {
        dataCursorCache.evictAll();
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistCursor{"
               + "booklist=" + booklist
               + ", pos=" + absPosition
               + ", pseudoCount=" + pseudoCount
               + ", pageSize=" + pageSize
               + ", dataCursorCache=" + dataCursorCache
               + '}';
    }

    private static class DataCursorCache
            extends LruCache<Integer, DataCursor> {
        private static final String TAG = "DataCursorCache";
        @NonNull
        private final Function<Integer, DataCursor> cursorSupplier;

        DataCursorCache(final int maxSize,
                        @NonNull final Function<Integer, DataCursor> cursorSupplier) {
            super(maxSize);
            this.cursorSupplier = cursorSupplier;
        }

        @Override
        protected DataCursor create(@NonNull final Integer key) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "create: " + key + ", createCount=" + createCount());
            }
            return cursorSupplier.apply(key);
        }

        @Override
        protected void entryRemoved(final boolean evicted,
                                    final Integer key,
                                    final DataCursor oldValue,
                                    final DataCursor newValue) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "entryRemoved: " + key + ", evictionCount=" + evictionCount());
            }
        }
    }

    /**
     * This class takes a {@link Cursor} and converts it to a {@code List<DataManager>}.
     * It then acts as minimalistic cursor, keeping tracks of the current position/row
     * and allowing a client to move up/down in the list and retrieve the current row.
     */
    private static class DataCursor {

        private final List<DataManager> list = new ArrayList<>();

        private int currentPosition = BEFORE_FIRST;

        /**
         * Constructor.
         *
         * @param cursor to load. It's up to the caller to close this cursor.
         */
        DataCursor(@NonNull final Cursor cursor) {
            while (cursor.moveToNext()) {
                final DataManager rowData = new DataManager();
                rowData.putAll(cursor);
                list.add(rowData);
            }
        }

        /**
         * Move to the given position.
         *
         * @param position to move to
         *
         * @return {@code true} if successful; {@code false} if the move failed.
         */
        boolean moveToPosition(@IntRange(from = BEFORE_FIRST) final int position) {
            // Make sure position isn't past the end of the cursor
            final int count = list.size();
            if (position >= count) {
                currentPosition = count;
                return false;
            }

            // Make sure position isn't before the beginning of the cursor
            if (position < 0) {
                currentPosition = BEFORE_FIRST;
                return false;
            }

            currentPosition = position;
            return true;
        }

        /**
         * Get the row at the current position.
         *
         * @return row
         */
        @NonNull
        DataManager getRow() {
            return list.get(currentPosition);
        }
    }
}
