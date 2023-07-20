/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Encapsulate the Book fields which can be shown on the Book-details screen
 * as defined <strong>by the current style</strong>.
 */
public class BookDetailsFieldVisibility
        extends FieldVisibility {

    /** The fields which will be visible by default. */
    public static final long DEFAULT = getBitValue(Set.of(
            DBKey.COVER[0],
            DBKey.COVER[1]));

    /** Show the cover images (front/back) for each book on the details screen. */
    public static final String[] PK_DETAILS_SHOW_COVER = {
            "style.details.show.thumbnail.0",
            "style.details.show.thumbnail.1",
    };

    private static final Set<String> DB_KEYS = Set.of(
            DBKey.COVER[0],
            DBKey.COVER[1]);

    /** Map preference key to DBKey. */
    private static final Map<String, String> PREF_TO_DB_KEY = new HashMap<>();

    static {
        PREF_TO_DB_KEY.put(PK_DETAILS_SHOW_COVER[0], DBKey.COVER[0]);
        PREF_TO_DB_KEY.put(PK_DETAILS_SHOW_COVER[1], DBKey.COVER[1]);
    }

    static boolean containsKey(@NonNull final String key) {
        return PREF_TO_DB_KEY.containsKey(key);
    }

    @NonNull
    static String getDBKeyForPrefKey(@NonNull final String key) {
        return Objects.requireNonNull(PREF_TO_DB_KEY.get(key));
    }

    /**
     * Constructor.
     */
    BookDetailsFieldVisibility() {
        super(DB_KEYS, DEFAULT);
    }
}
