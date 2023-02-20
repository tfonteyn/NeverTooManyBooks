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
package com.hardbacknutter.nevertoomanybooks.core.database;

import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class to store an index using a table name and a list of domain definitions.
 */
class IndexDefinition {

    /** Table to which index applies. */
    @NonNull
    private final TableDefinition table;
    /** suffix to add to the table name. */
    @NonNull
    private final String nameSuffix;
    /** Flag indicating index is unique. */
    private final boolean unique;

    /** Domains in index. */
    @NonNull
    private final List<Domain> domains;

    /**
     * Constructor.
     *
     * @param table      Table to which index applies
     * @param nameSuffix suffix to add to the table name; together this will become the full name.
     *                   The table name is read <strong>at index creation time</strong>
     * @param unique     Flag indicating index is unique
     * @param domains    Domains in index
     */
    IndexDefinition(@NonNull final TableDefinition table,
                    @NonNull final String nameSuffix,
                    final boolean unique,
                    @NonNull final List<Domain> domains) {
        this.table = table;
        this.nameSuffix = nameSuffix;
        this.unique = unique;
        // take a COPY of the list; but the domains themselves are references only.
        this.domains = new ArrayList<>(domains);
    }

    /**
     * Create the index.
     *
     * @param db Database Access
     */
    public void create(@NonNull final SQLiteDatabase db,
                       final boolean collationCaseSensitive) {
        db.execSQL(getCreateStatement(collationCaseSensitive));
    }

    /**
     * Return the SQL used to define the index.
     *
     * @return SQL Fragment
     */
    @NonNull
    private String getCreateStatement(final boolean collationCaseSensitive) {
        final StringBuilder sql = new StringBuilder("CREATE");
        if (unique) {
            sql.append(" UNIQUE");
        }
        sql.append(" INDEX ").append(table.getName()).append("_IDX_").append(nameSuffix)
           .append(" ON ").append(table.getName())
           .append(domains.stream()
                          .map(domain -> domain.getOrderByString(Sort.Unsorted,
                                                                 collationCaseSensitive))
                          .collect(Collectors.joining(",", "(", ")")));

        return sql.toString();
    }

    @Override
    @NonNull
    public String toString() {
        return "IndexDefinition{"
               + "table=" + table
               + ", nameSuffix=`" + nameSuffix + '`'
               + ", unique=" + unique
               + ", domains=" + domains
               + "}";
    }
}
