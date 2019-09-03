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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
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

    /**
     * Value stored in preferences if the user runs our app in the default device language.
     * The string "system" is hardcoded in the preference string-array and
     * in the default setting in the preference screen.
     */
    private static final String SYSTEM_LANGUAGE = "system";
    /**
     * TEST: We keep strong references. Check if this ever is an issue.
     */
    private static final List<OnLocaleChangedListener> sOnLocaleChangedListeners =
            new ArrayList<>();

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
     * @return a locale specification as used for Android resources;
     * or {@link #SYSTEM_LANGUAGE} to use the system settings
     */
    public static String getPersistedLocaleSpec() {
        return PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                .getString(Prefs.pk_ui_language, SYSTEM_LANGUAGE);
    }

    /**
     * Test if the passed Locale is actually a 'real' Locale by checking ISO codes.
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
            return locale.getISO3Language() != null
                   && locale.getISO3Country() != null;

        } catch (@NonNull final MissingResourceException e) {
            // log but ignore.
            Logger.debug(LocaleUtils.class, "isValid",
                         "e=" + e.getLocalizedMessage(),
                         "locale=" + locale);
            return false;

        } catch (@NonNull final RuntimeException ignore) {
            // NullPointerException can be thrown  when ISO3Language() fails.
            return false;
        }
    }

    /**
     * Check if the passed localeSpec is different from the user preferred Locale
     *
     * @param localeSpec to test
     *
     * @return {@code true} if different
     */
    public static boolean isChanged(@Nullable final String localeSpec) {
        if (BuildConfig.DEBUG /* always */) {
            // developer sanity check: for now, we don't use/support full local tags
            if (localeSpec != null && localeSpec.contains("_")) {
                throw new IllegalStateException("incoming localeSpec is a tag instead of a lang: "
                                                + localeSpec);
            }
        }
        return localeSpec == null || !localeSpec.equals(getPersistedLocaleSpec());
    }

    /**
     * this call is not needed. Use Locale.getDefault();
     */
    @NonNull
    @AnyThread
    public static Locale getLocale(@NonNull final Context context) {
        String localeSpec = getPersistedLocaleSpec();

        // create the Locale at first access, or if the requested is different from the current.
        if (sPreferredLocale == null || !sPreferredLocaleSpec.equals(localeSpec)) {
            sPreferredLocaleSpec = localeSpec;
            sPreferredLocale = createLocale(localeSpec);
        }

        insanityCheck(context);
        return sPreferredLocale;
    }

    /**
     * Set the system wide default Locale to the given localeSpec.
     * Set the context's locale to the given localeSpec and return the updated context.
     *
     * @param context to set the Locale on
     *
     * @return updated context
     */
    @NonNull
    public static Context applyLocale(@NonNull final Context context) {
        String localeSpec = getPersistedLocaleSpec();

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
     * @param localeSpec a locale specification as used for Android resources;
     *                   or {@link #SYSTEM_LANGUAGE} to use the system settings
     *
     * @return a new locale
     */
    @NonNull
    private static Locale createLocale(@Nullable final String localeSpec) {

        if (localeSpec == null || localeSpec.isEmpty() || SYSTEM_LANGUAGE.equals(localeSpec)) {
            return getSystemLocale();
        } else {
            // Create a Locale from a concatenated locale string (e.g. 'de', 'en_AU')
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
        String conIso3 = getConfiguredLocale(context).getISO3Language();
        String defIso3 = Locale.getDefault().getISO3Language();

        if (!defIso3.equals(conIso3)) {
            Error e = new java.lang.VerifyError();
            Logger.error(LocaleUtils.class, e, "defIso3=" + defIso3, "conIso3=" + conIso3);
            throw e;
        }
    }

    public static void registerOnLocaleChangedListener(
            @NonNull final OnLocaleChangedListener listener) {
        synchronized (sOnLocaleChangedListeners) {
            if (!sOnLocaleChangedListeners.contains(listener)) {
                sOnLocaleChangedListeners.add(listener);
            }
        }
    }

    public static void unregisterOnLocaleChangedListener(
            @NonNull final OnLocaleChangedListener listener) {
        synchronized (sOnLocaleChangedListeners) {
            sOnLocaleChangedListeners.remove(listener);
        }
    }

    public static void onLocaleChanged() {
        synchronized (sOnLocaleChangedListeners) {
            for (OnLocaleChangedListener listener : sOnLocaleChangedListeners) {
                listener.onLocaleChanged();
            }
        }
    }

    public interface OnLocaleChangedListener {

        void onLocaleChanged();
    }
}
