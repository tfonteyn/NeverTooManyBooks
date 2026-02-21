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

package com.hardbacknutter.nevertoomanybooks.core.database;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser.TextNormalizer;
import com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser.TextNormalizerApi26;
import com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser.TextNormalizerApi29;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;

/**
 * REMINDER: this test <strong>MUST</strong> run as "androidTest" as we're
 * interacting with Android JDK specific changes.
 * <p>
 * It's run twice; against the API specific versions:
 * {@link TextNormalizerApi29} and {@link TextNormalizerApi26}
 */
@SuppressWarnings("MissingJavadoc")
@RunWith(Parameterized.class)
public class SqlEncodeTest {

    private final TextNormalizer textNormalizer;

    public SqlEncodeTest(@NonNull final TextNormalizer textNormalizer) {
        this.textNormalizer = textNormalizer;
    }

    @Parameterized.Parameters
    public static Collection<TextNormalizer> data() {
        return List.of(new TextNormalizerApi26(),
                       new TextNormalizerApi29());
    }

    @Test
    public void latinFrench() {
        final Locale locale = new Locale("fr", "FR");
        assertEquals("France", locale.getDisplayCountry());
        assertEquals("French", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", textNormalizer.normalize(source));
        assertEquals("abcdef", textNormalizer.orderByColumn(source, locale));

        source = "États";
        assertEquals("Etats", textNormalizer.normalize(source));
        assertEquals("etats", textNormalizer.orderByColumn(source, locale));

        source = "Première République française";
        assertEquals("Premiere Republique francaise", textNormalizer.normalize(source));
        assertEquals("premiererepubliquefrancaise", textNormalizer.orderByColumn(source, locale));

        source = "États, (française) \"République\"";
        assertEquals("Etats francaise Republique", textNormalizer.normalize(source));
        assertEquals("etatsfrancaiserepublique", textNormalizer.orderByColumn(source, locale));
    }

    @Test
    public void latinGerman() {
        final Locale locale = new Locale("de", "DE");
        assertEquals("Germany", locale.getDisplayCountry());
        assertEquals("German", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", textNormalizer.normalize(source));
        assertEquals("abcdef", textNormalizer.orderByColumn(source, locale));

        source = "Jäger";
        assertEquals("Jager", textNormalizer.normalize(source));
        assertEquals("jager", textNormalizer.orderByColumn(source, locale));

        // 2025-09-21: behaviour change: "ß" is transliterated to "ss"
        source = "Jäger, (größte)";
        assertEquals("Jager grosste", textNormalizer.normalize(source));
        assertEquals("jagergrosste", textNormalizer.orderByColumn(source, locale));
    }

    @Test
    public void latinPortuguese() {
        final Locale locale = new Locale("pt", "PT");
        assertEquals("Portugal", locale.getDisplayCountry());
        assertEquals("Portuguese", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", textNormalizer.normalize(source));
        assertEquals("abcdef", textNormalizer.orderByColumn(source, locale));

        source = "Luís de Camões";
        assertEquals("Luis de Camoes", textNormalizer.normalize(source));
        assertEquals("luisdecamoes", textNormalizer.orderByColumn(source, locale));
    }

    // https://en.wikipedia.org/wiki/Georgian_scripts
    @Test
    public void georgian() {
        final Locale locale = new Locale("ka", "GE");
        assertEquals("Georgia", locale.getDisplayCountry());
        assertEquals("Georgian", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", textNormalizer.normalize(source));
        assertEquals("abcdef", textNormalizer.orderByColumn(source, locale));

        source = "ალექსანდრე ამილახვარი";
        assertEquals("ალექსანდრე ამილახვარი", textNormalizer.normalize(source));
        assertEquals("ალექსანდრეამილახვარი", textNormalizer.orderByColumn(source, locale));
    }

    @Test
    public void greek() {
        final Locale locale = new Locale("el", "GR");
        assertEquals("Greece", locale.getDisplayCountry());
        assertEquals("Greek", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", textNormalizer.normalize(source));
        assertEquals("abcdef", textNormalizer.orderByColumn(source, locale));

        source = "Ἀνδρέας Κάλβος";
        assertEquals("Ανδρεας Καλβος", textNormalizer.normalize(source));
        assertEquals("ανδρεαςκαλβος", textNormalizer.orderByColumn(source, locale));
    }

    @Test
    public void russian() {
        final Locale locale = new Locale("ru", "RU");
        assertEquals("Russia", locale.getDisplayCountry());
        assertEquals("Russian", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", textNormalizer.normalize(source));
        assertEquals("abcdef", textNormalizer.orderByColumn(source, locale));

        source = "Фёдор Алекса́ндрович Абра́мов";
        assertEquals("Федор Александрович Абрамов", textNormalizer.normalize(source));
        assertEquals("федоралександровичабрамов", textNormalizer.orderByColumn(source, locale));
    }

    @Test
    public void whitespace() {
        final Locale locale = new Locale("nl", "NL");
        final String source =
                "aBc  Def " +
                (char) 0x0009 +
                (char) 0x000D +
                (char) 0x00A0 +
                (char) 0x3000 +
                "ghi";
        assertEquals("aBc Def ghi", textNormalizer.normalize(source));
        assertEquals("abcdefghi", textNormalizer.orderByColumn(source, locale));
    }
}
