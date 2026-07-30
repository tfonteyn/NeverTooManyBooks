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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;

import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("CheckStyle")
class MoneyTest {

    private static final double VALUE = 12.34d;
    private final Money money = MoneyParser.parse(BigDecimal.valueOf(VALUE), MoneyParser.GBP);

    private DataManager dataManager;

    @SuppressWarnings({"SameParameterValue", "deprecation"})
    private static void checkPriceData(@NonNull final DataManager dataManager,
                                       @NonNull final String key,
                                       final double value,
                                       @Nullable final String currency) {
        final Bundle rawData = dataManager.getRawData();

        final Object v = rawData.get(key);
        assertInstanceOf(Double.class, v);
        assertEquals(value, (double) v);

        final Object c = rawData.get(key + DBKey.CURRENCY_SUFFIX);
        if (currency == null) {
            assertNull(c);
        } else {
            assertInstanceOf(String.class, c);
            assertEquals(currency, c);
        }
    }

    @BeforeEach
    void setup() {
        dataManager = new DataManager();
    }

    @Test
    void putMoney() {
        dataManager.putMoney(DBKey.PRICE_LISTED, money);
        checkPriceData(dataManager, DBKey.PRICE_LISTED, VALUE, MoneyParser.GBP);
    }

    @Test
    void putObject() {
        // Test for put(.., Object); do NOT replace with putMoney
        dataManager.put(DBKey.PRICE_LISTED, money);
        checkPriceData(dataManager, DBKey.PRICE_LISTED, VALUE, MoneyParser.GBP);
    }

    @Test
    void putComponents() {
        dataManager.putDouble(DBKey.PRICE_LISTED, VALUE);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, MoneyParser.GBP);

        checkPriceData(dataManager, DBKey.PRICE_LISTED, VALUE, MoneyParser.GBP);
    }

    @Test
    void putValueOnly() {
        dataManager.putDouble(DBKey.PRICE_LISTED, VALUE);
        checkPriceData(dataManager, DBKey.PRICE_LISTED, VALUE, null);
    }

    @Test
    void putValueAndIllegalCurrency() {
        dataManager.putDouble(DBKey.PRICE_LISTED, VALUE);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, "chocolates");
        checkPriceData(dataManager, DBKey.PRICE_LISTED, VALUE, "chocolates");
    }

    @SuppressWarnings("deprecation")
    @Test
    void putSentiment() {
        dataManager.putString(DBKey.PRICE_LISTED, "Far to much dosh");

        final Object out = dataManager.getRawData().get(DBKey.PRICE_LISTED);
        assertNotNull(out);
        assertInstanceOf(String.class, out);
        assertEquals("Far to much dosh", out);
    }
}
