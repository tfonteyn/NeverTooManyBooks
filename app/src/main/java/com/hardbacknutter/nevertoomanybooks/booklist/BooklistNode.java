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

import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.booklist.adapter.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

/**
 * A value class containing a minimal amount of details of a single row
 * as used in the list-table/view.
 */
public class BooklistNode {

    // See constructor
    public static final int NEXT_COL = 6;

    /** The row "_id" in the list-table. */
    private final long rowId;

    /**
     * The String based node key;
     * e.g. "/a=2453/s=1749" which stands for author=2453/series=1749
     * <p>
     * Completed by {@link #bookId} for actual Book nodes.
     */
    private final String key;

    @IntRange(from = 1)
    private final int level;

    @IntRange(from = 0)
    private final long bookId;

    private boolean expanded;

    private boolean visible;

    /** The position in the {@link BooklistAdapter}. Will be calculated/set if the list changed. */
    private int adapterPosition = -1;

    /**
     * Constructor.
     *
     * @param cursor to read from
     */
    BooklistNode(@NonNull final Cursor cursor) {
        rowId = cursor.getInt(0);
        level = cursor.getInt(1);
        key = cursor.getString(2);
        bookId = cursor.isNull(3) ? 0 : cursor.getInt(3);

        expanded = cursor.getInt(4) != 0;
        visible = cursor.getInt(5) != 0;
    }

    /**
     * Get a select clause (column list) with all the fields for a node.
     *
     * @param table to use
     *
     * @return CSV list of node columns
     */
    @NonNull
    static String getColumns(@NonNull final TableDefinition table) {
        return table.dot(DBKey.PK_ID)
               + ',' + table.dot(DBKey.BL_NODE_LEVEL)
               + ',' + table.dot(DBKey.BL_NODE_KEY)
               + ',' + table.dot(DBKey.FK_BOOK)

               + ',' + table.dot(DBKey.BL_NODE_EXPANDED)
               + ',' + table.dot(DBKey.BL_NODE_VISIBLE);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public boolean isVisible() {
        return visible;
    }

    void setFullyVisible() {
        expanded = true;
        visible = true;
    }

    /**
     * Get the row id (i.e. "_id") for this node in the list-table.
     *
     * @return "_id" of the row
     */
    public long getRowId() {
        return rowId;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    /**
     * Get the book id.
     *
     * @return book id; or {@code 0} if this row was not a book.
     */
    @IntRange(from = 0)
    public long getBookId() {
        return bookId;
    }

    @IntRange(from = 1)
    public int getLevel() {
        return level;
    }

    /**
     * Update the current state.
     *
     * @param nextState the state to set the node to
     */
    void setNextState(@NonNull final NextState nextState) {
        switch (nextState) {
            case Collapse:
                expanded = false;
                break;

            case Expand:
                expanded = true;
                break;

            case Toggle:
            default:
                expanded = !expanded;
                break;
        }
    }

    /**
     * The position in the {@link BooklistAdapter}.
     *
     * @return 0..x
     */
    public int getAdapterPosition() {
        if (adapterPosition < 0) {
            throw new IllegalStateException("position not set");
        }
        return adapterPosition;
    }

    /**
     * Update the node with the actual list position,
     * <strong>taking into account invisible rows</strong>.
     *
     * @param db        Database Access
     * @param listTable to use
     */
    void updateAdapterPosition(@NonNull final SynchronizedDb db,
                               @NonNull final TableDefinition listTable) {

        // We need to count the visible rows between the start of the list,
        // and the given row, to determine the ACTUAL row we want.
        final int count;
        try (SynchronizedStatement stmt = db.compileStatement(
                "SELECT COUNT(*) FROM " + listTable.getName()
                + " WHERE " + DBKey.BL_NODE_VISIBLE + "=1"
                + " AND " + DBKey.PK_ID + "<?")) {

            stmt.bindLong(1, getRowId());
            count = (int) stmt.simpleQueryForLongOrZero();
        }

        final int position;
        if (isVisible()) {
            // If the specified row is visible, then the count _is_ the position.
            position = count;
        } else {
            // otherwise it's the previous visible row == count-1 (or the top row)
            position = count > 0 ? count - 1 : 0;
        }

        adapterPosition = position;
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistNode{"
               + "rowId=" + rowId
               + ", key=" + key
               + ", bookId=" + bookId
               + ", level=" + level
               + ", expanded=" + expanded
               + ", visible=" + visible
               + ", adapterPosition=" + adapterPosition
               + '}';
    }

    /** The state we want a node to <strong>become</strong>. */
    public enum NextState {
        Expand,
        Collapse,
        Toggle
    }
}
