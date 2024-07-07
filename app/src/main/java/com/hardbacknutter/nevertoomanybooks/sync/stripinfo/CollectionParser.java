/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;

import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.JSoupHelper;

import org.jsoup.nodes.Element;

/**
 * The site has a concept of "I own this book" and "I have this book in my collection"
 * In my collection: these are books where the user made SOME note/flag/...
 * i.e. a book the user has 'touched' and is remembered on the site
 * until they remove it from the collection.
 * An 'owned' books is automatically part of the collection,
 * but a book in the collection can be 'owned', 'not owned', 'wanted', 'remark added', ...
 * The 'digital' flag is in the same list, and is NOT an equivalent to the 'owned' flag.
 * <p>
 * Note: due to the form id fields containing the book and/or collection id in their name,
 * we create a new object for each book. Wasteful, but almost unavoidable.
 */
class CollectionParser {

    /** Delegate common Element handling. */
    private final JSoupHelper jSoupHelper = new JSoupHelper();
    @Nullable
    private final Bookshelf wishListBookshelf;
    @Nullable
    private final Bookshelf ownedBooksBookshelf;

    @Nullable
    private final Bookshelf digitalBooksBookshelf;

    private final RatingParser ratingParser;

    /**
     * Constructor.
     *
     * @param context         Current context
     * @param bookshelfMapper mapper for the wishlist/owned flags
     */
    @AnyThread
    CollectionParser(@NonNull final Context context,
                     @NonNull final BookshelfMapper bookshelfMapper) {

        ratingParser = new RatingParser(10);

        ownedBooksBookshelf = bookshelfMapper.getOwnedBooksBookshelf(context).orElse(null);
        digitalBooksBookshelf = bookshelfMapper.getDigitalBooksBookshelf(context).orElse(null);
        wishListBookshelf = bookshelfMapper.getWishListBookshelf(context).orElse(null);
    }

    @AnyThread
    void parseReadFlag(@NonNull final Element root,
                       @NonNull final String nameAttr,
                       @NonNull final Book book) {

        jSoupHelper.getBoolean(root, nameAttr).ifPresent(value ->
                                                                 book.putBoolean(DBKey.READ__BOOL,
                                                                                 true));
    }

    @AnyThread
    void parseOwnedFlag(@NonNull final Element root,
                        @NonNull final String nameAttr,
                        @NonNull final Book book) {
        jSoupHelper.getBoolean(root, nameAttr).ifPresent(value -> {
            book.putBoolean(DBKey.STRIP_INFO_OWNED, true);
            if (ownedBooksBookshelf != null) {
                book.add(ownedBooksBookshelf);
            }
        });
    }

    @AnyThread
    void parseDigitalFlag(@NonNull final Element root,
                          @NonNull final String nameAttr,
                          @NonNull final Book book) {
        jSoupHelper.getBoolean(root, nameAttr).ifPresent(value -> {
            book.putBoolean(DBKey.STRIP_INFO_DIGITAL, true);
            if (digitalBooksBookshelf != null) {
                book.add(digitalBooksBookshelf);
            }
        });
    }

    @AnyThread
    void parseWishListFlag(@NonNull final Element root,
                           @NonNull final String nameAttr,
                           @NonNull final Book book) {
        jSoupHelper.getBoolean(root, nameAttr).ifPresent(value -> {
            book.putBoolean(DBKey.STRIP_INFO_WANTED, true);
            if (wishListBookshelf != null) {
                book.add(wishListBookshelf);
            }
        });
    }

    @AnyThread
    void parseLocation(@NonNull final Element root,
                       @NonNull final String nameAttr,
                       @NonNull final Book book) {
        jSoupHelper.getNonEmptyString(root, nameAttr).ifPresent(
                value -> book.putString(DBKey.LOCATION, value));
    }

    @AnyThread
    void parseNotes(@NonNull final Element root,
                    @NonNull final String nameAttr,
                    @NonNull final Book book) {
        jSoupHelper.getNonEmptyString(root, nameAttr).ifPresent(
                value -> book.putString(DBKey.PERSONAL_NOTES, value));
    }

    @AnyThread
    void parseDateAcquired(@NonNull final Element root,
                           @NonNull final String nameAttr,
                           @NonNull final Book book) {
        jSoupHelper.getNonEmptyString(root, nameAttr).ifPresent(value -> {
            // Incoming value attribute is in the format "DD/MM/YYYY".
            if (value.length() == 10) {
                // we could use the date parser...
                // but that would be overkill for this website
                // ISO formatted {@code "YYYY-MM-DD"}
                book.putString(DBKey.DATE_ACQUIRED,
                               value.substring(6, 10)
                               + '-' + value.substring(3, 5)
                               + '-' + value.substring(0, 2));
            }
        });

    }

    @AnyThread
    void parsePricePaid(@NonNull final Element root,
                        @NonNull final String nameAttr,
                        @NonNull final Book book) {
        // '0' is an acceptable value that should be stored.
        jSoupHelper.getPositiveOrZeroDouble(root, nameAttr).ifPresent(
                value -> book.putMoney(DBKey.PRICE_PAID,
                                       new Money(BigDecimal.valueOf(value), Money.EURO)));
    }

    @AnyThread
    void parseRating(@NonNull final Element root,
                     @NonNull final String nameAttr,
                     @NonNull final Book book) {
        final Element element = root.getElementById(nameAttr);
        if (element != null) {
            ratingParser.parse(element.val()).ifPresent(
                    rating -> book.putFloat(DBKey.RATING, rating));
        }
    }

    @AnyThread
    void parseEdition(@NonNull final Element root,
                      @NonNull final String nameAttr,
                      @NonNull final Book book) {
        // The edition ("druk") is a text-field
        jSoupHelper.getNonEmptyString(root, nameAttr).ifPresent(value -> {
            if ("1".equals(value)) {
                book.putLong(DBKey.EDITION__BITMASK, Book.Edition.FIRST);
            } else {
                final String notes = book.getString(DBKey.PERSONAL_NOTES);
                book.putString(DBKey.PERSONAL_NOTES, value + '\n' + notes);
            }
        });
    }

    /**
     * The site supports have multiple copies of the same book.
     *
     * @param root     Element to parse
     * @param nameAttr the FORM INPUT "name" attribute
     * @param book     to store the results in
     */
    @AnyThread
    void parseAmount(@NonNull final Element root,
                     @NonNull final String nameAttr,
                     @NonNull final Book book) {
        jSoupHelper.getPositiveInt(root, nameAttr).ifPresent(
                value -> book.putInt(DBKey.STRIP_INFO_AMOUNT, value));
    }
}
