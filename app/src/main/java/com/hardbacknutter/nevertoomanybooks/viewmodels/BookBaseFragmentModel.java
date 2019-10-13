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

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.dialogs.checklist.CheckListItem;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GoodreadsTasks;
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
        extends ViewModel {

    private final MutableLiveData<Object> mUserMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mNeedsGoodreads = new MutableLiveData<>();
    /** Database Access. */
    private DAO mDb;
    /** Flag to indicate we're dirty. */
    private boolean mIsDirty;

    /** The Book this model represents. */
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
    private List<String> mLanguagesCodes;
    /** Field drop down list. */
    private List<String> mPublishers;
    /** Field drop down list. */
    private List<String> mListPriceCurrencies;
    /** Lazy init, always use {@link #getGoodreadsTaskListener()}. */
    private TaskListener<Integer> mOnGoodreadsTaskListener;

    /** Should be set after something/anything modified a book. */
    private boolean mSomethingModified;

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
            mDb = new DAO();

            if (args != null) {
                // load the book data
                Bundle bookData = args.getBundle(UniqueId.BKEY_BOOK_DATA);
                if (bookData != null) {
                    // if we have a populated bundle, e.g. after an internet search, use that.
                    mBook = new Book(bookData);

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

    /**
     * @return {@code true} if our data was changed.
     */
    public boolean isDirty() {
        return mIsDirty;
    }

    /**
     * @param isDirty set to {@code true} if our data was changed.
     */
    public void setDirty(final boolean isDirty) {
        mIsDirty = isDirty;
        if (mIsDirty) {
            mSomethingModified = true;
        }
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

    public boolean isExistingBook() {
        return mBook.getId() > 0;
    }

    @NonNull
    public Book saveBook(@NonNull final Context context) {
        if (mBook.getId() == 0) {
            long id = mDb.insertBook(context, mBook);
            if (id > 0) {
                // if the user added a cover to the new book, make it permanent
                if (mBook.getBoolean(UniqueId.BKEY_IMAGE)) {
                    File downloadedFile = StorageUtils.getTempCoverFile();
                    File destination = StorageUtils.getCoverFileForUuid(mDb.getBookUuid(id));
                    StorageUtils.renameFile(downloadedFile, destination);
                }
            }
        } else {
            mDb.updateBook(context, mBook.getId(), mBook, 0);
        }

        mSomethingModified = true;

        return mBook;
    }

    /**
     * @return {@code true} if the book is available for lending.
     */
    public boolean isAvailable() {
        String loanee = getLoanee();
        return loanee == null || getLoanee().isEmpty();
    }

    /**
     * @return the one who shall not be mentioned.
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
        //mSomethingModified = true;
    }

    /**
     * Toggle the 'read' status of the book.
     *
     * @return the current/new 'read' status.
     */
    public boolean toggleRead() {
        return mBook.setRead(mDb, !mBook.getBoolean(Book.IS_READ));
    }

    public void refreshAuthorList(@NonNull final Context context) {
        mBook.refreshAuthorList(context, mDb);
    }

    public void refreshSeriesList(@NonNull final Context context) {
        mBook.refreshSeriesList(context, mDb);
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

    public MutableLiveData<Object> getUserMessage() {
        return mUserMessage;
    }

    public MutableLiveData<Boolean> getNeedsGoodreads() {
        return mNeedsGoodreads;
    }

    public TaskListener<Integer> getGoodreadsTaskListener() {
        if (mOnGoodreadsTaskListener == null) {
            mOnGoodreadsTaskListener = new TaskListener<Integer>() {

                @Override
                public void onTaskFinished(@NonNull final TaskFinishedMessage<Integer> message) {
                    switch (message.status) {
                        case Success:
                        case Failed: {
                            String msg = GoodreadsTasks.handleResult(App.getLocalizedAppContext(),
                                                                     message);
                            if (msg != null) {
                                mUserMessage.setValue(msg);
                            } else {
                                // Need authorization
                                mNeedsGoodreads.setValue(true);
                            }
                            break;
                        }
                        case Cancelled: {
                            mUserMessage.setValue(R.string.progress_end_cancelled);
                            break;
                        }
                    }
                }

                @Override
                public void onTaskProgress(@NonNull final TaskProgressMessage message) {
                    if (message.values != null && message.values.length > 0) {
                        mUserMessage.setValue(message.values[0]);
                    }
                }
            };
        }
        return mOnGoodreadsTaskListener;
    }

    /**
     * Check if *any* book which was displayed during the lifetime of this model, was modified.
     *
     * @return {@code true} if *any* book was modified.
     */
    public boolean isModified() {
        return mSomethingModified;
    }
}
