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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithFixableId;
import com.hardbacknutter.nevertoomanybooks.entities.SelectableEntity;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Holds the {@link Book} and whether it's dirty or not + some direct support functions.
 */
public class BookViewModel
        extends ResultDataModel {

    private static final String TAG = "BookViewModel";

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
                final Bundle bookData = args.getBundle(UniqueId.BKEY_BOOK_DATA);
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

    public void addFieldsFromBundle(@NonNull final Context context,
                                    @Nullable final Bundle args) {

        // Add any fields the book does not have yet (do not overwrite existing ones).
        if (args != null) {
            final Bundle rawData = args.getBundle(UniqueId.BKEY_BOOK_DATA);
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

    private void ensureBookshelf(@NonNull final Context context) {
        final ArrayList<Bookshelf> list =
                mBook.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        if (list.isEmpty()) {
            list.add(Bookshelf.getPreferredBookshelf(context, mDb, false));
            mBook.putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, list);
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
        mDb.deleteLoan(mBook.getId());

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
        putResultData(UniqueId.BKEY_BOOK_DELETED, true);
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
    public boolean isSingleUsage(final Context context,
                                 final Series series,
                                 final Locale bookLocale) {
        final long nrOfReferences = mDb.countBooksInSeries(context, series, bookLocale);
        return nrOfReferences <= (mBook.isNew() ? 0 : 1);
    }

    /**
     * Toggle the read-status for this book.
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    public boolean toggleRead() {
        putResultData(UniqueId.BKEY_BOOK_MODIFIED, true);
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
     * @return {@code true} on success
     */
    public boolean saveBook(@NonNull final Context context) {
        if (mBook.isNew()) {
            final long id = mDb.insertBook(context, mBook);
            if (id > 0) {
                // if the user added a cover to the new book, make it permanent
                for (int cIdx = 0; cIdx < 2; cIdx++) {
                    final String fileSpec = mBook.getString(UniqueId.BKEY_FILE_SPEC[cIdx]);
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
                // insert failed
                return false;
            }
        } else {
            if (!mDb.updateBook(context, mBook.getId(), mBook, 0)) {
                // update failed
                return false;
            }
        }

        putResultData(DBDefinitions.KEY_PK_ID, mBook.getId());
        putResultData(UniqueId.BKEY_BOOK_MODIFIED, true);
        return true;
    }

    /**
     * Gets a complete list of Bookshelves each reflecting the book being on that shelf or not.
     *
     * @return list with {@link SelectableEntity}
     */
    @NonNull
    public ArrayList<SelectableEntity> getEditableBookshelvesList() {
        final ArrayList<SelectableEntity> list = new ArrayList<>();
        // get the list of all shelves the book is currently on.
        final List<Bookshelf> currentShelves =
                mBook.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        // Loop through all bookshelves in the database and build the list for this book
        for (Bookshelf bookshelf : mDb.getBookshelves()) {
            final boolean selected = currentShelves.contains(bookshelf);
            list.add(new SelectableEntity(bookshelf, selected));
        }
        return list;
    }

    public DAO getDb() {
        return mDb;
    }

    public void refreshSeriesList(@NonNull final Context context) {
        mBook.refreshSeriesList(context, mDb);
    }

    public void refreshAuthorList(@NonNull final Context context) {
        mBook.refreshAuthorList(context, mDb);
    }

    public void prepareAuthorsAndSeries(@NonNull final Context context) {
        // Prune any duplicates.
        pruneList(context, UniqueId.BKEY_AUTHOR_ARRAY, LocaleUtils.getUserLocale(context));
        pruneList(context, UniqueId.BKEY_SERIES_ARRAY, mBook.getLocale(context));

        // No authors ? Fallback to a potential failed search result
        // which would contain whatever the user searched for.
        ArrayList<Author> list = mBook.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (list.isEmpty()) {
            String searchText = mBook.getString(UniqueId.BKEY_SEARCH_AUTHOR);
            if (!searchText.isEmpty()) {
                list.add(Author.fromString(searchText));
                mBook.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
                mBook.remove(UniqueId.BKEY_SEARCH_AUTHOR);
                mIsDirty = true;
            }
        }
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
}
