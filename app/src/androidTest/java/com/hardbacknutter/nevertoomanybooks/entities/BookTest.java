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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.util.Log;

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookDaoHelper;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BookTest
        extends BaseDBTest {

    private static final String TAG = "BookTest";

    private static final String INVALID_DEFAULT = "Invalid default";

    private Book book;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
        book = new Book();
    }

    /** US english book, price in $. */
    @Test
    public void preprocessPrices01() {
        final RealNumberParser realNumberParser = new RealNumberParser(List.of(Locale.US));

        book.putString(DBKey.LANGUAGE, "eng");
        final Money money = MoneyParser.parse(BigDecimal.valueOf(1.23d), MoneyParser.USD);
        assertNotNull(money);
        book.putMoney(DBKey.PRICE_LISTED, money);

        final BookDaoHelper bdh = new BookDaoHelper(context,
                                                    () -> serviceLocator.getCoverStorage(),
                                                    () -> serviceLocator.getReorderHelper(),
                                                    book, true);
        bdh.processPrice(DBKey.PRICE_LISTED);
        // dump(book);

        assertEquals(1.23d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals("USD", book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
    }

    /** US english book, price set, currency not set. */
    @Test
    public void preprocessPrices02() {
        final RealNumberParser realNumberParser = new RealNumberParser(List.of(Locale.US));

        book.putString(DBKey.LANGUAGE, "eng");
        final Money money = MoneyParser.parse(BigDecimal.valueOf(0d), "");
        assertNotNull(money);
        book.putMoney(DBKey.PRICE_LISTED, money);

        book.putDouble(DBKey.PRICE_PAID, 456.789d);
        // no PRICE_PAID_CURRENCY

        final BookDaoHelper bdh = new BookDaoHelper(context,
                                                    () -> serviceLocator.getCoverStorage(),
                                                    () -> serviceLocator.getReorderHelper(),
                                                    book,
                                                    true);
        bdh.processPrice(DBKey.PRICE_LISTED);
        bdh.processPrice(DBKey.PRICE_PAID);
        //dump(book);

        assertEquals(0d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertNull(book.get(DBKey.PRICE_LISTED_CURRENCY, realNumberParser));

        assertEquals(456.789d, book.getDouble(DBKey.PRICE_PAID, realNumberParser), 0);
        assertNull(book.get(DBKey.PRICE_PAID_CURRENCY, realNumberParser));
    }

    @Test
    public void preprocessPrices03() {
        final RealNumberParser realNumberParser = new RealNumberParser(List.of(Locale.FRANCE));

        book.putString(DBKey.LANGUAGE, "fra");
        // as a valid string
        book.putString(DBKey.PRICE_LISTED, "");
        book.putString(DBKey.PRICE_LISTED_CURRENCY, MoneyParser.EUR);
        // as an invalid string
        book.putString(DBKey.PRICE_PAID, "test");
        // no PRICE_PAID_CURRENCY

        final BookDaoHelper bdh = new BookDaoHelper(context,
                                                    () -> serviceLocator.getCoverStorage(),
                                                    () -> serviceLocator.getReorderHelper(),
                                                    book,
                                                    true);
        bdh.processPrice(DBKey.PRICE_LISTED);
        bdh.processPrice(DBKey.PRICE_PAID);
        //dump(book);

        assertEquals(0d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(MoneyParser.EUR, book.get(DBKey.PRICE_LISTED_CURRENCY, realNumberParser));

        // "test" is correct as preprocessPrices should NOT change illegal values.
        assertEquals("test", book.get(DBKey.PRICE_PAID, realNumberParser));
        assertNull(book.get(DBKey.PRICE_PAID_CURRENCY, realNumberParser));
    }

    @Test
    public void preprocessPrices04() {
        final RealNumberParser realNumberParser = new RealNumberParser(List.of(Locale.FRANCE));
        final MoneyParser moneyParser = new MoneyParser(Locale.FRANCE, realNumberParser);

        book.putString(DBKey.LANGUAGE, "eng");
        final Optional<Money> money = moneyParser.parse("EUR 45");
        assertTrue(money.isPresent());
        book.putMoney(DBKey.PRICE_LISTED, money.get());

        final BookDaoHelper bdh = new BookDaoHelper(context,
                                                    () -> serviceLocator.getCoverStorage(),
                                                    () -> serviceLocator.getReorderHelper(),
                                                    book,
                                                    true);
        bdh.processPrice(DBKey.PRICE_LISTED);
        //dump(book);

        assertEquals(45d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(MoneyParser.EUR, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
    }

    @Test
    public void preprocessExternalIdsForInsert() {

        // Long: valid number
        book.put(Identifier.SID_GOODREADS_BOOK, 2L);
        // Long: 0 -> should be removed
        book.put(Identifier.SID_ISFDB, 0L);
        // Long: null -> should be removed
        book.put(Identifier.SID_LAST_DODO_NL, null);
        // Long: blank string -> should be removed
        book.put(Identifier.SID_LIBRARY_THING, "");
        // Long: non-blank string -> should be removed
        book.put(Identifier.SID_STRIP_INFO, "test");


        // String: valid
        // (KEY_ISBN is the external key for Amazon)
        book.put(DBKey.BOOK_ISBN, "test");
        // blank string for a text field -> should be removed
        book.put(Identifier.SID_OPEN_LIBRARY, "");

        // Not tested: null string for a string field..

        final BookDaoHelper bdh = new BookDaoHelper(context,
                                                    () -> serviceLocator.getCoverStorage(),
                                                    () -> serviceLocator.getReorderHelper(),
                                                    book,
                                                    true);
        bdh.processExternalIds();
        dump(book);

        assertEquals("2", book.getString(Identifier.SID_GOODREADS_BOOK));
        assertFalse(book.contains(Identifier.SID_ISFDB));
        assertFalse(book.contains(Identifier.SID_LAST_DODO_NL));
        assertFalse(book.contains(Identifier.SID_LIBRARY_THING));
        assertFalse(book.contains(Identifier.SID_STRIP_INFO));

        assertEquals("test", book.getString(DBKey.BOOK_ISBN, null));
        assertFalse(book.contains(Identifier.SID_OPEN_LIBRARY));

        bdh.processNullsAndBlanks();
        dump(book);
        // should not have any effect, so same tests:
        assertEquals("2", book.getString(Identifier.SID_GOODREADS_BOOK));
        assertEquals("test", book.getString(DBKey.BOOK_ISBN, null));
    }

    @Test
    public void preprocessExternalIdsForUpdate() {
        final RealNumberParser realNumberParser = new RealNumberParser(List.of(Locale.US));

        // Long: valid number
        book.put(Identifier.SID_GOODREADS_BOOK, 2L);
        // Long: 0 -> should be defaulted to null
        book.put(Identifier.SID_ISFDB, 0L);
        // Long: null
        book.put(Identifier.SID_LAST_DODO_NL, null);
        // Long: blank string -> defaulted to null
        book.put(Identifier.SID_LIBRARY_THING, "");
        // Long: non-blank string -> defaulted to null
        book.put(Identifier.SID_STRIP_INFO, "test");


        // String: valid
        // (KEY_ISBN is the external key for Amazon)
        book.put(DBKey.BOOK_ISBN, "test");
        // blank string for a text field -> defaulted to null
        book.put(Identifier.SID_OPEN_LIBRARY, "");


        // Not tested: null string for a string field..


        final BookDaoHelper bdh = new BookDaoHelper(context,
                                                    () -> serviceLocator.getCoverStorage(),
                                                    () -> serviceLocator.getReorderHelper(),
                                                    book,
                                                    false);
        bdh.processExternalIds();
        dump(book);

        assertEquals("2", book.getString(Identifier.SID_GOODREADS_BOOK));
        assertNull(book.get(Identifier.SID_ISFDB, realNumberParser));
        assertNull(book.get(Identifier.SID_LAST_DODO_NL, realNumberParser));
        assertNull(book.get(Identifier.SID_LIBRARY_THING, realNumberParser));
        assertNull(book.get(Identifier.SID_STRIP_INFO, realNumberParser));

        assertEquals("test", book.getString(DBKey.BOOK_ISBN, null));
        assertNull(book.get(Identifier.SID_OPEN_LIBRARY, realNumberParser));


        bdh.processNullsAndBlanks();
        dump(book);
        // should not have any effect, so same tests:
        assertEquals("2", book.getString(Identifier.SID_GOODREADS_BOOK));
        assertNull(book.get(Identifier.SID_ISFDB, realNumberParser));
        assertNull(book.get(Identifier.SID_LAST_DODO_NL, realNumberParser));
        assertNull(book.get(Identifier.SID_LIBRARY_THING, realNumberParser));
        assertNull(book.get(Identifier.SID_STRIP_INFO, realNumberParser));

        assertEquals("test", book.getString(DBKey.BOOK_ISBN, null));
        assertNull(book.get(Identifier.SID_OPEN_LIBRARY, realNumberParser));
    }

    /**
     * If a default was changed then one or more tests in this class will be invalid.
     */
    @Test
    public void domainDefaults() {
        assertEquals(INVALID_DEFAULT, "", DBDefinitions.DOM_BOOK_DATE_ACQUIRED.getDefault());
        assertEquals(INVALID_DEFAULT, "", DBDefinitions.DOM_BOOK_DATE_READ_START.getDefault());
        assertEquals(INVALID_DEFAULT, "", DBDefinitions.DOM_BOOK_DATE_READ_END.getDefault());

        assertEquals(INVALID_DEFAULT, "0.0",
                     DBDefinitions.DOM_BOOK_PRICE_LISTED.getDefault());
    }

    /** Domain: text, default "". */
    @Test
    public void preprocessNullsAndBlanksForInsert() {
        final RealNumberParser realNumberParser = new RealNumberParser(List.of(Locale.US));

        book.put(DBKey.DATE_ACQUIRED, "2020-01-14");
        book.put(DBKey.READ_START__DATE, "");
        book.put(DBKey.READ_END__DATE, null);

        book.putDouble(DBKey.PRICE_LISTED, 12.34);
        book.putDouble(DBKey.PRICE_PAID, 0);

        final BookDaoHelper bdh = new BookDaoHelper(context,
                                                    () -> serviceLocator.getCoverStorage(),
                                                    () -> serviceLocator.getReorderHelper(),
                                                    book,
                                                    true);
        bdh.processNullsAndBlanks();

        assertEquals("2020-01-14", book.getString(DBKey.DATE_ACQUIRED, null));

        // text, default "". Storing an empty string is allowed.
        assertEquals("", book.getString(DBKey.READ_START__DATE, null));

        // text, default "". A null is removed.
        assertFalse(book.contains(DBKey.READ_END__DATE));

        assertEquals(12.34d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(0d, book.getDouble(DBKey.PRICE_PAID, realNumberParser), 0);
    }

    @Test
    public void preprocessNullsAndBlanksForUpdate() {
        final RealNumberParser realNumberParser = new RealNumberParser(List.of(Locale.US));

        book.put(DBKey.DATE_ACQUIRED, "2020-01-14");
        book.put(DBKey.READ_START__DATE, "");
        book.put(DBKey.READ_END__DATE, null);

        book.putDouble(DBKey.PRICE_LISTED, 12.34);
        book.putDouble(DBKey.PRICE_PAID, 0);

        final BookDaoHelper bdh = new BookDaoHelper(context,
                                                    () -> serviceLocator.getCoverStorage(),
                                                    () -> serviceLocator.getReorderHelper(),
                                                    book,
                                                    false);
        bdh.processNullsAndBlanks();

        assertEquals("2020-01-14", book.getString(DBKey.DATE_ACQUIRED, null));

        // text, default "". Storing an empty string is allowed.
        assertEquals("", book.getString(DBKey.READ_START__DATE, null));

        // text, default "". A null is replaced by the default
        assertEquals("", book.getString(DBKey.READ_END__DATE, null));

        assertEquals(12.34d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(0d, book.getDouble(DBKey.PRICE_PAID, realNumberParser), 0);
    }

    private void dump(@NonNull final DataManager data) {
        final RealNumberParser realNumberParser = new RealNumberParser(List.of(Locale.US));

        for (final String key : data.keySet()) {
            final Object value = data.get(key, realNumberParser);
            Log.d(TAG, key + "=" + value);
        }
    }
}
