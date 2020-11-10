/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.AuthorWorksFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.BookAsWork;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

public class AuthorWorksModel
        extends ViewModel
        implements ResultIntent {

    /** Log tag. */
    private static final String TAG = "AuthorWorksModel";
    private final ArrayList<AuthorWork> mWorkList = new ArrayList<>();
    /** Database Access. */
    private DAO mDb;
    /** Author is set in {@link #init}. */
    private Author mAuthor;
    /** Initial Bookshelf is set in {@link #init}. */
    private Bookshelf mBookshelf;
    /** Initially we get toc entries and books. */
    private boolean mWithTocEntries = true;
    /** Initially we get toc entries and books. */
    private boolean mWithBooks = true;
    /** Show all shelves, or only the initially selected shelf. */
    private boolean mAllBookshelves;

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultData = new Intent();

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * {@link BookViewModel#BKEY_BOOK_DELETED}
     */
    @NonNull
    @Override
    public Intent getResultIntent() {
        return mResultData;
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {

        if (mDb == null) {
            mDb = new DAO(TAG);

            final long authorId = args.getLong(DBDefinitions.KEY_PK_ID, 0);
            SanityCheck.requirePositiveValue(authorId, "authorId");
            mAuthor = Objects.requireNonNull(mDb.getAuthor(authorId), String.valueOf(authorId));

            final long bookshelfId = args.getLong(DBDefinitions.KEY_FK_BOOKSHELF,
                                                  Bookshelf.ALL_BOOKS);
            mBookshelf = Bookshelf.getBookshelf(context, mDb, bookshelfId, Bookshelf.ALL_BOOKS);
            mAllBookshelves = mBookshelf.getId() == Bookshelf.ALL_BOOKS;

            mWithTocEntries = args.getBoolean(AuthorWorksFragment.BKEY_WITH_TOC, mWithTocEntries);
            mWithBooks = args.getBoolean(AuthorWorksFragment.BKEY_WITH_BOOKS, mWithBooks);
            reloadWorkList();
        }
    }

    public void reloadWorkList(final boolean withTocEntries,
                               final boolean withBooks) {
        mWithTocEntries = withTocEntries;
        mWithBooks = withBooks;
        reloadWorkList();
    }

    public void reloadWorkList() {
        mWorkList.clear();
        final long bookshelfId = mAllBookshelves ? Bookshelf.ALL_BOOKS : mBookshelf.getId();
        mWorkList.addAll(mDb.getAuthorWorks(mAuthor, bookshelfId, mWithTocEntries, mWithBooks));
    }

    public long getBookshelfId() {
        return mBookshelf.getId();
    }

    public boolean isAllBookshelves() {
        return mAllBookshelves;
    }

    public void setAllBookshelves(final boolean all) {
        mAllBookshelves = all;
    }

    @NonNull
    public ArrayList<AuthorWork> getWorks() {
        return mWorkList;
    }

    @NonNull
    public ArrayList<Long> getBookIds(@NonNull final TocEntry tocEntry) {
        return mDb.getBookIdsByTocEntry(tocEntry.getId());
    }

    /**
     * Delete the given AuthorWork.
     *
     * @param context Current context
     * @param work    to delete
     *
     * @return {@code true} if a row was deleted
     */
    public boolean delete(@NonNull final Context context,
                          @NonNull final AuthorWork work) {
        final boolean success;
        if (work instanceof TocEntry) {
            success = mDb.deleteTocEntry(context, work.getId());

        } else if (work instanceof BookAsWork) {
            success = mDb.deleteBook(context, work.getId());

        } else {
            throw new IllegalArgumentException(String.valueOf(work));
        }

        if (success) {
            work.setId(0);
            mWorkList.remove(work);
            mResultData.putExtra(BookViewModel.BKEY_BOOK_DELETED, true);
        }
        return success;
    }

    /**
     * Screen title consists of the author name + the number of entries shown.
     *
     * @param context Current context
     *
     * @return title
     */
    @NonNull
    public String getScreenTitle(@NonNull final Context context) {
        return context.getString(R.string.name_hash_nr,
                                 mAuthor.getLabel(context), getWorks().size());
    }

    /**
     * Screen subtitle will show the bookshelf name (or nothing if all-shelves).
     *
     * @return subtitle
     */
    @Nullable
    public String getScreenSubtitle() {
        if (mAllBookshelves) {
            return null;
        } else {
            return "" + mBookshelf.getName();
        }
    }
}
