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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.database.Cursor;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

/**
 * A value class containing details of a single row as used in the list-view.
 */
public class BooklistNode {

    /** The state we want a node to <strong>become</strong>. */
    public static final int NEXT_STATE_TOGGLE = 0;
    /** The state we want a node to <strong>become</strong>. */
    public static final int NEXT_STATE_EXPANDED = 1;
    /** The state we want a node to <strong>become</strong>. */
    static final int NEXT_STATE_COLLAPSED = 2;
    /** Number of columns used in {@link #getColumns(TableDefinition)}. */
    static final int COLS = 5;

    private long mRowId;
    private String mKey;
    private int mLevel;
    private boolean mIsExpanded;
    private boolean mIsVisible;

    /** The position in the {@link BooklistAdapter}. Will be calculated/set if the list changed. */
    private int mAdapterPosition = -1;

    /**
     * Constructor. Intended to be used with loops without creating new objects over and over.
     * Use {@link #from(Cursor)} to populate the current values.
     */
    BooklistNode() {
    }

    /**
     * Constructor which expects a full cursor row.
     *
     * @param cursor to read from
     */
    BooklistNode(@NonNull final Cursor cursor) {
        from(cursor);
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
               + ',' + table.dot(DBKey.KEY_BL_NODE_KEY)
               + ',' + table.dot(DBKey.KEY_BL_NODE_LEVEL)
               + ',' + table.dot(DBKey.KEY_BL_NODE_EXPANDED)
               + ',' + table.dot(DBKey.KEY_BL_NODE_VISIBLE);
    }

    /**
     * Set the node based on the given cursor row.
     * <p>
     * Allows a single node to be used in a loop without creating new objects over and over.
     *
     * @param cursor to read from
     */
    void from(@NonNull final Cursor cursor) {
        mRowId = cursor.getInt(0);
        mKey = cursor.getString(1);
        mLevel = cursor.getInt(2);
        mIsExpanded = cursor.getInt(3) != 0;
        mIsVisible = cursor.getInt(4) != 0;
    }

    public boolean isExpanded() {
        return mIsExpanded;
    }

    public void setExpanded(final boolean expanded) {
        mIsExpanded = expanded;
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public void setVisible(final boolean visible) {
        mIsVisible = visible;
    }

    long getRowId() {
        return mRowId;
    }

    @NonNull
    public String getKey() {
        return mKey;
    }

    public int getLevel() {
        return mLevel;
    }

    /**
     * Update the current state.
     *
     * @param nextState the state to set the node to
     */
    void setNextState(@NextState final int nextState) {
        switch (nextState) {
            case NEXT_STATE_COLLAPSED:
                mIsExpanded = false;
                break;

            case NEXT_STATE_EXPANDED:
                mIsExpanded = true;
                break;

            case NEXT_STATE_TOGGLE:
            default:
                mIsExpanded = !mIsExpanded;
                break;
        }
    }

    /**
     * The position in the {@link BooklistAdapter}.
     *
     * @return 0..x
     */
    public int getAdapterPosition() {
        if (mAdapterPosition < 0) {
            throw new IllegalStateException("position not set");
        }
        return mAdapterPosition;
    }

    /**
     * The position in the {@link BooklistAdapter}.
     *
     * @param position to use
     */
    void setAdapterPosition(final int position) {
        mAdapterPosition = position;
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistNode{"
               + "mRowId=" + mRowId
               + ", mKey=" + mKey
               + ", mLevel=" + mLevel
               + ", mIsExpanded=" + mIsExpanded
               + ", mIsVisible=" + mIsVisible
               + ", mAdapterPosition=" + mAdapterPosition
               + '}';
    }

    @IntDef({NEXT_STATE_EXPANDED, NEXT_STATE_COLLAPSED, NEXT_STATE_TOGGLE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface NextState {

    }
}
