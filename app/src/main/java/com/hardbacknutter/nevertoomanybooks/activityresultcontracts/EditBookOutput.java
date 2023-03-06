/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public final class EditBookOutput {

    private static final String TAG = "EditBookOutput";

    public static final String BKEY_MODIFIED = TAG + ":m";

    public static final String BKEY_LAST_BOOK_ID_PROCESSED = TAG + ":lastId";

    /** The BoB should reposition on this book. Can be {@code 0}. */
    private final long repositionToBookId;

    /** SOMETHING was modified. This normally means that BoB will need to rebuild. */
    private final boolean modified;

    /**
     * If we processed a <strong>list</strong>> of books
     * than this is the last book id we processed.
     */
    private final long lastBookIdProcessed;

    private EditBookOutput(final long repositionToBookId,
                           final boolean modified,
                           final long lastBookIdProcessed) {
        this.repositionToBookId = repositionToBookId;
        this.modified = modified;
        this.lastBookIdProcessed = lastBookIdProcessed;
    }

    /**
     * Create the result which {@link ActivityResultContract#parseResult(int, Intent)} will receive.
     *
     * @param repositionToBookId  the book to which the list should reposition.
     *                            Pass in {@code 0} to skip repositioning.
     * @param modified            flag; whether ANY modifications were made
     * @param lastBookIdProcessed optional, if a <strong>list</strong>> of books was
     *                            processed, this is the last book id we processed.
     *                            Pass in {@code 0} when not applicable.
     *
     * @return Intent
     */
    @NonNull
    public static Intent createResult(final long repositionToBookId,
                                      final boolean modified,
                                      final long lastBookIdProcessed) {
        final Intent intent = new Intent().putExtra(DBKey.FK_BOOK, repositionToBookId)
                                          .putExtra(BKEY_MODIFIED, modified);
        if (repositionToBookId > 0) {
            intent.putExtra(BKEY_LAST_BOOK_ID_PROCESSED, lastBookIdProcessed);
        }
        return intent;
    }

    @NonNull
    public static Intent createResult(final long repositionToBookId,
                                      final boolean modified) {
        return createResult(repositionToBookId, modified, 0);
    }

    /**
     * Repack the result after processing a list of books.
     * <p>
     * See {@link com.hardbacknutter.nevertoomanybooks.search.SearchBookUpdatesViewModel}
     * as to why we need to do this.
     *
     * @param dataHolder to repack
     *
     * @return intent
     */
    @NonNull
    public static Intent createResult(@NonNull final DataHolder dataHolder) {

        final long firstBook = dataHolder.getLong(DBKey.FK_BOOK);
        final boolean modified = dataHolder.getBoolean(BKEY_MODIFIED);
        final long lastProcessed = dataHolder.getLong(BKEY_LAST_BOOK_ID_PROCESSED);

        return createResult(firstBook, modified, lastProcessed);
    }

    /**
     * Parse the intent according to {@link #createResult(long, boolean, long)}
     * and {@link #createResult(DataHolder)}.
     *
     * @param intent to parse
     *
     * @return result
     */
    @NonNull
    public static EditBookOutput parseResult(@NonNull final Intent intent) {
        final long repositionToBookId = intent.getLongExtra(DBKey.FK_BOOK, 0);
        final boolean modified = intent.getBooleanExtra(BKEY_MODIFIED, false);
        final long lastBookIdProcessed = intent.getLongExtra(BKEY_LAST_BOOK_ID_PROCESSED, 0);

        return new EditBookOutput(repositionToBookId, modified, lastBookIdProcessed);
    }

    /**
     * Create the result which {@link ActivityResultContract#parseResult(int, Intent)} will receive.
     *
     * @return Intent
     */
    @NonNull
    public Intent createResult() {
        return createResult(repositionToBookId, modified, lastBookIdProcessed);
    }

    /**
     * The BoB should reposition on this book.
     * <strong>DO NOT</strong> presume this is the book which was edited!
     *
     * @return book id
     */
    public long getRepositionToBookId() {
        return repositionToBookId;
    }

    /**
     * Whether <strong>something</strong> was modified.
     *
     * @return flag
     */
    public boolean isModified() {
        return modified;
    }

    public long getLastBookIdProcessed() {
        return lastBookIdProcessed;
    }
}
