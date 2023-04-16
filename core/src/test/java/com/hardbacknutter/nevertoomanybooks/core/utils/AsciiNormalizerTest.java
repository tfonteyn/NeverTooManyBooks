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

package com.hardbacknutter.nevertoomanybooks.core.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsciiNormalizerTest {

    @Test
    void latinAlphabet() {
        assertEquals("abcde", AsciiNormalizer.normalize("abcde"));
        assertEquals("Jager", AsciiNormalizer.normalize("Jäger"));
        assertEquals("Etats", AsciiNormalizer.normalize("États"));
        assertEquals("Premiere Republique francaise",
                     AsciiNormalizer.normalize("Première République française"));
        assertEquals("Luis de Camoes",
                     AsciiNormalizer.normalize("Luís de Camões"));
    }

    @Test
    void georgian() {
        // https://en.wikipedia.org/wiki/Georgian_scripts
        // Georgian / Georgia
        // final Locale bookLocale = new Locale("ka", "GE");
        assertEquals("ალექსანდრე ამილახვარი",
                     AsciiNormalizer.normalize("ალექსანდრე ამილახვარი"));
    }

    @Test
    void greek() {
        assertEquals("Ἀνδρέας Κάλβος",
                     AsciiNormalizer.normalize("Ἀνδρέας Κάλβος"));
    }

    @Test
    void russian() {
        assertEquals("Фёдор Алекса́ндрович Абра́мов",
                     AsciiNormalizer.normalize("Фёдор Алекса́ндрович Абра́мов"));
    }
}
