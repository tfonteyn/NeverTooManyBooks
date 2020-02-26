/*
 * @Copyright 2020 HardBackNutter
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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoSearchEngine;

/**
 * Languages.
 * <ul>
 * <li>ISO 639-1: two-letter codes, one per language</li>
 * <li>ISO 639-2: three-letter codes, for the same languages as 639-1</li>
 * </ul>
 * The JDK uses "ISO3" for the 3-character ISO 639-2 format (not to be confused with ISO 639-3)
 */
public final class LanguageUtils {

    /**
     * The SharedPreferences name where we'll maintain our language to ISO mappings.
     * Uses the {@link Locale#getDisplayName()} for the key,
     * and {@link Locale#getISO3Language} for the value.
     */
    private static final String LANGUAGE_MAP = "language2iso3";
    @Nullable
    private static Map<String, String> LANG3_TO_LANG2_MAP;

    private LanguageUtils() {
    }

    /**
     * Try to convert a Language ISO code to the display name.
     *
     * @param context Current context
     * @param iso     the ISO code
     *
     * @return the display name for the language,
     * or the input string itself if it was an invalid ISO code
     */
    @NonNull
    public static String getDisplayName(@NonNull final Context context,
                                        @NonNull final String iso) {
        Locale langLocale = LocaleUtils.getLocale(context, iso);
        if (langLocale == null) {
            return iso;
        }
        return langLocale.getDisplayLanguage(LocaleUtils.getUserLocale(context));
    }

    /**
     * Try to convert a Language DisplayName to an ISO3 code.
     * At installation time we generated the users System Locale + Locale.ENGLISH
     * Each time the user switches language, we generate an additional set.
     * That probably covers a lot if not all.
     *
     * @param context     Current context
     * @param displayName the string as normally produced by {@link Locale#getDisplayLanguage}
     *
     * @return the ISO code, or if conversion failed, the input string
     */
    @NonNull
    public static String getISO3FromDisplayName(@NonNull final Context context,
                                                @NonNull final String displayName) {

        String source = displayName.trim().toLowerCase(LocaleUtils.getUserLocale(context));
        return getLanguageCache(context).getString(source, source);
    }

    /**
     * Try to convert a "language-country" code to an ISO3 code.
     *
     * @param source a standard ISO string like "en" or "en-GB" or "en-GB*"
     *
     * @return the ISO code, or if conversion failed, the input string
     */
    @NonNull
    public static String getISO3Language(@NonNull final String source) {
        // shortcut for English "en", "en-GB", etc
        if ("en".equals(source) || source.startsWith("en_") || source.startsWith("en-")) {
            return "eng";
        } else {
            try {
                Locale bl = LocaleUtils.createLocale(source);
                return bl.getISO3Language();
            } catch (@NonNull final MissingResourceException ignore) {
                return source;
            }
        }
    }

    /**
     * Map an ISO 639-2 (3-char) language code to a language code suited
     * to be use with {@code new Locale(x)}.
     * <pre>
     * Rant:
     * Java 8 as bundled with Android Studio 3.5 on Windows:
     * new Locale("fr") ==> valid French Locale
     * new Locale("fre") ==> valid French Locale
     * new Locale("fra") ==> INVALID French Locale
     *
     * Android 8.0 in emulator bundled with Android Studio 3.5 on Windows:
     * new Locale("fr") ==> valid French Locale
     * new Locale("fre") ==> INVALID French Locale
     * new Locale("fra") ==>  valid French Locale
     * </pre>
     * A possible explanation is that Android use ICU classes internally.<br>
     * Also see {@link #toBibliographic} and {@link #toTerminology}.
     * <br><br>
     * <strong>Note:</strong> check the javadoc on {@link Locale#getISOLanguages()} for caveats.
     *
     * @param context Current context
     * @param iso3    ISO 639-2 (3-char) language code (either bibliographic or terminology coded)
     *
     * @return a language code that can be used with {@code new Locale(x)},
     * or the incoming string if conversion failed.
     */
    public static String getLocaleIsoFromIso3(@NonNull final Context context,
                                              @NonNull final String iso3) {
        // create the map on first usage
        if (LANG3_TO_LANG2_MAP == null) {
            String[] languages = Locale.getISOLanguages();
            LANG3_TO_LANG2_MAP = new HashMap<>(languages.length);
            for (String language : languages) {
                Locale locale = new Locale(language);
                LANG3_TO_LANG2_MAP.put(locale.getISO3Language(), language);
            }
        }

        String iso2 = LANG3_TO_LANG2_MAP.get(iso3);
        if (iso2 != null) {
            return iso2;
        }

        Locale locale = LocaleUtils.getUserLocale(context);

        // try again ('terminology' seems to be preferred/standard on Android (ICU?)
        String lang = LanguageUtils.toTerminology(locale, iso3);
        iso2 = LANG3_TO_LANG2_MAP.get(lang);
        if (iso2 != null) {
            return iso2;
        }

        // desperate and last attempt using 'bibliographic'.
        lang = LanguageUtils.toBibliographic(locale, iso3);
        iso2 = LANG3_TO_LANG2_MAP.get(lang);
        if (iso2 != null) {
            return iso2;
        }

        // give up
        return iso3;
    }

