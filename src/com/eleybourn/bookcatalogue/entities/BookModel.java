package com.eleybourn.bookcatalogue.entities;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

/**
 * This is the (obvious) replacement of the homegrown BookManager in previous commits.
 *
 * Used by the set of fragments that allow viewing and editing a Book.
 *
 * Holds the {@link Book} and whether it's dirty or not.
 */
public class BookModel
        extends ViewModel {

    /** Flag to indicate we're dirty. */
    private boolean mIsDirty;
    private Book book;

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
    private List<String> mLanguages;
    /** Field drop down list. */
    private List<String> mPublishers;
    /** Field drop down list. */
    private List<String> mListPriceCurrencies;

    /**
     * Conditional constructor.
     * If we already have been initialized, return silently.
     * Otherwise use the passed data to construct a Book.
     */
    public void init(@Nullable final Bundle args,
                     @NonNull final DBA db) {
        if (book == null) {
            if (args != null) {
                // load the book data
                Bundle bookData = args.getBundle(UniqueId.BKEY_BOOK_DATA);
                if (bookData != null) {
                    // if we have a populated bundle, e.g. after an internet search, use that.
                    setBook(new Book(bookData));
                } else {
                    // otherwise, check if we have an id, e.g. user clicked on a book in a list.
                    long bookId = args.getLong(DBDefinitions.KEY_ID, 0);
                    // If the id is valid, load from database.
                    // or if it's 0, create a new 'empty' book. Because paranoia.
                    setBook(new Book(bookId, db));
                }
            } else {
                // no args, we want a new book (e.g. user wants to add one manually).
                setBook(new Book());
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
        return book;
    }

    public void setBook(@NonNull final Book book) {
        this.book = book;
    }


    /**
     * Load a publisher list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of publishers
     */
    @NonNull
    public List<String> getPublishers(@NonNull final DBA db) {
        if (mPublishers == null) {
            mPublishers = db.getPublisherNames();
        }
        return mPublishers;
    }

    /**
     * Load a language list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * Returns a unique list of all languages in the database.
     *
     * @param desiredContext the DESIRED context; e.g. get the names in a different language.
     *
     * @return The list; expanded full displayName's in the desiredContext Locale
     */
    @NonNull
    public List<String> getLanguages(@NonNull final DBA db,
                                      @NonNull final Context desiredContext) {
        if (mLanguages == null) {
            mLanguages = new ArrayList<>();
            for (String code : db.getLanguageCodes()) {
                mLanguages.add(LocaleUtils.getDisplayName(desiredContext, code));
            }
        }
        return mLanguages;
    }

    /**
     * Load a format list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of formats
     */
    @NonNull
    public List<String> getFormats(@NonNull final DBA db) {
        if (mFormats == null) {
            mFormats = db.getFormats();
        }
        return mFormats;
    }

    /**
     * Load a currency list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of ISO currency codes
     */
    @NonNull
    public List<String> getListPriceCurrencyCodes(@NonNull final DBA db) {
        if (mListPriceCurrencies == null) {
            mListPriceCurrencies = db.getCurrencyCodes(DBDefinitions.KEY_PRICE_LISTED_CURRENCY);
        }
        return mListPriceCurrencies;
    }

    /**
     * Load a genre list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of genres
     */
    @NonNull
    public List<String> getGenres(@NonNull final DBA db) {
        if (mGenres == null) {
            mGenres = db.getGenres();
        }
        return mGenres;
    }

    /**
     * Load a location list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of locations
     */
    @NonNull
    public List<String> getLocations(@NonNull final DBA db) {
        if (mLocations == null) {
            mLocations = db.getLocations();
        }
        return mLocations;
    }

    /**
     * Load a currency list; reloading this list every time a tab changes is slow.
     * So we cache it.
     *
     * @return List of ISO currency codes
     */
    @NonNull
    public List<String> getPricePaidCurrencyCodes(@NonNull final DBA db) {
        if (mPricePaidCurrencies == null) {
            mPricePaidCurrencies = db.getCurrencyCodes(
                    DBDefinitions.KEY_PRICE_PAID_CURRENCY);
        }
        return mPricePaidCurrencies;
    }

}
