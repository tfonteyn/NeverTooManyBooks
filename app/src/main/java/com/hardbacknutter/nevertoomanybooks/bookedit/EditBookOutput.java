/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ContractOutput;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

//ENHANCE: embed a list of {book-id,modified} pairs instead of the single 'modified' flag
public final class EditBookOutput
        implements ContractOutput {

    private static final String TAG = "EditBookOutput";

    private static final String BKEY_MODIFIED = TAG + ":m";
    private static final String BKEY_LAST_BOOK_ID_PROCESSED = TAG + ":lastId";

    /** The BoB should reposition on this book. Can be {@code 0}. */
    @IntRange(from = 0)
    private long repositionToBookId;

    /** SOMETHING was modified. This normally means that BoB will need to rebuild. */
    private boolean modified;

    /**
     * If we processed a <strong>list</strong> of books
     * than this is the last book id we processed.
     * Can be {@code 0}.
     */
    @IntRange(from = 0)
    private long lastBookIdProcessed;

    /**
     * Constructor.
     */
    public EditBookOutput() {
    }

    /**
     * Constructor.
     *
     * @param modified            flag; whether ANY modifications were made
     * @param repositionToBookId  the book to which the list should reposition.
     *                            Pass in {@code 0} to skip repositioning.
     * @param lastBookIdProcessed optional, if a <strong>list</strong>> of books was
     *                            processed, this is the last book id we processed.
     *                            Pass in {@code 0} when not applicable.
     */
    public EditBookOutput(final boolean modified,
                          @IntRange(from = 0) final long repositionToBookId,
                          @IntRange(from = 0) final long lastBookIdProcessed) {
        this.modified = modified;
        this.repositionToBookId = repositionToBookId;
        this.lastBookIdProcessed = lastBookIdProcessed;
    }

    @NonNull
    public static EditBookOutput fromBundle(@NonNull final Bundle args) {
        final boolean modified = args.getBoolean(BKEY_MODIFIED, false);
        final long repositionToBookId = args.getLong(DBKey.FK_BOOK, 0);
        final long lastBookIdProcessed = args.getLong(BKEY_LAST_BOOK_ID_PROCESSED, 0);

        return new EditBookOutput(modified, repositionToBookId, lastBookIdProcessed);
    }

    @Override
    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(3);
        args.putBoolean(BKEY_MODIFIED, modified);
        if (repositionToBookId > 0) {
            args.putLong(DBKey.FK_BOOK, repositionToBookId);
        }
        if (lastBookIdProcessed > 0) {
            args.putLong(BKEY_LAST_BOOK_ID_PROCESSED, lastBookIdProcessed);
        }
        return args;
    }

    /**
     * Overwrite the current result with the new data <strong>but only if</strong> the new
     * data contains <i>more</i> information.
     *
     * @param data add/set
     */
    public void update(@NonNull final EditBookOutput data) {
        if (data.modified) {
            this.modified = true;
        }
        if (data.repositionToBookId > 0) {
            this.repositionToBookId = data.repositionToBookId;
        }
        if (data.lastBookIdProcessed > 0) {
            this.lastBookIdProcessed = data.lastBookIdProcessed;
        }
    }

    /**
     * Whether <strong>something</strong> was modified.
     *
     * @return flag
     */
    public boolean isModified() {
        return modified;
    }

    /**
     * The BoB should reposition on this book.
     * <strong>DO NOT</strong> presume this is the book which was edited!
     *
     * @return book id, or {@code 0} for none
     */
    @IntRange(from = 0)
    public long getRepositionToBookId() {
        return repositionToBookId;
    }

    /**
     * Get the last book id processed (i.e. when a <strong>list</strong> of books was done).
     *
     * @return the <strong>last</strong> book id which was processed, or {@code 0} for none
     */
    @SuppressWarnings("WeakerAccess")
    @IntRange(from = 0)
    public long getLastBookIdProcessed() {
        return lastBookIdProcessed;
    }

    @Override
    @NonNull
    public String toString() {
        return "EditBookOutput{"
               + "modified=" + modified
               + ", repositionToBookId=" + repositionToBookId
               + ", lastBookIdProcessed=" + lastBookIdProcessed
               + '}';
    }
}
