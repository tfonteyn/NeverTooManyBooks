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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

@SuppressWarnings("WeakerAccess")
public class AuthorWorksViewModel
        extends ViewModel {

    /** The list of TOC/Books we're displaying. */
    private final ArrayList<AuthorWork> mWorkList = new ArrayList<>();
    /** Database Access. */
    private BookDao mBookDao;
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
    /** Set to {@code true} when ... used to report back to BoB to decide rebuilding BoB list. */
    private boolean mDataModified;

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@NonNull final Context context,
              @NonNull final Bundle args) {

        if (mBookDao == null) {
            mBookDao = ServiceLocator.getInstance().getBookDao();

            final long authorId = args.getLong(DBKey.FK_AUTHOR, 0);
            SanityCheck.requirePositiveValue(authorId, "authorId");
            mAuthor = Objects.requireNonNull(
                    ServiceLocator.getInstance().getAuthorDao().getById(authorId),
                    String.valueOf(authorId));

            final long bookshelfId = args.getLong(DBKey.FK_BOOKSHELF,
                                                  Bookshelf.ALL_BOOKS);
            mBookshelf = Bookshelf.getBookshelf(context, bookshelfId, Bookshelf.ALL_BOOKS);
            mAllBookshelves = mBookshelf.getId() == Bookshelf.ALL_BOOKS;

            mWithTocEntries = args.getBoolean(AuthorWorksFragment.BKEY_WITH_TOC, mWithTocEntries);
            mWithBooks = args.getBoolean(AuthorWorksFragment.BKEY_WITH_BOOKS, mWithBooks);
            reloadWorkList();
        }
    }

    void reloadWorkList(final boolean withTocEntries,
                        final boolean withBooks) {
        mWithTocEntries = withTocEntries;
        mWithBooks = withBooks;
        reloadWorkList();
    }

    void reloadWorkList() {
        mWorkList.clear();
        final long bookshelfId = mAllBookshelves ? Bookshelf.ALL_BOOKS : mBookshelf.getId();

        final ArrayList<AuthorWork> authorWorks =
                ServiceLocator.getInstance().getAuthorDao()
                              .getAuthorWorks(mAuthor, bookshelfId, mWithTocEntries, mWithBooks);

        mWorkList.addAll(authorWorks);
    }

    long getBookshelfId() {
        return mBookshelf.getId();
    }

    boolean isAllBookshelves() {
        return mAllBookshelves;
    }

    void setAllBookshelves(final boolean all) {
        mAllBookshelves = all;
    }

    @NonNull
    ArrayList<AuthorWork> getWorks() {
        return mWorkList;
    }

    @NonNull
    ArrayList<Long> getBookIds(@NonNull final TocEntry tocEntry) {
        return ServiceLocator.getInstance().getTocEntryDao().getBookIds(tocEntry.getId());
    }

    /**
     * Delete the given AuthorWork.
     *
     * @param context Current context
     * @param work    to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull final Context context,
                   @NonNull final AuthorWork work) {
        final boolean success;
        switch (work.getWorkType()) {
            case AuthorWork.TYPE_TOC: {
                success = ServiceLocator.getInstance().getTocEntryDao()
                                        .delete(context, (TocEntry) work);
                break;
            }
            case AuthorWork.TYPE_BOOK: {
                success = mBookDao.delete(work.getId());
                if (success) {
                    mDataModified = true;
                }
                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(work));
        }

        if (success) {
            work.setId(0);
            mWorkList.remove(work);
        }
        return success;
    }

    /**
     * Activity title consists of the author name + the number of entries shown.
     *
     * @param context Current context
     *
     * @return title
     */
    @NonNull
    String getScreenTitle(@NonNull final Context context) {
        return context.getString(R.string.name_hash_nr,
                                 mAuthor.getLabel(context), getWorks().size());
    }

    /**
     * Activity subtitle will show the bookshelf name or nothing if all-shelves.
     *
     * @param context Current context
     *
     * @return subtitle
     */
    @Nullable
    String getScreenSubtitle(@NonNull final Context context) {
        if (mAllBookshelves) {
            return null;
        } else {
            return context.getString(R.string.name_colon_value,
                                     context.getString(R.string.lbl_bookshelf),
                                     mBookshelf.getName());
        }
    }

    boolean isDataModified() {
        return mDataModified;
    }
}
