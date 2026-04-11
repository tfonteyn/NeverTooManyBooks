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

package com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("SpellCheckingInspection")
class TextNormaliserTest {

    private TextNormaliser textNormaliser;

    @BeforeEach
    void setup() {
        textNormaliser = new TextNormaliser();
    }

    @Test
    void other() {
        final Locale locale = new Locale("en", "GB");

        String source;

        source = "JeanMarie";
        assertEquals("JeanMarie", textNormaliser.normalise(source));
        assertEquals("jeanmarie", textNormaliser.strict(source, locale));

        source = "Jean-Marie";
        assertEquals("Jean Marie", textNormaliser.normalise(source));
        assertEquals("jeanmarie", textNormaliser.strict(source, locale));
    }

    @Test
    void latinFrench() {

        final Locale locale = new Locale("fr", "FR");
        assertEquals("France", locale.getDisplayCountry());
        assertEquals("French", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", textNormaliser.normalise(source));
        assertEquals("abcdef", textNormaliser.strict(source, locale));

        source = "États";
        assertEquals("Etats", textNormaliser.normalise(source));
        assertEquals("etats", textNormaliser.strict(source, locale));

        source = "Première République française";
        assertEquals("Premiere Republique francaise", textNormaliser.normalise(source));
        assertEquals("premiererepubliquefrancaise", textNormaliser.strict(source, locale));

        source = "États, (française) \"République\"";
        assertEquals("Etats francaise Republique", textNormaliser.normalise(source));
        assertEquals("etatsfrancaiserepublique", textNormaliser.strict(source, locale));
    }

    @Test
    void latinGerman() {

        final Locale locale = new Locale("de", "DE");
        assertEquals("Germany", locale.getDisplayCountry());
        assertEquals("German", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", textNormaliser.normalise(source));
        assertEquals("abcdef", textNormaliser.strict(source, locale));

        source = "Jäger";
        assertEquals("Jager", textNormaliser.normalise(source));
        assertEquals("jager", textNormaliser.strict(source, locale));

        // 2025-09-21: behaviour change: "ß" is transliterated to "ss"
        source = "Jäger, (größte)";
        assertEquals("Jager grosste", textNormaliser.normalise(source));
        assertEquals("jagergrosste", textNormaliser.strict(source, locale));

        source = "Jan Groß";
        assertEquals("Jan Gross", textNormaliser.normalise(source));
        assertEquals("jangross", textNormaliser.strict(source, locale));

        source = "Jan Gross";
        assertEquals("Jan Gross", textNormaliser.normalise(source));
        assertEquals("jangross", textNormaliser.strict(source, locale));

    }

    @Test
    void latinPortuguese() {

        final Locale locale = new Locale("pt", "PT");
        assertEquals("Portugal", locale.getDisplayCountry());
        assertEquals("Portuguese", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", textNormaliser.normalise(source));
        assertEquals("abcdef", textNormaliser.strict(source, locale));

        source = "Luís de Camões";
        assertEquals("Luis de Camoes", textNormaliser.normalise(source));
        assertEquals("luisdecamoes", textNormaliser.strict(source, locale));
    }

    // https://en.wikipedia.org/wiki/Georgian_scripts
    @Test
    void georgian() {

        final Locale locale = new Locale("ka", "GE");
        assertEquals("Georgia", locale.getDisplayCountry());
        assertEquals("Georgian", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", textNormaliser.normalise(source));
        assertEquals("abcdef", textNormaliser.strict(source, locale));

        source = "ალექსანდრე ამილახვარი";
        assertEquals("ალექსანდრე ამილახვარი", textNormaliser.normalise(source));
        assertEquals("ალექსანდრეამილახვარი", textNormaliser.strict(source, locale));
    }

    @Test
    void greek() {

        final Locale locale = new Locale("el", "GR");
        assertEquals("Greece", locale.getDisplayCountry());
        assertEquals("Greek", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", textNormaliser.normalise(source));
        assertEquals("abcdef", textNormaliser.strict(source, locale));

        source = "Ἀνδρέας Κάλβος";
        assertEquals("Ανδρεας Καλβος", textNormaliser.normalise(source));
        assertEquals("ανδρεαςκαλβος", textNormaliser.strict(source, locale));
    }

    @Test
    void russian() {

        final Locale locale = new Locale("ru", "RU");
        assertEquals("Russia", locale.getDisplayCountry());
        assertEquals("Russian", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", textNormaliser.normalise(source));
        assertEquals("abcdef", textNormaliser.strict(source, locale));

        source = "Фёдор Алекса́ндрович Абра́мов";
        assertEquals("Федор Александрович Абрамов", textNormaliser.normalise(source));
        assertEquals("федоралександровичабрамов", textNormaliser.strict(source, locale));
    }

    @Test
    void whitespace() {

        final Locale locale = new Locale("nl", "NL");
        final String source =
                "aBc  Def " +
                (char) 0x0009 +
                (char) 0x000D +
                (char) 0x00A0 +
                (char) 0x3000 +
                "ghi";
        assertEquals("aBc Def ghi", textNormaliser.normalise(source));
        assertEquals("abcdefghi", textNormaliser.strict(source, locale));
    }
}
