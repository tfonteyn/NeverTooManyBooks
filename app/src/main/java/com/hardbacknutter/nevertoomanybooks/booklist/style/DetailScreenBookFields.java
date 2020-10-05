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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

/** Encapsulate the Book fields which can be shown on the Book-details screen. */
public class DetailScreenBookFields
        extends BookFields {

    /** Show the cover images (front/back) for each book on the details screen. */
    public static final String[] PK_COVER = new String[]{
            "style.details.show.thumbnail.0",
            "style.details.show.thumbnail.1",
            };

    /**
     * Constructor.
     *
     * @param context       Current context
     * @param stylePrefs    the SharedPreferences for the style
     * @param isUserDefined flag
     */
    DetailScreenBookFields(@NonNull final Context context,
                           @NonNull final SharedPreferences stylePrefs,
                           final boolean isUserDefined) {

        final SharedPreferences globalPrefs =
                PreferenceManager.getDefaultSharedPreferences(context);

        for (int cIdx = 0; cIdx < 2; cIdx++) {
            mFields.put(PK_COVER[cIdx],
                        new PBoolean(stylePrefs, isUserDefined, PK_COVER[cIdx],
                                     DBDefinitions.isCoverUsed(globalPrefs, cIdx)));
        }
    }

    /**
     * Convenience method to check if a cover (front/back) should be
     * show on the <strong>details</strong> screen.
     *
     * @param context     Current context
     * @param preferences the <strong>GLOBAL</strong> preferences
     * @param cIdx        0..n image index
     *
     * @return {@code true} if in use
     */
    public boolean isShowCover(@NonNull final Context context,
                               @NonNull final SharedPreferences preferences,
                               @IntRange(from = 0, to = 1) final int cIdx) {
        return isShowField(context, preferences, PK_COVER[cIdx]);
    }
}
