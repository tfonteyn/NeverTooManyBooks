/*
 * @Copyright 2020 HardBackNutter
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * Manage the custom Locale our app can run in, independently from the system Locale.
 * <pre>
 *  {@code
 *      public class MyActivity extends AppCompatActivity {
 *          private String mInitialLocaleSpec;
 *
 *          protected void onCreate(@Nullable final Bundle savedInstanceState) {
 *              super.onCreate(savedInstanceState);
 *              mInitialLocaleSpec = AppLocale.getInstance().getPersistedLocaleSpec(this);
 *          }
 *
 *          protected void applyLocale(@NonNull final Context base) {
 *              super.applyLocale(AppLocale.getInstance().apply(base));
 *          }
 *
 *          protected void onResume() {
 *              super.onResume();
 *              if (AppLocale.getInstance().isChanged(this, mInitialLocaleSpec)) {
 *                  setIsRecreating();
 *              }
 *          }
 *      }
 *  }
 * </pre>
 * <p>
 * Singleton.
 */
public class AppLocale {

    /**
     * Value stored in preferences if the user runs our app in the default device language.
     * The string "system" is hardcoded in the preference string-array and
     * in the default setting in the preference screen.
     */
    @VisibleForTesting
    public static final String SYSTEM_LANGUAGE = "system";
    /** Log tag. */
    private static final String TAG = "AppLocale";
    /** Singleton. */
    private static AppLocale sInstance;
    @NonNull
    private final Collection<WeakReference<OnLocaleChangedListener>>
            ON_LOCALE_CHANGED_LISTENERS = new ArrayList<>();
    /** Cache for Locales; key: the BOOK language (ISO3). */
    private final Map<String, Locale> LOCALE_MAP = new HashMap<>();
    /**
     * The currently active/preferred user Locale.
     * See {@link #apply} for why we keep store it.
     */
    @Nullable
    private Locale sPreferredLocale;

    /**
     * Remember the current Locale spec to detect when language is switched.
     * See {@link #apply}.
     */
    @NonNull
    private String sPreferredLocaleSpec = SYSTEM_LANGUAGE;

    /**
     * Constructor. Use {@link #getInstance()}.
     */
    private AppLocale() {
    }

