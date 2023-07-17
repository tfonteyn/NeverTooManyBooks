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

import androidx.annotation.NonNull;

import java.util.Locale;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * REMINDER: this test <strong>MUST</strong> run as "androidTest" as we're
 * interacting with Android JDK specific changes.
 */
public class SqlEncodeTest {

    private static String orderByColumn(@NonNull final CharSequence text,
                                        @NonNull final Locale locale) {
        return SqlEncode.orderByColumn(text, locale);
    }

    @Test
    public void latinFrench() {
        final Locale locale = new Locale("fr", "FR");
        assertEquals("France", locale.getDisplayCountry());
        assertEquals("French", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBcDef", SqlEncode.normalize(source));
        assertEquals("abcdef", orderByColumn(source, locale));

        source = "États";
        assertEquals("Etats", SqlEncode.normalize(source));
        assertEquals("etats", orderByColumn(source, locale));

        source = "Première République française";
        assertEquals("PremiereRepubliquefrancaise", SqlEncode.normalize(source));
        assertEquals("premiererepubliquefrancaise", orderByColumn(source, locale));

        source = "États, (française) \"République\"";
        assertEquals("EtatsfrancaiseRepublique", SqlEncode.normalize(source));
        assertEquals("etatsfrancaiserepublique", orderByColumn(source, locale));
    }

    @Test
    public void latinGerman() {
        final Locale locale = new Locale("de", "DE");
        assertEquals("Germany", locale.getDisplayCountry());
        assertEquals("German", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBcDef", SqlEncode.normalize(source));
        assertEquals("abcdef", orderByColumn(source, locale));

        source = "Jäger";
        assertEquals("Jager", SqlEncode.normalize(source));
        assertEquals("jager", orderByColumn(source, locale));

        source = "Jäger, (größte)";
        assertEquals("Jagergroßte", SqlEncode.normalize(source));
        assertEquals("jagergroßte", orderByColumn(source, locale));
    }

    @Test
    public void latinPortuguese() {
        final Locale locale = new Locale("pt", "PT");
        assertEquals("Portugal", locale.getDisplayCountry());
        assertEquals("Portuguese", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBcDef", SqlEncode.normalize(source));
        assertEquals("abcdef", orderByColumn(source, locale));

        source = "Luís de Camões";
        assertEquals("LuisdeCamoes", SqlEncode.normalize(source));
        assertEquals("luisdecamoes", orderByColumn(source, locale));
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
        assertEquals("aBcDef", SqlEncode.normalize(source));
        assertEquals("abcdef", orderByColumn(source, locale));

        source = "ალექსანდრე ამილახვარი";
        assertEquals("ალექსანდრეამილახვარი", SqlEncode.normalize(source));
        assertEquals("ალექსანდრეამილახვარი", orderByColumn(source, locale));
    }

    @Test
    public void greek() {
        final Locale locale = new Locale("el", "GR");
        assertEquals("Greece", locale.getDisplayCountry());
        assertEquals("Greek", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBcDef", SqlEncode.normalize(source));
        assertEquals("abcdef", orderByColumn(source, locale));

        source = "Ἀνδρέας Κάλβος";
        assertEquals("ΑνδρεαςΚαλβος", SqlEncode.normalize(source));
        assertEquals("ανδρεαςκαλβος", orderByColumn(source, locale));
    }

    @Test
    public void russian() {
        final Locale locale = new Locale("ru", "RU");
        assertEquals("Russia", locale.getDisplayCountry());
        assertEquals("Russian", locale.getDisplayLanguage());
        assertEquals("", locale.getScript());
        String source;

        source = "aBc Def";
        assertEquals("aBcDef", SqlEncode.normalize(source));
        assertEquals("abcdef", orderByColumn(source, locale));

        source = "Фёдор Алекса́ндрович Абра́мов";
        assertEquals("ФедорАлександровичАбрамов", SqlEncode.normalize(source));
        assertEquals("федоралександровичабрамов", orderByColumn(source, locale));
    }
}
