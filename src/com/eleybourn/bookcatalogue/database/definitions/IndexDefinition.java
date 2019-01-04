package com.eleybourn.bookcatalogue.database.definitions;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.DbSync;

/**
 * Class to store an index using a table name and a list of domain definitions.
 *
 * @author Philip Warner
 */
public class IndexDefinition {
    /** Full name of index */
    @NonNull
    private final String mName;
    /** Table to which index applies */
    @NonNull
    private final TableDefinition mTable;
    /** Domains in index */
    @NonNull
    private final DomainDefinition[] mDomains;
    /** Options indicating index is unique */
    private final boolean mIsUnique;

    /**
     * Constructor.
     *
     * @param name    name of index
     * @param unique  Options indicating index is unique
     * @param table   Table to which index applies
     * @param domains Domains in index
     */
    IndexDefinition(@NonNull final String name,
                    final boolean unique,
                    @NonNull final TableDefinition table,
                    @NonNull final DomainDefinition... domains) {
        this.mName = name;
        this.mIsUnique = unique;
        this.mTable = table;
        this.mDomains = domains;
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
    public DomainDefinition[] getDomains() {
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
    public IndexDefinition drop(@NonNull final DbSync.SynchronizedDb db) {
        db.execSQL("DROP INDEX If Exists " + mName);
        return this;
    }

    /**
     * Create the index.
     *
     * @param db Database to use.
     *
     * @return IndexDefinition (for chaining)
     */
    @NonNull
    public IndexDefinition create(@NonNull final DbSync.SynchronizedDb db) {
        db.execSQL(this.getSql());
        return this;
    }

    /**
     * Return the SQL used to define the index.
     *
     * @return SQL Fragment
     */
    @NonNull
    public String getSql() {
        StringBuilder sql = new StringBuilder("CREATE ");
        if (mIsUnique) {
            sql.append(" UNIQUE");
        }
        sql.append(" INDEX ");
        sql.append(mName);
        sql.append(" ON ").append(mTable.getName()).append("(\n");
        boolean first = true;
        for (DomainDefinition d : mDomains) {
            if (first) {
                first = false;
            } else {
                sql.append(",\n");
            }
            sql.append("    ");
            sql.append(d.name);
        }
        sql.append(")\n");
        return sql.toString();
    }
}