    /**
     * Get/create the singleton instance.
     *
     * @return instance
     */
    @NonNull
    public static AppLocale getInstance() {
        synchronized (AppLocale.class) {
            if (sInstance == null) {
                sInstance = new AppLocale();
            }
            return sInstance;
        }
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
    Locale create(@Nullable final String localeSpec) {

        if (localeSpec == null || localeSpec.isEmpty() || SYSTEM_LANGUAGE.equals(localeSpec)) {
            return getSystemLocale();
        } else {
            // Create a Locale from a concatenated Locale string (e.g. 'de', 'en_AU')
            final String[] parts;
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

    /**
     * Return the device Locale.
     * <p>
     * When running a JUnit test, this method will always return {@code Locale.ENGLISH}.
     *
     * @return Locale
     */
    @NonNull
    public Locale getSystemLocale() {
        // While running JUnit tests we cannot get access or mock Resources.getSystem(),
        // ... so we need to cheat.
        if (BuildConfig.DEBUG /* always */) {
            if (Logger.isJUnitTest) {
                return Locale.US;
            }
        }

        return Resources.getSystem().getConfiguration().getLocales().get(0);
    }

    /**
     * Return the user preferred Locale.
     *
     * @param context Current context
     *
     * @return Locale
     */
    @NonNull
    public Locale getUserLocale(@NonNull final Context context) {
        return context.getResources().getConfiguration().getLocales().get(0);
    }

    /**
     * Load a Resources set for the specified Locale.
     * This is an expensive lookup; we do not cache the Resources here,
     * but it's advisable to cache the strings (map of Locale/String for example) being looked up.
     *
     * @param context       Current context
     * @param desiredLocale the desired Locale, e.g. the Locale of a Book,Series,TOC,...
     *
     * @return the Resources
     */
    @NonNull
    public Resources getLocalizedResources(@NonNull final Context context,
                                           @NonNull final Locale desiredLocale) {
        final Configuration deltaConfig = new Configuration();
        final String lang = desiredLocale.getLanguage();
        if (lang.length() == 2) {
            deltaConfig.setLocale(desiredLocale);
        } else {
            // any 3-char code might need to be converted to 2-char be able to find the resource.
            final String iso = Languages.getInstance().getLocaleIsoFromISO3(context, lang);
            deltaConfig.setLocale(new Locale(iso));
        }

        return context.createConfigurationContext(deltaConfig).getResources();
    }

    /**
     * Get the user-preferred Locale as stored in the preferences.
     *
     * @param preferences Global preferences
     *
     * @return a Locale specification as used for Android resources;
     * or {@link #SYSTEM_LANGUAGE} to use the system settings
     */
    @NonNull
    public String getPersistedLocaleSpec(@NonNull final SharedPreferences preferences) {
        return preferences.getString(Prefs.pk_ui_locale, SYSTEM_LANGUAGE);
    }

    /**
     * Test if the passed Locale is actually a 'real' Locale
     * by comparing the ISO3 code and the display name. If they are different we assume 'valid'
     *
     * @param locale to test
     *
     * @return {@code true} if valid
     */
    private boolean isValid(@Nullable final Locale locale) {
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

            final String displayLanguage = locale.getDisplayLanguage();
            final String iso3 = locale.getISO3Language();

            return !displayLanguage.isEmpty() && !displayLanguage.equalsIgnoreCase(iso3);

        } catch (@NonNull final MissingResourceException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "isValid|e=" + e.getLocalizedMessage() + "|locale=" + locale);
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
     * @param preferences Global preferences
     * @param localeSpec  to test
     *
     * @return {@code true} if different
     */
    public boolean isChanged(@NonNull final SharedPreferences preferences,
                             @Nullable final String localeSpec) {
        return localeSpec == null || !localeSpec.equals(getPersistedLocaleSpec(preferences));
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
    public Context apply(@NonNull final Context context) {
        boolean changed = false;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Create the Locale at first access, or if the persisted is different from the current.
        final String localeSpec = getPersistedLocaleSpec(prefs);
        if (sPreferredLocale == null || !sPreferredLocaleSpec.equals(localeSpec)) {
            sPreferredLocaleSpec = localeSpec;
            sPreferredLocale = create(localeSpec);
            changed = true;
        }

        // Update the JDK usage of Locale - this MUST be done each and every time!
        Locale.setDefault(sPreferredLocale);

        // Update the Android usage of Locale - this MUST be done each and every time!
        final Configuration deltaConfig = new Configuration();
        deltaConfig.setLocale(sPreferredLocale);
        if (Build.VERSION.SDK_INT < 26) {
            // Workaround for platform bug on SDK < 26
            // (https://issuetracker.google.com/issues/140607881)
            deltaConfig.fontScale = 0f;
        }
        final Context localizedContext = context.createConfigurationContext(deltaConfig);

        if (changed) {
            // refresh Locale aware components; only needed when the Locale was indeed changed.
            onLocaleChanged(context);
        }
        return localizedContext;
    }

    /**
     * Add the specified listener. There is no protection against adding twice.
     *
     * @param listener to add
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void registerOnLocaleChangedListener(
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
    public void unregisterOnLocaleChangedListener(
            @NonNull final OnLocaleChangedListener listener) {
        synchronized (ON_LOCALE_CHANGED_LISTENERS) {
            Iterator<WeakReference<OnLocaleChangedListener>> it =
                    ON_LOCALE_CHANGED_LISTENERS.iterator();
            while (it.hasNext()) {
                final WeakReference<OnLocaleChangedListener> listenerRef = it.next();
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
    private void onLocaleChanged(@NonNull final Context context) {
        synchronized (ON_LOCALE_CHANGED_LISTENERS) {
            Iterator<WeakReference<OnLocaleChangedListener>> it =
                    ON_LOCALE_CHANGED_LISTENERS.iterator();
            while (it.hasNext()) {
                final WeakReference<OnLocaleChangedListener> listenerRef = it.next();
                if (listenerRef.get() != null) {
                    listenerRef.get().onLocaleChanged(context);
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
     * @param context   Current context
     * @param inputLang to use for Locale
     *
     * @return the Locale for the passed language OR the user locale if the inputLang was empty,
     * OR {@code null} if the inputLang was invalid.
     */
    @Nullable
    public Locale getLocale(@NonNull final Context context,
                            @NonNull final String inputLang) {

        if (inputLang.isEmpty()) {
            return null;
        }

        final Locale userLocale = getUserLocale(context);
        String lang = inputLang.trim().toLowerCase(userLocale);
        final int len = lang.length();
        if (len > 3) {
            lang = Languages.getInstance().getISO3FromDisplayName(context, userLocale, lang);
        }

        // THIS IS A MUST
        lang = Languages.getInstance().getLocaleIsoFromISO3(context, lang);

        // lang should now be an ISO (2 or 3 characters) code (or an invalid string)
        Locale locale = LOCALE_MAP.get(lang);
        if (locale == null) {
            locale = create(lang);
            if (isValid(locale)) {
                LOCALE_MAP.put(lang, locale);
            } else {
                locale = null;
            }
        }

        return locale;
    }

    public interface OnLocaleChangedListener {

        void onLocaleChanged(@NonNull final Context context);
    }
}
