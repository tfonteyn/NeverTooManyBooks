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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.LocaleList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.dao.IsoLanguageDao;
import com.hardbacknutter.nevertoomanybooks.tasks.BuildLanguageMappingsTask;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Languages.
 * <ul>
 *      <li><a href="https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes">ISO 639-1</a>
 *          two-letter codes, one per language</li>
 *      <li><a href="https://en.wikipedia.org/wiki/ISO_639-2">ISO 639-2</a>
 *          three-letter codes, for the same languages as 639-1</li>
 * </ul>
 * The JDK uses "ISO3" for the 3-character ISO 639-2 format (not to be confused with ISO 639-3)
 */
@SuppressWarnings("WeakerAccess")
public class Languages {

    private static final String TAG = "Languages";
    /** Prefix added to the iso code for the 'done' flag in the language cache. */
    public static final String PK_LANG_CREATED_PREFIX = "language.mapping.cached.";

    @NonNull
    private final Map<String, String> lang3ToLang2Map;
    @NonNull
    private final Supplier<IsoLanguageDao> isoLanguageDao;

    /**
     * Constructor.
     *
     * @param isoLanguageDao deferred supplier for the {@link IsoLanguageDao}.
     */
    public Languages(@NonNull final Supplier<IsoLanguageDao> isoLanguageDao) {
        this.isoLanguageDao = isoLanguageDao;

        final String[] languages = Locale.getISOLanguages();
        lang3ToLang2Map = new HashMap<>(languages.length);
        Arrays.stream(languages).forEach(
                language -> lang3ToLang2Map.put(getIsoCode(new Locale(language)), language));
    }

    /**
     * Try to convert a Language to a Locale.
     *
     * @param language ISO codes (2 or 3 char), or a display-string (4+ characters)
     * @param locale   Current Locale
     *
     * @return the best matching Locale we could determine
     *
     * @see AppLocale#getLocale(String, Locale)
     */
    @NonNull
    public Locale toLocale(@Nullable final String language,
                           @NonNull final Locale locale) {
        if (language == null || language.isBlank()) {
            return locale;
        }
        return ServiceLocator.getInstance().getAppLocale()
                             .getLocale(language, locale)
                             .orElse(locale);
    }

    /**
     * Try to convert a Language ISO code to the display name.
     *
     * @param iso3   the ISO code
     * @param locale Current Locale
     *
     * @return the display name for the language,
     *         or the input string itself if it was an invalid ISO code
     */
    @NonNull
    public String getDisplayLanguageFromISO3(@NonNull final String iso3,
                                             @NonNull final Locale locale) {
        return ServiceLocator.getInstance().getAppLocale().getLocale(iso3, locale)
                             .map(l -> l.getDisplayLanguage(locale))
                             .orElse(iso3);
    }

    /**
     * Try to convert a language string to an ISO3 code.
     * At installation time we generated the users System Locale + {@link Locale#ENGLISH}.
     * Each time the user switches language, we generate an additional set.
     * That probably covers a lot if not all.
     *
     * @param language the string as normally produced by {@link Locale#getDisplayLanguage}
     * @param locale   the locale of the language string
     *
     * @return the ISO code, or if conversion failed, the input string
     */
    @NonNull
    public String getISO3FromDisplayLanguage(@NonNull final String language,
                                             @NonNull final Locale locale) {

        final String source = language.strip().toLowerCase(locale);
        if (source.isEmpty()) {
            return "";
        }
        // create the mappings for the given locale if they don't exist yet
        createLanguageMappingCache(locale);

        return isoLanguageDao.get().findByDisplayName(source);
    }

    /**
     * Try to convert a "language-country" code to an ISO3 code.
     *
     * @param code a standard ISO string like "en" or "en-GB" or "en-GB*"
     *
     * @return the ISO code, or if conversion failed, the input string
     */
    @NonNull
    public String getISO3FromCode(@NonNull final String code) {
        // shortcut for English "en", "en-GB", etc
        if ("en".equals(code) || code.startsWith("en_") || code.startsWith("en-")) {
            return "eng";
        } else {
            try {
                return getIsoCode(ServiceLocator.getInstance().getAppLocale().create(code));
            } catch (@NonNull final MissingResourceException ignore) {
                return code;
            }
        }
    }

    /**
     * Map an ISO 639-2 (3-char) language code to a language code suited
     * to be used with {@code new Locale(x)}.
     * <pre>
     * Rant:
     * Java 8 as bundled with Android Studio 3.5 on Windows:
     * + OpenJDK Java 17 on Windows:
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
     * <strong>Note:</strong> check the Javadoc on {@link Locale#getISOLanguages()} for caveats.
     *
     * @param iso3   ISO 639-2 (3-char) language code
     *               (either bibliographic or terminology coded)
     * @param locale Current Locale
     *
     * @return a language code that can be used with {@code new Locale(x)},
     *         or the incoming string if conversion failed.
     */
    @NonNull
    String getLocaleIsoFromISO3(@NonNull final String iso3,
                                @NonNull final Locale locale) {

        String iso2 = lang3ToLang2Map.get(iso3);
        if (iso2 != null) {
            return iso2;
        }

        // try again ('terminology' seems to be preferred/standard on Android (ICU?)
        String lang = toTerminology(iso3, locale);
        iso2 = lang3ToLang2Map.get(lang);
        if (iso2 != null) {
            return iso2;
        }

        // desperate and last attempt using 'bibliographic'.
        lang = toBibliographic(iso3, locale);
        iso2 = lang3ToLang2Map.get(lang);
        if (iso2 != null) {
            return iso2;
        }

        // give up
        return iso3;
    }

