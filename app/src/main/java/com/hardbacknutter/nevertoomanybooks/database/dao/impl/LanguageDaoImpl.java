/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.LanguageDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

public class LanguageDaoImpl
        extends InlineStringDaoImpl
        implements LanguageDao {

    /** Log tag. */
    private static final String TAG = "LanguageDaoImpl";

    /** Code only, ordered by last-used. */
    private static final String SELECT_ALL =
            SELECT_DISTINCT_ + DBKey.LANGUAGE
            + _FROM_ + DBDefinitions.TBL_BOOKS.getName()
            + _WHERE_ + DBKey.LANGUAGE + "<> ''"
            + _ORDER_BY_ + DBKey.DATE_LAST_UPDATED__UTC;

    /**
     * Constructor.
     */
    public LanguageDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG, DBKey.LANGUAGE);
    }

    /**
     * Get the list of all language codes; sorted by by last-updated date.
     * i.e. the language last used when updating a book will show first in the list.
     *
     * @return list with iso3 language codes
     */
    @Override
    @NonNull
    public ArrayList<String> getList() {
        return getColumnAsStringArrayList(SELECT_ALL);
    }

    @Override
    @NonNull
    public ArrayList<String> getNameList(@NonNull final Context context) {
        try (Cursor cursor = db.rawQuery(SELECT_ALL, null)) {
            // Using a Set to avoid duplicates
            // The cursor is distinct, but we need to make sure code->name does not create
            // duplicates (very unlikely, but not impossible)
            final Set<String> set = new LinkedHashSet<>();
            final Languages languages = ServiceLocator.getInstance().getLanguages();

            while (cursor.moveToNext()) {
                final String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) {
                    set.add(languages.getDisplayNameFromISO3(context, name));
                }
            }
            return new ArrayList<>(set);
        }
    }
}
