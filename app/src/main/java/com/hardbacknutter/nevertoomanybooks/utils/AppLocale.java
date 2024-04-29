/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;

/**
 * Manage the custom Locale our app can run in, independently from the system Locale.
 * <p>
 * How to use: see {@link BaseActivity} #attachBaseContext(Context)
 * and {@link BaseActivity}#recreateIfNeeded()
 */
public interface AppLocale {

    /**
     * Value stored in preferences if the user runs our app in the default device language.
     * The string "system" is hardcoded in the preference string-array and
     * in the default setting in the preference screen.
     */
    String SYSTEM_LANGUAGE = "system";

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
    Context apply(@NonNull Context context);

    /**
     * Create a new Locale as specified by the localeSpec string.
     *
     * @param localeSpec a Locale specification as used for Android resources;
     *                   or {@link #SYSTEM_LANGUAGE} to use the system settings
     *
     * @return a new Locale
     */
    @NonNull
    Locale create(@Nullable String localeSpec);

    /**
     * Get a Locale for the given language.
     * This method accepts ISO codes (2 or 3 char), or a display-string (4+ characters).
     * <p>
     * <strong>Note:</strong> language with a display string of 1,2 or 3 character long
     * will be presumed to be an ISO code.
     * Example: display:"asu", has an actual iso3 code of "asa" but we will wrongly take "asu"
     * to be an ISO3 code. There are a couple more like this.
     *
     * @param context   Current context
     * @param inputLang to use for Locale
     *
     * @return the Locale for the passed language
     */
    @NonNull
    Optional<Locale> getLocale(@NonNull Context context,
                               @NonNull String inputLang);

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
    Resources getLocalizedResources(@NonNull Context context,
                                    @NonNull Locale desiredLocale);

    /**
     * Add the specified listener. There is no protection against adding twice.
     *
     * @param listener to add
     */
    void registerOnLocaleChangedListener(@NonNull OnLocaleChangedListener listener);

    /**
     * Remove the specified listener (and any dead ones we come across).
     *
     * @param listener to remove
     */
    void unregisterOnLocaleChangedListener(@NonNull OnLocaleChangedListener listener);

    @FunctionalInterface
    interface OnLocaleChangedListener {

        void onLocaleChanged(@NonNull Context context);
    }
}
