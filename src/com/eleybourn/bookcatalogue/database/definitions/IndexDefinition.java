package com.eleybourn.bookcatalogue.database.definitions;

import android.database.Cursor;
import android.database.SQLException;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.Csv;

/**
 * Class to store an index using a table name and a list of domain definitions.
 *
 * @author Philip Warner
 */
public class IndexDefinition {

    /** SQL to get the names of all indexes. */
    private static final String SQL_GET_INDEX_NAMES =
            "SELECT name FROM sqlite_master WHERE type = 'index' AND sql is not null;";

    /** Full name of index. */
    @NonNull
    private final String mName;
    /** Table to which index applies. */
    @NonNull
    private final TableDefinition mTable;
    /** Domains in index. */
    @NonNull
    private final List<DomainDefinition> mDomains;
    /** Flag indicating index is unique. */
    private final boolean mIsUnique;

    /**
     * Constructor.
     *
     * @param name    name of index
     * @param unique  Flag indicating index is unique
     * @param table   Table to which index applies
     * @param domains Domains in index
     */
    IndexDefinition(@NonNull final String name,
                    final boolean unique,
                    @NonNull final TableDefinition table,
                    @NonNull final List<DomainDefinition> domains) {
        mName = name;
        mIsUnique = unique;
        mTable = table;
        // take a COPY
        mDomains = new ArrayList<>(domains);
    }

    /**
     * Find and delete all indexes on all tables.
     *
     * @param db the database.
     */
    public static void dropAllIndexes(@NonNull final SynchronizedDb db) {
        try (Cursor current = db.rawQuery(SQL_GET_INDEX_NAMES, null)) {
            while (current.moveToNext()) {
                String indexName = current.getString(0);
                try {
                    db.execSQL("DROP INDEX " + indexName);
                } catch (SQLException e) {
                    // bad sql is a developer issue... die!
                    Logger.error(IndexDefinition.class, e);
                    throw e;
                } catch (RuntimeException e) {
                    Logger.error(IndexDefinition.class, e, "Index deletion failed: " + indexName);
                }
            }
        }
    }

    /**
     * @return UNIQUE flag.
     */
    public boolean getUnique() {
        return mIsUnique;
    }

    /**
     * @return list of domains in index.
     */
    @NonNull
    List<DomainDefinition> getDomains() {
        return mDomains;
    }

    /**
     * Drop the index, if it exists.
     *
     * @param db Database to use.
     *
     * @return IndexDefinition (for chaining)
     */
    @NonNull
    public IndexDefinition drop(@NonNull final SynchronizedDb db) {
        db.execSQL("DROP INDEX IF EXISTS " + mName);
        return this;
    }

    /**
     * Create the index.
     *
     * @param db Database to use.
     */
    public void create(@NonNull final SynchronizedDb db) {
        db.execSQL(getSqlCreateStatement());
    }

    /**
     * Return the SQL used to define the index.
     *
     * @return SQL Fragment
     */
    @NonNull
    private String getSqlCreateStatement() {
        StringBuilder sql = new StringBuilder("CREATE");
        if (mIsUnique) {
            sql.append(" UNIQUE");
        }
        sql.append(" INDEX ").append(mName).append(" ON ").append(mTable.getName())
           .append('(').append(Csv.join(",", mDomains)).append(')');

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SQL_CREATE_INDEX) {
            Logger.debugExit(this, "getSqlCreateStatement",
                             sql.toString());
        }
        return sql.toString();
    }
}
