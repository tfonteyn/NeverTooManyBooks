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
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.Locale;
import java.util.MissingResourceException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * Languages.
 * <ul>
 * <li>ISO 639-1: two-letter codes, one per language</li>
 * <li>ISO 639-2: three-letter codes, for the same languages as 639-1</li>
 * </ul>
 * The JDK uses "ISO3" for the 3-character ISO 639-2 format (not to be confused with ISO 639-3)
 */
public final class LocaleUtils {

    /** The SharedPreferences name where we'll maintain our language to ISO mappings. */
    private static final String LANGUAGE_MAP = "language2iso3";

    private static Locale mCachedPreferredLocale;

    private LocaleUtils() {
    }

    /**
     * Check if the user-preferred Locale is different from the currently in use Locale.
     *
     * @return {@code true} if there is a change (difference)
     */
    public static boolean isChanged(@NonNull final Context context) {
        boolean changed = !from(context).equals(getPreferredLocale());
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debug(LocaleUtils.class, "isChanged", "=" + changed);
        }
        return changed;
    }

    /**
     * Load the Locale setting from the users SharedPreference if needed.
     *
     * @param context to apply the user-preferred locale to.
     */
    public static void applyPreferred(@NonNull final Context context) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debugEnter(LocaleUtils.class, "applyPreferred",
                              toDebugString(context));
        }

        if (!isChanged(context)) {
            return;
        }

        // null it first, so the getPreferredLocale call will re-reset it.
        mCachedPreferredLocale = null;
        mCachedPreferredLocale = getPreferredLocale();

        // Apply the user-preferred Locale globally.... but this does not override already
        // loaded resources. So.. this is for FUTURE use when new resources get initialised.
        Locale.setDefault(mCachedPreferredLocale);

        // Apply to the resources as passed in.
        // create a delta-configuration; i.e. only to be used to modify the added items.
        Configuration deltaOnlyConfig = new Configuration();
        deltaOnlyConfig.setLocale(mCachedPreferredLocale);

        Resources resources = context.getResources();
        resources.updateConfiguration(deltaOnlyConfig, resources.getDisplayMetrics());

        // see if we need to add mappings for the new/current locale
        createLanguageMappingCache(getLanguageCache(), mCachedPreferredLocale);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debugExit(LocaleUtils.class, "applyPreferred",
                             toDebugString(context));
        }
    }

    /**
     * Test if the passed Locale is actually a 'real' Locale by checking ISO codes.
     */
    public static boolean isValid(@Nullable final Locale locale) {
        if (locale == null) {
            return false;
        }
        try {
            // MissingResourceException
            // NullPointerException can be thrown from within, when ISO3Language() fails.
            return locale.getISO3Language() != null && locale.getISO3Country() != null;
        } catch (@NonNull final MissingResourceException e) {
            // log but ignore.
            Logger.debug(LocaleUtils.class, "isValid",
                         "e=" + e.getLocalizedMessage(),
                         "locale=" + locale);
            return false;

        } catch (@NonNull final RuntimeException ignore) {
            return false;
        }
    }

    /**
     * @return the user-preferred Locale as stored in the preferences.
     */
    @NonNull
    public static Locale getPreferredLocale() {
        String lang = PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                       .getString(Prefs.pk_ui_language, null);

        if (mCachedPreferredLocale == null) {
            // the string "system" is also hardcoded in the preference string-array and
            // in the default setting in the preference screen.
            if (lang == null || lang.isEmpty() || "system".equalsIgnoreCase(lang)) {
                mCachedPreferredLocale = App.getSystemLocale();
            } else {
                mCachedPreferredLocale = from(lang);
            }
        }
        return mCachedPreferredLocale;
    }

    /**
     * syntax sugar...
     *
     * @return the current Locale for the passed context.
     */
    @NonNull
    public static Locale from(@NonNull final Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        return locale != null ? locale : Locale.ENGLISH;
    }

    /**
     * Creates a Locale from a concatenated locale string.
     *
     * @param code Locale name (e.g. 'de', 'en_AU')
     *
     * @return Locale corresponding to passed name
     */
    @NonNull
    private static Locale from(@NonNull final String code) {
        String[] parts;
        if (code.contains("_")) {
            parts = code.split("_");
        } else {
            parts = code.split("-");
        }
        Locale locale;
        switch (parts.length) {
            case 1:
                locale = new Locale(parts[0]);
                break;
            case 2:
                locale = new Locale(parts[0], parts[1]);
                break;
            default:
                locale = new Locale(parts[0], parts[1], parts[2]);
                break;
        }
        return locale;
    }

    /**
     * Load a Resources set for the specified Locale.
     * This is an expensive lookup; we do not cache the Resources here,
     * but it's advisable to cache the strings (map of locale/string for example) being looked up.
     *
     * @param context       Current context
     * @param desiredLocale the desired Locale, e.g. the locale of a book,series,toc,...
     *
     * @return the Resources
     */
    @NonNull
    public static Resources getLocalizedResources(@NonNull final Context context,
                                                  @NonNull final Locale desiredLocale) {
        Configuration current = context.getResources().getConfiguration();
        Configuration configuration = new Configuration(current);
        String lang = desiredLocale.getLanguage();
        if (lang.length() == 2) {
            configuration.setLocale(desiredLocale);
        } else {
            // any 3-char code needs to be converted to 2-char be able to find the resource.
            configuration.setLocale(new Locale(mapIso3toIso2(lang)));
        }

        Context localizedContext = context.createConfigurationContext(configuration);
        return localizedContext.getResources();
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
        String iso = getLanguageCache().getString(displayName, null);
        if (iso != null) {
            return iso;
        }
        return displayName;
    }

    /**
     * Try to convert a "language-country" code to an ISO code.
     *
     * @param iso2 a standard ISO string like "en" or "en-GB" or "en-GB*"
     *
     * @return the ISO code, or if conversion failed, the input string
     */
    @NonNull
    public static String getIso3FromIso2(@NonNull final String iso2) {
        // shortcut for English "en", "en-GB", etc
        if ("en".equals(iso2) || iso2.startsWith("en-")) {
            return "eng";
        } else {
            try {
                Locale bl = new Locale(iso2.split("-")[0]);
                return bl.getISO3Language();
            } catch (@NonNull final MissingResourceException ignore) {
                return iso2;
            }
        }
    }

    /**
     * Try to convert a Language ISO code to the display name.
     *
     * @param iso the ISO code
     *
     * @return the display name for the language,
     * or the input string itself if it was an invalid ISO code
     */
    @NonNull
    public static String getDisplayName(@NonNull final Context context,
                                        @NonNull final String iso) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debugEnter(LocaleUtils.class, "getLabel",
                              "iso=" + iso, toDebugString(context));
        }
        return getDisplayName(from(context), iso);
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
        Locale isoLocale = new Locale(iso);
        if (isValid(isoLocale)) {
            return isoLocale.getDisplayLanguage(locale);
        }
        return iso;
    }