    /**
     * Convert the 3-char terminology code to bibliographic code.
     *
     * <a href="https://www.loc.gov/standards/iso639-2/php/code_list.php">iso639-2</a>
     * <p>
     * This is the entire set correct as on 2019-08-22.
     *
     * @param iso3 ISO 639-2 (3-char) language code (either bibliographic or terminology coded)
     */
    @NonNull
    public static String toBibliographic(@NonNull final Locale locale,
                                         @NonNull final String iso3) {
        String source = iso3.trim().toLowerCase(locale);
        if (source.length() != 3) {
            return source;
        }

        switch (source) {
            // Albanian
            case "sqi":
                return "alb";
            // Armenian
            case "hye":
                return "arm";
            // Basque
            case "eus":
                return "baq";
            // Burmese
            case "mya":
                return "bur";
            // Chinese
            case "zho":
                return "chi";
            // Czech
            case "ces":
                return "cze";
            // Dutch
            case "dut":
                return "nld";
            // French
            case "fra":
                return "fre";
            // Georgian
            case "kat":
                return "geo";
            // German
            case "deu":
                return "ger";
            // Greek
            case "ell":
                return "gre";
            // Icelandic
            case "isl":
                return "ice";
            // Macedonian
            case "mkd":
                return "mac";
            // Maori
            case "mri":
                return "mao";
            // Malay
            case "msa":
                return "may";
            // Persian
            case "fas":
                return "per";
            // Romanian
            case "ron":
                return "rum";
            // Slovak
            case "slk":
                return "slo";
            // Tibetan
            case "bod":
                return "tib";
            // Welsh
            case "cym":
                return "wel";

            default:
                return source;
        }
    }

    /**
     * Convert the 3-char bibliographic code to terminology code.
     *
     * <a href="https://www.loc.gov/standards/iso639-2/php/code_list.php">iso639-2</a>
     * <p>
     * This is the entire set correct as on 2019-08-22.
     *
     * @param iso3 ISO 639-2 (3-char) language code (either bibliographic or terminology coded)
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    @NonNull
    public static String toTerminology(@NonNull final Locale locale,
                                       @NonNull final String iso3) {
        String source = iso3.trim().toLowerCase(locale);
        if (source.length() != 3) {
            return source;
        }
        switch (source) {
            // Albanian
            case "alb":
                return "sqi";
            // Armenian
            case "arm":
                return "hye";
            // Basque
            case "baq":
                return "eus";
            // Burmese
            case "bur":
                return "mya";
            // Chinese
            case "chi":
                return "zho";
            // Czech
            case "cze":
                return "ces";
            // Dutch
            case "dut":
                return "nld";
            // French
            case "fre":
                return "fra";
            // Georgian
            case "geo":
                return "kat";
            // German
            case "ger":
                return "deu";
            // Greek
            case "gre":
                return "ell";
            // Icelandic
            case "ice":
                return "isl";
            // Macedonian
            case "mac":
                return "mkd";
            // Maori
            case "mao":
                return "mri";
            // Malay
            case "may":
                return "msa";
            // Persian
            case "per":
                return "fas";
            // Romanian
            case "rum":
                return "ron";
            // Slovak
            case "slo":
                return "slk";
            // Tibetan
            case "tib":
                return "bod";
            // Welsh
            case "wel":
                return "cym";

            default:
                return source;
        }
    }

    /**
     * generate initial language2iso mappings.
     *
     * @param context Current context
     */
    public static void createLanguageMappingCache(@NonNull final Context context) {

        SharedPreferences prefs = getLanguageCache(context);

        // the one the user is using our app in (can be different from the system one)
        createLanguageMappingCache(prefs, LocaleUtils.getUserLocale(context));

        // the system default
        createLanguageMappingCache(prefs, LocaleUtils.getSystemLocale());

        //NEWTHINGS: add new site specific ID: if a site uses a specific language, add it here

        // Dutch
        createLanguageMappingCache(prefs, StripInfoSearchEngine.SITE_LOCALE);
        // Dutch
        //createLanguageMappingCache(prefs, KbNlSearchEngine.SITE_LOCALE);

        // and English for compatibility with lots of websites.
        createLanguageMappingCache(prefs, Locale.ENGLISH);
    }

    /**
     * Generate language mappings for a given Locale.
     *
     * @param prefs  the SharedPreferences used as our language names cache.
     * @param locale the Locale for which to create a mapping
     */
    private static void createLanguageMappingCache(@NonNull final SharedPreferences prefs,
                                                   @NonNull final Locale locale) {
        // just return if already done for this Locale.
        if (prefs.getBoolean(locale.getISO3Language(), false)) {
            return;
        }
        SharedPreferences.Editor ed = prefs.edit();
        for (Locale loc : Locale.getAvailableLocales()) {
            ed.putString(loc.getDisplayLanguage(locale).toLowerCase(locale),
                         loc.getISO3Language());
        }
        // signal this Locale was done
        ed.putBoolean(locale.getISO3Language(), true);
        ed.apply();
    }

    /**
     * Convenience method to get the language SharedPreferences file.
     *
     * @param context Current context
     */
    private static SharedPreferences getLanguageCache(@NonNull final Context context) {
        return context.getSharedPreferences(LANGUAGE_MAP, Context.MODE_PRIVATE);
    }

    public static String toDebugString(@NonNull final Context context) {
        Locale userLocale = LocaleUtils.getUserLocale(context);
        Locale systemLocale = LocaleUtils.getSystemLocale();
        Locale defLocale = Locale.getDefault();

        return ""
               + "\nsystemLocale       : " + systemLocale.getDisplayName()
               + "\nsystemLocale(user) : " + systemLocale.getDisplayName(userLocale)

               + "\ndefLocale          : " + defLocale.getDisplayName()
               + "\ndefLocale(user)    : " + defLocale.getDisplayName(userLocale)

               + "\nuserLocale         : " + userLocale.getDisplayName()
               + "\nuserLocale(user)   : " + userLocale.getDisplayName(userLocale);
    }
}
