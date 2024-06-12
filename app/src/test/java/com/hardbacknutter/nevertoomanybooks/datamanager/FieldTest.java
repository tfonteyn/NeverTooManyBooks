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

import android.widget.EditText;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks._mocks.os.BundleMock;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.fields.DecimalEditTextField;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DoubleNumberFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FieldTest
        extends Base {

    private DataManager dataManager;

    @BeforeEach
    @Override
    public void setup()
            throws Exception {
        super.setup();
        dataManager = new DataManager(BundleMock.create());
    }

    @Test
    void putStringValueAndGetMoney() {
        // use a locale with a "," as decimal separator
        setLocale(Locale.GERMANY);
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        dataManager.putString(DBKey.PRICE_LISTED, "7.0");

        final Object out = dataManager.get(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(out);
        assertInstanceOf(String.class, out);
        assertEquals("7.0", (String) out);

        final DoubleNumberFormatter doubleNumberFormatter = new DoubleNumberFormatter(
                realNumberParser);

        final Field<Number, EditText> moneyField =
                new DecimalEditTextField(FragmentId.Publication,
                                         R.id.price_listed,
                                         DBKey.PRICE_LISTED,
                                         doubleNumberFormatter);

        //FIXME: there is no view, so format is not called - this test is meaningless...
        moneyField.setInitialValue(context, dataManager, realNumberParser);
    }
}
