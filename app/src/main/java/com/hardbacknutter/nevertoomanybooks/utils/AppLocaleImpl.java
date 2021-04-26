/*
 * @Copyright 2018-2021 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public final class AppLocaleImpl
        implements AppLocale {

    /** Log tag. */
    private static final String TAG = "AppLocale";

    @NonNull
    private final Collection<WeakReference<OnLocaleChangedListener>>
            mOnLocaleChangedListeners = new ArrayList<>();

    /** Cache for Locales; key: the BOOK language (ISO3). */
    private final Map<String, Locale> mLocaleMap = new HashMap<>();

    /** The currently active/preferred user Locale. See {@link #apply} for why we store it. */
    @Nullable
    private Locale sPreferredLocale;

    /**
     * Remember the current Locale spec to detect when language is switched.
     * See {@link #apply}.
     */
    @NonNull
    private String sPreferredLocaleSpec = SYSTEM_LANGUAGE;

    @Override
    @NonNull
    public Context apply(@NonNull final Context context) {
        boolean changed = false;

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);

        // Create the Locale at first access, or if the persisted is different from the current.
        final String localeSpec = getPersistedLocaleSpec(global);
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

    @Override
    @NonNull
    public Locale create(@Nullable final String localeSpec) {

        if (localeSpec == null || localeSpec.isEmpty() || SYSTEM_LANGUAGE.equals(localeSpec)) {
            return ServiceLocator.getSystemLocale();
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

    @Override
    @Nullable
    public Locale getLocale(@NonNull final Context context,
                            @NonNull final String inputLang) {

        if (inputLang.isEmpty()) {
            return null;
        }

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        String lang = inputLang.trim().toLowerCase(userLocale);
        final int len = lang.length();
        if (len > 3) {
            lang = Languages.getInstance().getISO3FromDisplayName(userLocale, lang);
        }

        // THIS IS A MUST
        lang = Languages.getInstance().getLocaleIsoFromISO3(context, lang);

        // lang should now be an ISO (2 or 3 characters) code (or an invalid string)
        Locale locale = mLocaleMap.get(lang);
        if (locale == null) {
            locale = create(lang);
            if (isValid(locale)) {
                mLocaleMap.put(lang, locale);
            } else {
                locale = null;
            }
        }

        return locale;
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

    @Override
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

    @Override
    @NonNull
    public String getPersistedLocaleSpec(@NonNull final SharedPreferences global) {
        //noinspection ConstantConditions
        return global.getString(Prefs.pk_ui_locale, SYSTEM_LANGUAGE);
    }

    @Override
    public void registerOnLocaleChangedListener(
            @NonNull final OnLocaleChangedListener listener) {
        synchronized (mOnLocaleChangedListeners) {
            mOnLocaleChangedListeners.add(new WeakReference<>(listener));
        }
    }

    @Override
    public void unregisterOnLocaleChangedListener(
            @NonNull final OnLocaleChangedListener listener) {
        synchronized (mOnLocaleChangedListeners) {
            final Iterator<WeakReference<OnLocaleChangedListener>> it =
                    mOnLocaleChangedListeners.iterator();
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
     *
     * @param context Current context
     */
    private void onLocaleChanged(@NonNull final Context context) {
        synchronized (mOnLocaleChangedListeners) {
            final Iterator<WeakReference<OnLocaleChangedListener>> it =
                    mOnLocaleChangedListeners.iterator();
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
}
