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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

//TODO: remove the isUsed method, and use this class properly + migrate the dozen prefs to just one
// however.. we'd then need to implement a DataStore to use in the Preferences class,
// to convert them into a single preference... and setting a DataStore
// on individual preferences is permanently broken https://issuetracker.google.com/issues/232206237
public class GlobalFieldVisibility
        extends FieldVisibility {

    /**
     * NEWTHINGS: new fields visibility.
     */
    private static final Set<String> KEYS = Set.of(
            DBKey.COVER[0],
            DBKey.COVER[1],
            DBKey.FK_AUTHOR,
            DBKey.FK_BOOKSHELF,

            DBKey.FK_SERIES,
            DBKey.FK_PUBLISHER,
            // represents "lending functionality enabled"
            DBKey.LOANEE_NAME,
            DBKey.AUTHOR_TYPE__BITMASK,

            DBKey.BOOK_CONDITION,
            DBKey.BOOK_CONDITION_COVER,
            DBKey.BOOK_ISBN,
            DBKey.BOOK_PUBLICATION__DATE,

            DBKey.COLOR,
            DBKey.DESCRIPTION,
            DBKey.EDITION__BITMASK,
            DBKey.FIRST_PUBLICATION__DATE,

            DBKey.FORMAT,
            DBKey.GENRE,
            DBKey.LANGUAGE,
            DBKey.LOCATION,

            DBKey.PAGE_COUNT,
            DBKey.PRICE_LISTED,
            DBKey.PRICE_PAID,
            DBKey.PERSONAL_NOTES,

            DBKey.RATING,
            DBKey.READ_START__DATE,
            DBKey.READ_END__DATE,
            DBKey.SIGNED__BOOL,

            DBKey.FK_TOC_ENTRY);

    /** All visible by default. */
    public static final long DEFAULT = getBitValue(KEYS);

    /**
     * Users can select which fields they use / don't want to use.
     * Each field has an entry in the Preferences.
     * The key is suffixed with the name of the field.
     */
    private static final String PREFS_PREFIX_FIELD_VISIBILITY = "fields.visibility.";
    public static final String[] PREFS_COVER_VISIBILITY_KEY = {
            // fields.visibility.thumbnail.0
            PREFS_PREFIX_FIELD_VISIBILITY + DBKey.COVER[0],
            // fields.visibility.thumbnail.1
            PREFS_PREFIX_FIELD_VISIBILITY + DBKey.COVER[1]
    };

    /**
     * Constructor.
     */
    GlobalFieldVisibility() {
        super(KEYS, DEFAULT);
    }

    /**
     * Is the field in use; i.e. is it enabled in the user-preferences.
     *
     * @param dbdKey DBKey.KEY_x to lookup
     *
     * @return {@code true} if the user wants to use this field.
     */
    public static boolean isUsed(@NonNull final Context context,
                                 @NonNull final String dbdKey) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PREFS_PREFIX_FIELD_VISIBILITY + dbdKey, true);
    }
}
