/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.database;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertomanybooks.database.definitions.TableDefinition;

/**
 * Class used to build complex joins. Maintaining context and uses foreign keys
 * to automatically build standard joins.
 */
public class Joiner {

    /** Text of join statement. */
    @NonNull
    private final StringBuilder mSql;
    /** Last table added to join. */
    private TableDefinition mCurrentTable;

    /**
     * Constructor.
     *
     * @param table Table that starts join
     */
    public Joiner(@NonNull final TableDefinition table) {
        mCurrentTable = table;
        mSql = new StringBuilder(mCurrentTable.getName())
                       .append(' ')
                       .append(mCurrentTable.getAlias());
    }

    /**
     * Add a new table to the join, connecting it to previous table using foreign keys.
     *
     * @param to New table to add
     *
     * @return Join object (for chaining)
     */
    @NonNull
    public Joiner join(@NonNull final TableDefinition to) {
        mSql.append(mCurrentTable.join(to))
            .append('\n');
        mCurrentTable = to;
        return this;
    }

    /**
     * Add a new table to the join, connecting it to 'from' using foreign keys.
     *
     * @param from Parent table in join
     * @param to   New table to join
     *
     * @return Join object (for chaining)
     */
    @NonNull
    public Joiner join(@NonNull final TableDefinition from,
                       @NonNull final TableDefinition to) {
        mSql.append(from.join(to))
            .append('\n');
        mCurrentTable = to;
        return this;
    }

    /**
     * Same as 'join', but do a 'left outer' join.
     *
     * @param to New table to add.
     *
     * @return Join object (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Joiner leftOuterJoin(@NonNull final TableDefinition to) {
        mSql.append(" LEFT OUTER");
        return join(to);
    }

    /**
     * Same as 'join', but do a 'left outer' join.
     *
     * @param from Parent table in join
     * @param to   New table to join
     *
     * @return Join object (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Joiner leftOuterJoin(@NonNull final TableDefinition from,
                                @NonNull final TableDefinition to) {
        mSql.append(" LEFT OUTER");
        return join(from, to);
    }

    /**
     * Append arbitrary text to the generated SQL.
     * Useful for adding extra conditions to a join clause.
     *
     * @param sql Extra SQL to append
     *
     * @return Join object (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Joiner append(@NonNull final String sql) {
        mSql.append(sql);
        return this;
    }

    /**
     * @return the current SQL
     */
    @NonNull
    public String getSql() {
        return mSql.toString();
    }
}
