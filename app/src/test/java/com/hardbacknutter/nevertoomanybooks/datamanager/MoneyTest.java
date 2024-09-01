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

package com.hardbacknutter.nevertoomanybooks.datamanager;

import java.math.BigDecimal;
import java.util.Currency;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.MoneyVerifier;
import com.hardbacknutter.nevertoomanybooks._mocks.os.BundleMock;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MoneyTest
        extends Base {

    private static final double VALUE = 12.34d;
    private static final BigDecimal twelveDotThreeFour = BigDecimal.valueOf(VALUE);

    private DataManager dataManager;

    @BeforeEach
    @Override
    public void setup()
            throws Exception {
        super.setup();
        dataManager = new DataManager(BundleMock.create());
    }

    /**
     * value+currency
     * <p>
     * putMoney
     * getMoney
     */
    @Test
    void putMoneyAndGetMoney() {
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        final Money money = MoneyParser.parse(twelveDotThreeFour, MoneyParser.GBP);
        assertNotNull(money);
        dataManager.putMoney(DBKey.PRICE_LISTED, money);

        final Money out = dataManager.getMoney(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);
        assertEquals(twelveDotThreeFour, out.getValue());
        final Currency currency = out.getCurrency();
        assertNotNull(currency);
        assertEquals("GBP", currency.getCurrencyCode());

        MoneyVerifier.checkRawData(dataManager, twelveDotThreeFour, "GBP");
    }

    /**
     * value+currency
     * <p>
     * putMoney
     * get object
     */
    @Test
    void putMoneyAndGetObject() {
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        final Money money = MoneyParser.parse(twelveDotThreeFour, MoneyParser.GBP);
        assertNotNull(money);
        dataManager.putMoney(DBKey.PRICE_LISTED, money);

        final Object out = dataManager.get(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);
        assertInstanceOf(Money.class, out);
        final Money moneyOut = (Money) out;
        assertEquals(twelveDotThreeFour, moneyOut.getValue());
        final Currency currency = moneyOut.getCurrency();
        assertNotNull(currency);
        assertEquals("GBP", currency.getCurrencyCode());

        MoneyVerifier.checkRawData(dataManager, twelveDotThreeFour, "GBP");
    }

    /**
     * value+currency
     * <p>
     * put object
     * getMoney
     */
    @Test
    void putObjectAndGetMoney() {
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        final Money money = MoneyParser.parse(twelveDotThreeFour, MoneyParser.GBP);
        assertNotNull(money);
        // Test for put(.., Object); do NOT replace with putMoney
        dataManager.put(DBKey.PRICE_LISTED, money);

        final Money out = dataManager.getMoney(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);
        assertEquals(twelveDotThreeFour, out.getValue());
        final Currency currency = out.getCurrency();
        assertNotNull(currency);
        assertEquals("GBP", currency.getCurrencyCode());

        MoneyVerifier.checkRawData(dataManager, twelveDotThreeFour, "GBP");
    }

    /**
     * value+currency
     * <p>
     * put object
     * get object
     */
    @Test
    void putObjectAndGetObject() {
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        final Money money = MoneyParser.parse(twelveDotThreeFour, MoneyParser.GBP);
        assertNotNull(money);
        // Test for put(.., Object); do NOT replace with putMoney
        dataManager.put(DBKey.PRICE_LISTED, money);

        // Test for get(.., Object); do NOT replace with getMoney
        final Object out = dataManager.get(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);

        assertInstanceOf(Money.class, out);
        final Money moneyOut = (Money) out;
        assertEquals(twelveDotThreeFour, moneyOut.getValue());
        final Currency currency = moneyOut.getCurrency();
        assertNotNull(currency);
        assertEquals("GBP", currency.getCurrencyCode());

        MoneyVerifier.checkRawData(dataManager, twelveDotThreeFour, "GBP");
    }


    /**
     * value+currency
     * <p>
     * put (double,string)
     * getMoney
     */
    @Test
    void putComponentsAndGetMoney() {
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        dataManager.putDouble(DBKey.PRICE_LISTED, VALUE);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, "GBP");

        final Money out = dataManager.getMoney(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);

        assertEquals(twelveDotThreeFour, out.getValue());
        final Currency currency = out.getCurrency();
        assertNotNull(currency);
        assertEquals("GBP", currency.getCurrencyCode());

        MoneyVerifier.checkRawData(dataManager, twelveDotThreeFour, "GBP");
    }

    /**
     * value+currency
     * <p>
     * put (double,string)
     * get object
     */
    @Test
    void putComponentsAndGetObject() {
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        dataManager.putDouble(DBKey.PRICE_LISTED, VALUE);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, "GBP");

        final Object out = dataManager.get(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);

        assertInstanceOf(Money.class, out);
        final Money moneyOut = (Money) out;
        assertEquals(twelveDotThreeFour, moneyOut.getValue());
        final Currency currency = moneyOut.getCurrency();
        assertNotNull(currency);
        assertEquals("GBP", currency.getCurrencyCode());

        MoneyVerifier.checkRawData(dataManager, twelveDotThreeFour, "GBP");
    }

    /**
     * value+currency
     * <p>
     * put (double,string)
     * get double / get string
     */
    @Test
    void putComponentsAndGetComponents() {
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        dataManager.putDouble(DBKey.PRICE_LISTED, VALUE);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, "GBP");

        final double outValue = dataManager.getDouble(DBKey.PRICE_LISTED, realNumberParser);
        final String outCurrency = dataManager.getString(DBKey.PRICE_LISTED_CURRENCY);

        assertEquals(VALUE, outValue);
        assertEquals("GBP", outCurrency);

        MoneyVerifier.checkRawData(dataManager, twelveDotThreeFour, "GBP");
    }


    /**
     * value only
     * <p>
     * put (double)
     * getMoney
     */
    @Test
    void putValueAndGetMoney() {
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        dataManager.putDouble(DBKey.PRICE_LISTED, VALUE);

        final Money out = dataManager.getMoney(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);
        assertEquals(twelveDotThreeFour, out.getValue());
        assertNull(out.getCurrency());

        MoneyVerifier.checkRawData(dataManager, twelveDotThreeFour, null);
    }

    /**
     * value only
     * <p>
     * put (double)
     * get object
     */
    @Test
    void putValueAndGetObject() {
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        dataManager.putDouble(DBKey.PRICE_LISTED, VALUE);

        final Object out = dataManager.get(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);
        assertInstanceOf(Money.class, out);
        final Money moneyOut = (Money) out;
        assertEquals(twelveDotThreeFour, moneyOut.getValue());
        assertNull(moneyOut.getCurrency());

        MoneyVerifier.checkRawData(dataManager, twelveDotThreeFour, null);
    }

    @Test
    void putSentiment() {
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        dataManager.putString(DBKey.PRICE_LISTED, "Far to much dosh");

        final Object out = dataManager.get(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);
        assertInstanceOf(String.class, out);
        assertEquals("Far to much dosh", out);
    }
}