    /**
     * Convert the 3-char terminology code to bibliographic code.
     * <p>
     * <a href="https://www.loc.gov/standards/iso639-2/php/code_list.php">iso639-2</a>
     * <p>
     * This is the entire set correct as on 2019-08-22.
     *
     * @param iso3   ISO 639-2 (3-char) language code (either bibliographic or terminology coded)
     * @param locale Current Locale
     *
     * @return the bibliographic code
     */
    @NonNull
    public String toBibliographic(@NonNull final String iso3,
                                  @NonNull final Locale locale) {
        final String source = iso3.strip().toLowerCase(locale);
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
     * <p>
     * <a href="https://www.loc.gov/standards/iso639-2/php/code_list.php">iso639-2</a>
     * <p>
     * This is the entire set correct as on 2019-08-22.
     *
     * @param iso3   ISO 639-2 (3-char) language code (either bibliographic or terminology coded)
     * @param locale Current Locale
     *
     * @return the terminology code
     */
    @NonNull
    public String toTerminology(@NonNull final String iso3,
                                @NonNull final Locale locale) {
        final String source = iso3.strip().toLowerCase(locale);
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
     * Create all cache files. This method is called during startup
     * from {@link BuildLanguageMappingsTask}.
     *
     * @param context Current context
     */
    public void createLanguageMappingCache(@NonNull final Context context) {
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = new ArrayList<>(LocaleListUtils.asList(userLocales));
        // Always add English
        allLocales.add(Locale.ENGLISH);
        allLocales.forEach(this::createLanguageMappingCache);

        // Locales from SearchEngine's are added automatically as/when needed
    }

    /**
     * Generate language mappings for a given Locale.
     *
     * @param locale the Locale for which to create a mapping
     */
    @VisibleForTesting
    public void createLanguageMappingCache(@NonNull final Locale locale) {
        final SharedPreferences preferences =
                ServiceLocator.getInstance().getSharedPreferences();

        final String isoCode = getIsoCode(locale);

        // Paranoia...
        // - when during an import the pref key would accidentally be included
        // - in the developer environment, when databases are swapped in manually
        if (isoLanguageDao.get().count() == 0) {
            final SharedPreferences.Editor editor = preferences.edit();
            preferences.getAll()
                       .keySet()
                       .stream()
                       .filter(key -> key.startsWith(PK_LANG_CREATED_PREFIX))
                       .forEach(editor::remove);
            editor.apply();
        }

        // just return if already done for this Locale.
        if (preferences.getBoolean(PK_LANG_CREATED_PREFIX + isoCode, false)) {
            return;
        }

        try {
            isoLanguageDao.get().add(locale);
        } catch (@NonNull final DaoInsertException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }

        // remember this Locale was done
        preferences.edit()
                   .putBoolean(PK_LANG_CREATED_PREFIX + isoCode, true)
                   .apply();
    }

    /**
     * We've seen {@link Locale#getISO3Language()} throw {@link MissingResourceException}
     * on a HUAWEI model "ADA-AL10U" with Android 12; see GitHub #58
     * The OS code returned the invalid code "zz" for a language
     * in {@link Locale#getAvailableLocales()}.
     *
     * @param locale to get the ISO3/ISO2 code from.
     *
     * @return iso3/2 code
     *
     * @see <a href="https://github.com/tfonteyn/NeverTooManyBooks/issues/58">GitHub #58</a>
     */
    @NonNull
    private String getIsoCode(@NonNull final Locale locale) {
        String isoCode;
        try {
            isoCode = locale.getISO3Language();
        } catch (@NonNull final MissingResourceException mre) {
            // Fallback to 2-character code
            isoCode = locale.getLanguage();
        }
        return isoCode;
    }

    /**
     * Check if the device or user Locales has the given language enabled.
     * <p>
     * Non-English sites are by default only enabled if either the device or
     * this app has the specified language enabled.
     * The user can still enable/disable them at will of course.
     *
     * @param context Current context
     * @param iso     language code to check
     *
     * @return {@code true} if sites should be enabled by default.
     */
    public boolean isUserLanguage(@NonNull final Context context,
                                  @NonNull final String iso) {
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        return LocaleListUtils.asList(userLocales)
                              .stream()
                              .map(this::getIsoCode)
                              .anyMatch(iso::equals);
    }

    /**
     * Generate and return a list of language ISO codes consisting
     * of the user languages and the supported app locales.
     * <p>
     * This is used as an initial list for new users when the database
     * does not contain any languages yet.
     *
     * @param locales to create codes for
     *
     * @return The list of ISO 639-2 codes
     */
    @NonNull
    public List<String> getDefaultCodes(@NonNull final List<Locale> locales) {
        final Set<String> set = new LinkedHashSet<>();
        // Keep in mind all these are JDK language/locale codes
        // which need converting to ISO3 (639-2)

        // all user locales
        locales.stream()
               .map(Locale::getLanguage)
               .map(this::getISO3FromCode)
               .forEach(set::add);

        // and all supported locales.
        Arrays.stream(BuildConfig.SUPPORTED_LOCALES)
              .map(this::getISO3FromCode)
              .forEach(set::add);

        return new ArrayList<>(set);
    }
}
