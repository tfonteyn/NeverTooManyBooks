/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.viewmodels;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertomanybooks.AuthorWorksFragment;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.entities.Author;
import com.hardbacknutter.nevertomanybooks.entities.TocEntry;

public class AuthorWorksModel
        extends ViewModel {

    /** Database Access. */
    private DAO mDb;

    private boolean mAtLeastOneBookDeleted;

    @Nullable
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
     * Pseudo constructor.
     * If we already have been initialized and the incoming bookId has not changed, return silently.
     *
     * @param args Bundle with arguments
     */
    public void init(@NonNull final Bundle args) {
        long authorId = args.getLong(DBDefinitions.KEY_PK_ID, 0);
        if (mDb == null) {
            mDb = new DAO();
        }
        if (mAuthor == null || authorId != mAuthor.getId()) {
            mAuthor = mDb.getAuthor(authorId);
            if (mAuthor != null) {
                mWithTocEntries = args.getBoolean(AuthorWorksFragment.BKEY_WITH_TOC,
                                                  mWithTocEntries);
                mWithBooks = args.getBoolean(AuthorWorksFragment.BKEY_WITH_BOOKS, mWithBooks);
                mTocEntries = mDb.getTocEntryByAuthor(mAuthor, mWithTocEntries, mWithBooks);
            } else {
                throw new IllegalArgumentException("author was NULL for id=" + authorId);
            }
        }
    }

    @NonNull
    public Author getAuthor() {
        Objects.requireNonNull(mAuthor);
        return mAuthor;
    }

    public void loadTocEntries(final boolean withTocEntries,
                               final boolean withBooks) {
        mWithTocEntries = withTocEntries;
        mWithBooks = withBooks;
        //noinspection ConstantConditions
        mTocEntries = mDb.getTocEntryByAuthor(mAuthor, mWithTocEntries, mWithBooks);
    }

    @NonNull
    public ArrayList<TocEntry> getTocEntries() {
        Objects.requireNonNull(mTocEntries);
        return mTocEntries;
    }

    @NonNull
    public ArrayList<Long> getBookIds(@NonNull final TocEntry item) {
        return mDb.getBookIdsByTocEntry(item.getId());
    }

    public void delTocEntry(@NonNull final TocEntry item) {
        Objects.requireNonNull(mTocEntries);
        switch (item.getType()) {
            case Toc:
                if (mDb.deleteTocEntry(item.getId()) == 1) {
                    mTocEntries.remove(item);
                }
                break;

            case Book:
                if (mDb.deleteBook(item.getId()) == 1) {
                    mTocEntries.remove(item);
                    mAtLeastOneBookDeleted = true;
                }
                break;

            default:
                throw new IllegalArgumentException("type=" + item.getType());
        }
    }

    public boolean isAtLeastOneBookDeleted() {
        return mAtLeastOneBookDeleted;
    }
}
