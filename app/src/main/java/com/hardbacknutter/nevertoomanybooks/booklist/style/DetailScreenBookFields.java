/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.content.SharedPreferences;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;

/**
 * Encapsulate the Book fields which can be shown on the Book-details screen.
 */
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
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     */
    DetailScreenBookFields(final boolean isPersistent,
                           @NonNull final StylePersistenceLayer persistenceLayer) {

        final SharedPreferences global = ServiceLocator.getGlobalPreferences();

        for (int cIdx = 0; cIdx < 2; cIdx++) {
            addField(new PBoolean(isPersistent, persistenceLayer,
                                  PK_COVER[cIdx], DBKeys.isCoverUsed(global, cIdx)));
        }
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param bookFields       to copy from
     */
    DetailScreenBookFields(final boolean isPersistent,
                           @NonNull final StylePersistenceLayer persistenceLayer,
                           @NonNull final DetailScreenBookFields bookFields) {
        super(isPersistent, persistenceLayer, bookFields);
    }

    /**
     * Convenience method to check if a cover (front/back) should be
     * show on the <strong>details</strong> screen.
     *
     * @param global the <strong>GLOBAL</strong> preferences
     * @param cIdx   0..n image index
     *
     * @return {@code true} if in use
     */
    public boolean isShowCover(@NonNull final SharedPreferences global,
                               @IntRange(from = 0, to = 1) final int cIdx) {
        return isShowField(global, PK_COVER[cIdx]);
    }

    @Override
    @NonNull
    public String toString() {
        return "DetailScreenBookFields{"
               + super.toString()
               + '}';
    }
}
