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

package com.hardbacknutter.nevertoomanybooks.datamanager;

import java.math.BigDecimal;

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

public class MoneyTest
        extends Base {

    private static final double VALUE = 12.34d;
    private static final BigDecimal twelveDotThreeFour = BigDecimal.valueOf(VALUE);
    private final Money money = MoneyParser.parse(twelveDotThreeFour, MoneyParser.GBP);

    private DataManager dataManager;

    @BeforeEach
    @Override
    public void setup()
            throws Exception {
        super.setup();

        assertNotNull(money);

        dataManager = new DataManager(BundleMock.create());
    }

    @Test
    void putMoney() {
        dataManager.putMoney(DBKey.PRICE_LISTED, money);
        MoneyVerifier.checkPriceData(dataManager, DBKey.PRICE_LISTED, VALUE, "GBP");
    }

    @Test
    void putObject() {
        // Test for put(.., Object); do NOT replace with putMoney
        dataManager.put(DBKey.PRICE_LISTED, money);
        MoneyVerifier.checkPriceData(dataManager, DBKey.PRICE_LISTED, VALUE, "GBP");
    }

    @Test
    void putComponents() {
        dataManager.putDouble(DBKey.PRICE_LISTED, VALUE);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, "GBP");

        MoneyVerifier.checkPriceData(dataManager, DBKey.PRICE_LISTED, VALUE, "GBP");
    }

    @Test
    void putValueOnly() {
        dataManager.putDouble(DBKey.PRICE_LISTED, VALUE);
        MoneyVerifier.checkPriceData(dataManager, DBKey.PRICE_LISTED, VALUE, null);
    }

    @Test
    void putValueAndIllegalCurrency() {
        dataManager.putDouble(DBKey.PRICE_LISTED, VALUE);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, "chocolates");
        MoneyVerifier.checkPriceData(dataManager, DBKey.PRICE_LISTED, VALUE, "chocolates");
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
