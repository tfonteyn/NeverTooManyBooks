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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import java.util.ArrayList;

import org.jsoup.nodes.Element;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.JSoupHelper;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

/**
 * The site has a concept of "I own this book" and "I have this book in my collection"
 * In my collection: these are books where the user made SOME note/flag/...
 * i.e. a book the user has 'touched' and is remembered on the site
 * until they remove it from the collection.
 * An 'owned' books is automatically part of the collection,
 * but a book in the collection can be 'owned', 'not owned', 'wanted', 'remark added', ...
 * <p>
 * Note: due to the form id fields containing the book and/or collection id in their name,
 * we create a new object for each book. Wasteful, but almost unavoidable.
 */
abstract class CollectionBaseParser {

    /** Delegate common Element handling. */
    private final JSoupHelper jSoupHelper = new JSoupHelper();
    @Nullable
    private final Bookshelf wishListBookshelf;
    @Nullable
    private final Bookshelf ownedBooksBookshelf;

    String idOwned;
    String idRead;
    String idWanted;
    String idLocation;
    String idNotes;
    String idDateAcquired;
    String idRating;
    String idEdition;
    String idPricePaid;
    String idAmount;

    @AnyThread
    CollectionBaseParser(@NonNull final Context context,
                         @NonNull final BookshelfMapper bookshelfmapper) {

        ownedBooksBookshelf = bookshelfmapper.getOwnedBooksBookshelf(context);
        wishListBookshelf = bookshelfmapper.getWishListBookshelf(context);
    }

    @AnyThread
    void parseFlags(@NonNull final Element root,
                    @NonNull final Bundle destBundle) {

        if (jSoupHelper.getBoolean(root, idRead)) {
            destBundle.putBoolean(DBKey.READ__BOOL, true);
        }

        final ArrayList<Bookshelf> bookshelves = new ArrayList<>();

        if (jSoupHelper.getBoolean(root, idOwned)) {
            destBundle.putBoolean(DBKey.STRIP_INFO_OWNED, true);
            if (ownedBooksBookshelf != null) {
                bookshelves.add(ownedBooksBookshelf);
            }
        }

        if (jSoupHelper.getBoolean(root, idWanted)) {
            destBundle.putBoolean(DBKey.STRIP_INFO_WANTED, true);
            if (wishListBookshelf != null) {
                bookshelves.add(wishListBookshelf);
            }
        }

        if (!bookshelves.isEmpty()) {
            destBundle.putParcelableArrayList(Book.BKEY_BOOKSHELF_LIST, bookshelves);
        }
    }

    @AnyThread
    void parseDetails(@NonNull final Element root,
                      @NonNull final Bundle destBundle) {
        String tmpStr;
        int tmpInt;

        tmpStr = jSoupHelper.getString(root, idLocation);
        if (!tmpStr.isEmpty()) {
            destBundle.putString(DBKey.LOCATION, tmpStr);
        }

        tmpStr = jSoupHelper.getString(root, idNotes);
        if (!tmpStr.isEmpty()) {
            destBundle.putString(DBKey.PERSONAL_NOTES, tmpStr);
        }

        // Incoming value attribute is in the format "DD/MM/YYYY".
        tmpStr = jSoupHelper.getString(root, idDateAcquired);
        if (tmpStr.length() == 10) {
            // we could use the date parser...
            // but that would be overkill for this website
            // ISO formatted {@code "YYYY-MM-DD"}
            tmpStr = tmpStr.substring(6, 10)
                     + '-' + tmpStr.substring(3, 5)
                     + '-' + tmpStr.substring(0, 2);
            destBundle.putString(DBKey.DATE_ACQUIRED, tmpStr);
        }

        // '0' is an acceptable value that should be stored.
        final Double tmpDbl = jSoupHelper.getDoubleOrNull(root, idPricePaid);
        if (tmpDbl != null) {
            destBundle.putDouble(DBKey.PRICE_PAID, tmpDbl);
            destBundle.putString(DBKey.PRICE_PAID_CURRENCY, Money.EUR);
        }

        tmpInt = jSoupHelper.getInt(root, idRating);
        if (tmpInt > 0) {
            // site is int 1..10; convert to float 0.5 .. 5 (and clamp because paranoia)
            destBundle.putFloat(DBKey.RATING,
                                MathUtils.clamp(((float) tmpInt) / 2, 0.5f, 5f));
        }

        tmpInt = jSoupHelper.getInt(root, idEdition);
        if (tmpInt == 1) {
            destBundle.putLong(DBKey.EDITION__BITMASK, Book.Edition.FIRST);
        }

        tmpInt = jSoupHelper.getInt(root, idAmount);
        if (tmpInt > 0) {
            destBundle.putInt(DBKey.STRIP_INFO_AMOUNT, tmpInt);
        }
    }
}
