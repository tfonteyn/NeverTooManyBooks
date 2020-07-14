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
package com.hardbacknutter.nevertoomanybooks.tasks;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Build the dedicated SharedPreferences file with the language mappings.
 * Only build once per Locale.
 */
public class BuildLanguageMappingsTask
        implements Runnable {

    /** Log tag. */
    private static final String TAG = "BuildLanguageMappings";
    /** Prefix added to the iso code for the 'done' flag in the language cache. */
    private static final String LANG_CREATED_PREFIX = "___";

    @Override
    public void run() {
        Thread.currentThread().setName(TAG);
        final Context context = App.getTaskContext();
        final SharedPreferences prefs = LanguageUtils.getLanguageCache(context);

        try {
            // the one the user is using our app in (can be different from the system one)
            createLanguageMappingCache(prefs, LocaleUtils.getUserLocale(context));
            // the system default
            createLanguageMappingCache(prefs, LocaleUtils.getSystemLocale());
            // Always add English
            createLanguageMappingCache(prefs, Locale.ENGLISH);

            //NEWTHINGS: add new site specific ID: add mappings for site specific languages

            // Dutch:
            // StripInfoSearchEngine
            // KbNlSearchEngine
            createLanguageMappingCache(prefs, new Locale("nl"));

        } catch (@NonNull final RuntimeException e) {
            Logger.error(context, TAG, e);
        }
    }

    /**
     * Generate language mappings for a given Locale.
     *
     * @param prefs  the SharedPreferences used as our language names cache.
     * @param locale the Locale for which to create a mapping
     */
    private void createLanguageMappingCache(@NonNull final SharedPreferences prefs,
                                            @NonNull final Locale locale) {
        // just return if already done for this Locale.
        if (prefs.getBoolean(LANG_CREATED_PREFIX + locale.getISO3Language(), false)) {
            return;
        }
        final SharedPreferences.Editor ed = prefs.edit();
        for (Locale loc : Locale.getAvailableLocales()) {
            ed.putString(loc.getDisplayLanguage(locale).toLowerCase(locale),
                         loc.getISO3Language());
        }
        // signal this Locale was done
        ed.putBoolean(LANG_CREATED_PREFIX + locale.getISO3Language(), true);
        ed.apply();
    }
}
