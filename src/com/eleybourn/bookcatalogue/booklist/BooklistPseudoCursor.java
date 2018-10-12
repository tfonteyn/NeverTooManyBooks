/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.booklist;

import android.database.AbstractCursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.database.cursors.BooklistCursor;
import com.eleybourn.bookcatalogue.database.cursors.BooklistRowView;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map.Entry;

/**
 * Yet Another Rabbit Burrow ("YARB" -- did I invent a new acronym?). What led to this?
 *
 * 1. The call to getCount() that ListView does when it is passed a cursor added approximately 25%
 * to the total time building a book list. Given the way book lists are constructed (a flat table
 * with an index table that is ordered by ID), it was worth replacing getCount() with a local
 * version that simply returned the number of 'visible' rows in the nav table.
 *
 * 2. This worked (approx 5ms vs. 500ms for a big list), but failed if the current position was
 * saved at the end of a long list. For some reason this caused lots of 'skip_rows' messages
 * from 'Cursor', and a *very* jittery backwards scroll.
 *
 * 3. FUD: SQLite cursors seem to use memory based on the number of rows in the cursor. They doo
 * not *seem* to refer back to the database and cache a window. If true, with lots of a books and
 * a small phone memory, this would lead to problems.
 *
 * The result?
 *
 * A pseudo cursor that is made up of multiple cursors around a given position. Originally, the
 * plan was to build the surrounding cursors in a background thread, but the build time for small
 * cursors is remarkably small (approx 10ms on a 1.5GHz dual CPU).
 * So, this much simpler implementation was chosen.
 *
 * What does it do?
 *
 * getCount() is implemented as one would hope: a direct count of visible rows
 *
 * onMove(...) results in a new cursor being built when the row is not available in existing cursors.
 *
 * Cursors are kept in a hash based on their position; cursors more than 3 'windows' away from the
 * current position are eligible for purging if they are not in the Most Recently Used (MRU) list.
 * The MRU list holds 8 cursors.
 *
 * @author Philip Warner
 */
public class BooklistPseudoCursor extends AbstractCursor implements BooklistSupportProvider {
    /** Number of rows to return in each cursor. No tuning has been done to pick this number. */
    private final static int CURSOR_SIZE = 20;
    /** Size of MRU list. Not based on tuning; just set to more than 2*3+1. */
    private final static int MRU_LIST_SIZE = 8;
    /** Underlying BooklistBuilder object */
    @NonNull
    private final BooklistBuilder mBuilder;
    /** Collection of current cursors */
    @NonNull
    private final Hashtable<Integer, BooklistCursor> mCursors;
    /** MRU ring buffer of cursors */
    @NonNull
    private final int[] mMruList;
    /** Cached RowView for this cursor */
    @Nullable
    private BooklistRowView mRowView = null;
    /** The cursor to use for the last onMove() event */
    @Nullable
    private BooklistCursor mActiveCursor = null;
    /** Current MRU ring buffer position */
    private int mMruListPos = 0;
    /** Pseudo-count obtained from Builder */
    @Nullable
    private Integer mPseudoCount = null;

    /**
     * Constructor
     *
     * @param builder The BooklistBuilder that created the table to which this cursor refers
     */
    BooklistPseudoCursor(@NonNull final BooklistBuilder builder) {
        mBuilder = builder;
        mCursors = new Hashtable<>();
        mMruList = new int[MRU_LIST_SIZE];
        for (int i = 0; i < MRU_LIST_SIZE; i++)
            mMruList[i] = -1;

        Tracker.handleEvent(this, "Created " + this, Tracker.States.Running);
    }

    /**
     * Get the builder used to make this cursor.
     */
    @NonNull
    public BooklistBuilder getBuilder() {
        return mBuilder;
    }

    /**
     * Get a RowView for this cursor. Constructs one if necessary.
     */
    @NonNull
    public BooklistRowView getRowView() {
        if (mRowView == null)
            mRowView = new BooklistRowView(this, mBuilder);
        return mRowView;
    }

    /**
     * Cursor AbstractCursor method
     */
    @Override
    @NonNull
    public String[] getColumnNames() {
        return mBuilder.getListColumnNames();
    }

