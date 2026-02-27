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

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.BooleanParser;
import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

/**
 * All keys <strong>MUST</strong> be kept in sync with "src/main/res/xml/preferences*.xml".
 * <p>
 * This class acts as a wrapper to {@link SharedPreferences}.
 * When reading {@code int/long} and {@code float/double} values from a backup/import
 * the JSON parser will always read/return the 'shortest' type.
 * For example: if {@code long} value was {@code 5}, the parser reads it as an {@code int},
 * and later on the {@link SharedPreferences#getLong(String, long)}
 * will throw a {@link ClassCastException}.
 * <p>
 * Hence, the methods here catch such, and do a manual cast to the desired type.
 * <p>
 * Separately, but essentially the same issue:
 * <p>
 * GitHub #211 some android-variations seem to 'optimise' floating point numbers
 * when storing them in SharedPreferences,  with the results that a {@code 0.0} value
 * will be written as {@code 0}, and subsequently treated falsly as an {@code int} by the OS.
 * <p>
 * Dev. note: there are some key definitions and static methods here for historical reasons.
 */
public class Prefs
        implements SharedPreferences {

    /**
     * The locale the user is running our app in (which can be different from the device).
     * {@code String}: The literal {@code "system"} or an Android Locale code.
     *
     * @see com.hardbacknutter.nevertoomanybooks.utils.AppLocaleImpl
     */
    public static final String PK_UI_LOCALE = "ui.locale";

    private static final String PK_NORMALIZE_SERIES_TITLE = "normalize.series.title";
    private static final String PK_NORMALIZE_TOC_TITLE = "normalize.toc.title";
    private static final String PK_NORMALIZE_PUBLISHER_NAME = "normalize.publisher.name";

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

    @NonNull
    private final SharedPreferences sharedPreferences;

    /**
     * Constructor.
     *
     * @param sharedPreferences to wrap
     */
    public Prefs(@NonNull final SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
    }

    public static boolean normalizeSeriesTitle() {
        return ServiceLocator.getInstance().getSharedPreferences()
                             .getBoolean(PK_NORMALIZE_SERIES_TITLE, false);
    }

    public static boolean normalizePublisherName() {
        return ServiceLocator.getInstance().getSharedPreferences()
                             .getBoolean(PK_NORMALIZE_PUBLISHER_NAME, false);
    }

    public static boolean normalizeTocEntryName() {
        return ServiceLocator.getInstance().getSharedPreferences()
                             .getBoolean(PK_NORMALIZE_TOC_TITLE, false);
    }

    @Override
    @NonNull
    public SharedPreferences.Editor edit() {
        return sharedPreferences.edit();
    }

    @Override
    public boolean contains(@NonNull final String key) {
        return sharedPreferences.contains(key);
    }

    @Override
    @NonNull
    public Map<String, ?> getAll() {
        return sharedPreferences.getAll();
    }

    @Override
    @Nullable
    public Set<String> getStringSet(@NonNull final String key,
                                    @Nullable final Set<String> defValues) {
        // NO ClassCastException PROTECTION
        return sharedPreferences.getStringSet(key, defValues);
    }

    @Override
    public boolean getBoolean(@NonNull final String key,
                              final boolean defValue) {
        try {
            return sharedPreferences.getBoolean(key, defValue);
        } catch (@NonNull final ClassCastException ignore) {
            // ignore
        }
        // getAll() to bypass the type check.
        final Object value = sharedPreferences.getAll().get(key);
        try {
            return BooleanParser.toBoolean(value);
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }
        return defValue;
    }

    @Override
    @Nullable
    public String getString(@NonNull final String key,
                            @Nullable final String defValue) {
        try {
            return sharedPreferences.getString(key, defValue);
        } catch (@NonNull final ClassCastException ignore) {
            // ignore
        }
        // getAll() to bypass the type check.
        final Object value = sharedPreferences.getAll().get(key);
        if (value instanceof CharSequence) {
            return String.valueOf(value);
        }
        return defValue;
    }

    @Override
    public int getInt(@NonNull final String key,
                      final int defValue) {
        try {
            return sharedPreferences.getInt(key, defValue);
        } catch (@NonNull final ClassCastException ignore) {
            // ignore
        }
        // getAll() to bypass the type check.
        final Object value = sharedPreferences.getAll().get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defValue;
    }

    /**
     * {@code ListPreference} stores the selected {@code int} value as a {@code String}.
     * This convenience method reads the value as a {@code String}
     * and parses/returns it as an {@code int}.
     *
     * @param key      The name of the preference to retrieve.
     * @param defValue Value to return if this preference does not exist,
     *                 or if the stored value is somehow invalid
     *
     * @return Returns the preference value if it exists, or defValue.
     */
    public int getIntFromString(@NonNull final String key,
                                final int defValue) {
        final String value = sharedPreferences.getString(key, null);
        if (value == null || value.isEmpty()) {
            return defValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (@NonNull final NumberFormatException ignore) {
            return defValue;
        }
    }

    @Override
    public long getLong(@NonNull final String key,
                        final long defValue) {
        try {
            return sharedPreferences.getLong(key, defValue);
        } catch (@NonNull final ClassCastException ignore) {
            // ignore
        }
        // getAll() to bypass the type check.
        final Object value = sharedPreferences.getAll().get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defValue;
    }

    @Override
    public float getFloat(@NonNull final String key,
                          final float defValue) {
        try {
            return sharedPreferences.getFloat(key, defValue);
        } catch (@NonNull final ClassCastException ignore) {
            // ignore
        }
        // getAll() to bypass the type check.
        final Object value = sharedPreferences.getAll().get(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return defValue;
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            @NonNull final OnSharedPreferenceChangeListener listener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            @NonNull final OnSharedPreferenceChangeListener listener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    @Override
    @NonNull
    public String toString() {
        return "Prefs{" + sharedPreferences + '}';
    }
}
