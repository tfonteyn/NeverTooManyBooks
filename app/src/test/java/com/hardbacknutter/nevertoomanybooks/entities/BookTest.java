/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks._mocks.os.BundleMock;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ESID_GOODREADS_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ESID_ISFDB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ESID_LAST_DODO_NL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ESID_LIBRARY_THING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ESID_OPEN_LIBRARY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ESID_STRIP_INFO_BE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRICE_LISTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRICE_LISTED_CURRENCY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRICE_PAID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRICE_PAID_CURRENCY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ_START;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_GOODREADS_LAST_SYNC_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BookTest
        extends Base {

    private static final String INVALID_DEFAULT = "Invalid default";

    protected Bundle mBundleHelper;

    @BeforeEach
    public void setUp() {
        super.setUp();

        mBundleHelper = BundleMock.create();

        SearchSites.registerSearchEngineClasses();
    }

    /** US english book, price in $. */
    @Test
    void preprocessPrices01() {
        setLocale(Locale.US);

        final Book book = new Book(mRawData);
        book.putString(DBDefinitions.KEY_LANGUAGE, "eng");
        book.putDouble(KEY_PRICE_LISTED, 1.23d);
        book.putString(KEY_PRICE_LISTED_CURRENCY, "$");

        final Locale bookLocale = book.getLocale(mContext);

        book.preprocessPrice(bookLocale, KEY_PRICE_LISTED, KEY_PRICE_LISTED_CURRENCY);
        // dump(book);

        assertEquals(1.23d, book.getDouble(KEY_PRICE_LISTED));
        assertEquals("$", book.getString(KEY_PRICE_LISTED_CURRENCY));
    }

    /** US english book, price set, currency not set. */
    @Test
    void preprocessPrices02() {
        setLocale(Locale.US);

        final Book book = new Book(mRawData);
        book.putString(DBDefinitions.KEY_LANGUAGE, "eng");
        book.putDouble(KEY_PRICE_LISTED, 0d);
        book.putString(KEY_PRICE_LISTED_CURRENCY, "");
        book.putDouble(KEY_PRICE_PAID, 456.789d);
        // no KEY_PRICE_PAID_CURRENCY

        final Locale bookLocale = book.getLocale(mContext);

        book.preprocessPrice(bookLocale, KEY_PRICE_LISTED,
                             KEY_PRICE_LISTED_CURRENCY);
        book.preprocessPrice(bookLocale, KEY_PRICE_PAID,
                             KEY_PRICE_PAID_CURRENCY);
        //dump(book);

        assertEquals(0d, book.getDouble(KEY_PRICE_LISTED));
        assertNotNull(book.get(KEY_PRICE_LISTED_CURRENCY));
        assertEquals(456.789d, book.getDouble(KEY_PRICE_PAID));
        assertNull(book.get(KEY_PRICE_PAID_CURRENCY));
    }

    @Test
    void preprocessPrices03() {
        setLocale(Locale.FRANCE);

        final Book book = new Book(mRawData);
        book.putString(DBDefinitions.KEY_LANGUAGE, "fra");
        book.putString(KEY_PRICE_LISTED, "");
        book.putString(KEY_PRICE_LISTED_CURRENCY, "EUR");
        book.putString(KEY_PRICE_PAID, "test");
        // no KEY_PRICE_PAID_CURRENCY

        final Locale bookLocale = book.getLocale(mContext);

        book.preprocessPrice(bookLocale, KEY_PRICE_LISTED, KEY_PRICE_LISTED_CURRENCY);
        book.preprocessPrice(bookLocale, KEY_PRICE_PAID, KEY_PRICE_PAID_CURRENCY);
        //dump(book);

        assertEquals(0d, book.getDouble(KEY_PRICE_LISTED));
        assertEquals("EUR", book.get(KEY_PRICE_LISTED_CURRENCY));
        // "test" is correct according to preprocessPrices NOT changing illegal values.
        assertEquals("test", book.get(KEY_PRICE_PAID));
        assertNull(book.get(KEY_PRICE_PAID_CURRENCY));
    }

    @Test
    void preprocessPrices04() {
        setLocale(Locale.FRANCE);

        final Book book = new Book(mRawData);
        book.putString(DBDefinitions.KEY_LANGUAGE, "eng");
        book.putString(KEY_PRICE_LISTED, "EUR 45");

        final Locale bookLocale = book.getLocale(mContext);

        book.preprocessPrice(bookLocale, KEY_PRICE_LISTED,
                             KEY_PRICE_LISTED_CURRENCY);
        //dump(book);

        assertEquals(45d, book.getDouble(KEY_PRICE_LISTED));
        assertEquals("EUR", book.get(KEY_PRICE_LISTED_CURRENCY));
    }

    @Test
    void preprocessExternalIdsForInsert() {

        final Book book = new Book(mRawData);

        // Long: valid number
        book.put(KEY_ESID_GOODREADS_BOOK, 2L);
        // Long: 0 -> should be removed
        book.put(KEY_ESID_ISFDB, 0L);
        // Long: null -> should be removed
        book.put(KEY_ESID_LAST_DODO_NL, null);
        // Long: blank string -> should be removed
        book.put(KEY_ESID_LIBRARY_THING, "");
        // Long: non-blank string -> should be removed
        book.put(KEY_ESID_STRIP_INFO_BE, "test");


        // String: valid
        // (KEY_ISBN is the external key for Amazon)
        book.put(KEY_ISBN, "test");
        // blank string for a text field -> should be removed
        book.put(KEY_ESID_OPEN_LIBRARY, "");


        // Not tested: null string for a string field..


        book.preprocessExternalIds(true);
        dump(book);

        assertEquals(2, book.getLong(KEY_ESID_GOODREADS_BOOK));
        assertFalse(book.contains(KEY_ESID_ISFDB));
        assertFalse(book.contains(KEY_ESID_LAST_DODO_NL));
        assertFalse(book.contains(KEY_ESID_LIBRARY_THING));
        assertFalse(book.contains(KEY_ESID_STRIP_INFO_BE));

        assertEquals("test", book.getString(KEY_ISBN));
        assertFalse(book.contains(KEY_ESID_OPEN_LIBRARY));


        book.preprocessNullsAndBlanks(true);
        dump(book);
        // should not have any effect, so same tests:
        assertEquals(2, book.getLong(KEY_ESID_GOODREADS_BOOK));
        assertEquals("test", book.getString(KEY_ISBN));
    }

    @Test
    void preprocessExternalIdsForUpdate() {

        final Book book = new Book(mRawData);

        // Long: valid number
        book.put(KEY_ESID_GOODREADS_BOOK, 2L);
        // Long: 0 -> should be defaulted to null
        book.put(KEY_ESID_ISFDB, 0L);
        // Long: null
        book.put(KEY_ESID_LAST_DODO_NL, null);
        // Long: blank string -> defaulted to null
        book.put(KEY_ESID_LIBRARY_THING, "");
        // Long: non-blank string -> defaulted to null
        book.put(KEY_ESID_STRIP_INFO_BE, "test");


        // String: valid
        // (KEY_ISBN is the external key for Amazon)
        book.put(KEY_ISBN, "test");
        // blank string for a text field -> defaulted to null
        book.put(KEY_ESID_OPEN_LIBRARY, "");


        // Not tested: null string for a string field..


        book.preprocessExternalIds(false);
        dump(book);

        assertEquals(2, book.getLong(KEY_ESID_GOODREADS_BOOK));
        assertNull(book.get(KEY_ESID_ISFDB));
        assertNull(book.get(KEY_ESID_LAST_DODO_NL));
        assertNull(book.get(KEY_ESID_LIBRARY_THING));
        assertNull(book.get(KEY_ESID_STRIP_INFO_BE));

        assertEquals("test", book.getString(KEY_ISBN));
        assertNull(book.get(KEY_ESID_OPEN_LIBRARY));


        book.preprocessNullsAndBlanks(false);
        dump(book);
        // should not have any effect, so same tests:
        assertEquals(2, book.getLong(KEY_ESID_GOODREADS_BOOK));
        assertNull(book.get(KEY_ESID_ISFDB));
        assertNull(book.get(KEY_ESID_LAST_DODO_NL));
        assertNull(book.get(KEY_ESID_LIBRARY_THING));
        assertNull(book.get(KEY_ESID_STRIP_INFO_BE));

        assertEquals("test", book.getString(KEY_ISBN));
        assertNull(book.get(KEY_ESID_OPEN_LIBRARY));
    }

    /**
     * If a default was changed then one or more tests in this class will be invalid.
     */
    @Test
    void domainDefaults() {
        assertEquals("", DBDefinitions.DOM_BOOK_DATE_ACQUIRED.getDefault(), INVALID_DEFAULT);
        assertEquals("", DBDefinitions.DOM_BOOK_DATE_READ_START.getDefault(), INVALID_DEFAULT);
        assertEquals("", DBDefinitions.DOM_BOOK_DATE_READ_END.getDefault(), INVALID_DEFAULT);

        assertEquals("0.0",
                     DBDefinitions.DOM_BOOK_PRICE_LISTED.getDefault(), INVALID_DEFAULT);
        assertEquals("0000-00-00",
                     DBDefinitions.DOM_UTC_LAST_SYNC_DATE_GOODREADS.getDefault(), INVALID_DEFAULT);

    }

    /** Domain: text, default "". */
    @Test
    void preprocessNullsAndBlanksForInsert() {
        final Book book = new Book(mRawData);
        book.put(KEY_DATE_ACQUIRED, "2020-01-14");
        book.put(KEY_READ_START, "");
        book.put(KEY_READ_END, null);

        book.put(KEY_UTC_GOODREADS_LAST_SYNC_DATE, null);
        book.putDouble(KEY_PRICE_LISTED, 12.34);
        book.putDouble(KEY_PRICE_PAID, 0);

        book.preprocessNullsAndBlanks(true);

        assertEquals("2020-01-14", book.getString(KEY_DATE_ACQUIRED));

        // text, default "". Storing an empty string is allowed.
        assertEquals("", book.getString(KEY_READ_START));

        // text, default "". A null is removed.
        assertFalse(book.contains(KEY_READ_END));

        // text, default "0000-00-00". A null is removed.
        assertFalse(book.contains(KEY_UTC_GOODREADS_LAST_SYNC_DATE));

        assertEquals(12.34d, book.getDouble(KEY_PRICE_LISTED));
        assertEquals(0d, book.getDouble(KEY_PRICE_PAID));
    }

    @Test
    void preprocessNullsAndBlanksForUpdate() {
        final Book book = new Book(mRawData);
        book.put(KEY_DATE_ACQUIRED, "2020-01-14");
        book.put(KEY_READ_START, "");
        book.put(KEY_READ_END, null);

        book.put(KEY_UTC_GOODREADS_LAST_SYNC_DATE, null);
        book.putDouble(KEY_PRICE_LISTED, 12.34);
        book.putDouble(KEY_PRICE_PAID, 0);

        book.preprocessNullsAndBlanks(false);

        assertEquals("2020-01-14", book.getString(KEY_DATE_ACQUIRED));

        // text, default "". Storing an empty string is allowed.
        assertEquals("", book.getString(KEY_READ_START));

        // text, default "". A null is replaced by the default
        assertEquals("", book.getString(KEY_READ_END));

        // text, default "". A null is replaced by the default
        assertEquals("0000-00-00", book.getString(KEY_UTC_GOODREADS_LAST_SYNC_DATE));

        assertEquals(12.34d, book.getDouble(KEY_PRICE_LISTED));
        assertEquals(0d, book.getDouble(KEY_PRICE_PAID));
    }

    private void dump(@NonNull final Book book) {
        for (final String key : mRawData.keySet()) {
            final Object value = book.get(key);
            System.out.println(key + "=" + value);
        }
    }
}
