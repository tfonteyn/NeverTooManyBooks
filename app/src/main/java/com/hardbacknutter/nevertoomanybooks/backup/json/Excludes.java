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

package com.hardbacknutter.nevertoomanybooks.backup.json;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

final class Excludes {

    /** The prefix of all "acra" settings which need to be excluded during import/export. */
    private static final String EXCLUDE_ACRA_PREFIX = "^acra\\..*";

    private static final String EXCLUDE_VOLUME_INDEX =
            CoverVolume.PK_VOLUME_INDEX.replace(".", "\\.");
    private static final String EXCLUDE_LANG_CREATED_PREFIX =
            Languages.PK_LANG_CREATED_PREFIX.replace(".", "\\.") + ".*";

    /**
     * Regular expressions for the keys which will be excluded
     * during an export of the preferences.
     */
    static final List<String> EXCLUDE_WHEN_EXPORTING = List.of(
            EXCLUDE_ACRA_PREFIX,
            EXCLUDE_VOLUME_INDEX,
            EXCLUDE_LANG_CREATED_PREFIX
    );

    /**
     * Regular expressions for the keys which will be excluded
     * during an import of the preferences.
     */
    static final List<String> EXCLUDE_WHEN_IMPORTING = List.of(
            EXCLUDE_ACRA_PREFIX,
            EXCLUDE_VOLUME_INDEX,
            EXCLUDE_LANG_CREATED_PREFIX
    );

    private Excludes() {
    }
}
