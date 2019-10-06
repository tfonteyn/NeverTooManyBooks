/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.os.Bundle;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.hardbacknutter.nevertoomanybooks.BundleMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyUtilsTest {

    private static final String KEY_PRICE = "p";
    private static final String KEY_CURRENCY = "c";

    @Mock
    private Bundle bundle;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        bundle = BundleMock.mock();
    }

    /** Country with ',' as thousands, and '.' as decimal separator. */
    @Test
    void splitPrice10() {

        Locale locale = Locale.UK;

        CurrencyUtils.splitPrice(locale, "$10.50", KEY_PRICE, KEY_CURRENCY, bundle);
        assertEquals("USD", bundle.get(KEY_CURRENCY));
        assertEquals("10.50", bundle.get(KEY_PRICE));

        CurrencyUtils.splitPrice(locale, "£10.50", KEY_PRICE, KEY_CURRENCY, bundle);
        assertEquals("GBP", bundle.get(KEY_CURRENCY));
        assertEquals("10.50", bundle.get(KEY_PRICE));

        CurrencyUtils.splitPrice(locale, "EUR 10.50", KEY_PRICE, KEY_CURRENCY, bundle);
        assertEquals("EUR", bundle.get(KEY_CURRENCY));
        assertEquals("10.50", bundle.get(KEY_PRICE));
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice20() {
        Locale locale = new Locale("nl", "BE");

        CurrencyUtils.splitPrice(locale, "fr10,50", KEY_PRICE, KEY_CURRENCY, bundle);
        assertEquals("BEF", bundle.get(KEY_CURRENCY));
        // BEF uses no decimal digits by JDK default
        assertEquals("11", bundle.get(KEY_PRICE));

        CurrencyUtils.splitPrice(locale, "$10.50", KEY_PRICE, KEY_CURRENCY, bundle);
        assertEquals("USD", bundle.get(KEY_CURRENCY));
        assertEquals("10.50", bundle.get(KEY_PRICE));
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice21() {
        Locale locale = new Locale("nl", "NL");

        CurrencyUtils.splitPrice(locale, "£10.50", KEY_PRICE, KEY_CURRENCY, bundle);
        assertEquals("GBP", bundle.get(KEY_CURRENCY));
        assertEquals("10.50", bundle.get(KEY_PRICE));
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice22() {
        Locale locale = new Locale("nl", "NL");

        CurrencyUtils.splitPrice(locale, "EUR 10.50", KEY_PRICE, KEY_CURRENCY, bundle);
        assertEquals("EUR", bundle.get(KEY_CURRENCY));
        assertEquals("10.50", bundle.get(KEY_PRICE));
    }
}