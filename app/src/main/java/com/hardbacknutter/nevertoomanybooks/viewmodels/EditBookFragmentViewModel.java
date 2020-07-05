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

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class EditBookFragmentViewModel
        extends BookBaseFragmentViewModel {

    /**
     * Field drop down lists.
     * Lists in database so far, we cache them for performance but only load
     * them when really needed.
     */
    private List<String> mGenres;
    /** Field drop down list. */
    private List<String> mLocations;
    /** Field drop down list. */
    private List<String> mFormats;
    /** Field drop down list. */
    private List<String> mColors;
    /** Field drop down list. */
    private List<String> mLanguagesCodes;
    /** Field drop down list. */
    private List<String> mPricePaidCurrencies;
    /** Field drop down list. */
    private List<String> mListPriceCurrencies;

    /** Field drop down list. */
    private List<Bookshelf> mBookshelves;
    /** Field drop down list. */
    private List<String> mAuthorNames;
    /** Field drop down list. */
    private List<String> mPublisherNames;

    @IdRes
    private int[] mCurrentDialogFieldId;

    @IdRes
    public int[] getCurrentDialogFieldId() {
        return mCurrentDialogFieldId;
    }

    public void setCurrentDialogFieldId(@IdRes final int... currentDialogFieldId) {
        mCurrentDialogFieldId = currentDialogFieldId;
    }

    @NonNull
    public List<Bookshelf> getAllBookshelves() {
        if (mBookshelves == null) {
            mBookshelves = mDb.getBookshelves();
        }
        return mBookshelves;
    }

    @NonNull
    public List<String> getAllAuthorNames() {
        if (mAuthorNames == null) {
            mAuthorNames = mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FORMATTED);
        }
        return mAuthorNames;
    }

    /**
     * Load a publisher list.
     *
     * @return List of publishers
     */
    @NonNull
    public List<String> getAllPublishers() {
        if (mPublisherNames == null) {
            mPublisherNames = mDb.getPublisherNames();
        }
        return mPublisherNames;
    }

    /**
     * Load a language list.
     * <p>
     * Returns a unique list of all languages in the database.
     * The list is ordered by {@link DBDefinitions#KEY_UTC_LAST_UPDATED}.
     *
     * @return The list of ISO 639-2 codes
     */
    @NonNull
    public List<String> getAllLanguagesCodes() {
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
    public List<String> getAllFormats() {
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
    public List<String> getAllColors() {
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
    public List<String> getAllListPriceCurrencyCodes() {
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
    public List<String> getAllGenres() {
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
    public List<String> getAllLocations() {
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
    public List<String> getAllPricePaidCurrencyCodes() {
        if (mPricePaidCurrencies == null) {
            mPricePaidCurrencies = mDb.getCurrencyCodes(DBDefinitions.KEY_PRICE_PAID_CURRENCY);
        }
        return mPricePaidCurrencies;
    }

    /**
     * Delete an individual TocEntry.
     *
     * @param context Current context
     * @param id      to delete.
     *
     * @return {@code true} if a row was deleted
     */
    public boolean deleteTocEntry(@NonNull final Context context,
                                  final long id) {
        return mDb.deleteTocEntry(context, id);
    }
}
