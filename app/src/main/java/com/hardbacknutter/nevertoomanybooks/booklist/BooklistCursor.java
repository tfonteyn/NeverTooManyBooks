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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.database.AbstractCursor;
import android.database.Cursor;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;

/**
 * TODO: https://developer.android.com/topic/libraries/architecture/paging.html
 * <p>
 * Philip Warner: Yet Another Rabbit Burrow ("YARB" -- did I invent a new acronym?).
 * What led to this?
 * <p>
 * 1. The call to getCount() that ListView does when it is passed a cursor added approximately 25%
 * to the total time building a booklist. Given the way the booklist is constructed (a flat table
 * with an index table that is ordered by ID), it was worth replacing getCount() with a local
 * version that simply returned the number of 'visible' rows in the nav table.
 * <p>
 * 2. This worked (approx 5ms vs. 500ms for a big list), but failed if the current position was
 * saved at the end of a long list. For some reason this caused lots of 'skip_rows' messages
 * from 'Cursor', and a *very* jittery backwards scroll.
 * <p>
 * 3. FUD: SQLite cursors seem to use memory based on the number of rows in the cursor. They do
 * not *seem* to refer back to the database and cache a window. If true, with lots of a books and
 * a small device memory, this would lead to problems.
 * <p>
 * The result?
 * <p>
 * A pseudo cursor that is made up of multiple cursors around a given position. Originally, the
 * plan was to build the surrounding cursors in a background thread, but the build time for small
 * cursors is remarkably small (approx 10ms on a 1.5GHz dual CPU).
 * So, this much simpler implementation was chosen.
 * <p>
 * What does it do?
 * <p>
 * getCount() is implemented as one would hope: a direct count of visible rows.
 * <p>
 * onMove(...) results in a new cursor being built when the row is not available in
 * existing cursors.
 * <p>
 * Cursors are kept in a hash based on their position.
 * Cursors more than {'link PAGES_AWAY_FOR_PURGE} 'windows' away from the
 * current position are eligible for purging if they are not in the Most Recently Used (MRU) list.
 * The MRU list holds {@link #MRU_LIST_SIZE} cursors.
 * Each cursor hold {@link #PAGE_SIZE} rows.
 */
