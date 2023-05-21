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

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
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
            + _ORDER_BY_ + DBKey.DATE_LAST_UPDATED__UTC + _DESC;
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
     * Get the list of all language <strong>ISO codes</strong>; sorted by by last-updated date.
     * i.e. the language last used when updating a book will show first in the list.
     *
     * @return list with unique iso3 language codes
     */
    @Override
    @NonNull
    public List<String> getList() {
        return getColumnAsStringArrayList(SELECT_ALL);
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
                    LoggerFactory.getLogger().d(TAG, "bulkUpdate",
                                                "|from=" + lang,
                                                "to=" + iso);
                }
                if (!iso.equals(lang)) {
                    rename(lang, iso);
                }
            }
        }
    }
}
