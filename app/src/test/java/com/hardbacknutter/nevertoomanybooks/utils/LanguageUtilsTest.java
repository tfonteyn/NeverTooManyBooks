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

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is not so much a test, but code written to decipher some anomalies sees in how
 * Locales uses iso3, iso2 and display names ... and not in a consistent way.
 * <p>
 * Locale locales[] = Locale.getAvailableLocales();
 *
 * <a href="https://avajava.com/tutorials/general-java/how-do-i-display-all-available-locales/available-locales.htm">
 * https://avajava.com/tutorials/general-java/how-do-i-display-all-available-locales/available-locales.htm</a>
 */
class LanguageUtilsTest {

    @Test
    void loc() {
        Locale en = new Locale("en");
        System.out.println(" en:"
                           + " " + en.getLanguage()
                           + " " + en.getISO3Language()
                           + " " + en.getDisplayLanguage()
                           + " " + en.getDisplayName());

        Locale eng = new Locale("eng");
        System.out.println("eng:"
                           + " " + eng.getLanguage()
                           + " " + eng.getISO3Language()
                           + " " + eng.getDisplayLanguage()
                           + " " + eng.getDisplayName());

        Locale de = new Locale("de");
        System.out.println(" de:"
                           + " " + de.getLanguage()
                           + " " + de.getISO3Language()
                           + " " + de.getDisplayLanguage()
                           + " " + de.getDisplayName());

        Locale ger = new Locale("ger");
        System.out.println("ger:"
                           + " " + ger.getLanguage()
                           + " " + ger.getISO3Language()
                           + " " + ger.getDisplayLanguage()
                           + " " + ger.getDisplayName());

        Locale deu = new Locale("deu");
        System.out.println("deu:"
                           + " " + deu.getLanguage()
                           + " " + deu.getISO3Language()
                           + " " + deu.getDisplayLanguage()
                           + " " + deu.getDisplayName());

        Locale fr = new Locale("fr");
        System.out.println(" fr: "
                           + " " + fr.getLanguage()
                           + " " + fr.getISO3Language()
                           + " " + fr.getDisplayLanguage()
                           + " " + fr.getDisplayName());

        Locale fre = new Locale("fre");
        System.out.println("fre: "
                           + " " + fre.getLanguage()
                           + " " + fre.getISO3Language()
                           + " " + fre.getDisplayLanguage()
                           + " " + fre.getDisplayName());

        Locale fra = new Locale("fra");
        System.out.println("fra: "
                           + " " + fra.getLanguage()
                           + " " + fra.getISO3Language()
                           + " " + fra.getDisplayLanguage()
                           + " " + fra.getDisplayName());

        Locale it = new Locale("it");
        System.out.println(" it: "
                           + " " + it.getLanguage()
                           + " " + it.getISO3Language()
                           + " " + it.getDisplayLanguage()
                           + " " + it.getDisplayName());

        Locale ita = new Locale("ita");
        System.out.println("ita: "
                           + " " + ita.getLanguage()
                           + " " + ita.getISO3Language()
                           + " " + ita.getDisplayLanguage()
                           + " " + ita.getDisplayName());

        // en: en eng English English    => getISO3Language CAN be used to create the locale
        //eng: eng eng English English

        // de: de deu German German      => getISO3Language CANNOT be used to create the locale
        //ger: ger ger German German
        //deu: deu deu deu deu

        // fr:  fr fra French French     => getISO3Language CANNOT be used to create the locale
        //fre:  fre fre French French
        //fra:  fra fra fra fra

        //  it:  it ita Italian Italian  => getISO3Language CAN be used to create the locale
        //ita:  ita ita Italian Italian
    }

    @Test
    void format() {
        assertEquals("English", LanguageUtils.getDisplayName(Locale.ENGLISH, "en"));
        assertEquals("English", LanguageUtils.getDisplayName(Locale.ENGLISH, "eng"));

        // Note the upper case
        assertEquals("German", LanguageUtils.getDisplayName(Locale.ENGLISH, "de"));
        // Note the lower case.
        assertEquals("allemand", LanguageUtils.getDisplayName(Locale.FRENCH, "de"));
        // Note the upper case.
        assertEquals("Duits", LanguageUtils.getDisplayName(new Locale("nl"), "de"));


        assertEquals("German", LanguageUtils.getDisplayName(Locale.ENGLISH, "ger"));
        assertEquals("German", LanguageUtils.getDisplayName(Locale.ENGLISH, "deu"));

        assertEquals("Allemand", LanguageUtils.getDisplayName(Locale.FRENCH, "ger"));
        assertEquals("Allemand", LanguageUtils.getDisplayName(Locale.FRENCH, "deu"));
    }
}