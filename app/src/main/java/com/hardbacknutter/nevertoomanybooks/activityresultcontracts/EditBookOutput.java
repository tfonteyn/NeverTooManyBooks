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
    private long repositionToBookId;

    /** SOMETHING was modified. This normally means that BoB will need to rebuild. */
    private boolean modified;

    /**
     * If we processed a <strong>list</strong> of books
     * than this is the last book id we processed.
     * Can be {@code 0}.
     */
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
    private EditBookOutput(final boolean modified,
                           final long repositionToBookId,
                           final long lastBookIdProcessed) {
        this.repositionToBookId = repositionToBookId;
        this.modified = modified;
        this.lastBookIdProcessed = lastBookIdProcessed;
    }

    /**
     * Create the result which {@link ActivityResultContract#parseResult(int, Intent)} will receive.
     *
     * @param modified           flag; whether ANY modifications were made
     * @param repositionToBookId the book to which the list should reposition.
     *                           Pass in {@code 0} to skip repositioning.
     *
     * @return Intent
     */
    @NonNull
    public static Intent createResultIntent(final boolean modified,
                                            final long repositionToBookId) {
        return new EditBookOutput(modified, repositionToBookId, 0)
                .createResultIntent();
    }

    /**
     * Create the result which {@link ActivityResultContract#parseResult(int, Intent)} will receive.
     *
     * @param dataHolder to repack
     *
     * @return intent
     */
    @NonNull
    public static Intent createResultIntent(@NonNull final DataHolder dataHolder) {

        final long firstBook = dataHolder.getLong(DBKey.FK_BOOK);
        final boolean modified = dataHolder.getBoolean(BKEY_MODIFIED);
        final long lastProcessed = dataHolder.getLong(BKEY_LAST_BOOK_ID_PROCESSED);

        return new EditBookOutput(modified, firstBook, lastProcessed)
                .createResultIntent();
    }

    /**
     * Parse the intent according to {@link #createResultIntent()}.
     *
     * @param intent to parse
     *
     * @return result
     */
    @NonNull
    public static EditBookOutput parseResult(@NonNull final Intent intent) {
        final boolean modified = intent.getBooleanExtra(BKEY_MODIFIED, false);
        final long repositionToBookId = intent.getLongExtra(DBKey.FK_BOOK, 0);
        final long lastBookIdProcessed = intent.getLongExtra(BKEY_LAST_BOOK_ID_PROCESSED, 0);

        return new EditBookOutput(modified, repositionToBookId, lastBookIdProcessed);
    }

    /**
     * Create the result which {@link ActivityResultContract#parseResult(int, Intent)} will receive.
     *
     * @return Intent
     */
    @NonNull
    public Intent createResultIntent() {
        final Intent intent = new Intent().putExtra(BKEY_MODIFIED, modified);
        if (repositionToBookId > 0) {
            intent.putExtra(DBKey.FK_BOOK, repositionToBookId);
        }
        if (lastBookIdProcessed > 0) {
            intent.putExtra(BKEY_LAST_BOOK_ID_PROCESSED, lastBookIdProcessed);
        }
        return intent;
    }

    /**
     * Overwrite the current result with the new data <strong>if</strong> the new
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
     * @return book id
     */
    public long getRepositionToBookId() {
        return repositionToBookId;
    }

    /**
     * Get the last book id processed (i.e. when a <strong>list</strong> of books was done).
     *
     * @return the <strong>last</strong> book id which was processed
     */
    @SuppressWarnings("WeakerAccess")
    public long getLastBookIdProcessed() {
        return lastBookIdProcessed;
    }
}
