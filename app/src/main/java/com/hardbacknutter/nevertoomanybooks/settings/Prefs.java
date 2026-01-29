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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.AppBarLayout;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

/**
 * All keys <strong>MUST</strong> be kept in sync with "src/main/res/xml/preferences*.xml".
 */
@SuppressWarnings("WeakerAccess")
public final class Prefs {

    /**
     * The locale the user is running our app in (which can be different from the device).
     * {@code String}: The literal {@code "system"} or an Android Locale code.
     *
     * @see com.hardbacknutter.nevertoomanybooks.utils.AppLocaleImpl
     */
    public static final String PK_UI_LOCALE = "ui.locale";

    public static final String PK_UI_TOP_MENU = "ui.screen.systembars.fixed";

    public static final String PK_NORMALIZE_SERIES_TITLE = "normalize.series.title";
    public static final String PK_NORMALIZE_TOC_TITLE = "normalize.toc.title";
    public static final String PK_NORMALIZE_PUBLISHER_NAME = "normalize.publisher.name";

    /** The prefix of all "acra" settings which need to be excluded during import/export. */
    private static final String EXCLUDE_ACRA_PREFIX = "^acra\\..*";

    /**
     * Regular expressions for the keys which will be excluded
     * during an import of the preferences.
     */
    public static final List<String> EXCLUDE_WHEN_IMPORTING = List.of(
            EXCLUDE_ACRA_PREFIX,
            CoverVolume.PK_VOLUME_INDEX.replace(".", "\\."),
            Languages.PK_LANG_CREATED_PREFIX.replace(".", "\\.") + ".*"
    );

    /**
     * Regular expressions for the keys which will be excluded
     * during an export of the preferences.
     */
    public static final List<String> EXCLUDE_WHEN_EXPORTING = List.of(
            EXCLUDE_ACRA_PREFIX,
            CoverVolume.PK_VOLUME_INDEX.replace(".", "\\."),
            Languages.PK_LANG_CREATED_PREFIX.replace(".", "\\.") + ".*"
    );

    private Prefs() {
    }

    /**
     * Retrieve a float value from the preferences.
     * <p>
     * GitHub #211 some android-variations seem to 'optimise' floating point numbers
     * when storing them in SharedPreferences,  with the results that a {@code 0.0} value
     * will be written as {@code 0}, and subsequently fails to read as the OS thinks
     * it's an integer.
     *
     * <pre>
     *     java.lang.ClassCastException: java.lang.Integer cannot be cast to java.lang.Float
     *     at android.app.SharedPreferencesImpl.getFloat(SharedPreferencesImpl.java:388)
     * </pre>
     *
     * @param prefs    go read
     * @param key      The name of the preference to retrieve.
     * @param defValue Value to return if this preference does not exist.
     *
     * @return Returns the preference value if it exists, or defValue.
     */
    public static float getFloat(@NonNull final SharedPreferences prefs,
                                 @NonNull final String key,
                                 final float defValue) {
        try {
            return prefs.getFloat(key, defValue);
        } catch (@NonNull final ClassCastException e) {
            // getAll() to bypass the type check.
            final Object value = prefs.getAll().get(key);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
            return defValue;
        }
    }


    public static boolean normalizeSeriesTitle(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PK_NORMALIZE_SERIES_TITLE, false);
    }

    public static boolean normalizePublisherName(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PK_NORMALIZE_PUBLISHER_NAME, false);
    }

    public static boolean normalizeTocEntryName(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PK_NORMALIZE_TOC_TITLE, false);
    }

    public static boolean isFixedHeaderAndFooter(@NonNull final Context context) {
        // 0 -> scroll
        // 1 -> fixed
        return 0 != IntListPref.getInt(context, PK_UI_TOP_MENU, 0);
    }

    public static void applyScrollFlags(@NonNull final Toolbar toolbar) {
        final AppBarLayout.LayoutParams lp = (AppBarLayout.LayoutParams)
                toolbar.getLayoutParams();
        if (isFixedHeaderAndFooter(toolbar.getContext())) {
            lp.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL);
        } else {
            lp.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                              | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                              | AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
            );
        }
        toolbar.setLayoutParams(lp);
    }
}
