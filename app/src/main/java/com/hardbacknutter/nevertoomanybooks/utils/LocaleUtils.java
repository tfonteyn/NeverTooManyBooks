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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * A fresh and new approach to setting the custom Locale.
 * <p>
 * <pre>
 *  {@code
 *      public class MyActivity extends AppCompatActivity {
 *          private String mInitialLocaleSpec;
 *
 *          protected void onCreate(@Nullable final Bundle savedInstanceState) {
 *              super.onCreate(savedInstanceState);
 *              mInitialLocaleSpec = LocaleUtils.getPersistedLocaleSpec(this);
 *          }
 *
 *          protected void applyLocale(@NonNull final Context base) {
 *              super.applyLocale(LocaleUtils.applyLocale(base));
 *          }
 *
 *          protected void onResume() {
 *              super.onResume();
 *              if (LocaleUtils.isChanged(this, mInitialLocaleSpec)) {
 *                  recreate();
 *              }
 *          }
 *      }
 *  }
 * </pre>
 */
public final class LocaleUtils {

    private static final String TAG = "LocaleUtils";

    /**
     * Value stored in preferences if the user runs our app in the default device language.
     * The string "system" is hardcoded in the preference string-array and
     * in the default setting in the preference screen.
     */
    @VisibleForTesting
    public static final String SYSTEM_LANGUAGE = "system";

    private static final List<WeakReference<OnLocaleChangedListener>>
            ON_LOCALE_CHANGED_LISTENERS = new ArrayList<>();

    /** Cache for Locales; key: the BOOK language (ISO3). */
    private static final Map<String, Locale> LOCALE_MAP = new HashMap<>();

    /** Cache for the pv_reformat_titles_prefixes strings. */
    private static final Map<Locale, String> LOCALE_PREFIX_MAP = new HashMap<>();

    /** Remember the current language to detect when language is switched. */
    @NonNull
    private static String sPreferredLocaleSpec = SYSTEM_LANGUAGE;
    @Nullable
    private static Locale sPreferredLocale;

    private LocaleUtils() {
    }