public class BooklistCursor
        extends AbstractCursor {

    /** Log tag. */
    private static final String TAG = "BooklistCursor";

    /** Number of 'pages' a cursor has to 'away' to be considered for purging. */
    private static final int PAGES_AWAY_FOR_PURGE = 3;
    /** Number of rows to return in each cursor. No tuning has been done to pick this number. */
    private static final int PAGE_SIZE = 20;
    /** Size of MRU list. Not based on tuning; just set to more than 2*3+1. */
    private static final int MRU_LIST_SIZE = 8;
    /** Collection of current cursors. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final SparseArray<Cursor> mCursors = new SparseArray<>();
    /** MRU ring buffer of cursors. */
    @NonNull
    private final int[] mMruList = new int[MRU_LIST_SIZE];

    @NonNull
    private final BooklistBuilder mBooklistBuilder;
    /** The Currently active cursor. */
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private Cursor mActiveCursor;
    /** Current MRU ring buffer position. */
    private int mMruListPos;
    /** Pseudo-count obtained from Builder. */
    @Nullable
    private Integer mPseudoCount;

    /**
     * Constructor.
     *
     * @param booklistBuilder that created the table to which this cursor refers.
     */
    BooklistCursor(@NonNull final BooklistBuilder booklistBuilder) {
        mBooklistBuilder = booklistBuilder;
        for (int i = 0; i < MRU_LIST_SIZE; i++) {
            mMruList[i] = -1;
        }
    }

    @NonNull
    public BooklistBuilder getBooklistBuilder() {
        return mBooklistBuilder;
    }

    private void clearCursors() {
        // Synchronize cursor adjustments!
        synchronized (this) {
            for (int i = 0; i < mCursors.size(); i++) {
                mCursors.valueAt(i).close();
            }

            mCursors.clear();
            mActiveCursor = null;

            for (int i = 0; i < MRU_LIST_SIZE; i++) {
                mMruList[i] = -1;
            }
        }
    }

    /**
     * Check if the passed cursor id is in the MRU list.
     *
     * @param id of cursor to check
     *
     * @return {@code true} if cursor is in list
     */
    private boolean checkMru(@NonNull final Integer id) {
        for (int i : mMruList) {
            if (id == i) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@link AbstractCursor} method.
     */
    @Override
    public int getCount() {
        if (mPseudoCount == null) {
            mPseudoCount = mBooklistBuilder.countVisibleRows();
        }
        return mPseudoCount;
    }

    /**
     * {@link AbstractCursor} method.
     */
    @Override
    @NonNull
    public String[] getColumnNames() {
        return mBooklistBuilder.getListColumnNames();
    }

    @Override
    public String getString(final int column) {
        //noinspection ConstantConditions
        return mActiveCursor.getString(column);
    }

    @Override
    public short getShort(final int column) {
        //noinspection ConstantConditions
        return mActiveCursor.getShort(column);
    }

    @Override
    public int getInt(final int column) {
        //noinspection ConstantConditions
        return mActiveCursor.getInt(column);
    }

    @Override
    public long getLong(final int column) {
        //noinspection ConstantConditions
        return mActiveCursor.getLong(column);
    }

    @Override
    public float getFloat(final int column) {
        //noinspection ConstantConditions
        return mActiveCursor.getFloat(column);
    }

    @Override
    public double getDouble(final int column) {
        //noinspection ConstantConditions
        return mActiveCursor.getDouble(column);
    }

    @Override
    public boolean isNull(final int column) {
        //noinspection ConstantConditions
        return mActiveCursor.isNull(column);
    }

    /**
     * Implement re-query; this invalidate our existing cursors, update the position,
     * and call the superclass.
     *
     * @return {@code true} if the move and the requery was successful
     */
    @Override
    @CallSuper
    public boolean requery() {
        clearCursors();
        mPseudoCount = null;

        return onMove(getPosition(), getPosition()) && super.requery();
    }

    /**
     * Close this cursor and all related cursors.
     */
    @Override
    @CallSuper
    public void close() {
        super.close();
        clearCursors();
    }

    /**
     * Handle a position change. Manage cursor based on new position.
     *
     * @return {@code true} if the move was successful
     */
    @Override
    public boolean onMove(final int oldPosition,
                          final int newPosition) {
        if (newPosition < 0 || newPosition >= getCount()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_PSEUDO_CURSOR) {
                Log.d(TAG, "ENTER|onMove|illegal position"
                           + "|newPosition=" + newPosition
                           + "|getCount()=" + getCount());
            }
            return false;
        }

        // Get the id we use for the cursor at the new position
        Integer cursorId = newPosition / PAGE_SIZE;
        // Determine the actual start position
        int cursorStartPos = cursorId * PAGE_SIZE;

        // Synchronize cursor adjustments!
        synchronized (this) {
            if (mCursors.get(cursorId) == null) {
                // Create a new cursor
                mCursors.put(cursorId, mBooklistBuilder.getOffsetCursor(cursorStartPos, PAGE_SIZE));

                // Add this cursor id to the 'top' of the MRU list.
                mMruListPos = (mMruListPos + 1) % MRU_LIST_SIZE;
                mMruList[mMruListPos] = cursorId;

                // Build a list of stale cursors to purge
                Collection<Integer> toPurge = new ArrayList<>();
                for (int i = 0; i < mCursors.size(); i++) {
                    Integer key = mCursors.keyAt(i);
                    // If it is more than 3 'pages' from the current position, it's a candidate
                    if (Math.abs(key) > PAGES_AWAY_FOR_PURGE) {
                        // Must not be in the MRU list
                        if (!checkMru(key)) {
                            toPurge.add(key);
                        }
                    }
                }
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_PSEUDO_CURSOR) {
                    Log.d(TAG, "purging cursors: " + toPurge);
                }
                // Purge them
                for (Integer i : toPurge) {
                    Cursor c = mCursors.get(i);
                    if (c != null) {
                        c.close();
                        mCursors.remove(i);
                    }
                }

            } else {
                // Bring to top of MRU list, if present.
                // It may not be in the MRU list if it was
                // preserved because it was in the window
                int oldPos = -1;
                for (int i = 0; i < MRU_LIST_SIZE; i++) {
                    if (mMruList[i] == cursorId) {
                        if (oldPos >= 0) {
                            throw new IllegalStateException("Cursor appears twice in MRU list");
                        }
                        oldPos = i;
                    }
                }

                if (oldPos < 0) {
                    // Not in MRU; just add it to the top
                    mMruListPos = (mMruListPos + 1) % MRU_LIST_SIZE;
                    mMruList[mMruListPos] = cursorId;

                } else {

                    if (oldPos <= mMruListPos) {
                        // Just shuffle intervening items down
                        int nextPosition = oldPos;
                        int currentPosition;
                        while (nextPosition < mMruListPos) {
                            currentPosition = nextPosition++;
                            mMruList[currentPosition] = mMruList[nextPosition];
                        }
                    } else {
                        // Need to shuffle intervening items 'down' with a wrap;
                        // this code would actually work for the above case, but it's slower.
                        // Not sure it really matters.
                        int nextPosition = oldPos;
                        int currentPosition;
                        // (Only really need '%' for case where oldPos<=listPos.)
                        int rowsToMove = (MRU_LIST_SIZE - (oldPos - mMruListPos)) % MRU_LIST_SIZE;
                        while (rowsToMove-- > 0) {
                            currentPosition = nextPosition;
                            nextPosition = (nextPosition + 1) % MRU_LIST_SIZE;
                            mMruList[currentPosition] = mMruList[nextPosition];
                        }
                    }
                    mMruList[mMruListPos] = cursorId;
                }
            }

            // Set as the active cursor
            mActiveCursor = mCursors.get(cursorId);
            Objects.requireNonNull(mActiveCursor, "onMove"
                                                  + "|cursorId=" + cursorId
                                                  + "|mActiveCursor is NULL");

            // and finally set its position correctly
            return mActiveCursor.moveToPosition(newPosition - cursorStartPos);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistCursor{"
               + "mBooklistBuilder=" + mBooklistBuilder
               + ", mPseudoCount=" + mPseudoCount
               + ", mMruList=" + Arrays.toString(mMruList)
               + ", mMruListPos=" + mMruListPos
               + ", " + super.toString()
               + '}';
    }
}
