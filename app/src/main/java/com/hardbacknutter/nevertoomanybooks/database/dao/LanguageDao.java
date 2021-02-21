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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;

public final class LanguageDao
        extends BaseDao {

    /** Log tag. */
    private static final String TAG = "LanguageDao";

    /** Code only. */
    private static final String SELECT_ALL =
            SELECT_DISTINCT_ + KEY_LANGUAGE
            + _FROM_ + TBL_BOOKS.getName()
            + _WHERE_ + KEY_LANGUAGE + "<> ''"
            + _ORDER_BY_ + KEY_UTC_LAST_UPDATED + _COLLATION;

    /** Global rename. */
    private static final String UPDATE =
            UPDATE_ + TBL_BOOKS.getName()
            + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
            + ',' + KEY_LANGUAGE + "=?"
            + _WHERE_ + KEY_LANGUAGE + "=?";

    /** Singleton. */
    private static LanguageDao sInstance;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param logTag  of this DAO for logging.
     */
    private LanguageDao(@NonNull final Context context,
                        @NonNull final String logTag) {
        super(context, logTag);
    }

    public static LanguageDao getInstance() {
        if (sInstance == null) {
            sInstance = new LanguageDao(App.getDatabaseContext(), TAG);
        }

        return sInstance;
    }

    /**
     * Get a unique list of all language codes.
     * The list is ordered by {@link DBDefinitions#KEY_UTC_LAST_UPDATED}.
     *
     * @return The list; normally all ISO codes
     */
    @NonNull
    public ArrayList<String> getCodeList() {
        try (Cursor cursor = mDb.rawQuery(SELECT_ALL, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    /**
     * Get a unique list of all resolved/localized language names.
     * The list is ordered by {@link DBDefinitions#KEY_UTC_LAST_UPDATED}.
     *
     * @param context Current context
     *
     * @return The list; may contain custom names.
     */
    @NonNull
    public ArrayList<String> getNameList(@NonNull final Context context) {
        try (Cursor cursor = mDb.rawQuery(SELECT_ALL, null)) {
            // Using a Set to avoid duplicates
            // The cursor is distinct, but we need to make sure code->name does not create
            // duplicates (very unlikely, but not impossible)
            final Set<String> set = new LinkedHashSet<>();
            while (cursor.moveToNext()) {
                final String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) {
                    set.add(Languages.getInstance().getDisplayNameFromISO3(context, name));
                }
            }
            return new ArrayList<>(set);
        }
    }

    public void update(@NonNull final String from,
                       @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }

        try (SynchronizedStatement stmt = mDb.compileStatement(UPDATE)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

}
