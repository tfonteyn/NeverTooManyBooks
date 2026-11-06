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

import android.content.ContentValues;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.TableInfo;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookDaoHelperTest
        extends BaseDBTest {

    private static final String TAG = "BookDaoHelperTest";

    private TableInfo tableInfo;

    @BeforeEach
    protected void setup()
            throws Exception {
        super.setup();

        tableInfo = ServiceLocator.getInstance().getDb().getTableInfo(DBDefinitions.TBL_BOOKS);
    }

    @Test
    void contentValues() {
        final Context context = createContext();

        final List<Locale> userLocales = List.of(Locale.US);

        final Book book = new Book();
        book.setId(1);
        book.setDescription("Six million years ago");
        book.setPages(516);
        book.setLanguage("eng");
        book.setFirstPublicationDate("2008-04-17");
        book.setPublicationDate("2009-06-02");

        final Author author0 = Author.from("Alastair Reynolds");
        author0.setRole(AuthorRole.WRITER);
        final Author author1 = Author.from("Alastair Reynolds");
        book.setAuthors(List.of(author0, author1));

        book.setPublishers(List.of(Publisher.from("Ace"), Publisher.from("Gollancz")));
        book.setTags(List.of(new Tag("Audiobook"),
                             new Tag("Space"),
                             new Tag("Opera")
        ));

        book.setIdentifiers(List.of(new Identifier.Value(Identifier.SID_GOODREADS,
                                                         "18306114")));

        final BookDaoHelper bookDaoHelper = new BookDaoHelper(tableInfo, userLocales);
        final ContentValues cv = bookDaoHelper.process(context, book, false);

        assertEquals(5, cv.size());
        assertTrue(cv.containsKey(DBKey.DESCRIPTION));
        assertTrue(cv.containsKey(DBKey.PAGES));
        assertTrue(cv.containsKey(DBKey.LANGUAGE));
        assertTrue(cv.containsKey(DBKey.FIRST_PUBLICATION_DATE));
        assertTrue(cv.containsKey(DBKey.PUBLICATION_DATE));
    }

    /** US English book, price in $. */
    @Test
    void preprocessPrices01() {
        final List<Locale> userLocales = List.of(Locale.US);
        final MoneyParser parser = new MoneyParser(userLocales.get(0), userLocales);

        final Book book = new Book();
        book.setLanguage("eng");
        book.setPriceListed(new Money(new BigDecimal("1.23"),
                                      Currency.getInstance(MoneyParser.USD)));

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, userLocales);
        bdh.processPrices(book, parser);
        dump(book);

        assertPriceListed(book, "1.23", MoneyParser.USD, parser);
    }

    /** US English book, price set, currency not set. */
    @Test
    void preprocessPrices02() {
        final List<Locale> userLocales = List.of(Locale.US);
        final MoneyParser parser = new MoneyParser(userLocales.get(0), userLocales);

        final Book book = new Book();
        book.setLanguage("eng");
        book.setPriceListed(new Money(BigDecimal.ZERO, null));
        book.setPricePaid(new Money(new BigDecimal("456.789"), null));

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, userLocales);
        bdh.processPrices(book, parser);
        dump(book);

        assertPriceListed(book, "0", null, parser);
        assertPricePaid(book, "456.789", null, parser);
    }

    @Test
    void preprocessPrices03() {
        final List<Locale> userLocales = List.of(Locale.FRANCE);
        final MoneyParser parser = new MoneyParser(userLocales.get(0), userLocales);

        final Book book = new Book();
        book.setLanguage("fra");
        // BigDecimal as an invalid string
        book.putString(DBKey.PRICE_LISTED, "A lot of");
        // Currency as a valid String
        book.putString(DBKey.PRICE_LISTED_CURRENCY, MoneyParser.EUR);

        // BigDecimal as a valid String
        book.putString(DBKey.PRICE_PAID, "");
        // Currency not present
        book.remove(DBKey.PRICE_PAID_CURRENCY);

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, userLocales);
        bdh.processPrices(book, parser);
        dump(book);

        // "A lot of" is correct as preprocessPrices should NOT change illegal values.
        // Explicitly using 'get' to bypass type conversions
        //noinspection deprecation
        final Object actual = book.getRawData().get(DBKey.PRICE_LISTED);
        assertNotNull(actual);
        assertInstanceOf(String.class, actual);
        assertEquals("A lot of", actual);
        assertEquals(MoneyParser.EUR, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        assertPricePaid(book, "0", null, parser);
    }

    @Test
    void preprocessPrices04() {
        final List<Locale> userLocales = List.of(Locale.FRANCE);
        final MoneyParser moneyParser = new MoneyParser(userLocales.get(0), userLocales);

        final Book book = new Book();
        book.setLanguage("eng");
        final Optional<Money> money = moneyParser.parse("EUR 45");
        assertTrue(money.isPresent());
        book.setPriceListed(money.get());

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, userLocales);
        bdh.processPrices(book, moneyParser);
        dump(book);

        assertPriceListed(book, "45", MoneyParser.EUR, moneyParser);
    }

    @Test
    void preprocessExternalIdsForInsert() {
        final List<Locale> userLocales = List.of(Locale.US);

        final Book book = new Book();
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

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, userLocales);
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
    void preprocessExternalIdsForUpdate() {
        final List<Locale> userLocales = List.of(Locale.US);

        final Book book = new Book();
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

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, userLocales);
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

    /** Domain: text, default "". */
    @Test
    void preprocessNullsAndBlanksForInsert() {
        final List<Locale> userLocales = List.of(Locale.US);
        final MoneyParser moneyParser = new MoneyParser(userLocales.get(0), userLocales);

        final Book book = new Book();
        book.put(DBKey.DATE_ACQUIRED, "2020-01-14");
        book.put(DBKey.READ_START__DATE, "");
        book.put(DBKey.READ_END__DATE, null);

        book.setPriceListed(new Money(new BigDecimal("12.34"), null));
        book.setPricePaid(new Money(BigDecimal.ZERO, null));

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, userLocales);
        bdh.processNullsAndBlanks(book, true);

        assertEquals("2020-01-14", book.getString(DBKey.DATE_ACQUIRED, null));

        // text, default "". Storing an empty string is allowed.
        assertEquals("", book.getString(DBKey.READ_START__DATE, null));

        // text, default "". A null is removed.
        assertFalse(book.contains(DBKey.READ_END__DATE));

        assertPriceListed(book, "12.34", null, moneyParser);
        assertPricePaid(book, BigDecimal.ZERO, null, moneyParser);
    }

    @Test
    void preprocessNullsAndBlanksForUpdate() {
        final List<Locale> userLocales = List.of(Locale.US);
        final MoneyParser moneyParser = new MoneyParser(userLocales.get(0), userLocales);

        final Book book = new Book();
        book.put(DBKey.DATE_ACQUIRED, "2020-01-14");
        book.put(DBKey.READ_START__DATE, "");
        book.put(DBKey.READ_END__DATE, null);

        book.putBigDecimal(DBKey.PRICE_LISTED, new BigDecimal("12.34"));
        book.putBigDecimal(DBKey.PRICE_PAID, BigDecimal.ZERO);

        final BookDaoHelper bdh = new BookDaoHelper(tableInfo, userLocales);
        bdh.processNullsAndBlanks(book, false);

        assertEquals("2020-01-14", book.getString(DBKey.DATE_ACQUIRED, null));

        // text, default "". Storing an empty string is allowed.
        assertEquals("", book.getString(DBKey.READ_START__DATE, null));

        // text, default "". A null is replaced by the default
        assertEquals("", book.getString(DBKey.READ_END__DATE, null));

        assertPriceListed(book, "12.34", null, moneyParser);
        assertEquals(0, BigDecimal.ZERO.compareTo(
                book.getBigDecimal(DBKey.PRICE_PAID, moneyParser.getRealNumberParser())));
    }

    private void dump(@NonNull final DataManager data) {
        for (final String key : data.keySet()) {
            //noinspection deprecation
            final Object value = data.getRawData().get(key);
            Log.d(TAG, key + "=" + value);
        }
    }
}