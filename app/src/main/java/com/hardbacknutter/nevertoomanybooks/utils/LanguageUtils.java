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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.MissingResourceException;

import com.hardbacknutter.nevertoomanybooks.App;

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

    private LanguageUtils() {
    }

    /**
     * Try to convert a Language ISO code to the display name.
     *
     * @param locale to use for the output language
     * @param iso    the ISO code
     *
     * @return the display name for the language,
     * or the input string itself if it was an invalid ISO code
     */
    @NonNull
    public static String getDisplayName(@NonNull final Locale locale,
                                        @NonNull final String iso) {
        Locale isoLocale = new Locale(iso3ToBibliographic(iso.trim()));
        if (LocaleUtils.isValid(isoLocale)) {
            return isoLocale.getDisplayLanguage(locale);
        }
        return iso;
    }

    /**
     * Try to convert a Language DisplayName to an ISO3 code.
     * At installation (or upgrade to v200) we generated the users System Locale + Locale.ENGLISH
     * Each time the user switches language, we generate an additional set.
     * That probably covers a lot if not all.
     *
     * @param displayName the string as normally produced by {@link Locale#getDisplayLanguage}
     *
     * @return the ISO code, or if conversion failed, the input string
     */
    @NonNull
    public static String getIso3fromDisplayName(@NonNull final String displayName) {
        return getIso3fromDisplayName(displayName,
                                      LocaleUtils.getLocale(App.getLocalizedAppContext()));
    }

    /**
     * Try to convert a Language DisplayName to an ISO3 code.
     * At installation (or upgrade to v200) we generated the users System Locale + Locale.ENGLISH
     * Each time the user switches language, we generate an additional set.
     * That probably covers a lot if not all.
     *
     * <strong>Note:</strong> {@code Locale.getISO3Language()} returns "deu" for German; while
     * {@code new Locale("ger")} needs "ger". So once again {@link #iso3ToBibliographic} is needed.
     *
     * @param displayName the string as normally produced by {@link Locale#getDisplayLanguage}
     * @param userLocale  the locale the user is running the app in.
     *
     * @return the ISO code, or if conversion failed, the input string
     */
    @NonNull
    public static String getIso3fromDisplayName(@NonNull final String displayName,
                                                @NonNull final Locale userLocale) {

        String source = displayName.trim();
        String iso = getLanguageCache().getString(source.toLowerCase(userLocale), null);
        if (iso == null) {
            return source;
        }
        return iso3ToBibliographic(iso);
    }

    /**
     * Try to convert a "language-country" code to an ISO code.
     *
     * @param iso2 a standard ISO string like "en" or "en-GB" or "en-GB*"
     *
     * @return the ISO code, or if conversion failed, the input string
     */
    @NonNull
    public static String getIso3fromIso2(@NonNull final String iso2) {
        String source = iso2.trim();
        // shortcut for English "en", "en-GB", etc
        if ("en".equals(source) || source.startsWith("en-")) {
            return "eng";
        } else {
            try {
                Locale bl = new Locale(source.split("-")[0]);
                return bl.getISO3Language();
            } catch (@NonNull final MissingResourceException ignore) {
                return source;
            }
        }
    }

    /**
     * Map an ISO 639-2 (3-char) language code to an ISO 639-1 (2-char) language code.
     * <p>
     * There is one entry here for each language supported.
     * NEWKIND: if a new resource language is added, add a mapping here.
     *
     * @param iso3 ISO 639-2 (3-char) language code (either bibliographic or terminology coded)
     *
     * @return ISO 639-1 (2-char) language code
     */
    public static String getIso2fromIso3(@NonNull final String iso3) {
        String source = iso3.trim();
        if (source.length() < 3) {
            return source;
        }

        switch (source) {
            // English
            case "eng":
                return "en";
            // Czech
            case "cze":
            case "ces":
                return "cs";
            // German
            case "ger":
            case "deu":
                return "de";
            // Greek
            case "gre":
            case "ell":
                return "el";
            // Spanish
            case "spa":
                return "es";
            // French
            case "fre":
            case "fra":
                return "fr";
            // Italian
            case "ita":
                return "it";
            // Dutch
            case "dut":
            case "nld":
                return "nl";
            // Polish
            case "pol":
                return "pl";
            // Russian
            case "rus":
                return "ru";
            // Turkish
            case "tur":
                return "tr";

            default:
                // anything else is not supported, and (FLW) should never happen.
                return "en";
        }
    }

    //    Overkill... use the switch structure instead.
