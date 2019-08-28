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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * A fresh and new approach to setting the custom Locale.
 * <p>
 * <pre>
 *  {@code
 *      public class MyActivity extends AppCompatActivity {
 *          private String mInitialLocale;
 *
 *          protected void onCreate(@Nullable final Bundle savedInstanceState) {
 *              super.onCreate(savedInstanceState);
 *              mInitialLocale = LocaleHelper.getPersistedLocale(this);
 *          }
 *
 *          protected void attachBaseContext(@NonNull final Context base) {
 *              super.attachBaseContext(LocaleHelper.onAttach(base));
 *          }
 *
 *          protected void onResume() {
 *              super.onResume();
 *              if (LocaleHelper.isChanged(this, mInitialLocale)) {
 *                  recreate();
 *              }
 *          }
 *      }
 *  }
 * </pre>
 */
public class LocaleHelper {

    /**
     * Value stored in preferences if the user runs our app in the default device language.
     * The string "system" is hardcoded in the preference string-array and
     * in the default setting in the preference screen.
     */
    private static final String SYSTEM_LANGUAGE = "system";

    public static Context attachBaseContext(@NonNull final Context context) {
        return setLocale(context, getPersistedLocaleSpec(context));
    }

    public static boolean isChanged(@NonNull final Context context,
                                    @Nullable final String initialLocale) {
        return (initialLocale != null && !initialLocale.equals(getPersistedLocaleSpec(context)));
    }

    private static String getPersistedLocaleSpec(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(Prefs.pk_ui_language, SYSTEM_LANGUAGE);
    }

    /**
     * Creates a Locale from a concatenated locale string.
     *
     * @param localeSpec Locale name (e.g. 'de', 'en_AU')
     *
     * @return Locale corresponding to passed name
     */
    @NonNull
    private static Locale from(@NonNull final String localeSpec) {
        String[] parts;
        if (localeSpec.contains("_")) {
            parts = localeSpec.split("_");
        } else {
            parts = localeSpec.split("-");
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
     * Set the app's locale to the one specified by the given localeSpec.
     *
     * @param context    Current context
     * @param localeSpec a locale specification as used for Android resources;
     *                   or {@link #SYSTEM_LANGUAGE} to use the system settings
     *
     * @return updated context
     */
    private static Context setLocale(@NonNull final Context context,
                                     @NonNull final String localeSpec) {
        Locale locale;
        if (localeSpec.equals(SYSTEM_LANGUAGE)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locale = Resources.getSystem().getConfiguration().getLocales().get(0);
            } else {
                locale = Resources.getSystem().getConfiguration().locale;
            }
        } else {
            locale = from(localeSpec);
        }

        Locale.setDefault(locale);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return updateResources(context, locale);
        } else {
            return updateResourcesLegacy(context, locale);
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static Context updateResources(@NonNull final Context context,
                                           @NonNull final Locale locale) {
        Configuration configuration = context.getResources().getConfiguration();
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);

        return context.createConfigurationContext(configuration);
    }

    private static Context updateResourcesLegacy(@NonNull final Context context,
                                                 @NonNull final Locale locale) {
        Resources resources = context.getResources();

        Configuration configuration = resources.getConfiguration();
        configuration.locale = locale;
        configuration.setLayoutDirection(locale);

        resources.updateConfiguration(configuration, resources.getDisplayMetrics());

        return context;
    }
}
