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

package com.hardbacknutter.nevertoomanybooks.datamanager;

import java.math.BigDecimal;
import javax.xml.parsers.ParserConfigurationException;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.MoneyVerifier;
import com.hardbacknutter.nevertoomanybooks._mocks.os.BundleMock;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoneyTest
        extends Base {

    private static final BigDecimal twelveDotThreeFour = BigDecimal.valueOf(12.34d);

    private DataManager dataManager;
    private RealNumberParser realNumberParser;

    @BeforeEach
    @Override
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        dataManager = new DataManager(BundleMock.create());

        realNumberParser = new RealNumberParser(locales);
    }

    /**
     * value+currency
     * <p>
     * putMoney
     * getMoney
     */
    @Test
    void putMoneyAndGetMoney() {
        final Money money = MoneyParser.parse(twelveDotThreeFour, MoneyParser.GBP);
        assertNotNull(money);
        dataManager.putMoney(DBKey.PRICE_LISTED, money);

        final Money out = dataManager.getMoney(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);
        assertEquals(twelveDotThreeFour, out.getValue());
        assertEquals("GBP", out.getCurrencyCode());

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
        final Money money = MoneyParser.parse(twelveDotThreeFour, MoneyParser.GBP);
        assertNotNull(money);
        dataManager.putMoney(DBKey.PRICE_LISTED, money);

        final Object out = dataManager.get(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);
        assertTrue(out instanceof Money);
        final Money mOut = (Money) out;
        assertEquals(twelveDotThreeFour, mOut.getValue());
        assertEquals("GBP", mOut.getCurrencyCode());

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
        final Money money = MoneyParser.parse(twelveDotThreeFour, MoneyParser.GBP);
        assertNotNull(money);
        dataManager.put(DBKey.PRICE_LISTED, money);

        final Money out = dataManager.getMoney(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);
        assertEquals(twelveDotThreeFour, out.getValue());
        assertEquals("GBP", out.getCurrencyCode());

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
        final Money money = MoneyParser.parse(twelveDotThreeFour, MoneyParser.GBP);
        assertNotNull(money);
        dataManager.put(DBKey.PRICE_LISTED, money);

        final Object out = dataManager.get(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);

        assertTrue(out instanceof Money);
        final Money mOut = (Money) out;
        assertEquals(twelveDotThreeFour, mOut.getValue());
        assertEquals("GBP", mOut.getCurrencyCode());

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
        dataManager.putDouble(DBKey.PRICE_LISTED, 12.34d);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, "GBP");

        final Money out = dataManager.getMoney(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);

        assertEquals(twelveDotThreeFour, out.getValue());
        assertEquals("GBP", out.getCurrencyCode());

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
        dataManager.putDouble(DBKey.PRICE_LISTED, 12.34d);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, "GBP");

        final Object out = dataManager.get(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);

        assertTrue(out instanceof Money);
        final Money mOut = (Money) out;
        assertEquals(twelveDotThreeFour, mOut.getValue());
        assertEquals("GBP", mOut.getCurrencyCode());

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
        dataManager.putDouble(DBKey.PRICE_LISTED, 12.34d);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, "GBP");

        final double outValue = dataManager.getDouble(DBKey.PRICE_LISTED, realNumberParser);
        final String outCurrency = dataManager.getString(DBKey.PRICE_LISTED_CURRENCY);

        assertEquals(12.34d, outValue);
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
        dataManager.putDouble(DBKey.PRICE_LISTED, 12.34d);

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
        dataManager.putDouble(DBKey.PRICE_LISTED, 12.34d);

        final Object out = dataManager.get(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);
        assertTrue(out instanceof Money);
        final Money mOut = (Money) out;
        assertEquals(twelveDotThreeFour, mOut.getValue());
        assertNull(mOut.getCurrency());

        MoneyVerifier.checkRawData(dataManager, twelveDotThreeFour, null);
    }

    @Test
    void putSentiment() {
        dataManager.putString(DBKey.PRICE_LISTED, "Far to much dosh");

        final Object out = dataManager.get(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);
        assertTrue(out instanceof String);
        assertEquals("Far to much dosh", (String) out);
    }
}
