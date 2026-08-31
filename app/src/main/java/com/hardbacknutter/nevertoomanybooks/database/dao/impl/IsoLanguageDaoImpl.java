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

package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.database.SQLException;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.IsoLanguageDao;

public class IsoLanguageDaoImpl
        extends BaseDaoImpl
        implements IsoLanguageDao {

    private static final String TAG = "IsoLanguageDaoImpl";

    /**
     * Constructor.
     *
     * @param db Database Access
     */
    public IsoLanguageDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    @NonNull
    @Override
    public String findByDisplayName(@NonNull final String displayName) {

        try (SynchronizedStatement stmt = db.compileStatement(Sql.FIND_BY_DNAME)) {
            stmt.bindString(1, displayName);
            final String iso3 = stmt.simpleQueryForStringOrNull();
            return iso3 != null ? iso3 : displayName;
        }
    }

    @Override
    public int count() {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.COUNT_ALL)) {
            return (int) stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public void add(@NonNull final Locale userLocale)
            throws DaoInsertException {

        final String userIso3 = getIsoCode(userLocale);

        // We get many duplicates
        // iso | name              | display-name
        // eng : English (Jamaica) : English
        // eng : English (Niue)    : English
        // Collect the iso/dname and run a 'distinct'
        final List<Pair<String, String>> iso3Name = Arrays
                .stream(Locale.getAvailableLocales())
                .map(locale -> new Pair<>(getIsoCode(locale), locale
                        .getDisplayLanguage(userLocale).toLowerCase(userLocale)))
                .distinct()
                .collect(Collectors.toList());

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }
            try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
                for (final Pair<String, String> loc : iso3Name) {
                    stmt.bindString(1, userIso3);
                    stmt.bindString(2, loc.first);
                    stmt.bindString(3, loc.second);

                    stmt.executeInsert(() -> "Failed top insert: "
                                             + userIso3 + ": loc: " + loc);
                }
            } catch (@NonNull final SQLException e) {
                throw new DaoInsertException(e);
            }

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    /**
     * We've seen {@link Locale#getISO3Language()} throw {@link MissingResourceException}
     * on a HUAWEI model "ADA-AL10U" with Android 12; see GitHub #58
     * The OS code returned the invalid code "zz" for a language
     * in {@link Locale#getAvailableLocales()}.
     *
     * @param locale to get the ISO3/ISO2 code from.
     *
     * @return iso3/2 code
     *
     * @see <a href="https://github.com/tfonteyn/NeverTooManyBooks/issues/58">GitHub #58</a>
     */
    @NonNull
    private String getIsoCode(@NonNull final Locale locale) {
        String isoCode;
        try {
            isoCode = locale.getISO3Language();
        } catch (@NonNull final MissingResourceException mre) {
            // Fallback to 2-character code
            isoCode = locale.getLanguage();
        }
        return isoCode;
    }

    private static final class Sql {
        static final String COUNT_ALL =
                SELECT_COUNT_FROM_ + DBDefinitions.TBL_LANG_MAPPINGS;

        static final String FIND_BY_DNAME =
                SELECT_ + DBKey.LANG_MAPPING.ISO3
                + _FROM_ + DBDefinitions.TBL_LANG_MAPPINGS
                + _WHERE_ + DBKey.LANG_MAPPING.DISPLAY_NAME + "=?";

        static final String INSERT =
                INSERT_INTO_ + DBDefinitions.TBL_LANG_MAPPINGS
                + '(' + DBKey.LANG_MAPPING.ISO3_USER
                + ',' + DBKey.LANG_MAPPING.ISO3
                + ',' + DBKey.LANG_MAPPING.DISPLAY_NAME
                + ") VALUES(?,?,?)";
    }
}
