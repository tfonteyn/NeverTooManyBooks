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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.AuthorWorksFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

public class AuthorWorksModel
        extends ResultDataModel {

    /** Log tag. */
    private static final String TAG = "AuthorWorksModel";

    /** Database Access. */
    private DAO mDb;

    /** Author is set in {@link #init}. */
    @SuppressWarnings("NullableProblems")
    @NonNull
    private Author mAuthor;
    @Nullable
    private ArrayList<TocEntry> mTocEntries;
    /** Initially we get toc entries and books. */
    private boolean mWithTocEntries = true;
    /** Initially we get toc entries and books. */
    private boolean mWithBooks = true;

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * NEVER close this database.
     *
     * @return the DAO
     */
    public DAO getDb() {
        return mDb;
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Bundle args) {

        if (mDb == null) {
            mDb = new DAO(TAG);

            final long authorId = args.getLong(DBDefinitions.KEY_PK_ID, 0);
            if (authorId == 0) {
                throw new IllegalArgumentException("Author id must be passed in args");
            }
            mAuthor = Objects.requireNonNull(mDb.getAuthor(authorId));
            mWithTocEntries = args.getBoolean(AuthorWorksFragment.BKEY_WITH_TOC, mWithTocEntries);
            mWithBooks = args.getBoolean(AuthorWorksFragment.BKEY_WITH_BOOKS, mWithBooks);
            mTocEntries = mDb.getTocEntryByAuthor(mAuthor, mWithTocEntries, mWithBooks);
        }
    }

    public void loadTocEntries(final boolean withTocEntries,
                               final boolean withBooks) {
        mWithTocEntries = withTocEntries;
        mWithBooks = withBooks;
        mTocEntries = mDb.getTocEntryByAuthor(mAuthor, mWithTocEntries, mWithBooks);
    }

    @NonNull
    public ArrayList<TocEntry> getTocEntries() {
        Objects.requireNonNull(mTocEntries, ErrorMsg.NULL_TOC_ENTRY);
        return mTocEntries;
    }

    @NonNull
    public ArrayList<Long> getBookIds(@NonNull final TocEntry item) {
        return mDb.getBookIdsByTocEntry(item.getId());
    }

    public void delTocEntry(@NonNull final Context context,
                            @NonNull final TocEntry item) {
        Objects.requireNonNull(mTocEntries, ErrorMsg.NULL_TOC_ENTRY);
        switch (item.getType()) {
            case Toc:
                if (mDb.deleteTocEntry(item.getId()) == 1) {
                    mTocEntries.remove(item);
                }
                break;

            case Book:
                if (mDb.deleteBook(context, item.getId()) == 1) {
                    mTocEntries.remove(item);
                    putResultData(UniqueId.BKEY_BOOK_DELETED, true);
                }
                break;

            default:
                throw new UnexpectedValueException(item.getType());
        }
    }

    public String getScreenTitle(@NonNull final Context context) {
        return context.getString(R.string.name_hash_nr,
                                 mAuthor.getLabel(context),
                                 getTocEntries().size());
    }
}
