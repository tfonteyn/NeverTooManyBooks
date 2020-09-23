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
package com.hardbacknutter.nevertoomanybooks.database.definitions;

import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DAOSql;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;

/**
 * Class to store an index using a table name and a list of domain definitions.
 */
class IndexDefinition {

    /** Table to which index applies. */
    @NonNull
    private final TableDefinition mTable;
    /** Domains in index. */
    @NonNull
    private final List<Domain> mDomains;
    /** Flag indicating index is unique. */
    private final boolean mIsUnique;
    /** suffix to add to the table name. */
    @NonNull
    private final String mNameSuffix;

    /**
     * Constructor.
     *
     * @param table      Table to which index applies
     * @param nameSuffix suffix to add to the table name; together this will become the full name.
     *                   The table name is read <strong>at actual creation time</strong>
     * @param unique     Flag indicating index is unique
     * @param domains    Domains in index
     */
    IndexDefinition(@NonNull final TableDefinition table,
                    @NonNull final String nameSuffix,
                    final boolean unique,
                    @NonNull final List<Domain> domains) {
        mNameSuffix = nameSuffix;
        mIsUnique = unique;
        mTable = table;
        // take a COPY of the list; but the domains themselves are references only.
        mDomains = new ArrayList<>(domains);
    }

    /**
     * Check if this index is unique.
     *
     * @return UNIQUE flag.
     */
    boolean getUnique() {
        return mIsUnique;
    }

    /**
     * Get a copy of the list with domains.
     *
     * @return list of domains in index.
     */
    @NonNull
    List<Domain> getDomains() {
        return new ArrayList<>(mDomains);
    }

    @NonNull
    String getNameSuffix() {
        return mNameSuffix;
    }

    /**
     * Create the index.
     *
     * @param db SQLiteDatabase
     */
    public void create(@NonNull final SQLiteDatabase db) {
        db.execSQL(def());
    }

    /**
     * Create the index.
     *
     * @param db Database Access
     */
    public void create(@NonNull final SynchronizedDb db) {
        db.execSQL(def());
    }

    /**
     * Return the SQL used to define the index.
     *
     * @return SQL Fragment
     */
    @NonNull
    private String def() {
        StringBuilder sql = new StringBuilder("CREATE");
        if (mIsUnique) {
            sql.append(" UNIQUE");
        }
        sql.append(" INDEX ").append(mTable.getName()).append("_IDX_").append(mNameSuffix)
           .append(" ON ").append(mTable.getName())
           .append('(').append(Csv.join(",", mDomains, element -> {
            if (element.isCollationLocalized()) {
                return element.getName() + DAOSql._COLLATION;
            } else {
                return element.getName();
            }
        })).append(')');

        return sql.toString();
    }
}