    /**
     * Get the user-preferred Locale as stored in the preferences.
     *
     * @param context Current context
     *
     * @return a Locale specification as used for Android resources;
     * or {@link #SYSTEM_LANGUAGE} to use the system settings
     */
    @NonNull
    public static String getPersistedLocaleSpec(@NonNull final Context context) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);
        return p.getString(Prefs.pk_ui_locale, SYSTEM_LANGUAGE);
    }

    /**
     * Test if the passed Locale is actually a 'real' Locale
     * by comparing the ISO3 code and the display name. If they are different we assume 'valid'
     *
     * @param locale to test
     *
     * @return {@code true} if valid
     */
    public static boolean isValid(@Nullable final Locale locale) {
        if (locale == null) {
            return false;
        }
        try {
            // Running with system/default Locale set to Locale.ENGLISH:
            //
            // French: new Locale("fr")
            // fr.getLanguage()            fr
            // fr.getISO3Language()        fra      => CANNOT be used to recreate the Locale
            // fr.getDisplayLanguage()     French
            // fr.getDisplayName()         French
            //
            // French: new Locale("fre")
            // fre.getLanguage()            fre
            // fre.getISO3Language()        fre
            // fre.getDisplayLanguage()     French
            // fre.getDisplayName()         French
            //
            // French: new Locale("fra")
            // fra.getLanguage()            fra
            // fra.getISO3Language()        fra
            // fra.getDisplayLanguage()     fra
            // fra.getDisplayName()         fra

            String displayLanguage = locale.getDisplayLanguage();
            String iso3 = locale.getISO3Language();

            return !displayLanguage.isEmpty() && !displayLanguage.equalsIgnoreCase(iso3);

        } catch (@NonNull final MissingResourceException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "isValid|e=" + e.getLocalizedMessage() + "locale=" + locale);
            }
            return false;

        } catch (@NonNull final RuntimeException ignore) {
            // NullPointerException can be thrown  when ISO3Language() fails.
            return false;
        }
    }

    /**
     * Check if the passed localeSpec is different from the user preferred Locale.
     *
     * @param context    Current context
     * @param localeSpec to test
     *
     * @return {@code true} if different
     */
    public static boolean isChanged(@NonNull final Context context,
                                    @Nullable final String localeSpec) {
        return localeSpec == null || !localeSpec.equals(getPersistedLocaleSpec(context));
    }

    /**
     * Set the context's Locale to the user preferred localeSpec and return the updated context.
     * <p>
     * Set the system wide default Locale to the user preferred localeSpec.
     *
     * @param context to set the Locale on
     *
     * @return updated context
     */
    @NonNull
    public static Context applyLocale(@NonNull final Context context) {
        String localeSpec = getPersistedLocaleSpec(context);

        // create the Locale at first access, or if the requested is different from the current.
        if (sPreferredLocale == null || !sPreferredLocaleSpec.equals(localeSpec)) {
            sPreferredLocaleSpec = localeSpec;
            sPreferredLocale = createLocale(localeSpec);
        }

        // ALWAYS ALWAYS ALWAYS ALWAYS ALWAYS
        Locale.setDefault(sPreferredLocale);
        // ALWAYS ALWAYS ALWAYS ALWAYS ALWAYS

        Context updatedContext;
        if (Build.VERSION.SDK_INT >= 24) {
            updatedContext = updateResources(context, sPreferredLocale);
        } else {
            updatedContext = updateResourcesLegacy(context, sPreferredLocale);
        }
        insanityCheck(updatedContext);
        return updatedContext;
    }

    /**
     * Create a new Locale as specified by the localeSpec string.
     *
     * @param localeSpec a Locale specification as used for Android resources;
     *                   or {@link #SYSTEM_LANGUAGE} to use the system settings
     *
     * @return a new Locale
     */
    @NonNull
    public static Locale createLocale(@Nullable final String localeSpec) {

        if (localeSpec == null || localeSpec.isEmpty() || SYSTEM_LANGUAGE.equals(localeSpec)) {
            return getSystemLocale();
        } else {
            // Create a Locale from a concatenated Locale string (e.g. 'de', 'en_AU')
            String[] parts;
            if (localeSpec.contains("_")) {
                parts = localeSpec.split("_");
            } else {
                parts = localeSpec.split("-");
            }
            switch (parts.length) {
                case 1:
                    return new Locale(parts[0]);
                case 2:
                    return new Locale(parts[0], parts[1]);
                default:
                    return new Locale(parts[0], parts[1], parts[2]);
            }
        }
    }

    @NonNull
    public static Locale getSystemLocale() {
        if (Build.VERSION.SDK_INT >= 24) {
            return Resources.getSystem().getConfiguration().getLocales().get(0);
        } else {
            //noinspection deprecation
            return Resources.getSystem().getConfiguration().locale;
        }
    }

    @NonNull
    private static Locale getConfiguredLocale(@NonNull final Context context) {
        if (Build.VERSION.SDK_INT >= 24) {
            return context.getResources().getConfiguration().getLocales().get(0);
        } else {
            //noinspection deprecation
            return context.getResources().getConfiguration().locale;
        }
    }

    @TargetApi(24)
    @NonNull
    private static Context updateResources(@NonNull final Context context,
                                           @NonNull final Locale locale) {
        Configuration configuration = context.getResources().getConfiguration();
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);

        return context.createConfigurationContext(configuration);
    }

    @NonNull
    private static Context updateResourcesLegacy(@NonNull final Context context,
                                                 @NonNull final Locale locale) {
        Resources resources = context.getResources();

        Configuration configuration = resources.getConfiguration();
        configuration.locale = locale;
        configuration.setLayoutDirection(locale);

        resources.updateConfiguration(configuration, resources.getDisplayMetrics());

        return context;
    }

    public static void insanityCheck(@NonNull final Context context) {
        String persistedIso3 = createLocale(getPersistedLocaleSpec(context)).getISO3Language();
        String conIso3 = getConfiguredLocale(context).getISO3Language();
        String defIso3 = Locale.getDefault().getISO3Language();

        if (!defIso3.equals(conIso3)
            || !defIso3.equals(persistedIso3)
        ) {
            String error = "Locale.getDefault()=" + defIso3
                           + ", getConfiguration().locale=" + conIso3
                           + ", SharedPreferences=" + persistedIso3;
            Error e = new java.lang.VerifyError(error);
            Logger.error(context, TAG, e, error);
            throw e;
        }
    }

    /**
     * Add the specified listener. There is no protection against adding twice.
     *
     * @param listener to add
     */
    @SuppressWarnings("unused")
    public static void registerOnLocaleChangedListener(
            @NonNull final OnLocaleChangedListener listener) {
        synchronized (ON_LOCALE_CHANGED_LISTENERS) {
            ON_LOCALE_CHANGED_LISTENERS.add(new WeakReference<>(listener));
        }
    }

    /**
     * Remove the specified listener (and any dead ones we come across).
     *
     * @param listener to remove
     */
    @SuppressWarnings("unused")
    public static void unregisterOnLocaleChangedListener(
            @NonNull final OnLocaleChangedListener listener) {
        synchronized (ON_LOCALE_CHANGED_LISTENERS) {
            Iterator<WeakReference<OnLocaleChangedListener>> it =
                    ON_LOCALE_CHANGED_LISTENERS.iterator();
            while (it.hasNext()) {
                WeakReference<OnLocaleChangedListener> listenerRef = it.next();
                if (listenerRef.get() == null || listenerRef.get().equals(listener)) {
                    // remove dead listeners and the specified listener
                    it.remove();
                }
            }
        }
    }

    /**
     * Broadcast to registered listener. Dead listeners are removed.
     */
    public static void onLocaleChanged() {
        synchronized (ON_LOCALE_CHANGED_LISTENERS) {
            Iterator<WeakReference<OnLocaleChangedListener>> it =
                    ON_LOCALE_CHANGED_LISTENERS.iterator();
            while (it.hasNext()) {
                WeakReference<OnLocaleChangedListener> listenerRef = it.next();
                if (listenerRef.get() != null) {
                    listenerRef.get().onLocaleChanged();
                } else {
                    // remove dead listeners.
                    it.remove();
                }
            }
        }
    }


    /**
     * Get a Locale for the given language.
     * This method accepts ISO codes (2 or 3 char), or a display-string (4+ characters).
     *
     * <strong>Note:</strong> language with a display string of 1,2 or 3 character long
     * will be presumed to be an ISO code.
     * Example: display:"asu", has an actual iso3 code of "asa" but we will wrongly take "asu"
     * to be an ISO3 code. There are a couple more like this.
     *
     * @param inputLang to use for Locale
     *
     * @return the Locale, or {@code null} if the inputLang was invalid.
     */
    @Nullable
    public static Locale getLocale(@NonNull final String inputLang) {
        Context context = App.getLocalizedAppContext();

        String lang = inputLang.trim().toLowerCase(Locale.getDefault());
        int len = lang.length();
        if (len > 3) {
            lang = LanguageUtils.getISO3FromDisplayName(context, lang);
        }

        // THIS IS A MUST
        lang = LanguageUtils.getLocaleIsoFromIso3(lang);

        // lang should now be an ISO (2 or 3 characters) code (or an invalid string)
        Locale locale = LOCALE_MAP.get(lang);
        if (locale == null) {
            locale = createLocale(lang);
            if (isValid(locale)) {
                LOCALE_MAP.put(lang, locale);
            } else {
                if (BuildConfig.DEBUG /* always */) {
                    Logger.warn(context, TAG, "getLocale", "invalid locale",
                                "inputLang=" + inputLang,
                                "lang=" + lang,
                                "locale=" + locale);
                }

                locale = null;
            }
        }

        return locale;
    }

    /**
     * Move "The, A, An" etc... to the end of the title. e.g. "The title" -> "title, The".
     * This method is case sensitive on purpose.
     *
     * @param userContext Current context, should be an actual user context,
     *                    and not the ApplicationContext.
     * @param title       to reorder
     * @param titleLocale (optional) Locale matching the title.
     *                    When {@code null} Locale.getDefault() is used.
     *
     * @return reordered title, or the original if the pattern was not found
     */
    @NonNull
    public static String reorderTitle(@NonNull final Context userContext,
                                      @NonNull final String title,
                                      @Nullable final Locale titleLocale) {

        String[] titleWords = title.split(" ");
        // Single word titles (or empty titles).. just return.
        if (titleWords.length < 2) {
            return title;
        }

        // we check in order - first match returns.
        // 1. the Locale passed in
        // 2. the default Locale
        // 3. the user device Locale
        // 4. ENGLISH.
        Locale[] locales = {titleLocale, Locale.getDefault(),
                            App.getSystemLocale(), Locale.ENGLISH};

        for (Locale locale : locales) {
            if (locale == null) {
                continue;
            }
            // Creating the pattern is slow, so we cache it for every Locale.
            String words = LOCALE_PREFIX_MAP.get(locale);
            if (words == null) {
                // the resources bundle in the language that the book (item) is written in.
                Resources localeResources = App.getLocalizedResources(userContext, locale);
                words = localeResources.getString(R.string.pv_reformat_titles_prefixes);
                LOCALE_PREFIX_MAP.put(locale, words);
            }
            // case sensitive, see notes in res/values/string.xml/pv_reformat_titles_prefixes
            if (words.contains(titleWords[0])) {
                StringBuilder newTitle = new StringBuilder();
                for (int i = 1; i < titleWords.length; i++) {
                    if (i != 1) {
                        newTitle.append(' ');
                    }
                    newTitle.append(titleWords[i]);
                }
                newTitle.append(", ").append(titleWords[0]);
                return newTitle.toString();
            }
        }
        return title;
    }

    public interface OnLocaleChangedListener {

        void onLocaleChanged();
    }
}