//    Overkill... use the switch structure instead.
//    @Nullable
//    private static Map<String, String> LANG3_TO_LANG2_MAP;
//
//    private static String mapIso3toIso2viaMap(@NonNull final String iso3) {
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
     * Map an ISO 639-2 (3-char) language code to an ISO 639-1 (2-char) language code.
     * <p>
     * There is one entry here for each language supported.
     * NEWKIND: if a new resource language is added, add a mapping here.
     *
     * @param iso3 ISO 639-2 (3-char) language code (either bibliographic or terminology coded)
     *
     * @return ISO 639-1 (2-char) language code
     */
    private static String mapIso3toIso2(@NonNull final String iso3) {
        if (iso3.length() < 3) {
            return iso3;
        }

        switch (iso3) {
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

    /**
     * Convert the 3-char bibliographic code to the Java used terminology code.
     * <p>
     * Goodreads uses the bibliographic code while Java uses the terminology code.
     * For example, in Java french is "fra" but GR uses "fre".
     *
     * <a href="https://www.loc.gov/standards/iso639-2/php/code_list.php">
     * https://www.loc.gov/standards/iso639-2/php/code_list.php</a>
     * <p>
     * This is the entire set correct as on 2019-08-22.
     */
    @NonNull
    public static String normaliseIso3(@NonNull final String lang) {
        switch (lang) {
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
                return lang;
        }
    }

    /**
     * generate initial language2iso mappings.
     */
    public static void createLanguageMappingCache() {
        SharedPreferences prefs = getLanguageCache();

        // the one the user has configured our app into using
        createLanguageMappingCache(prefs, Locale.getDefault());

        // the system default
        createLanguageMappingCache(prefs, App.getSystemLocale());
        // and English for compatibility with lots of websites.
        createLanguageMappingCache(prefs, Locale.ENGLISH);
    }

    /**
     * Generate language mappings for a given locale.
     */
    private static void createLanguageMappingCache(@NonNull final SharedPreferences prefs,
                                                   @NonNull final Locale myLocale) {
        // just return if already done for this locale.
        if (prefs.getBoolean(myLocale.getISO3Language(), false)) {
            return;
        }
        SharedPreferences.Editor ed = prefs.edit();
        for (Locale loc : Locale.getAvailableLocales()) {
            ed.putString(loc.getDisplayLanguage(myLocale), loc.getISO3Language());
        }
        // signal this locale was done
        ed.putBoolean(myLocale.getISO3Language(), true);
        ed.apply();
    }

    /** Convenience method to get the language SharedPreferences file. */
    private static SharedPreferences getLanguageCache() {
        return App.getAppContext().getSharedPreferences(LANGUAGE_MAP, Context.MODE_PRIVATE);
    }

    public static String toDebugString(@NonNull final Context context) {
        Locale current = from(context);
        Locale configLocale = context.getResources().getConfiguration().locale;
        if (configLocale == null) {
            configLocale = Locale.ENGLISH;
        }
        return ""
               + "\nsSystemInitialLocale       : " + App.getSystemLocale().getDisplayName()
               + "\nsSystemInitialLocale(cur)  : " + App.getSystemLocale().getDisplayName(current)
               + "\nconfiguration.locale       : " + configLocale.getDisplayName()
               + "\nconfiguration.locale(cur)  : " + configLocale.getDisplayName(current)

               + "\nLocale.getDefault()        : " + Locale.getDefault().getDisplayName()
               + "\nLocale.getDefault(cur)     : " + Locale.getDefault().getDisplayName(current)
               + "\ngetPreferredLocale()       : " + getPreferredLocale().getDisplayName()
               + "\ngetPreferredLocale(cur)    : " + getPreferredLocale().getDisplayName(current)

               + "\nApp.isInNeedOfRecreating() : " + App.isInNeedOfRecreating()
               + "\nApp.isRecreating()         : " + App.isRecreating();
    }
}
