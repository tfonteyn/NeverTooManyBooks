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

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * REMINDER: this test <strong>MUST</strong> run as "androidTest" as we're
 * interacting with Android JDK specific changes.
 * <p>
 * It's run twice; against the API specific versions:
 * {@link TextNormalizerApi29} and {@link TextNormalizerApi26}
 */
@SuppressWarnings("MissingJavadoc")
class TextNormalizerTest {

    @NonNull
    static Stream<Arguments> normalizers() {
        return Stream.of(
                Arguments.of(new TextNormalizerApi26()),
                Arguments.of(new TextNormalizerApi29())
        );
    }

    @ParameterizedTest
    @MethodSource("normalizers")
    void latinFrench(@NonNull final TextNormalizer textNormalizer) {
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

    @ParameterizedTest
    @MethodSource("normalizers")
    void latinGerman(@NonNull final TextNormalizer textNormalizer) {
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

    @ParameterizedTest
    @MethodSource("normalizers")
    void latinPortuguese(@NonNull final TextNormalizer textNormalizer) {
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
    @ParameterizedTest
    @MethodSource("normalizers")
    void georgian(@NonNull final TextNormalizer textNormalizer) {
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

    @ParameterizedTest
    @MethodSource("normalizers")
    void greek(@NonNull final TextNormalizer textNormalizer) {
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

    @ParameterizedTest
    @MethodSource("normalizers")
    void russian(@NonNull final TextNormalizer textNormalizer) {
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

    @ParameterizedTest
    @MethodSource("normalizers")
    void whitespace(@NonNull final TextNormalizer textNormalizer) {
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
