/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import android.content.Context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.MoneyFormatter;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MoneyFormatterTest {

    @Mock
    Context mContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = mock(Context.class);
        when(mContext.getApplicationContext()).thenReturn(mContext);
    }

    @Test
    void format01() {
        FieldFormatter<Money> f = new MoneyFormatter();
        assertEquals("$10.50", f.format(mContext, new Money(10.50d, "USD")));
    }

    @Test
    void format02() {
        FieldFormatter<Money> f = new MoneyFormatter();
        assertEquals("USD10.50", f.format(mContext, new Money(10.50d, "USD")));
    }

    @Test
    void format03() {
        FieldFormatter<Money> f = new MoneyFormatter();
        assertEquals("10,50 USD", f.format(mContext, new Money(10.50d, "USD")));
    }


    /**
     * Parsing will fail, we get the source back
     */
    @Test
    void format10() {
        FieldFormatter<Money> f = new MoneyFormatter();
        assertEquals("10,50 €", f.format(mContext, new Money(10.50d, "EUR")));
    }

    /**
     * Parsing will fail, we get the source back
     */
    @Test
    void format11() {
        FieldFormatter<Money> f = new MoneyFormatter();
        assertEquals("€10.50", f.format(mContext, new Money(10.50d, "EUR")));
    }
}
