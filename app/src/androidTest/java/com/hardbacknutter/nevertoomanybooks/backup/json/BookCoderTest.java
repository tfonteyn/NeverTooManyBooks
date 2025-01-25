/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.backup.json;

import java.math.BigDecimal;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.IdentifierCoder;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONObject;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("MissingJavadoc")
public class BookCoderTest
        extends BaseDBTest {

    private Book book;
    private BookCoder bookCoder;
    private IdentifierCoder identifierCoder;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
        book = new Book();
        bookCoder = new BookCoder(context, serviceLocator.getStyles().getDefault());
        identifierCoder = new IdentifierCoder();
    }

    @Test
    public void putMoney() {
        final Money money = new Money(BigDecimal.valueOf(12.34d), Money.EURO);
        book.putMoney(DBKey.PRICE_LISTED, money);

        final JSONObject encode = bookCoder.encode(book);

        assertEquals(12.34d, encode.getDouble(DBKey.PRICE_LISTED), 0);
        assertEquals("EUR", encode.getString(DBKey.PRICE_LISTED_CURRENCY));
    }

    @Test
    public void putMoneyComponents() {
        book.putDouble(DBKey.PRICE_LISTED, 12.34d);
        book.putString(DBKey.PRICE_LISTED_CURRENCY, MoneyParser.EUR);

        final JSONObject encode = bookCoder.encode(book);

        assertEquals(12.34d, encode.getDouble(DBKey.PRICE_LISTED), 0);
        assertEquals("EUR", encode.getString(DBKey.PRICE_LISTED_CURRENCY));
    }

    @Test
    public void putMoneyComponentsNoCurrency() {
        book.putDouble(DBKey.PRICE_LISTED, 12.34d);

        final JSONObject encode = bookCoder.encode(book);

        assertTrue(encode.has(DBKey.PRICE_LISTED));
        assertFalse(encode.has(DBKey.PRICE_LISTED_CURRENCY));

        assertEquals(12.34d, encode.getDouble(DBKey.PRICE_LISTED), 0);
    }

    @Test
    public void putMoneyCustomString() {
        book.putString(DBKey.PRICE_LISTED, "a lot of money");

        final JSONObject encode = bookCoder.encode(book);

        assertTrue(encode.has(DBKey.PRICE_LISTED));
        assertFalse(encode.has(DBKey.PRICE_LISTED_CURRENCY));

        assertEquals("a lot of money", encode.getString(DBKey.PRICE_LISTED));
    }

    @Test
    public void putIdentifiersValid() {
        book.setIdentifierValue(Identifier.SID_GOODREADS_BOOK, 1234);
        book.setIdentifierValue(Identifier.SID_OPEN_LIBRARY, "ol123");

        final JSONObject encodedBook = bookCoder.encode(book);

        final JSONArray identifiers = encodedBook.optJSONArray(Book.BKEY_IDENTIFIER_LIST);
        assertNotNull(identifiers);

        final List<Identifier.Value> list = identifierCoder.decode(identifiers);
        assertEquals(2, list.size());

        final Book decodedBook = bookCoder.decode(encodedBook);
        assertEquals("1234", decodedBook.requireIdentifierValue(Identifier.SID_GOODREADS_BOOK));
        assertEquals("ol123", decodedBook.requireIdentifierValue(Identifier.SID_OPEN_LIBRARY));
    }

    @Test
    public void putIdentifiersInvalid() {
        book.setIdentifierValue(Identifier.SID_GOODREADS_BOOK, -1234);
        book.setIdentifierValue(Identifier.SID_OPEN_LIBRARY, "");

        final JSONObject encodedBook = bookCoder.encode(book);

        final JSONArray identifiers = encodedBook.optJSONArray(Book.BKEY_IDENTIFIER_LIST);
        assertNull(identifiers);

        final Book decodedBook = bookCoder.decode(encodedBook);
        // Invalid values were not stored
        assertTrue(decodedBook.getIdentifierValue(Identifier.SID_GOODREADS_BOOK).isEmpty());
        assertTrue(decodedBook.getIdentifierValue(Identifier.SID_OPEN_LIBRARY).isEmpty());
    }
}
