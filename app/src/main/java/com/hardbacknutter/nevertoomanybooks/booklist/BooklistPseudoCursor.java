/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Hashtable;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.database.cursors.BooklistCursor;
import com.hardbacknutter.nevertoomanybooks.database.cursors.BooklistMappedCursorRow;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;

/**
 * Philip Warner: Yet Another Rabbit Burrow ("YARB" -- did I invent a new acronym?).
 * What led to this?
 * <p>
 * 1. The call to getCount() that ListView does when it is passed a cursor added approximately 25%
 * to the total time building a book list. Given the way book lists are constructed (a flat table
 * with an index table that is ordered by ID), it was worth replacing getCount() with a local
 * version that simply returned the number of 'visible' rows in the nav table.
 * <p>
 * 2. This worked (approx 5ms vs. 500ms for a big list), but failed if the current position was
 * saved at the end of a long list. For some reason this caused lots of 'skip_rows' messages
 * from 'Cursor', and a *very* jittery backwards scroll.
 * <p>
 * 3. FUD: SQLite cursors seem to use memory based on the number of rows in the cursor. They do
 * not *seem* to refer back to the database and cache a window. If true, with lots of a books and
 * a small phone memory, this would lead to problems.
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
 * Cursors are kept in a hash based on their position; cursors more than 3 'windows' away from the
 * current position are eligible for purging if they are not in the Most Recently Used (MRU) list.
 * The MRU list holds 8 cursors.
 */
