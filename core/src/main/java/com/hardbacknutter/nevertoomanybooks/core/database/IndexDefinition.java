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
package com.hardbacknutter.nevertoomanybooks.core.database;

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.DEBUG_FLAGS;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Class to store an index using a table name and a list of domain definitions.
 */
public class IndexDefinition {

    private static final String TAG = "IndexDefinition";

    /** Table to which index applies. */
    @NonNull
    private final TableDefinition table;
    /** suffix to add to the table name. */
    @NonNull
    private final String nameSuffix;
    /** Constructed index name. */
    private final String name;
    /** Flag indicating index is unique. */
    private final boolean unique;

    /** Domains in index. */
    @NonNull
    private final List<Domain> domains;


    /**
     * Constructor.
     * <p>
     * The full name of the index will be constructed as
     * {@code table.getName() + "_IDX_" + nameSuffix + "_" + indexNumber}.
     * The table name is read <strong>at index creation time</strong>
     *
     * @param table       Table to which index applies
     * @param nameSuffix  suffix for the index name
     * @param indexNumber secondary suffix for the index name
     * @param unique      Flag indicating index is unique
     * @param domains     Domains in index
     */
    IndexDefinition(@NonNull final TableDefinition table,
                    @NonNull final String nameSuffix,
                    final int indexNumber,
                    final boolean unique,
                    @NonNull final List<Domain> domains) {
        this.table = table;
        this.nameSuffix = nameSuffix;
        this.name = table.getName() + "_IDX_" + nameSuffix + "_" + indexNumber;

        this.unique = unique;
        // take a COPY of the list; but the domains themselves are references only.
        this.domains = new ArrayList<>(domains);
    }

    /**
     * Get the name as set in the constructor.
     *
     * @return name
     */
    @NonNull
    String getNameSuffix() {
        return nameSuffix;
    }

    /**
     * Get the full/constructed name.
     *
     * @return name
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Create the index.
     *
     * @param db                     Underlying database
     * @param collationCaseSensitive flag; whether the database uses case-sensitive collation
     *
     * @throws SQLException on unexpected failures
     */
    public void create(@NonNull final SQLiteDatabase db,
                       final boolean collationCaseSensitive) {
        final String createStatement = getCreateStatement(collationCaseSensitive);
        if (BuildConfig.DEBUG && DEBUG_FLAGS.DEBUG_EXEC_SQL) {
            LoggerFactory.getLogger()
                         .d(TAG, "execute", createStatement);
        }
        db.execSQL(createStatement);
    }

    /**
     * Delete the index.
     *
     * @param db Underlying database
     *
     * @throws SQLException on unexpected failures
     */
    public void delete(@NonNull final SQLiteDatabase db) {
        db.execSQL("DROP INDEX IF EXISTS " + name);
    }

    /**
     * Return the SQL used to define the index.
     *
     * @param collationCaseSensitive flag; whether the database uses case-sensitive collation
     *
     * @return SQL Fragment
     */
    @NonNull
    private String getCreateStatement(final boolean collationCaseSensitive) {
        final StringBuilder sql = new StringBuilder("CREATE");
        if (unique) {
            sql.append(" UNIQUE");
        }
        sql.append(" INDEX ").append(name).append(" ON ").append(table.getName())
           .append(domains.stream()
                          .map(domain -> domain.getOrderByString(domain.getIndexSortingOrder(),
                                                                 collationCaseSensitive))
                          .collect(Collectors.joining(",", "(", ")")));

        return sql.toString();
    }

    @Override
    @NonNull
    public String toString() {
        return "IndexDefinition{"
               + "table=" + table
               + ", name=`" + name + '`'
               + ", nameSuffix=`" + nameSuffix + '`'
               + ", unique=" + unique
               + ", domains=" + domains
               + "}";
    }
}
