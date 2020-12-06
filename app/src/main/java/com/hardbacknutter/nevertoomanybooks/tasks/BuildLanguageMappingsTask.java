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
package com.hardbacknutter.nevertoomanybooks.tasks;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

/**
 * Build the dedicated SharedPreferences file with the language mappings.
 * Only build once per Locale.
 */
public class BuildLanguageMappingsTask
        extends LTask<Boolean> {

    /** Log tag. */
    private static final String TAG = "BuildLanguageMappings";
    /** Prefix added to the iso code for the 'done' flag in the language cache. */
    private static final String LANG_CREATED_PREFIX = "___";

    public BuildLanguageMappingsTask(@NonNull final TaskListener<Boolean> taskListener) {
        super(R.id.TASK_ID_BUILD_LANG_MAP, taskListener);
    }

    @NonNull
    @Override
    @WorkerThread
    protected Boolean doWork(@NonNull final Context context) {
        Thread.currentThread().setName(TAG);
        final SharedPreferences languageCache = Languages.getInstance().getCacheFile(context);

        try {
            // the one the user is using our app in (can be different from the system one)
            createLanguageMappingCache(languageCache,
                                       AppLocale.getInstance().getUserLocale(context));
            // the system default
            createLanguageMappingCache(languageCache, AppLocale.getInstance().getSystemLocale());
            // Always add English
            createLanguageMappingCache(languageCache, Locale.ENGLISH);

            //NEWTHINGS: adding a new search engine: add mappings for site specific languages

            // Dutch: StripInfoSearchEngine, KbNlSearchEngine
            createLanguageMappingCache(languageCache, new Locale("nl"));

            return true;

        } catch (@NonNull final RuntimeException e) {
            Logger.error(context, TAG, e);
            return false;
        }
    }

    /**
     * Generate language mappings for a given Locale.
     *
     * @param languageCache the SharedPreferences used as our language names cache.
     * @param locale        the Locale for which to create a mapping
     */
    private void createLanguageMappingCache(@NonNull final SharedPreferences languageCache,
                                            @NonNull final Locale locale) {
        // just return if already done for this Locale.
        if (languageCache.getBoolean(LANG_CREATED_PREFIX + locale.getISO3Language(), false)) {
            return;
        }
        final SharedPreferences.Editor ed = languageCache.edit();
        for (final Locale loc : Locale.getAvailableLocales()) {
            ed.putString(loc.getDisplayLanguage(locale).toLowerCase(locale),
                         loc.getISO3Language());
        }
        // signal this Locale was done
        ed.putBoolean(LANG_CREATED_PREFIX + locale.getISO3Language(), true);
        ed.apply();
    }
}
