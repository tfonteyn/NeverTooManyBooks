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
import android.os.LocaleList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("CheckStyle")
class MoneyTest
        extends BaseDBTest {

    /**
     * Tests include adding the price value as Money, BigDecimal, double and String.
     * The result must be parsable to a BigDecimal.
     */
    private static final double D_VALUE = 12.34d;
    private static final String S_VALUE = "12.34";
    private static final BigDecimal BD_VALUE = new BigDecimal(S_VALUE);
    private final Money money = MoneyParser.parse(BD_VALUE, MoneyParser.GBP);

    private RealNumberParser parser;

    private DataManager dataManager;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        dataManager = new DataManager();

        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(userLocales);
        parser = RealNumberParser.money(allLocales);
    }

    @SuppressWarnings({"SameParameterValue", "deprecation"})
    private void checkPriceData(@NonNull final DataManager dataManager,
                                       @NonNull final String key,
                                       @NonNull final BigDecimal expected,
                                       @Nullable final String currency) {
        final Bundle rawData = dataManager.getRawData();

        final Object v = rawData.get(key);
        assertNotNull(v);

        final BigDecimal value = parser.toBigDecimal(v);

        assertEquals(0, expected.compareTo(value));

        final Object c = rawData.get(key + DBKey.CURRENCY_SUFFIX);
        if (currency == null) {
            assertNull(c);
        } else {
            assertInstanceOf(String.class, c);
            assertEquals(currency, c);
        }
    }

    @Test
    void putMoney() {
        dataManager.putMoney(DBKey.PRICE_LISTED, money);
        checkPriceData(dataManager, DBKey.PRICE_LISTED, BD_VALUE, MoneyParser.GBP);
    }

    @Test
    void putObject() {
        // Test for put(.., Object); do NOT replace with putMoney
        dataManager.put(DBKey.PRICE_LISTED, money);
        checkPriceData(dataManager, DBKey.PRICE_LISTED, BD_VALUE, MoneyParser.GBP);
    }


    @Test
    void putComponentsBigDecimal() {
        dataManager.putBigDecimal(DBKey.PRICE_LISTED, BD_VALUE);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, MoneyParser.GBP);

        checkPriceData(dataManager, DBKey.PRICE_LISTED, BD_VALUE, MoneyParser.GBP);
    }

    @Test
    void putComponentsBigDouble() {
        dataManager.putDouble(DBKey.PRICE_LISTED, D_VALUE);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, MoneyParser.GBP);

        checkPriceData(dataManager, DBKey.PRICE_LISTED, BD_VALUE, MoneyParser.GBP);
    }

    @Test
    void putComponentsString() {
        dataManager.putString(DBKey.PRICE_LISTED, S_VALUE);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, MoneyParser.GBP);

        checkPriceData(dataManager, DBKey.PRICE_LISTED, BD_VALUE, MoneyParser.GBP);
    }

    @Test
    void putValueOnlyBigDecimal() {
        dataManager.putBigDecimal(DBKey.PRICE_LISTED, BD_VALUE);
        checkPriceData(dataManager, DBKey.PRICE_LISTED, BD_VALUE, null);
    }

    @Test
    void putValueOnlyBigDouble() {
        dataManager.putDouble(DBKey.PRICE_LISTED, D_VALUE);
        checkPriceData(dataManager, DBKey.PRICE_LISTED, BD_VALUE, null);
    }

    @Test
    void putValueOnlyBigString() {
        dataManager.putString(DBKey.PRICE_LISTED, S_VALUE);
        checkPriceData(dataManager, DBKey.PRICE_LISTED, BD_VALUE, null);
    }

    @Test
    void putValueAndIllegalCurrencyBigDecimal() {
        dataManager.putBigDecimal(DBKey.PRICE_LISTED, BD_VALUE);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, "chocolates");
        checkPriceData(dataManager, DBKey.PRICE_LISTED, BD_VALUE, "chocolates");
    }

    @Test
    void putValueAndIllegalCurrencyDouble() {
        dataManager.putDouble(DBKey.PRICE_LISTED, D_VALUE);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, "chocolates");
        checkPriceData(dataManager, DBKey.PRICE_LISTED, BD_VALUE, "chocolates");
    }

    @Test
    void putValueAndIllegalCurrencyString() {
        dataManager.putString(DBKey.PRICE_LISTED, S_VALUE);
        dataManager.putString(DBKey.PRICE_LISTED_CURRENCY, "chocolates");
        checkPriceData(dataManager, DBKey.PRICE_LISTED, BD_VALUE, "chocolates");
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
