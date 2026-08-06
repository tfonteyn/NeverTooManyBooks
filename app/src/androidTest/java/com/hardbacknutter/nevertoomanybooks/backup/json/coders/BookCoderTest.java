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

package com.hardbacknutter.nevertoomanybooks.backup.json.coders;

import java.math.BigDecimal;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookCoderTest
        extends BaseDBTest {

    private Book book;
    private BookCoder bookCoder;
    private IdentifierValueCoder identifierValueCoder;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
        book = new Book();
        bookCoder = new BookCoder(context, serviceLocator.getStyles().getDefault());
        identifierValueCoder = new IdentifierValueCoder();
    }

    @Test
    void putPrice() {
        book.setPriceListed(new Money(new BigDecimal("12.34"), Money.EURO));

        final JSONObject encode = bookCoder.encode(book);

        // Fetch BigDecimal/String
        assertEquals("12.34", encode.getString(DBKey.PRICE_LISTED));
        assertEquals("EUR", encode.getString(DBKey.PRICE_LISTED_CURRENCY));
    }

    @Test
    void putMoney() {
        final Money money = new Money(new BigDecimal("12.34"), Money.EURO);
        book.putMoney(DBKey.PRICE_LISTED, money);

        final JSONObject encode = bookCoder.encode(book);

        // Fetch BigDecimal/String
        assertEquals("12.34", encode.getString(DBKey.PRICE_LISTED));
        assertEquals("EUR", encode.getString(DBKey.PRICE_LISTED_CURRENCY));
    }

    @Test
    void putMoneyComponents() {
        book.putBigDecimal(DBKey.PRICE_LISTED, new BigDecimal("12.34"));
        book.putString(DBKey.PRICE_LISTED_CURRENCY, MoneyParser.EUR);

        final JSONObject encode = bookCoder.encode(book);

        // Fetch BigDecimal/String
        assertEquals("12.34", encode.getString(DBKey.PRICE_LISTED));
        assertEquals("EUR", encode.getString(DBKey.PRICE_LISTED_CURRENCY));
    }

    @Test
    void putMoneyComponentsNoCurrency() {
        book.putBigDecimal(DBKey.PRICE_LISTED, new BigDecimal("12.34"));

        final JSONObject encode = bookCoder.encode(book);

        assertTrue(encode.has(DBKey.PRICE_LISTED));
        assertFalse(encode.has(DBKey.PRICE_LISTED_CURRENCY));

        // Fetch BigDecimal/String
        assertEquals("12.34", encode.getString(DBKey.PRICE_LISTED));
    }

    /**
     * This is a non-sensical test, but is meant to test that {@link BookCoder#encode(Book)}
     * properly writes a Money String WITHOUT caring about the validity.
     */
    @Test
    void putPriceListedCustomString() {
        book.putString(DBKey.PRICE_LISTED, "a lot of money");

        final JSONObject encode = bookCoder.encode(book);

        assertTrue(encode.has(DBKey.PRICE_LISTED));
        assertFalse(encode.has(DBKey.PRICE_LISTED_CURRENCY));

        assertEquals("a lot of money", encode.getString(DBKey.PRICE_LISTED));
    }

    /**
     * This is a non-sensical test, but is meant to test that {@link BookCoder#encode(Book)}
     * properly writes a Money String WITHOUT caring about the validity.
     */
    @Test
    void putPricePaidCustomString() {
        book.putString(DBKey.PRICE_PAID, "a lot of money");

        final JSONObject encode = bookCoder.encode(book);

        assertTrue(encode.has(DBKey.PRICE_PAID));
        assertFalse(encode.has(DBKey.PRICE_PAID_CURRENCY));

        assertEquals("a lot of money", encode.getString(DBKey.PRICE_PAID));
    }

    @Test
    void putIdentifiersValid() {
        book.setIdentifierValue(Identifier.SID_GOODREADS, 1234);
        book.setIdentifierValue(Identifier.SID_OPEN_LIBRARY, "ol123");

        final JSONObject encodedBook = bookCoder.encode(book);

        final JSONArray identifiers = encodedBook.optJSONArray(Identifier.Value.BKEY_LIST);
        assertNotNull(identifiers);

        final Collection<Identifier.Value> list = identifierValueCoder.decode(identifiers);
        assertEquals(2, list.size());

        final Book decodedBook = bookCoder.decode(encodedBook);
        assertEquals("1234", decodedBook.requireIdentifierValue(Identifier.SID_GOODREADS));
        assertEquals("ol123", decodedBook.requireIdentifierValue(Identifier.SID_OPEN_LIBRARY));
    }

    @Test
    void putIdentifiersInvalid() {
        book.setIdentifierValue(Identifier.SID_GOODREADS, -1234);
        book.setIdentifierValue(Identifier.SID_OPEN_LIBRARY, "");

        final JSONObject encodedBook = bookCoder.encode(book);

        final JSONArray identifiers = encodedBook.optJSONArray(Identifier.Value.BKEY_LIST);
        assertNull(identifiers);

        final Book decodedBook = bookCoder.decode(encodedBook);
        // Invalid values were not stored
        assertTrue(decodedBook.getIdentifierValue(Identifier.SID_GOODREADS).isEmpty());
        assertTrue(decodedBook.getIdentifierValue(Identifier.SID_OPEN_LIBRARY).isEmpty());
    }
}
