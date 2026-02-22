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

package com.hardbacknutter.nevertoomanybooks.utils;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageTest
        extends BaseDBTest {

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @Test
    void ancientGreek() {
        final Languages l = serviceLocator.getLanguages();

        // result is "grc" which is correct
        assertEquals("grc", l.getLocaleIsoFromISO3("grc", Locale.UK));

        // result is "Ancient Greek" again correct
        assertEquals("Ancient Greek", l.getDisplayLanguageFromISO3("grc", Locale.UK));

        // This fails as Ancient Greek is not a supported Locale on Android,
        // so we get "grc" as the display name
        // 2025-12-02: this now returns the correct "Ancient Greek".
        // but I cannot find any reference how/when android/icu might have added this.
        assertEquals("Ancient Greek", new Locale("grc").getDisplayName());
    }

    @Test
    void databazeKnihCzech() {
        // 'český': 'cs',
        // 'slovenský': 'sk',
        // 'německý': 'de',
        // 'polský': 'pl',
        // 'anglický': 'en',
        // 'francouzský': 'fr',
        // 'španělský': 'es',
        // 'italský': 'it'}
        final Languages l = serviceLocator.getLanguages();

        final Locale czech = new Locale("cs", "CZ");
        l.createLanguageMappingCache(czech);

        assertEquals("cs", l.getLocaleIsoFromISO3("ces", Locale.UK));
        assertEquals("Czech", l.getDisplayLanguageFromISO3("ces", Locale.UK));
        assertEquals("cs", l.getLocaleIsoFromISO3("cze", Locale.UK));
        assertEquals("Czech", l.getDisplayLanguageFromISO3("cze", Locale.UK));

        assertEquals("český", l.getISO3FromDisplayLanguage("český", czech));
        assertEquals("slovenský", l.getISO3FromDisplayLanguage("slovenský", czech));
        assertEquals("německý", l.getISO3FromDisplayLanguage("německý", czech));
        assertEquals("polský", l.getISO3FromDisplayLanguage("polský", czech));
        assertEquals("anglický", l.getISO3FromDisplayLanguage("anglický", czech));
        assertEquals("francouzský", l.getISO3FromDisplayLanguage("francouzský", czech));
        assertEquals("španělský", l.getISO3FromDisplayLanguage("španělský", czech));
        assertEquals("italský", l.getISO3FromDisplayLanguage("italský", czech));
    }

    @Test
    void databazeKnihSlovak() {
        // lang_mapping = {'český': 'cs',
        //   'slovenský': 'sk',
        //   'německý': 'de',
        //   'polský': 'pl',
        //   'anglický': 'en',
        //   'francouzský': 'fr',
        //   'španělský': 'es',
        //   'italský': 'it'}
        final Languages l = serviceLocator.getLanguages();

        final Locale slovak = new Locale("sk", "SK");
        l.createLanguageMappingCache(slovak);

        assertEquals("sk", l.getLocaleIsoFromISO3("slk", Locale.UK));
        assertEquals("Slovak", l.getDisplayLanguageFromISO3("slk", Locale.UK));
        assertEquals("sk", l.getLocaleIsoFromISO3("slo", Locale.UK));
        assertEquals("Slovak", l.getDisplayLanguageFromISO3("slo", Locale.UK));

        assertEquals("český", l.getISO3FromDisplayLanguage("český", slovak));
        assertEquals("slovenský", l.getISO3FromDisplayLanguage("slovenský", slovak));
        assertEquals("německý", l.getISO3FromDisplayLanguage("německý", slovak));
        assertEquals("polský", l.getISO3FromDisplayLanguage("polský", slovak));
        assertEquals("anglický", l.getISO3FromDisplayLanguage("anglický", slovak));
        assertEquals("francouzský", l.getISO3FromDisplayLanguage("francouzský", slovak));
        assertEquals("španělský", l.getISO3FromDisplayLanguage("španělský", slovak));
        assertEquals("italský", l.getISO3FromDisplayLanguage("italský", slovak));
    }
}
