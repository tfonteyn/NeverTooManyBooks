/*
 * @Copyright 2018-2023 HardBackNutter
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
import java.util.Currency;
import javax.xml.parsers.ParserConfigurationException;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BookCoderTest
        extends Base {

    private BookCoder bookCoder;

    @BeforeEach
    @Override
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        bookCoder = new BookCoder(context, style, locales);
    }

    @Test
    void putMoney() {
        final Money money = new Money(BigDecimal.valueOf(12.34d), Currency.getInstance(Money.EUR));
        book.putMoney(DBKey.PRICE_LISTED, money);

        final JSONObject encode = bookCoder.encode(book);

        assertEquals(12.34d, encode.getDouble(DBKey.PRICE_LISTED));
        assertEquals("EUR", encode.getString(DBKey.PRICE_LISTED_CURRENCY));
    }

    @Test
    void putMoneyComponents() {
        book.putDouble(DBKey.PRICE_LISTED, 12.34d);
        book.putString(DBKey.PRICE_LISTED_CURRENCY, Money.EUR);

        final JSONObject encode = bookCoder.encode(book);

        assertEquals(12.34d, encode.getDouble(DBKey.PRICE_LISTED));
        assertEquals("EUR", encode.getString(DBKey.PRICE_LISTED_CURRENCY));
    }

    @Test
    void putMoneyComponentsNoCurrency() {
        book.putDouble(DBKey.PRICE_LISTED, 12.34d);

        final JSONObject encode = bookCoder.encode(book);

        assertTrue(encode.has(DBKey.PRICE_LISTED));
        assertFalse(encode.has(DBKey.PRICE_LISTED_CURRENCY));

        assertEquals(12.34d, encode.getDouble(DBKey.PRICE_LISTED));
    }

    @Test
    void putMoneyCustomString() {
        book.putString(DBKey.PRICE_LISTED, "a lot of money");

        final JSONObject encode = bookCoder.encode(book);

        System.out.println(encode);

        assertTrue(encode.has(DBKey.PRICE_LISTED));
        assertFalse(encode.has(DBKey.PRICE_LISTED_CURRENCY));

        assertEquals("a lot of money", encode.getString(DBKey.PRICE_LISTED));
    }
}
