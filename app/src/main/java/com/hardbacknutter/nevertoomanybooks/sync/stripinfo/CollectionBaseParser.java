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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import java.util.ArrayList;

import org.jsoup.nodes.Element;

import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.JSoupHelper;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

// The site has a concept of "I own this book" and "I have this book in my collection"
// In my collection: these are books where the user made SOME note/flag/...
// i.e. a book the user has 'touched' and is remembered on the site
// until they remove it from the collection.
// An 'owned' books is automatically part of the collection,
// but a book in the collection can be 'owned', 'not owned', 'wanted', 'remark added', ...
//
public abstract class CollectionBaseParser
        implements CollectionParser {

    protected final long mExternalId;
    protected final long mCollectionId;
    /** Delegate common Element handling. */
    protected final JSoupHelper mJSoupHelper = new JSoupHelper();
    protected String mIdOwned;
    protected String mIdRead;
    protected String mIdWanted;
    protected String mIdLocation;
    protected String mIdNotes;
    protected String mIdDateAcquired;
    protected String mIdRating;
    protected String mIdEdition;
    protected String mIdPricePaid;
    protected String mIdAmount;
    @Nullable
    private Bookshelf mWishListBookshelf;

    /**
     * Constructor.
     *
     * @param externalId   the book id from the web site
     * @param collectionId the user specific book id from the web site
     */
    CollectionBaseParser(final long externalId,
                         final long collectionId) {
        mExternalId = externalId;
        mCollectionId = collectionId;
    }

    @Override
    public void setWishListBookshelf(@Nullable final Bookshelf wishListBookshelf) {
        mWishListBookshelf = wishListBookshelf;
    }

    void parseFlags(@NonNull final Element root,
                    @NonNull final Bundle destBundle) {

        if (mJSoupHelper.getBoolean(root, mIdOwned)) {
            destBundle.putBoolean(StripInfoSearchEngine.SiteField.OWNED, true);
        }

        if (mJSoupHelper.getBoolean(root, mIdRead)) {
            destBundle.putBoolean(DBKeys.KEY_READ, true);
        }

        if (mJSoupHelper.getBoolean(root, mIdWanted)) {
            if (mWishListBookshelf != null) {
                final ArrayList<Bookshelf> list = new ArrayList<>();
                list.add(mWishListBookshelf);
                destBundle.putParcelableArrayList(Book.BKEY_BOOKSHELF_LIST, list);
            }
        }
    }

    void parseDetails(@NonNull final Element root,
                      @NonNull final Bundle destBundle) {
        String tmpStr;
        int tmpInt;

        tmpStr = mJSoupHelper.getString(root, mIdLocation);
        if (!tmpStr.isEmpty()) {
            destBundle.putString(DBKeys.KEY_LOCATION, tmpStr);
        }

        tmpStr = mJSoupHelper.getString(root, mIdNotes);
        if (!tmpStr.isEmpty()) {
            destBundle.putString(DBKeys.KEY_PRIVATE_NOTES, tmpStr);
        }

        // Incoming value attribute is in the format "DD/MM/YYYY".
        tmpStr = mJSoupHelper.getString(root, mIdDateAcquired);
        if (tmpStr.length() == 10) {
            // we could use the date parser...
            // but that would be overkill for this website
            // ISO formatted {@code "YYYY-MM-DD"}
            tmpStr = tmpStr.substring(6, 10)
                     + '-' + tmpStr.substring(3, 5)
                     + '-' + tmpStr.substring(0, 2);
            destBundle.putString(DBKeys.KEY_DATE_ACQUIRED, tmpStr);
        }

        // '0' is an acceptable value that should be stored.
        final Double tmpDbl = mJSoupHelper.getDoubleOrNull(root, mIdPricePaid);
        if (tmpDbl != null) {
            destBundle.putDouble(DBKeys.KEY_PRICE_PAID, tmpDbl);
            destBundle.putString(DBKeys.KEY_PRICE_PAID_CURRENCY, Money.EUR);
        }

        tmpInt = mJSoupHelper.getInt(root, mIdRating);
        if (tmpInt > 0) {
            // site is int 1..10; convert to float 0.5 .. 5 (and clamp because paranoia)
            destBundle.putFloat(DBKeys.KEY_RATING,
                                MathUtils.clamp(((float) tmpInt) / 2, 0.5f, 5f));
        }

        tmpInt = mJSoupHelper.getInt(root, mIdEdition);
        if (tmpInt == 1) {
            destBundle.putInt(DBKeys.KEY_EDITION_BITMASK, Book.Edition.FIRST);
        }

        tmpInt = mJSoupHelper.getInt(root, mIdAmount);
        if (tmpInt > 0) {
            destBundle.putInt(StripInfoSearchEngine.SiteField.AMOUNT, tmpInt);
        }

        // Add as last one in case of errors thrown
        destBundle.putLong(StripInfoSearchEngine.SiteField.COLLECTION_ID, mCollectionId);
    }
}
