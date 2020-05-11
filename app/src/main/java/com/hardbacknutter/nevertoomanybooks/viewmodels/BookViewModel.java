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
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithFixableId;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Holds the {@link Book} and whether it's dirty or not + some direct support functions.
 */
public class BookViewModel
        extends ResultDataModel {

    /** Log tag. */
    private static final String TAG = "BookViewModel";

    /**
     * One <strong>or more</strong> books were created (or not).
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_BOOK_CREATED = TAG + ":created";

    /**
     * One <strong>or more</strong> books and/or global data (author etc) was modified (or not).
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_BOOK_MODIFIED = TAG + ":modified";

    /**
     * One <strong>or more</strong> books were deleted (or not).
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_BOOK_DELETED = TAG + ":deleted";

    private final MutableLiveData<ArrayList<Author>> mAuthorList = new MutableLiveData<>();
    private final MutableLiveData<ArrayList<Series>> mSeriesList = new MutableLiveData<>();

    /** key: fragmentTag. */
    private final Collection<String> mFragmentsWithUnfinishedEdits = new HashSet<>();
    /** Database Access. */
    private DAO mDb;
    /** Flag to indicate we're dirty. */
    private boolean mIsDirty;
    /**
     * The Book this model represents. The only time this can be {@code null}
     * is when this model is just initialized, or when the Book was deleted.
     */
    private Book mBook;

    /** Set after {@link #saveBook} has been called. */
    private boolean mIsSaved;

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Pseudo constructor.
     * <p>
     * Loads the book data upon first start.
     *
     * @param context Current context, will not get cached.
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {
        if (mDb == null) {
            mDb = new DAO(TAG);

            if (args != null) {
                // 1. Do we have a bundle?
                final Bundle bookData = args.getBundle(Book.BKEY_BOOK_DATA);
                if (bookData != null) {
                    // if we have a populated bundle, e.g. after an internet search, use that.
                    mBook = new Book();
                    mBook.putAll(bookData);
                    // a new book is always dirty
                    mIsDirty = true;

                } else {
                    // 2. Do we have an id?, e.g. user clicked on a book in a list.
                    final long bookId = args.getLong(DBDefinitions.KEY_PK_ID, 0);
                    // Either load from database or create a new 'empty' book.
                    mBook = new Book(bookId, mDb);
                }

            } else {
                // 3. No args, we want an empty new book (e.g. user wants to add one manually).
                mBook = new Book();
            }

            // If the new book is not on any Bookshelf, add the preferred/current bookshelf
            if (mBook.isNew()) {
                ensureBookshelf(context);
            }
        }
    }

    /**
     * Add the DATA validator (not field validators) validator to the book.
     * This cannot be undone during an edit session.
     */
    public void enableValidators() {
        mBook.addValidators();
    }

    /**
     * Add any fields the book does not have yet (does not overwrite existing ones).
     *
     * @param context Current context
     * @param args    to check
     */
    public void addFieldsFromBundle(@NonNull final Context context,
                                    @Nullable final Bundle args) {
        if (args != null) {
            final Bundle rawData = args.getBundle(Book.BKEY_BOOK_DATA);
            if (rawData != null) {
                for (String key : rawData.keySet()) {
                    if (!mBook.contains(key)) {
                        mBook.put(key, rawData.get(key));
                    }
                }
            }
        }

        // If the book is not on any Bookshelf, add the preferred/current bookshelf
        ensureBookshelf(context);
    }

    /**
     * Ensure the book has a bookshelf.
     *
     * @param context Current context
     */
    private void ensureBookshelf(@NonNull final Context context) {
        final ArrayList<Bookshelf> list =
                mBook.getParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY);
        if (list.isEmpty()) {
            list.add(Bookshelf.getBookshelf(context, mDb, Bookshelf.PREFERRED, Bookshelf.DEFAULT));
            mBook.putParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY, list);
        }
    }

    /**
     * Check if <strong>anything at all</strong> was changed.
     *
     * @return {@code true} if changes made
     */
    public boolean isDirty() {
        return mIsDirty;
    }

    /**
     * Set the status of our data.
     *
     * @param isDirty set to {@code true} if our data was changed.
     */
    public void setDirty(final boolean isDirty) {
        mIsDirty = isDirty;
    }

    /**
     * Get the list of fragments (their tags) which have unfinished edits.
     *
     * @return list
     */
    public Collection<String> getUnfinishedEdits() {
        return mFragmentsWithUnfinishedEdits;
    }

    /**
     * Add or remove the given fragment tag from the list of unfinished edits.
     *
     * @param tag                of fragment
     * @param hasUnfinishedEdits flag
     */
    public void setUnfinishedEdits(@NonNull final String tag,
                                   final boolean hasUnfinishedEdits) {
        if (hasUnfinishedEdits) {
            // Flag up this fragment as having unfinished edits.
            mFragmentsWithUnfinishedEdits.add(tag);
        } else {
            mFragmentsWithUnfinishedEdits.remove(tag);
        }
    }

    @NonNull
    public Book getBook() {
        return mBook;
    }

    public void loadBook(final long bookId) {
        mBook = new Book(bookId, mDb);
    }

    public void reload() {
        mBook.reload(mDb);
    }

    /**
     * Check if the book already exists in the database.
     *
     * @return {@code true} if it does
     */
    public boolean bookExists() {
        if (mBook.isNew()) {
            final String isbnStr = mBook.getString(DBDefinitions.KEY_ISBN);
            if (!isbnStr.isEmpty()) {
                final ISBN isbn = ISBN.createISBN(isbnStr);
                return mDb.getBookIdFromIsbn(isbn) > 0;
            }
        }

        return false;
    }

    /**
     * Get the current loanee.
     *
     * @return the one who shall not be mentioned, or {@code null} if none
     */
    @Nullable
    public String getLoanee() {
        return mBook.getLoanee(mDb);
    }

    /**
     * The book was returned, remove the loanee.
     */
    public void deleteLoan() {
        mBook.remove(DBDefinitions.KEY_LOANEE);
        mDb.lendBook(mBook.getId(), null);

        // don't do this for now, BoB does not display the loan field.
        //mResultData.putExtra(UniqueId.BKEY_BOOK_MODIFIED,true);
    }

    /**
     * Delete the current book.
     *
     * @param context Current context
     */
    public void deleteBook(@NonNull final Context context) {
        mDb.deleteBook(context, mBook.getId());
        putResultData(BKEY_BOOK_DELETED, true);
        mBook = null;
    }

    /**
     * Check if this book available in our library; or if it was lend out.
     *
     * @return {@code true} if the book is available for lending.
     */
    public boolean isAvailable() {
        final String loanee = getLoanee();
        return loanee == null || getLoanee().isEmpty();
    }

    /**
     * Check if the passed Author is only used by this book.
     *
     * @param context Current context
     * @param author  to check
     *
     * @return {@code true} if the Author is only used by this book
     */
    public boolean isSingleUsage(@NonNull final Context context,
                                 @NonNull final Author author) {
        final long nrOfReferences = mDb.countBooksByAuthor(context, author)
                                    + mDb.countTocEntryByAuthor(context, author);
        return nrOfReferences <= (mBook.isNew() ? 0 : 1);
    }

    /**
     * Check if the passed Series is only used by this book.
     *
     * @param context    Current context
     * @param series     to check
     * @param bookLocale Locale to use if the series has none set
     *
     * @return {@code true} if the Series is only used by this book
     */
    public boolean isSingleUsage(@NonNull final Context context,
                                 @NonNull final Series series,
                                 @NonNull final Locale bookLocale) {
        final long nrOfReferences = mDb.countBooksInSeries(context, series, bookLocale);
        return nrOfReferences <= (mBook.isNew() ? 0 : 1);
    }

    /**
     * Toggle the read-status for this book.
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    public boolean toggleRead() {
        putResultData(BKEY_BOOK_MODIFIED, true);
        return mBook.toggleRead(mDb);
    }

    @NonNull
    @Override
    public Intent getResultData() {
        // always set the *current* book, so the BoB list can reposition correctly.
        if (mBook != null) {
            putResultData(DBDefinitions.KEY_PK_ID, mBook.getId());
        }

        return super.getResultData();
    }

    /**
     * Insert/update the book into the database, store cover files, and prepare activity results.
     *
     * @param context Current context
     *
     * @throws DAO.DaoWriteException on failure
     */
    public void saveBook(@NonNull final Context context)
            throws DAO.DaoWriteException {
        if (mBook.isNew()) {
            final long id = mDb.insertBook(context, 0, mBook);
            putResultData(BKEY_BOOK_CREATED, true);

            // if the user added a cover to the new book, make it permanent
            for (int cIdx = 0; cIdx < 2; cIdx++) {
                final String fileSpec = mBook.getString(Book.BKEY_FILE_SPEC[cIdx]);
                if (!fileSpec.isEmpty()) {
                    final String uuid = mDb.getBookUuid(id);
                    if (uuid != null) {
                        final File downloadedFile = new File(fileSpec);
                        final File destination = AppDir
                                .getCoverFile(context, uuid, cIdx);
                        FileUtils.rename(downloadedFile, destination);
                    }
                }
            }
        } else {
            mDb.updateBook(context, mBook.getId(), mBook, 0);
            putResultData(BKEY_BOOK_MODIFIED, true);
        }

        putResultData(DBDefinitions.KEY_PK_ID, mBook.getId());

        mIsSaved = true;
    }

    public boolean isSaved() {
        return mIsSaved;
    }

    public void refreshSeriesList(@NonNull final Context context) {
        mBook.refreshSeriesList(context, mDb);
    }

    public void refreshAuthorList(@NonNull final Context context) {
        mBook.refreshAuthorList(context, mDb);
    }

    public void pruneAuthors(@NonNull final Context context) {
        pruneList(context, Book.BKEY_AUTHOR_ARRAY, LocaleUtils.getUserLocale(context));

        // No authors ? Fallback to a potential failed search result
        // which would contain whatever the user searched for.
        ArrayList<Author> list = mBook.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
        if (list.isEmpty()) {
            String searchText = mBook.getString(
                    BooksOnBookshelfModel.SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR);
            if (!searchText.isEmpty()) {
                list.add(Author.from(searchText));
                mBook.putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, list);
                mBook.remove(BooksOnBookshelfModel.SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR);
                mIsDirty = true;
            }
        }
    }

    public void pruneSeries(@NonNull final Context context) {
        pruneList(context, Book.BKEY_SERIES_ARRAY, mBook.getLocale(context));
    }

    private void pruneList(@NonNull final Context context,
                           @NonNull final String key,
                           @NonNull final Locale locale) {

        final ArrayList<? extends ItemWithFixableId> list = mBook.getParcelableArrayList(key);
        if (!list.isEmpty() && ItemWithFixableId.pruneList(list, context, mDb, locale, false)) {
            mBook.putParcelableArrayList(key, list);
            mIsDirty = true;
        }
    }

    public MutableLiveData<ArrayList<Author>> getAuthorList() {
        return mAuthorList;
    }

    public void updateAuthors(final ArrayList<Author> list) {
        mBook.putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, list);
        mAuthorList.setValue(list);
    }

    public MutableLiveData<ArrayList<Series>> getSeriesList() {
        return mSeriesList;
    }

    public void updateSeries(final ArrayList<Series> list) {
        mBook.putParcelableArrayList(Book.BKEY_SERIES_ARRAY, list);
        mSeriesList.setValue(list);
    }
}
