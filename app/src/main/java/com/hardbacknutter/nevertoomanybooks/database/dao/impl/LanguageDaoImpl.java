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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.LanguageDao;
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
    @NonNull
    private final Supplier<Languages> languagesSupplier;


    /**
     * Constructor.
     *
     * @param db                Underlying database
     * @param languagesSupplier deferred supplier for the {@link Languages}
     */
    public LanguageDaoImpl(@NonNull final SynchronizedDb db,
                           @NonNull final Supplier<Languages> languagesSupplier) {
        super(db, TAG, DBKey.LANGUAGE);
        this.languagesSupplier = languagesSupplier;
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
            while (cursor.moveToNext()) {
                final String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) {
                    set.add(languagesSupplier.get().getDisplayNameFromISO3(context, name));
                }
            }
            return new ArrayList<>(set);
        }
    }

    @Override
    public void bulkUpdate(@NonNull final Context context) {
        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

        for (final String lang : getList()) {
            if (lang != null && !lang.isEmpty()) {
                final String iso;

                if (lang.length() > 3) {
                    // It's likely a 'display' name of a language.
                    iso = languagesSupplier.get().getISO3FromDisplayName(context, locale, lang);
                } else {
                    // It's almost certainly a language code
                    iso = languagesSupplier.get().getISO3FromCode(lang);
                }

                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "languages|Global language update"
                               + "|from=" + lang
                               + "|to=" + iso);
                }
                if (!iso.equals(lang)) {
                    rename(lang, iso);
                }
            }
        }
    }
}
