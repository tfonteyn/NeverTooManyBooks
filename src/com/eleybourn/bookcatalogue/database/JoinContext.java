package com.eleybourn.bookcatalogue.database;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;

/**
 * Class used to build complex joins. Maintaining context and uses foreign keys
 * to automatically build standard joins.
 *
 * @author Philip Warner
 */
public class JoinContext {

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
    public JoinContext(@NonNull final TableDefinition table) {
        mCurrentTable = table;
        mSql = new StringBuilder();
        mSql.append(mCurrentTable.getName()).append(' ').append(mCurrentTable.getAlias());
    }

    /**
     * Add a new table to the join, connecting it to previous table using foreign keys.
     *
     * @param to New table to add
     *
     * @return Join object (for chaining)
     */
    @NonNull
    public JoinContext join(@NonNull final TableDefinition to) {
        mSql.append(mCurrentTable.join(to));
        mSql.append('\n');
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
    public JoinContext join(@NonNull final TableDefinition from,
                            @NonNull final TableDefinition to) {
        mSql.append(from.join(to));
        mSql.append('\n');
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
    public JoinContext leftOuterJoin(@NonNull final TableDefinition to) {
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
    public JoinContext leftOuterJoin(@NonNull final TableDefinition from,
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
    public JoinContext append(@NonNull final String sql) {
        mSql.append(sql);
        return this;
    }

    /**
     * @return the current SQL
     */
    @Override
    @NonNull
    public String toString() {
        return mSql.toString();
    }
}
