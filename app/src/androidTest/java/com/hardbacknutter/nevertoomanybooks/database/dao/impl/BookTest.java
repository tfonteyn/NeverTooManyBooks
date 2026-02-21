/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.util.Log;

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.TableInfo;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser.TextNormalizer;
import com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser.TextNormalizerFactory;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("MissingJavadoc")
public class BookTest
        extends BaseDBTest {

    private static final String TAG = "BookTest";

    private static final String INVALID_DEFAULT = "Invalid default";

    private Book book;
    private TableInfo tableInfo;
    private TextNormalizer textNormalizer;

    @Before
    public void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
        book = new Book();

        tableInfo = serviceLocator.getDb().getTableInfo(DBDefinitions.TBL_BOOKS);
        textNormalizer = TextNormalizerFactory.create();
    }

    /** US English book, price in $. */
    @Test
    public void preprocessPrices01() {
        final List<Locale> userLocales = List.of(Locale.US);
        final MoneyParser moneyParser = new MoneyParser(userLocales.get(0), userLocales);

        book.setLanguage("eng");
        final Money money = MoneyParser.parse(BigDecimal.valueOf(1.23d), MoneyParser.USD);
        book.putMoney(DBKey.PRICE_LISTED, money);

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, textNormalizer, userLocales);
        bdh.processPrice(book, DBKey.PRICE_LISTED, moneyParser);
        dump(book);

        assertEquals(1.23d, book.getDouble(DBKey.PRICE_LISTED,
                                           moneyParser.getRealNumberParser()), 0);
        assertEquals(MoneyParser.USD, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
    }

    /** US English book, price set, currency not set. */
    @Test
    public void preprocessPrices02() {
        final List<Locale> userLocales = List.of(Locale.US);
        final MoneyParser moneyParser = new MoneyParser(userLocales.get(0), userLocales);

        book.setLanguage("eng");
        final Money money = MoneyParser.parse(BigDecimal.valueOf(0d), "");
        book.putMoney(DBKey.PRICE_LISTED, money);

        book.putDouble(DBKey.PRICE_PAID, 456.789d);
        // no PRICE_PAID_CURRENCY

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, textNormalizer, userLocales);
        bdh.processPrice(book, DBKey.PRICE_LISTED, moneyParser);
        bdh.processPrice(book, DBKey.PRICE_PAID, moneyParser);
        //dump(book);

        assertEquals(0d, book.getDouble(DBKey.PRICE_LISTED,
                                        moneyParser.getRealNumberParser()), 0);
        assertNull(book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        assertEquals(456.789d, book.getDouble(DBKey.PRICE_PAID,
                                              moneyParser.getRealNumberParser()), 0);
        assertNull(book.getString(DBKey.PRICE_PAID_CURRENCY, null));
    }

    @Test
    public void preprocessPrices03() {
        final List<Locale> userLocales = List.of(Locale.FRANCE);
        final MoneyParser moneyParser = new MoneyParser(userLocales.get(0), userLocales);

        book.setLanguage("fra");
        // as a valid string
        book.putString(DBKey.PRICE_LISTED, "");
        book.putString(DBKey.PRICE_LISTED_CURRENCY, MoneyParser.EUR);
        // as an invalid string
        book.putString(DBKey.PRICE_PAID, "test");
        // no PRICE_PAID_CURRENCY

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, textNormalizer, userLocales);
        bdh.processPrice(book, DBKey.PRICE_LISTED, moneyParser);
        bdh.processPrice(book, DBKey.PRICE_PAID, moneyParser);
        //dump(book);

        assertEquals(0d, book.getDouble(DBKey.PRICE_LISTED,
                                        moneyParser.getRealNumberParser()), 0);
        assertEquals(MoneyParser.EUR, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        // "test" is correct as preprocessPrices should NOT change illegal values.
        assertEquals("test", book.getRawData().get(DBKey.PRICE_PAID));
        assertNull(book.getString(DBKey.PRICE_PAID_CURRENCY, null));
    }

    @Test
    public void preprocessPrices04() {
        final List<Locale> userLocales = List.of(Locale.FRANCE);
        final MoneyParser moneyParser = new MoneyParser(userLocales.get(0), userLocales);

        book.setLanguage("eng");
        final Optional<Money> money = moneyParser.parse("EUR 45");
        assertTrue(money.isPresent());
        book.putMoney(DBKey.PRICE_LISTED, money.get());

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, textNormalizer, userLocales);
        bdh.processPrice(book, DBKey.PRICE_LISTED, moneyParser);
        dump(book);

        assertEquals(45d, book.getDouble(DBKey.PRICE_LISTED,
                                         moneyParser.getRealNumberParser()), 0);
        assertEquals(MoneyParser.EUR, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
    }

    @Test
    public void preprocessExternalIdsForInsert() {
        final List<Locale> userLocales = List.of(Locale.US);

        //noinspection DataFlowIssue
        book.setIdentifiers(List.of(
                // Long: valid number
                new Identifier.Value(Identifier.SID_GOODREADS, 2L),

                // Long: 0 -> should be removed
                new Identifier.Value(Identifier.SID_ISFDB, 0L),
                // Long: null -> should be removed
                new Identifier.Value(Identifier.SID_LAST_DODO_NL, null),
                // Long: blank string -> should be removed
                new Identifier.Value(Identifier.SID_LIBRARY_THING, ""),
                // Long: non-blank string -> should be removed
                new Identifier.Value(Identifier.SID_STRIP_INFO, "test"),

                // blank string for a text field -> should be removed
                new Identifier.Value(Identifier.SID_OPEN_LIBRARY, "")
        ));

        // Not tested: null string for a string field..

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, textNormalizer, userLocales);
        bdh.processExternalIds(book);
        dump(book);

        assertEquals("2", book.requireIdentifierValue(Identifier.SID_GOODREADS));

        assertTrue(book.getIdentifierValue(Identifier.SID_ISFDB).isEmpty());
        assertTrue(book.getIdentifierValue(Identifier.SID_LAST_DODO_NL).isEmpty());
        assertTrue(book.getIdentifierValue(Identifier.SID_LIBRARY_THING).isEmpty());
        assertTrue(book.getIdentifierValue(Identifier.SID_STRIP_INFO).isEmpty());
        assertTrue(book.getIdentifierValue(Identifier.SID_OPEN_LIBRARY).isEmpty());

        bdh.processNullsAndBlanks(book, true);
        dump(book);
        // should not have any effect, so same tests:
        assertEquals("2", book.requireIdentifierValue(Identifier.SID_GOODREADS));
    }

    @Test
    public void preprocessExternalIdsForUpdate() {
        final List<Locale> userLocales = List.of(Locale.US);

        //noinspection DataFlowIssue
        book.setIdentifiers(List.of(
                // Long: valid number
                new Identifier.Value(Identifier.SID_GOODREADS, 2L),
                // Long: 0 -> should be defaulted to null
                new Identifier.Value(Identifier.SID_ISFDB, 0L),
                // Long: null
                new Identifier.Value(Identifier.SID_LAST_DODO_NL, null),
                // Long: blank string -> defaulted to null
                new Identifier.Value(Identifier.SID_LIBRARY_THING, ""),
                // Long: non-blank string -> defaulted to null
                new Identifier.Value(Identifier.SID_STRIP_INFO, "test"),

                // blank string for a text field -> defaulted to null
                new Identifier.Value(Identifier.SID_OPEN_LIBRARY, "")
        ));

        // Not tested: null string for a string field..

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, textNormalizer, userLocales);
        bdh.processExternalIds(book);
        dump(book);

        assertEquals("2", book.requireIdentifierValue(Identifier.SID_GOODREADS));
        assertTrue(book.getIdentifierValue(Identifier.SID_ISFDB).isEmpty());
        assertTrue(book.getIdentifierValue(Identifier.SID_LAST_DODO_NL).isEmpty());
        assertTrue(book.getIdentifierValue(Identifier.SID_LIBRARY_THING).isEmpty());
        assertTrue(book.getIdentifierValue(Identifier.SID_STRIP_INFO).isEmpty());

        assertTrue(book.getIdentifierValue(Identifier.SID_OPEN_LIBRARY).isEmpty());


        bdh.processNullsAndBlanks(book, false);
        dump(book);
        // should not have any effect, so same tests:
        assertEquals("2", book.requireIdentifierValue(Identifier.SID_GOODREADS));
        assertTrue(book.getIdentifierValue(Identifier.SID_ISFDB).isEmpty());
        assertTrue(book.getIdentifierValue(Identifier.SID_LAST_DODO_NL).isEmpty());
        assertTrue(book.getIdentifierValue(Identifier.SID_LIBRARY_THING).isEmpty());
        assertTrue(book.getIdentifierValue(Identifier.SID_STRIP_INFO).isEmpty());

        assertTrue(book.getIdentifierValue(Identifier.SID_OPEN_LIBRARY).isEmpty());
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
        final List<Locale> userLocales = List.of(Locale.US);
        final MoneyParser moneyParser = new MoneyParser(userLocales.get(0), userLocales);

        book.put(DBKey.DATE_ACQUIRED, "2020-01-14");
        book.put(DBKey.READ_START__DATE, "");
        book.put(DBKey.READ_END__DATE, null);

        book.putDouble(DBKey.PRICE_LISTED, 12.34);
        book.putDouble(DBKey.PRICE_PAID, 0);

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, textNormalizer, userLocales);
        bdh.processNullsAndBlanks(book, true);

        assertEquals("2020-01-14", book.getString(DBKey.DATE_ACQUIRED, null));

        // text, default "". Storing an empty string is allowed.
        assertEquals("", book.getString(DBKey.READ_START__DATE, null));

        // text, default "". A null is removed.
        assertFalse(book.contains(DBKey.READ_END__DATE));

        assertEquals(12.34d, book.getDouble(DBKey.PRICE_LISTED,
                                            moneyParser.getRealNumberParser()), 0);
        assertEquals(0d, book.getDouble(DBKey.PRICE_PAID,
                                        moneyParser.getRealNumberParser()), 0);
    }

    @Test
    public void preprocessNullsAndBlanksForUpdate() {
        final List<Locale> userLocales = List.of(Locale.US);
        final MoneyParser moneyParser = new MoneyParser(userLocales.get(0), userLocales);

        book.put(DBKey.DATE_ACQUIRED, "2020-01-14");
        book.put(DBKey.READ_START__DATE, "");
        book.put(DBKey.READ_END__DATE, null);

        book.putDouble(DBKey.PRICE_LISTED, 12.34);
        book.putDouble(DBKey.PRICE_PAID, 0);

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, textNormalizer, userLocales);
        bdh.processNullsAndBlanks(book, false);

        assertEquals("2020-01-14", book.getString(DBKey.DATE_ACQUIRED, null));

        // text, default "". Storing an empty string is allowed.
        assertEquals("", book.getString(DBKey.READ_START__DATE, null));

        // text, default "". A null is replaced by the default
        assertEquals("", book.getString(DBKey.READ_END__DATE, null));

        assertEquals(12.34d, book.getDouble(DBKey.PRICE_LISTED,
                                            moneyParser.getRealNumberParser()), 0);
        assertEquals(0d, book.getDouble(DBKey.PRICE_PAID,
                                        moneyParser.getRealNumberParser()), 0);
    }

    private void dump(@NonNull final DataManager data) {

        for (final String key : data.keySet()) {
            final Object value = data.getRawData().get(key);
            Log.d(TAG, key + "=" + value);
        }
    }
}
