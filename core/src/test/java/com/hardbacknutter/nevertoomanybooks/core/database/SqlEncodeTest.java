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

package com.hardbacknutter.nevertoomanybooks.core.database;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.core.utils.AsciiNormalizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlEncodeTest {

    @Test
    void latinFrench() {
        final Locale locale = new Locale("fr", "FR");
        assertEquals("France", locale.getDisplayCountry());
        assertEquals("French", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "abc def";
        assertEquals("abc def", AsciiNormalizer.normalize(source));
        assertEquals("abcdef", SqlEncode.orderByColumn(source, locale));

        source = "États";
        assertEquals("Etats", AsciiNormalizer.normalize(source));
        assertEquals("etats", SqlEncode.orderByColumn(source, locale));

        source = "Première République française";
        assertEquals("Premiere Republique francaise", AsciiNormalizer.normalize(source));
        assertEquals("premiererepubliquefrancaise", SqlEncode.orderByColumn(source, locale));

        source = "États, (française) \"République\"";
        assertEquals("Etats, (francaise) \"Republique\"", AsciiNormalizer.normalize(source));
        assertEquals("etatsfrancaiserepublique", SqlEncode.orderByColumn(source, locale));
    }

    @Test
    void latinGerman() {
        final Locale locale = new Locale("de", "DE");
        assertEquals("Germany", locale.getDisplayCountry());
        assertEquals("German", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "abc def";
        assertEquals("abc def", AsciiNormalizer.normalize(source));
        assertEquals("abcdef", SqlEncode.orderByColumn(source, locale));

        source = "Jäger";
        assertEquals("Jager", AsciiNormalizer.normalize(source));
        assertEquals("jager", SqlEncode.orderByColumn(source, locale));

        source = "Jäger, (größte)";
        assertEquals("Jager, (grote)", AsciiNormalizer.normalize(source));
        assertEquals("jagergrote", SqlEncode.orderByColumn(source, locale));
    }

    @Test
    void latinPortuguese() {
        final Locale locale = new Locale("pt", "PT");
        assertEquals("Portugal", locale.getDisplayCountry());
        assertEquals("Portuguese", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "abc def";
        assertEquals("abc def", AsciiNormalizer.normalize(source));
        assertEquals("abcdef", SqlEncode.orderByColumn(source, locale));

        source = "Luís de Camões";
        assertEquals("Luis de Camoes", AsciiNormalizer.normalize(source));
        assertEquals("luisdecamoes", SqlEncode.orderByColumn(source, locale));
    }

    // https://en.wikipedia.org/wiki/Georgian_scripts
    @Test
    void georgianKa() {
        final Locale locale = new Locale("ka", "GE");
        assertEquals("Georgia", locale.getDisplayCountry());
        assertEquals("Georgian", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "abc def";
        assertEquals("abc def", AsciiNormalizer.normalize(source));
        assertEquals("abcdef", SqlEncode.orderByColumn(source, locale));

        source = "ალექსანდრე ამილახვარი";
        assertEquals("ალექსანდრე ამილახვარი", AsciiNormalizer.normalize(source));
        assertEquals("ალექსანდრეამილახვარი", SqlEncode.orderByColumn(source, locale));
    }

    @Test
    void greek() {
        final Locale locale = new Locale("el", "GR");
        assertEquals("Greece", locale.getDisplayCountry());
        assertEquals("Greek", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "abc def";
        assertEquals("abc def", AsciiNormalizer.normalize(source));
        assertEquals("abcdef", SqlEncode.orderByColumn(source, locale));

        source = "Ἀνδρέας Κάλβος";
        assertEquals("Ἀνδρέας Κάλβος", AsciiNormalizer.normalize(source));
        assertEquals("ἈνδρέαςΚάλβος", SqlEncode.orderByColumn(source, locale));
    }

    @Test
    void russian() {
        final Locale locale = new Locale("ru", "RU");
        assertEquals("Russia", locale.getDisplayCountry());
        assertEquals("Russian", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "abc def";
        assertEquals("abc def", AsciiNormalizer.normalize(source));
        assertEquals("abcdef", SqlEncode.orderByColumn(source, locale));

        source = "Фёдор Алекса́ндрович Абра́мов";
        assertEquals("Фёдор Алекса́ндрович Абра́мов", AsciiNormalizer.normalize(source));
        assertEquals("ФёдорАлекса́ндровичАбра́мов", SqlEncode.orderByColumn(source, locale));
    }
}
