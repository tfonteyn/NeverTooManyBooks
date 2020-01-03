/*
 * @Copyright 2019 HardBackNutter
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.dialogs.checklist.CheckListItem;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GoodreadsTaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Used by the set of fragments that allow viewing and editing a Book.
 * <p>
 * Holds the {@link Book} and whether it's dirty or not + some direct support functions.
 * <p>
 * Holds the fields collection.
 */
public class BookBaseFragmentModel
        extends ViewModel
        implements ActivityResultDataModel {

    /** Log tag. */
    private static final String TAG = "BookBaseFragmentModel";

    private final MutableLiveData<String> mUserMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mNeedsGoodreads = new MutableLiveData<>();
    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultData = new Intent();
    /** Database Access. */
    private DAO mDb;
    /** Flag to indicate we're dirty. */
    private boolean mIsDirty;
    /**
     * The Book this model represents. The only time this can be {@code null}
     * is when this model is just initialized, or when the Book was deleted.
     * W'll get a {@code NullPointerException} because the developer made a boo-boo
     */
    private Book mBook;

    /**
     * Field drop down lists.
     * Lists in database so far, we cache them for performance but only load
     * them when really needed.
     */
    private List<String> mGenres;
    /** Field drop down list. */
    private List<String> mLocations;
    /** Field drop down list. */
    private List<String> mPricePaidCurrencies;
    /** Field drop down list. */
    private List<String> mFormats;
    /** Field drop down list. */
    private List<String> mColors;
    /** Field drop down list. */
    private List<String> mLanguagesCodes;
    /** Field drop down list. */
    private List<String> mPublishers;
    /** Field drop down list. */
    private List<String> mListPriceCurrencies;

    /** Lazy init, always use {@link #getGoodreadsTaskListener()}. */
    private TaskListener<Integer> mGoodreadsTaskListener;

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
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@Nullable final Bundle args) {

        if (mDb == null) {
            mDb = new DAO(TAG);

            if (args != null) {
                // load the book data
                Bundle bookData = args.getBundle(UniqueId.BKEY_BOOK_DATA);
                if (bookData != null) {
                    // if we have a populated bundle, e.g. after an internet search, use that.
                    mBook = new Book(bookData);
                    // a new book is always dirty
                    mIsDirty = true;

                } else {
                    // otherwise, check if we have an id, e.g. user clicked on a book in a list.
                    long bookId = args.getLong(DBDefinitions.KEY_PK_ID, 0);
                    // Either load from database or create a new 'empty' book.
                    mBook = new Book(bookId, mDb);
                }
            } else {
                // no args, we want a new book (e.g. user wants to add one manually).
                mBook = new Book();
            }
        }
    }

    @NonNull
    @Override
    public Intent getActivityResultData() {
        // always set the *current* book, so the BoB list can reposition correctly.
        if (mBook != null) {
            mResultData.putExtra(DBDefinitions.KEY_PK_ID, mBook.getId());
        }

        return mResultData;
    }

    public void putResultData(@NonNull final Intent data) {
        mResultData.putExtras(data);
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

    @NonNull
    public DAO getDb() {
        return mDb;
    }

    @NonNull
    public Book getBook() {
        return mBook;
    }

    public void setBook(final long bookId) {
        mBook = new Book(bookId, mDb);
    }

    @NonNull
    public ArrayList<CheckListItem<Bookshelf>> getEditableBookshelvesList() {
        return mBook.getEditableBookshelvesList(mDb);
    }

    public void reload() {
        mBook.reload(mDb);
    }

    /**
     * Called after the user swipes back/forwards through the flattened booklist.
     *
     * @param bookId to load
     */
    public void moveTo(final long bookId) {
        mBook.reload(mDb, bookId);
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
        long nrOfReferences = mDb.countBooksByAuthor(context, author)
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
        long nrOfReferences = mDb.countBooksInSeries(context, series, bookLocale);
        return nrOfReferences <= (mBook.isNew() ? 0 : 1);
    }

    /**
     * Insert/update the book into the database, store cover files, and prepare activity results.
     *
     * @param context Current context
     */
    public void saveBook(@NonNull final Context context) {
        if (mBook.isNew()) {
            long id = mDb.insertBook(context, mBook);
            if (id > 0) {
                // if the user added a cover to the new book, make it permanent
                for (int cIdx = 0; cIdx < 2; cIdx++) {
                    String fileSpec = mBook.getString(UniqueId.BKEY_FILE_SPEC[cIdx]);
                    if (!fileSpec.isEmpty()) {
                        File downloadedFile = new File(fileSpec);
                        File destination = StorageUtils
                                .getCoverFileForUuid(context, mDb.getBookUuid(id), cIdx);
                        StorageUtils.renameFile(downloadedFile, destination);
                    }
                }
            }
        } else {
            mDb.updateBook(context, mBook.getId(), mBook, 0);
        }

        mResultData.putExtra(DBDefinitions.KEY_PK_ID, mBook.getId());
        mResultData.putExtra(UniqueId.BKEY_BOOK_MODIFIED, true);
    }

    /**
     * Delete the current book.
     *
     * @param context Current context
     */
    public void deleteBook(@NonNull final Context context) {
        mDb.deleteBook(context, mBook.getId());
        mResultData.putExtra(UniqueId.BKEY_BOOK_DELETED, true);
        mBook = null;
    }

    /**
     * Check if this book available in our library; or if it was lend out.
     *
     * @return {@code true} if the book is available for lending.
     */
    public boolean isAvailable() {
        String loanee = getLoanee();
        return loanee == null || getLoanee().isEmpty();
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
     * Toggle the 'read' status of the book.
     *
     * @return the current/new 'read' status.
     */
    public boolean toggleRead() {
        mResultData.putExtra(UniqueId.BKEY_BOOK_MODIFIED, true);
        return mBook.setRead(mDb, !mBook.getBoolean(DBDefinitions.KEY_READ));
    }

    @NonNull
    public Bookshelf getBookshelf(@NonNull final Context context) {
        return Bookshelf.getBookshelf(context, mDb, false);
    }

    /**
     * Load a publisher list.
     *
     * @return List of publishers
     */
    @NonNull
    public List<String> getPublishers() {
        if (mPublishers == null) {
            mPublishers = mDb.getPublisherNames();
        }
        return mPublishers;
    }

    /**
     * Load a language list.
     * <p>
     * Returns a unique list of all languages in the database.
     *
     * @return The list of ISO 639-2 codes
     */
    @NonNull
    public List<String> getLanguagesCodes() {
        if (mLanguagesCodes == null) {
            mLanguagesCodes = mDb.getLanguageCodes();
        }
        return mLanguagesCodes;
    }

    /**
     * Load a format list.
     *
     * @return List of formats
     */
    @NonNull
    public List<String> getFormats() {
        if (mFormats == null) {
            mFormats = mDb.getFormats();
        }
        return mFormats;
    }

    /**
     * Load a color list.
     *
     * @return List of colors
     */
    @NonNull
    public List<String> getColors() {
        if (mColors == null) {
            mColors = mDb.getColors();
        }
        return mColors;
    }

    /**
     * Load a currency list.
     *
     * @return List of ISO currency codes
     */
    @NonNull
    public List<String> getListPriceCurrencyCodes() {
        if (mListPriceCurrencies == null) {
            mListPriceCurrencies = mDb.getCurrencyCodes(DBDefinitions.KEY_PRICE_LISTED_CURRENCY);
        }
        return mListPriceCurrencies;
    }

    /**
     * Load a genre list.
     *
     * @return List of genres
     */
    @NonNull
    public List<String> getGenres() {
        if (mGenres == null) {
            mGenres = mDb.getGenres();
        }
        return mGenres;
    }

    /**
     * Load a location list.
     *
     * @return List of locations
     */
    @NonNull
    public List<String> getLocations() {
        if (mLocations == null) {
            mLocations = mDb.getLocations();
        }
        return mLocations;
    }

    /**
     * Load a currency list.
     *
     * @return List of ISO currency codes
     */
    @NonNull
    public List<String> getPricePaidCurrencyCodes() {
        if (mPricePaidCurrencies == null) {
            mPricePaidCurrencies = mDb.getCurrencyCodes(DBDefinitions.KEY_PRICE_PAID_CURRENCY);
        }
        return mPricePaidCurrencies;
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<String> getUserMessage() {
        return mUserMessage;
    }

    /** Observable. */
    @NonNull
    public MutableLiveData<Boolean> getNeedsGoodreads() {
        return mNeedsGoodreads;
    }

    @NonNull
    public TaskListener<Integer> getGoodreadsTaskListener() {
        if (mGoodreadsTaskListener == null) {
            mGoodreadsTaskListener = new GoodreadsTaskListener(mUserMessage, mNeedsGoodreads);
        }
        return mGoodreadsTaskListener;
    }
}
