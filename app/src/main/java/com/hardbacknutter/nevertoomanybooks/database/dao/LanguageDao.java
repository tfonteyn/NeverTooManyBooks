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

package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public interface LanguageDao
        extends InlineStringDao {

    /**
     * Get a unique list of all resolved/localized language names.
     * The list is ordered by {@link DBKey#DATE_LAST_UPDATED__UTC}.
     *
     * @param context Current context
     *
     * @return The list; may contain custom names.
     */
    @NonNull
    ArrayList<String> getNameList(@NonNull Context context);

    /**
     * Do a bulk update of any languages not yet converted to ISO codes.
     * Special entries are left untouched; example "Dutch+French" a bilingual edition.
     *
     * @param context Current context
     */
    void bulkUpdate(@NonNull Context context);
}