public class BooklistPseudoCursor
        extends AbstractCursor
        implements BooklistSupportProvider {

    /** Number of 'pages' a cursor has to 'away' to be considered for purging. */
    private static final int PAGES_AWAY_FOR_PURGE = 3;
    /** Number of rows to return in each cursor. No tuning has been done to pick this number. */
    private static final int CURSOR_SIZE = 20;
    /** Size of MRU list. Not based on tuning; just set to more than 2*3+1. */
    private static final int MRU_LIST_SIZE = 8;

    /** Underlying BooklistBuilder object. */
    @NonNull
    private final BooklistBuilder mBuilder;
    /** Collection of current cursors. */
    @NonNull
    private final Hashtable<Integer, BooklistCursor> mCursors;
    /** MRU ring buffer of cursors. */
    @NonNull
    private final int[] mMruList;
    /** Cached RowView for this cursor. */
    @Nullable
    private BooklistMappedCursorRow mCursorRow;
    /** The cursor to use for the last onMove() event. */
    private BooklistCursor mActiveCursor;
    /** Current MRU ring buffer position. */
    private int mMruListPos;
    /** Pseudo-count obtained from Builder. */
    @Nullable
    private Integer mPseudoCount;

    /**
     * Constructor.
     *
     * @param builder The BooklistBuilder that created the table to which this cursor refers.
     */
    BooklistPseudoCursor(@NonNull final BooklistBuilder builder) {
        mBuilder = builder;
        mCursors = new Hashtable<>();
        mMruList = new int[MRU_LIST_SIZE];
        for (int i = 0; i < MRU_LIST_SIZE; i++) {
            mMruList[i] = -1;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
            Logger.debugExit(this, "BooklistPseudoCursor " + this);
        }
    }

    /**
     * Get a MappedCursorRow for this cursor. Constructs one if necessary.
     *
     * @return BooklistMappedCursorRow
     */
    @Override
    @NonNull
    public BooklistMappedCursorRow getCursorRow() {
        if (mCursorRow == null) {
            mCursorRow = new BooklistMappedCursorRow(this, mBuilder.getStyle());
        }
        return mCursorRow;
    }

    /**
     * Get the builder used to make this cursor.
     *
     * @return BooklistBuilder
     */
    @Override
    @NonNull
    public BooklistBuilder getBuilder() {
        return mBuilder;
    }

    /**
     * Remove any old cursors that can be purged.
     */
    private void purgeOldCursors() {
        // List of cursors to purge
        ArrayList<Integer> toPurge = new ArrayList<>();
        // Loop the hash
        for (Integer key : mCursors.keySet()) {
            // If it is more than 3 'pages' from the current position, it's a candidate
            if (Math.abs(key) > PAGES_AWAY_FOR_PURGE) {
                // Must not be in the MRU list
                if (!checkMru(key)) {
                    toPurge.add(key);
                }
            }
        }
        // Purge them
        for (Integer i : toPurge) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
                Logger.debug(this, "purgeOldCursors", "Removing cursor at " + i);
            }
            BooklistCursor c = mCursors.remove(i);
            if (c != null) {
                c.close();
            }
        }
    }

    private void clearCursors() {

        for (BooklistCursor cursor : mCursors.values()) {
            cursor.close();
        }

        mCursors.clear();
        mActiveCursor = null;

        for (int i = 0; i < mMruList.length; i++) {
            mMruList[i] = -1;
        }
    }

    /**
     * Check if the passed cursor ID is in the MRU list.
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
            mPseudoCount = mBuilder.getPseudoCount();
        }
        return mPseudoCount;
    }

    /**
     * {@link AbstractCursor} method.
     */
    @Override
    @NonNull
    public String[] getColumnNames() {
        return mBuilder.getListColumnNames();
    }

    @Override
    public String getString(final int column) {
        return mActiveCursor.getString(column);
    }

    @Override
    public short getShort(final int column) {
        return mActiveCursor.getShort(column);
    }

    @Override
    public int getInt(final int column) {
        return mActiveCursor.getInt(column);
    }

    @Override
    public long getLong(final int column) {
        return mActiveCursor.getLong(column);
    }

    @Override
    public float getFloat(final int column) {
        return mActiveCursor.getFloat(column);
    }

    @Override
    public double getDouble(final int column) {
        return mActiveCursor.getDouble(column);
    }

    @Override
    public boolean isNull(final int column) {
        return mActiveCursor.isNull(column);
    }

    /**
     * Implement re-query; this needs to invalidate our existing cursors and call the superclass.
     */
    @Override
    @CallSuper
    public boolean requery() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
            Logger.debugEnter(this, "requery ", this);
        }

        clearCursors();
        mPseudoCount = null;
        onMove(getPosition(), getPosition());

        return super.requery();
    }

    /**
     * Close this cursor and all related cursors.
     */
    @Override
    @CallSuper
    public void close() {
        //Logger.debugWithStackTrace(this,"close" , "closing cursor: " + this);
        super.close();
        clearCursors();
    }

    /**
     * Handle a position change. Manage cursor based on new position.
     */
    @Override
    public boolean onMove(final int oldPosition,
                          final int newPosition) {
        if (newPosition < 0 || newPosition >= getCount()) {
            return false;
        }
        // Get the ID we use for the cursor at the new position
        Integer cursorId = newPosition / CURSOR_SIZE;
        // Determine the actual start position
        int cursorStartPos = cursorId * CURSOR_SIZE;

        // Synchronize cursor adjustments. Just in case.
        synchronized (this) {
            if (!mCursors.containsKey(cursorId)) {
                // Get a new cursor
                mCursors.put(cursorId, mBuilder.getOffsetCursor(cursorStartPos, CURSOR_SIZE));

                // Add this cursor id to the 'top' of the MRU list.
                mMruListPos = (mMruListPos + 1) % MRU_LIST_SIZE;
                mMruList[mMruListPos] = cursorId;

                // Remove any stale cursors
                purgeOldCursors();

            } else {
                // Bring to top of MRU list, if present. It may not be in the MRU list if it was
                // preserved because it was in the window
                int oldPos = -1;
                for (int i = 0; i < MRU_LIST_SIZE; i++) {
                    if (mMruList[i] == cursorId) {
                        // TODO: (4.1+): Remove Sanity check
                        // should just 'break' from loop after setting oldPos
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
                        // Need to shuffle intervening items 'down' with a wrap; this code
                        // would actually work for the above case, but it's slower. Not sure
                        // it really matters.
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

            // Set as the active cursor, and finally set its position correctly
            mActiveCursor = mCursors.get(cursorId);
            //noinspection ConstantConditions
            mActiveCursor.moveToPosition(newPosition - cursorStartPos);

//            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
//                Logger.debugExit(this, "onMove",
//                             "cursorId=" + cursorId,
//                             "mActiveCursor=" + mActiveCursor);
//            }
        }

        Logger.debug(this, "onMove",
                     "ImageUtils.cacheChecks=" + ImageUtils.cacheChecks,
                     "ImageUtils.cacheTicks=" + (ImageUtils.cacheTicks.get() / 1_000_000),

                     "ImageUtils.fileChecks=" + ImageUtils.fileChecks,
                     "ImageUtils.fileTicks=" + (ImageUtils.fileTicks.get() / 1_000_000)
                    );
        return true;
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistPseudoCursor{"
               + ", mBuilder=" + mBuilder
               + ", mActiveCursor=" + mActiveCursor
               + ", mCursors=" + mCursors
               + ", " + super.toString()
               + '}';
    }
}