    /**
     * Handle a position change. Manage cursor based on new position.
     */
    @Override
    public boolean onMove(final int oldPosition, final int newPosition) {
        if (newPosition < 0 || newPosition >= getCount())
            return false;
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
                        // TODO (4.1+): Remove Sanity check for com.eleybourn.bookcatalogue.debug; should just 'break' from loop after setting oldPos
                        if (oldPos >= 0)
                            throw new RuntimeException("Cursor appears twice in MRU list");
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
                        int n = oldPos;
                        int i;
                        while (n < mMruListPos) {
                            i = n++;
                            mMruList[i] = mMruList[n];
                        }
                    } else {
                        // Need to shuffle intervening items 'down' with a wrap; this code
                        // would actually work for the above case, but it's slower. Not sure
                        // it really matters.
                        int n = oldPos; // 'next' position
                        int i; // current position
                        // Count of rows to move
                        int c = (MRU_LIST_SIZE - (oldPos - mMruListPos)) % MRU_LIST_SIZE; // Only really need '%' for case where oldPos<=listPos.
                        while (c-- > 0) {
                            i = n;
                            n = (n + 1) % MRU_LIST_SIZE;
                            mMruList[i] = mMruList[n];
                        }
                    }
                    mMruList[mMruListPos] = cursorId;
                }

            }
            // DEBUG: Remove dump of MRU list!
            //Logger.info("MRU: ");
            //for(int i = 0; i < MRU_LIST_SIZE; i++)
            //	Logger.info(mMruList[(mMruListPos+1+i)%MRU_LIST_SIZE] + " ");


            // Set the active cursor, and set its position correctly
            mActiveCursor = mCursors.get(cursorId);
            mActiveCursor.moveToPosition(newPosition - cursorStartPos);
        }
        return true;
    }

    /**
     * Remove any old cursors that can be purged.
     */
    private void purgeOldCursors() {
        // List of cursors to purge
        ArrayList<Integer> toPurge = new ArrayList<>();
        // Scan the hash
        for (Entry<Integer, BooklistCursor> cursorEntry : mCursors.entrySet()) {
            // If it is more than 3 'pages' from the current position, it's a candidate
            final Integer thisKey = cursorEntry.getKey();
            if (Math.abs(thisKey) > 3) {
                // Must not be in the MRU list
                if (!checkMru(thisKey)) {
                    toPurge.add(thisKey);
                }
            }
        }
        // Purge them
        for (Integer i : toPurge) {
            if (BuildConfig.DEBUG) {
                Logger.info("Removing cursor at " + i);
            }
            BooklistCursor c = mCursors.remove(i);
            c.close();
        }
    }

    /**
     * Check if the passed cursor ID is in the MRU list
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
     * Cursor AbstractCursor method
     */
    @Override
    public int getCount() {
        if (mPseudoCount == null) {
            mPseudoCount = mBuilder.getPseudoCount();
        }
        return mPseudoCount;
    }

    /**
     * Get the number of book records in the list
     */
    public int getBookCount() {
        return mBuilder.getBookCount();
    }

    /**
     * Get the number of unique book records in the list
     */
    public int getUniqueBookCount() {
        return mBuilder.getUniqueBookCount();
    }

    /**
     * Cursor AbstractCursor method
     */
    @Override
    public double getDouble(final int column) {
        return mActiveCursor.getDouble(column);
    }

    /**
     * Cursor AbstractCursor method
     */
    @Override
    public float getFloat(final int column) {
        return mActiveCursor.getFloat(column);
    }

    /**
     * Cursor AbstractCursor method
     */
    @Override
    public int getInt(final int column) {
        return mActiveCursor.getInt(column);
    }

    /**
     * Cursor AbstractCursor method
     */
    @Override
    public long getLong(final int column) {
        return mActiveCursor.getLong(column);
    }

    /**
     * Cursor AbstractCursor method
     */
    @Override
    public short getShort(final int column) {
        return mActiveCursor.getShort(column);
    }

    /**
     * Cursor AbstractCursor method
     */
    @Override
    public String getString(final int column) {
        return mActiveCursor.getString(column);
    }

    /**
     * Cursor AbstractCursor method
     */
    @Override
    public boolean isNull(final int column) {
        return mActiveCursor.isNull(column);
    }

    /**
     * Get the number of levels in the book list.
     */
    public int numLevels() {
        return mBuilder.numLevels();
    }

    /**
     * Implement re-query; this needs to invalidate our existing cursors and
     * call the superclass
     */
    @Override
    public boolean requery() {
        clearCursors();
        mPseudoCount = null;
        onMove(getPosition(), getPosition());

        return super.requery();
    }

    private void clearCursors() {
        for (Entry<Integer, BooklistCursor> cursorEntry : mCursors.entrySet()) {
            cursorEntry.getValue().close();
        }
        mCursors.clear();
//		if (mActiveCursor != null)
//			mActiveCursor.close();

        mActiveCursor = null;

        for (int i = 0; i < mMruList.length; i++)
            mMruList[i] = -1;
    }

    /**
     * Close this cursor and all related cursors.
     */
    @Override
    public void close() {
        Tracker.handleEvent(this, "Close " + this, Tracker.States.Enter);
        super.close();

        clearCursors();
        Tracker.handleEvent(this, "Close " + this, Tracker.States.Exit);
    }
}
