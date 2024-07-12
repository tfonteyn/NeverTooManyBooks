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

package com.hardbacknutter.nevertoomanybooks;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

public final class MoneyVerifier {
    private MoneyVerifier() {
    }

    public static void checkRawData(@NonNull final DataManager dataManager,
                                    @NonNull final BigDecimal value,
                                    @Nullable final String currency) {
        final Bundle rawData = dataManager.getRawData();

        final Object v = rawData.get(DBKey.PRICE_LISTED);
        assertInstanceOf(Double.class, v);
        assertEquals(value.doubleValue(), (double) v);

        final Object c = rawData.get(DBKey.PRICE_LISTED_CURRENCY);
        if (currency == null) {
            assertNull(c);
        } else {
            assertInstanceOf(String.class, c);
            assertEquals(currency, c);
        }
    }
}