//    @Nullable
//    private static Map<String, String> LANG3_TO_LANG2_MAP;
//
//    private static String getIso2fromIso3viaMap(@NonNull final String iso3) {
//        if (LANG3_TO_LANG2_MAP == null) {
//            String[] languages = Locale.getISOLanguages();
//            LANG3_TO_LANG2_MAP = new HashMap<>(languages.length);
//            for (String language : languages) {
//                Locale locale = new Locale(language);
//                LANG3_TO_LANG2_MAP.put(locale.getISO3Language(), language);
//            }
//        }
//        String iso2 = LANG3_TO_LANG2_MAP.get(iso3);
//        return iso2 != null ? iso2 : iso3;
//    }

    /**
     * Convert the 3-char terminology code to bibliographic code.
     *
     * <a href="https://www.loc.gov/standards/iso639-2/php/code_list.php">
     * https://www.loc.gov/standards/iso639-2/php/code_list.php</a>
     * <p>
     * This is the entire set correct as on 2019-08-22.
     */
    @NonNull
    public static String iso3ToBibliographic(@NonNull final String lang) {
        String source = lang.trim();
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
     * Convert the 3-char bibliographic code to terminology code..
     *
     * <a href="https://www.loc.gov/standards/iso639-2/php/code_list.php">
     * https://www.loc.gov/standards/iso639-2/php/code_list.php</a>
     * <p>
     * This is the entire set correct as on 2019-08-22.
     */
    @SuppressWarnings("unused")
    @NonNull
    public static String iso3ToTerminology(@NonNull final String lang) {
        String source = lang.trim();
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
     */
    public static void createLanguageMappingCache(@NonNull final Locale userLocale) {
        SharedPreferences prefs = getLanguageCache();

        // the one the user has configured our app into using
        createLanguageMappingCache(prefs, userLocale);

        // the system default
        createLanguageMappingCache(prefs, App.getSystemLocale());
        // and English for compatibility with lots of websites.
        createLanguageMappingCache(prefs, Locale.ENGLISH);
    }

    /**
     * Generate language mappings for a given locale.
     *
     * @param prefs      the preferences 'file' used as our language names cache.
     * @param userLocale the locale the user is running the app in.
     */
    private static void createLanguageMappingCache(@NonNull final SharedPreferences prefs,
                                                   @NonNull final Locale userLocale) {
        // just return if already done for this locale.
        if (prefs.getBoolean(userLocale.getISO3Language(), false)) {
            return;
        }
        SharedPreferences.Editor ed = prefs.edit();
        for (Locale loc : Locale.getAvailableLocales()) {
            ed.putString(loc.getDisplayLanguage(userLocale).toLowerCase(userLocale),
                         loc.getISO3Language());
        }
        // signal this locale was done
        ed.putBoolean(userLocale.getISO3Language(), true);
        ed.apply();
    }

    /** Convenience method to get the language SharedPreferences file. */
    private static SharedPreferences getLanguageCache() {
        return App.getAppContext().getSharedPreferences(LANGUAGE_MAP, Context.MODE_PRIVATE);
    }

    public static String toDebugString(@NonNull final Context context) {
        Locale configLocale;
        if (Build.VERSION.SDK_INT >= 24) {
            configLocale = context.getResources().getConfiguration().getLocales().get(0);
        } else {
            configLocale = context.getResources().getConfiguration().locale;
        }

        Locale systemLocale = App.getSystemLocale();
        Locale defaultLocale = Locale.getDefault();
        Locale prefLocale = LocaleUtils.getLocale(context);

        return ""
               + "\nsSystemInitialLocale       : " + systemLocale.getDisplayName()
               + "\nsSystemInitialLocale(cur)  : " + systemLocale.getDisplayName(configLocale)
               + "\nconfiguration.locale       : " + configLocale.getDisplayName()
               + "\nconfiguration.locale(cur)  : " + configLocale.getDisplayName(configLocale)
               + "\nLocale.getDefault()        : " + defaultLocale.getDisplayName()
               + "\nLocale.getDefault(cur)     : " + defaultLocale.getDisplayName(configLocale)
               + "\ngetPreferredLocale()       : " + prefLocale.getDisplayName()
               + "\ngetPreferredLocale(cur)    : " + prefLocale.getDisplayName(configLocale)
               + "\nApp.isInNeedOfRecreating() : " + App.isInNeedOfRecreating()
               + "\nApp.isRecreating()         : " + App.isRecreating();
    }
}
