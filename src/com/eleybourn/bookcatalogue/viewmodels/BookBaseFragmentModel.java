package com.eleybourn.bookcatalogue.viewmodels;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.util.List;

import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * This is the (obvious) replacement of the homegrown BookManager in previous commits.
 * <p>
 * Used by the set of fragments that allow viewing and editing a Book.
 * <p>
 * Holds the {@link Book} and whether it's dirty or not + some direct support functions.
 */
public class BookBaseFragmentModel
        extends ViewModel {

    /** Database access. */
    private DAO mDb;

    /** Flag to indicate we're dirty. */
    private boolean mIsDirty;
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

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Conditional constructor.
     * If we already have been initialized, return silently.
     * Otherwise use the passed data to construct a Book.
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
                    long bookId = args.getLong(DBDefinitions.KEY_ID, 0);
                    // If the id is valid, load from database.
                    // or if it's 0, create a new 'empty' book. Because paranoia.
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
    }

    public Book getBook() {
        return mBook;
    }

    public void setBook(final long bookId) {
        mBook = new Book(bookId, mDb);
    }

    public void reload() {
        mBook.reload(mDb);
    }

    public void reload(final long bookId) {
        mBook.reload(mDb, bookId);
    }

    public boolean isExistingBook() {
        return mBook.getId() > 0;
    }

    public Book saveBook() {
        if (mBook.getId() == 0) {
            long id = mDb.insertBook(mBook);
            if (id > 0) {
                // if we got a cover while searching the internet, make it permanent
                if (mBook.getBoolean(UniqueId.BKEY_COVER_IMAGE)) {
                    String uuid = mDb.getBookUuid(id);
                    // get the temporary downloaded file
                    File source = StorageUtils.getTempCoverFile();
                    File destination = StorageUtils.getCoverFile(uuid);
                    // and rename it to the permanent UUID one.
                    StorageUtils.renameFile(source, destination);
                }
            }
        } else {
            mDb.updateBook(mBook.getId(), mBook, 0);
        }

        return mBook;
    }

    /**
     * @return {@code true} if the book is available for lending.
     */
    public boolean isAvailable() {
        return mDb.getLoaneeByBookId(mBook.getId()) == null;
    }

    public String getLoanee() {
        return mBook.getLoanee(mDb);
    }

    /**
     * The book was returned, remove the loanee.
     */
    public void deleteLoan() {
        mDb.deleteLoan(mBook.getId());
    }

    /**
     * Toggle the 'read' status of the book.
     *
     * @return the current/new 'read' status.
     */
    public boolean toggleRead() {
        return (mBook.setRead(mDb, !mBook.getBoolean(Book.IS_READ)));
    }

    public void refreshAuthorList() {
        mBook.refreshAuthorList(mDb);
    }

    public void refreshSeriesList() {
        mBook.refreshSeriesList(mDb);
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
     * @return The list of ISO3 codes
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
}
