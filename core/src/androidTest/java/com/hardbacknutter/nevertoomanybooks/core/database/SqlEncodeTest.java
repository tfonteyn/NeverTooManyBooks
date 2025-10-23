/*
 * @Copyright 2018-2025 HardBackNutter
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

import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.core.utils.TextNormalizer;
import com.hardbacknutter.nevertoomanybooks.core.utils.TextNormalizerApi26;
import com.hardbacknutter.nevertoomanybooks.core.utils.TextNormalizerApi29;

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

    private final int api;

    public SqlEncodeTest(final int api) {
        this.api = api;
    }

    @Parameterized.Parameters
    public static Collection<Integer> data() {
        return List.of(Build.VERSION_CODES.O,
                       Build.VERSION_CODES.Q);
    }

    @NonNull
    private String normalize(@NonNull final CharSequence source) {
        if (api == Build.VERSION_CODES.Q) {
            return TextNormalizerApi29.normalize(source, TextNormalizer.ALPHANUMERIC_PATTERN);
        } else {
            return TextNormalizerApi26.normalize(source, TextNormalizer.ALPHANUMERIC_PATTERN);
        }
    }

    @NonNull
    private String orderedByColumn(@NonNull final CharSequence source,
                                   @NonNull final Locale locale) {
        return normalize(source).toLowerCase(locale);
    }

    @Test
    public void latinFrench() {
        final Locale locale = new Locale("fr", "FR");
        assertEquals("France", locale.getDisplayCountry());
        assertEquals("French", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", normalize(source));
        assertEquals("abc def", orderedByColumn(source, locale));

        source = "États";
        assertEquals("Etats", normalize(source));
        assertEquals("etats", orderedByColumn(source, locale));

        source = "Première République française";
        assertEquals("Premiere Republique francaise", normalize(source));
        assertEquals("premiere republique francaise", orderedByColumn(source, locale));

        source = "États, (française) \"République\"";
        assertEquals("Etats francaise Republique", normalize(source));
        assertEquals("etats francaise republique", orderedByColumn(source, locale));
    }

    @Test
    public void latinGerman() {
        final Locale locale = new Locale("de", "DE");
        assertEquals("Germany", locale.getDisplayCountry());
        assertEquals("German", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", normalize(source));
        assertEquals("abc def", orderedByColumn(source, locale));

        source = "Jäger";
        assertEquals("Jager", normalize(source));
        assertEquals("jager", orderedByColumn(source, locale));

        // 2025-09-21: behaviour change: "ß" is transliterated to "ss"
        source = "Jäger, (größte)";
        assertEquals("Jager grosste", normalize(source));
        assertEquals("jager grosste", orderedByColumn(source, locale));
    }

    @Test
    public void latinPortuguese() {
        final Locale locale = new Locale("pt", "PT");
        assertEquals("Portugal", locale.getDisplayCountry());
        assertEquals("Portuguese", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", normalize(source));
        assertEquals("abc def", orderedByColumn(source, locale));

        source = "Luís de Camões";
        assertEquals("Luis de Camoes", normalize(source));
        assertEquals("luis de camoes", orderedByColumn(source, locale));
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
        assertEquals("aBc Def", normalize(source));
        assertEquals("abc def", orderedByColumn(source, locale));

        source = "ალექსანდრე ამილახვარი";
        assertEquals("ალექსანდრე ამილახვარი", normalize(source));
        assertEquals("ალექსანდრე ამილახვარი", orderedByColumn(source, locale));
    }

    @Test
    public void greek() {
        final Locale locale = new Locale("el", "GR");
        assertEquals("Greece", locale.getDisplayCountry());
        assertEquals("Greek", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", normalize(source));
        assertEquals("abc def", orderedByColumn(source, locale));

        source = "Ἀνδρέας Κάλβος";
        assertEquals("Ανδρεας Καλβος", normalize(source));
        assertEquals("ανδρεας καλβος", orderedByColumn(source, locale));
    }

    @Test
    public void russian() {
        final Locale locale = new Locale("ru", "RU");
        assertEquals("Russia", locale.getDisplayCountry());
        assertEquals("Russian", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBc Def", normalize(source));
        assertEquals("abc def", orderedByColumn(source, locale));

        source = "Фёдор Алекса́ндрович Абра́мов";
        assertEquals("Федор Александрович Абрамов", normalize(source));
        assertEquals("федор александрович абрамов", orderedByColumn(source, locale));
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
        assertEquals("aBc Def ghi", normalize(source));
        assertEquals("abc def ghi", orderedByColumn(source, locale));
    }
}
