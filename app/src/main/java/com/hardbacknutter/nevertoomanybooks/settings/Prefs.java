/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
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

    /**
     * Whether to normalize {@link DBKey#FORMAT} values after a search.
     * <p>
     * {@code boolean}
     *
     * @see com.hardbacknutter.nevertoomanybooks.utils.mappers.FormatMapper
     */
    public static final String PK_SEARCH_REFORMAT_FORMAT = "search.reformat.format";
    /**
     * Whether to normalize {@link DBKey#COLOR} values after a search.
     * <p>
     * {@code boolean}
     *
     * @see com.hardbacknutter.nevertoomanybooks.utils.mappers.ColorMapper
     */
    public static final String PK_SEARCH_REFORMAT_COLOR = "search.reformat.color";

    public static final String PK_NORMALIZE_SERIES_TITLE = "normalize.series.title";
    public static final String PK_NORMALIZE_TOC_TITLE = "normalize.toc.title";
    public static final String PK_NORMALIZE_PUBLISHER_NAME = "normalize.publisher.name";

    public static final String PK_CAMERA_IMAGE_AUTOROTATE = "camera.image.autorotate";
    public static final String PK_CAMERA_IMAGE_ACTION = "camera.image.action";
    public static final String PK_CAMERA_LENS_FACING = "camera.lens.facing";

    public static final String PK_SCANNER_MODE_SINGLE = "scan.mode.single";

    /** The prefix of all "acra" settings which need to be excluded during import/export. */
    public static final String ACRA_EXCLUDE_PREFIX = "^acra\\..*";

    /**
     * Regular expressions for the keys which will be excluded
     * during an import of the preferences.
     */
    public static final List<String> EXCLUDE_WHEN_IMPORTING = List.of(
            /* The prefix of all "acra" settings. */
            ACRA_EXCLUDE_PREFIX,
            CoverVolume.PK_VOLUME_INDEX.replace(".", "\\."),
            Languages.PK_LANG_CREATED_PREFIX.replace(".", "\\.") + ".*"
    );

    /**
     * Regular expressions for the keys which will be excluded
     * during an export of the preferences.
     */
    public static final List<String> EXCLUDE_WHEN_EXPORTING = List.of(
            /* The prefix of all "acra" settings. */
            ACRA_EXCLUDE_PREFIX,
            CoverVolume.PK_VOLUME_INDEX.replace(".", "\\."),
            Languages.PK_LANG_CREATED_PREFIX.replace(".", "\\.") + ".*"
    );

    private Prefs() {
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
}
