package com.eleybourn.bookcatalogue.database.definitions;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.DbSync;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to store table name and a list of domain definitions.
 *
 * @author Philip Warner
 */
public class TableDefinition implements AutoCloseable, Cloneable {
    private final static String mExistsSql =
            "SELECT (SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?) + " +
            "(SELECT COUNT(*) FROM sqlite_temp_master WHERE type='table' AND name=?)";
    /** List of index definitions for this table */
    private final Map<String, IndexDefinition> mIndexes = Collections.synchronizedMap(new HashMap<String, IndexDefinition>());
    /** List of domains in this table */
    @NonNull
    private final List<DomainDefinition> mDomains;
    /** Used for checking if a domain has already been added */
    private final Set<DomainDefinition> mDomainCheck = new HashSet<>();
    /** Used for checking if a domain NAME has already been added */
    private final Map<String, DomainDefinition> mDomainNameCheck = Collections.synchronizedMap(new HashMap<String, DomainDefinition>());
    /** List of domains forming primary key */
    private final List<DomainDefinition> mPrimaryKey = new ArrayList<>();
    /** List of parent tables (tables referred to by foreign keys on this table) */
    private final Map<TableDefinition, FkReference> mParents = Collections.synchronizedMap(new HashMap<TableDefinition, FkReference>());
    /** List of child tables (tables referring to by foreign keys to this table) */
    private final Map<TableDefinition, FkReference> mChildren = Collections.synchronizedMap(new HashMap<TableDefinition, FkReference>());
    /** Table name */
    private String mName;
    /** Table alias */
    private String mAlias;
    /** Options indicating table is temporary */
    @NonNull
    private TableTypes mType = TableTypes.Standard;

    /**
     * Constructor
     *
     * @param name    Table name
     * @param domains List of domains in table
     */
    public TableDefinition(@NonNull final String name, @NonNull final DomainDefinition... domains) {
        this.mName = name;
        this.mAlias = name;
        this.mDomains = new ArrayList<>();
        this.mDomains.addAll(Arrays.asList(domains));
    }

    /**
     * Constructor (empty table)
     */
    private TableDefinition() {
        this.mName = "";
        this.mDomains = new ArrayList<>();
    }

    /**
     * Static method to drop the passed table, if it exists.
     */
    private static void drop(@NonNull final DbSync.SynchronizedDb db, @NonNull final String name) {
        if (BuildConfig.DEBUG) {
            Logger.debug("Dropping TABLE " + name);
        }
        db.execSQL("DROP TABLE If Exists " + name);
    }

    /**
     * Given arrays of table and index definitions, create the database.
     *
     * @param db     Blank database
     * @param tables Table list
     */
    public static void createTables(@NonNull final DbSync.SynchronizedDb db, @NonNull final TableDefinition... tables) {
        for (TableDefinition table : tables) {
            table.create(db, true);
            for (IndexDefinition index : table.getIndexes()) {
                db.execSQL(index.getSql());
            }
        }
    }

    /**
     * @return list of domains.
     */
    @NonNull
    public List<DomainDefinition> getDomains() {
        return mDomains;
    }

    /**
     * Remove all references and resources used by this table.
     */
    @Override
    public void close() {
        mDomains.clear();
        mDomainCheck.clear();
        mDomainNameCheck.clear();
        mPrimaryKey.clear();
        mIndexes.clear();

        // Need to make local copies to avoid 'collection modified' errors
        List<TableDefinition> tmpParents = new ArrayList<>();
        for (Map.Entry<TableDefinition, FkReference> fkEntry : mParents.entrySet()) {
            FkReference fk = fkEntry.getValue();
            tmpParents.add(fk.parent);
        }
        for (TableDefinition parent : tmpParents) {
            removeReference(parent);
        }

        // Need to make local copies to avoid 'collection modified' errors
        List<TableDefinition> tmpChildren = new ArrayList<>();
        for (Map.Entry<TableDefinition, FkReference> fkEntry : mChildren.entrySet()) {
            FkReference fk = fkEntry.getValue();
            tmpChildren.add(fk.child);
        }
        for (TableDefinition child : tmpChildren) {
            child.removeReference(this);
        }
    }

    /**
     * Make a copy of this table.
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @NonNull
    public TableDefinition clone() {
        TableDefinition newTbl = new TableDefinition();
        newTbl.setName(mName);
        newTbl.setAlias(mAlias);
        newTbl.addDomains(mDomains);
        newTbl.setPrimaryKey(mPrimaryKey);
        newTbl.setType(mType);

        for (Map.Entry<TableDefinition, FkReference> fkEntry : mParents.entrySet()) {
            FkReference fk = fkEntry.getValue();
            newTbl.addReference(fk.parent, fk.domains);
        }
        for (Map.Entry<TableDefinition, FkReference> fkEntry : mChildren.entrySet()) {
            FkReference fk = fkEntry.getValue();
            fk.child.addReference(newTbl, fk.domains);
        }
        for (Map.Entry<String, IndexDefinition> e : mIndexes.entrySet()) {
            IndexDefinition i = e.getValue();
            newTbl.addIndex(e.getKey(), i.getUnique(), i.getDomains());
        }
        return newTbl;
    }

    /**
     * @return indexes on this table.
     */
    @NonNull
    private Collection<IndexDefinition> getIndexes() {
        return mIndexes.values();
    }

    /**
     * Accessor. Get the table name.
     *
     * @return table name.
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Set the table name. Useful for cloned tables.
     *
     * @param newName New table name
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public TableDefinition setName(@NonNull final String newName) {
        this.mName = newName;
        return this;
    }

    /**
     * Accessor. Get the table alias, or if blank, return the table name.
     *
     * @return Alias
     */
    @NonNull
    public String getAlias() {
        if (mAlias == null || mAlias.isEmpty()) {
            return getName();
        } else {
            return mAlias;
        }
    }

    /**
     * Set the table alias. Useful for cloned tables.
     *
     * @param newAlias New table alias
     *
     * @return TableDefinition (for chaining)
     */
    @NonNull
    public TableDefinition setAlias(@NonNull final String newAlias) {
        mAlias = newAlias;
        return this;
    }

    /**
     * Utility routine to return <table-alias>.<domain-name>.
     *
     * @param d Domain
     *
     * @return SQL fragment
     */
    @NonNull
    public String dot(@NonNull final DomainDefinition d) {
        return getAlias() + "." + d.name;
    }

    /**
     * Utility routine to return [table-alias].[domain-name] as [domain_name]; this format
     * is useful in older SQLite installations that add make the alias part of the output
     * column name.
     *
     * @param d Domain
     *
     * @return SQL fragment
     */
    @SuppressWarnings("unused")
    @NonNull
    public String dotAs(@NonNull final DomainDefinition d) {
        return getAlias() + "." + d.name + " AS " + d.name;
    }

    /**
     * Utility routine to return [table-alias].[domain-name] as [asDomain]; this format
     * is useful when multiple differing versions of a domain are retrieved.
     *
     * @param d Domain
     *
     * @return SQL fragment
     */
    @NonNull
    public String dotAs(@NonNull final DomainDefinition d, @NonNull final DomainDefinition asDomain) {
        return getAlias() + "." + d.name + " AS " + asDomain.name;
    }

    /**
     * Utility routine to return <table-alias>.<name>.
     *
     * @param s Domain name
     *
     * @return SQL fragment
     */
    @NonNull
    public String dot(@NonNull final String s) {
        return getAlias() + "." + s;
    }

    /**
     * Set the primary key domains
     *
     * @param domains List of domains in PK
     *
     * @return TableDefinition (for chaining)
     */
    @NonNull
    public TableDefinition setPrimaryKey(@NonNull final DomainDefinition... domains) {
        mPrimaryKey.clear();
        Collections.addAll(mPrimaryKey, domains);
        return this;
    }

    /**
     * Common code to add a foreign key (FK) references to another (parent) table.
     *
     * @param fk The FK object
     *
     * @return TableDefinition (for chaining)
     */
    @NonNull
    private TableDefinition addReference(@NonNull final FkReference fk) {
        if (fk.child != this) {
            throw new IllegalStateException("Foreign key does not include this table as child");
        }
        mParents.put(fk.parent, fk);
        fk.parent.addChild(this, fk);
        return this;
    }

    /**
     * Add a foreign key (FK) references to another (parent) table.
     *
     * @param parent  The referenced table
     * @param domains Domains in this table that reference Primary Key (PK) in parent
     *
     * @return TableDefinition (for chaining)
     */
    @NonNull
    public TableDefinition addReference(@NonNull final TableDefinition parent,
                                        @NonNull final DomainDefinition... domains) {
        FkReference fk = new FkReference(parent, this, domains);
        return addReference(fk);
    }

    /**
     * Add a foreign key (FK) references to another (parent) table.
     *
     * @param parent  The referenced table
     * @param domains Domains in this table that reference Primary Key (PK) in parent
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    private TableDefinition addReference(@NonNull final TableDefinition parent,
                                         @NonNull final List<DomainDefinition> domains) {
        FkReference fk = new FkReference(parent, this, domains);
        return addReference(fk);
    }

    /**
     * Remove FK reference to parent table
     *
     * @param parent The referenced Table
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    private TableDefinition removeReference(@NonNull final TableDefinition parent) {
        mParents.remove(parent);
        parent.removeChild(this);
        return this;
    }

    /**
     * Add a child table reference to this table.
     *
     * @param child Child table
     * @param fk    FK object
     *
     * @return TableDefinition (for chaining)
     */
    @NonNull
    @SuppressWarnings("UnusedReturnValue")
    private TableDefinition addChild(@NonNull final TableDefinition child,
                                     @NonNull final FkReference fk) {
        if (!mChildren.containsKey(child)) {
            mChildren.put(child, fk);
        }
        return this;
    }

    /**
     * Remove a child FK reference from this table.
     *
     * @param child Child table
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    private TableDefinition removeChild(@NonNull final TableDefinition child) {
        mChildren.remove(child);
        return this;
    }

    /**
     * Add a domain to this table
     *
     * @param domain Domain object to add
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public TableDefinition addDomain(@NonNull final DomainDefinition domain) {
        // Make sure it's not already in the table
        if (mDomainCheck.contains(domain)) {
            return this;
        }
        // Make sure one with same name is not already in table
        if (mDomainNameCheck.containsKey(domain.name.toLowerCase())) {
            throw new IllegalArgumentException("A domain '"+ domain +"' has already been added");
        }
        // Add it
        mDomains.add(domain);
        mDomainCheck.add(domain);
        mDomainNameCheck.put(domain.name, domain);
        return this;
    }

    /**
     * Add a list of domains to this table.
     *
     * @param domains List of domains to add
     *
     * @return TableDefinition (for chaining)
     */
    @NonNull
    public TableDefinition addDomains(@NonNull final DomainDefinition... domains) {
        for (DomainDefinition d : domains) {
            addDomain(d);
        }
        return this;
    }

    /**
     * Add a list of domains to this table.
     *
     * @param domains List of domains to add
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    private TableDefinition addDomains(@NonNull final List<DomainDefinition> domains) {
        for (DomainDefinition d : domains) {
            addDomain(d);
        }
        return this;
    }

    /**
     * Add an index to this table
     *
     * @param localKey Local name, unique for this table, to give this index. Alphanumeric Only.
     * @param unique   FLag indicating index is UNIQUE
     * @param domains  List of domains index
     *
     * @return TableDefinition (for chaining)
     */
    @NonNull
    public TableDefinition addIndex(@NonNull final String localKey,
                                    final boolean unique,
                                    @NonNull final DomainDefinition... domains) {
        // Make sure not already defined
        if (mIndexes.containsKey(localKey)) {
            throw new IllegalStateException("Index with local name '" + localKey + "' already defined");
        }
        // Construct the full index name
        String name = this.mName + "_IX" + (mIndexes.size() + 1) + "_" + localKey;
        mIndexes.put(localKey, new IndexDefinition(name, unique, this, domains));
        return this;
    }

    /**
     * Drop this table from the passed DB.
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public TableDefinition drop(@NonNull final DbSync.SynchronizedDb db) {
        drop(db, mName);
        return this;
    }

    /**
     * Create this table.
     *
     * @param db              Database in which to create table
     * @param withConstraints Indicates if fields should have constraints applied
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public TableDefinition create(@NonNull final DbSync.SynchronizedDb db,
                                  final boolean withConstraints) {
        if (BuildConfig.DEBUG) {
            String s = this.getSql(mName, withConstraints, false);
            if (DEBUG_SWITCHES.SQL) {
                Logger.debug(s);
            } else {
                Logger.debug("Creating table " + s.substring(0, 30));
            }
        }
        db.execSQL(this.getSql(mName, withConstraints, false));
        return this;
    }

    /**
     * Create this table and related objects (indices).
     *
     * @param db              Database in which to create table
     * @param withConstraints Indicates if fields should have constraints applied
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public TableDefinition createAll(@NonNull final DbSync.SynchronizedDb db,
                                     final boolean withConstraints) {
        db.execSQL(this.getSql(mName, withConstraints, false));
        createIndices(db);
        return this;
    }

    /**
     * Create this table if it is not already present.
     *
     * @param db              Database in which to create table
     * @param withConstraints Indicates if fields should have constraints applied
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("unused")
    @NonNull
    public TableDefinition createIfNecessary(@NonNull final DbSync.SynchronizedDb db,
                                             final boolean withConstraints) {
        db.execSQL(this.getSql(mName, withConstraints, true));
        return this;
    }

    /**
     * Get a base INSERT statement for this table using the passed list of domains. Returns partial
     * SQL of the form: 'INSERT into [table-name] ( [domain-list] )'.
     *
     * @param domains List of domains to use
     *
     * @return SQL fragment
     */
    @NonNull
    public String getInsert(@NonNull final DomainDefinition... domains) {
        StringBuilder s = new StringBuilder("Insert INTO ");
        s.append(mName);
        s.append(" (\n");

        s.append("	");
        s.append(domains[0]);
        for (int i = 1; i < domains.length; i++) {
            s.append(",\n	");
            s.append(domains[i]);
        }
        s.append(")");
        return s.toString();
    }

    /**
     * Get a base list of registered domain fields for this table. Returns partial
     * SQL of the form: '[alias].[domain-1], ..., [alias].[domain-n]'.
     *
     * Keep in mind that not all tables are fully registered in {@link DatabaseDefinitions}
     * Add domains there when needed. Eventually the lists will get semi-complete.
     *
     * @return SQL fragment
     */
    @NonNull
    public String refAll() {
        final String aliasDot = getAlias() + ".";
        final StringBuilder s = new StringBuilder(aliasDot);
        s.append(mDomains.get(0).name);

        for (int i = 1; i < mDomains.size(); i++) {
            s.append(",\n");
            s.append(aliasDot);
            s.append(mDomains.get(i).name);
        }
        return s.toString();
    }
    /**
     * Get a base list of fields for this table using the passed list of domains. Returns partial
     * SQL of the form: '[alias].[domain-1], ..., [alias].[domain-n]'.
     *
     * @param domains List of domains to use
     *
     * @return SQL fragment
     */
    @NonNull
    public String ref(@Nullable final DomainDefinition... domains) {
        if (domains == null || domains.length == 0) {
            return "";
        }

        final String aliasDot = getAlias() + ".";
        final StringBuilder s = new StringBuilder(aliasDot);
        s.append(domains[0].name);

        for (int i = 1; i < domains.length; i++) {
            s.append(",\n");
            s.append(aliasDot);
            s.append(domains[i].name);
        }
        return s.toString();
    }

    /**
     * Get a base UPDATE statement for this table using the passed list of domains. Returns partial
     * SQL of the form: 'UPDATE [table-name] Set [domain-1] = ?, ..., [domain-n] = ?'.
     *
     * @param domains List of domains to use
     *
     * @return SQL fragment
     */
    @NonNull
    public String getUpdate(@NonNull final DomainDefinition... domains) {
        StringBuilder s = new StringBuilder("UPDATE ");
        s.append(mName);
        s.append(" SET ");
        s.append(domains[0]);
        s.append("=?");
        for (int i = 1; i < domains.length; i++) {
            s.append(",	");
            s.append(domains[i]);
            s.append("=?");
        }
        s.append("\n");
        return s.toString();
    }

    /**
     * Get a base 'INSERT or REPLACE' statement for this table using the passed list of domains. Returns partial
     * SQL of the form: 'INSERT or REPLACE INTO [table-name] ( [domain-1] ) Values (?, ..., ?)'.
     *
     * @param domains List of domains to use
     *
     * @return SQL fragment
     */
    @NonNull
    public String getInsertOrReplaceValues(@NonNull final DomainDefinition... domains) {
        StringBuilder s = new StringBuilder("INSERT OR REPLACE INTO ");
        StringBuilder sPlaceholders = new StringBuilder("?");
        s.append(mName);
        s.append(" ( ");
        s.append(domains[0]);

        for (int i = 1; i < domains.length; i++) {
            s.append(", ");
            s.append(domains[i]);

            sPlaceholders.append(", ?");
        }
        s.append(")\n	values (");
        s.append(sPlaceholders);
        s.append(")\n");
        return s.toString();
    }

    /**
     * Setter. Set type indicating table is a TEMPORARY table.
     *
     * @param type type
     *
     * @return TableDefinition (for chaining)
     */
    @NonNull
    public TableDefinition setType(@NonNull final TableTypes type) {
        mType = type;
        return this;
    }

    /** useful for using the TableDefinition in place of a table name */
    @Override
    @NonNull
    public String toString() {
        return mName;
    }

    /**
     * Return the SQL that can be used to define this table.
     *
     * @param name            Name to use for table
     * @param withConstraints Options indicating domain constraints should be applied
     * @param ifNecessary     Options indicating if creation should not be done if table exists
     *
     * @return SQL to create table
     */
    @NonNull
    private String getSql(@NonNull final String name, final boolean withConstraints, final boolean ifNecessary) {
        StringBuilder sql = new StringBuilder("CREATE ");
        switch (mType) {
            case Standard:
                break;
            case FTS3:
            case FTS4:
                sql.append("Virtual ");
                break;
            case Temporary:
                sql.append("Temporary ");
                break;
        }

        sql.append("TABLE ");
        if (ifNecessary) {
            if (mType == TableTypes.FTS3 || mType == TableTypes.FTS4) {
                throw new IllegalStateException("'if not exists' can not be used when creating virtual tables");
            }
            sql.append("if not exists ");
        }

        sql.append(name);

        if (mType == TableTypes.FTS3) {
            sql.append(" USING fts3");
        } else if (mType == TableTypes.FTS3) {
            sql.append(" USING fts4");
        }

        sql.append(" (\n");
        boolean first = true;
        for (DomainDefinition d : mDomains) {
            if (first) {
                first = false;
            } else {
                sql.append(",\n");
            }
            sql.append("    ");
            sql.append(d.getDefinition(withConstraints));
        }
        sql.append(")\n");
        return sql.toString();
    }

    /**
     * Utility code to return a join and condition from this table to another using foreign keys.
     *
     * @param to Table this table will be joined with
     *
     * @return SQL fragment (eg. 'join [to-name] [to-alias] On [pk/fk match]')
     */
    @NonNull
    public String join(@NonNull final TableDefinition to) {
        return " JOIN " + to.ref() + " ON (" + fkMatch(to) + ")";
    }

    /**
     * Return the FK condition that applies between this table and the 'to' table
     *
     * @param to Table that is other part of FK/PK
     *
     * @return SQL fragment (eg. <to-alias>.<to-pk> = <from-alias>.<from-pk>').
     */
    @NonNull
    public String fkMatch(@NonNull final TableDefinition to) {
        FkReference fk;
        if (mChildren.containsKey(to)) {
            fk = mChildren.get(to);
        } else {
            fk = mParents.get(to);
        }
        if (fk == null) {
            throw new IllegalStateException("No foreign key between '" + this.getName() + "' AND '" + to.getName() + "'");
        }

        return fk.getPredicate();
    }

    /**
     * Accessor. Get the domains forming the PK of this table.
     *
     * @return Domain List
     */
    @NonNull
    private List<DomainDefinition> getPrimaryKey() {
        return mPrimaryKey;
    }

    /**
     * Set the primary key domains
     *
     * @param domains List of domains in PK
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    private TableDefinition setPrimaryKey(@NonNull final List<DomainDefinition> domains) {
        mPrimaryKey.clear();
        mPrimaryKey.addAll(domains);
        return this;
    }

    /**
     * Utility routine to return an SQL fragment of the form '<table-name> <table-alias>', eg. 'books b'.
     *
     * @return SQL Fragment
     */
    @NonNull
    public String ref() {
        return mName + " " + getAlias();
    }

    /**
     * Create all indices defined for this table.
     *
     * @param db Database to use
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public TableDefinition createIndices(@NonNull final DbSync.SynchronizedDb db) {
        for (IndexDefinition i : getIndexes()) {
            db.execSQL(i.getSql());
        }
        return this;
    }

    /**
     * Check if the table exists within the passed DB
     */
    public boolean exists(@NonNull final DbSync.SynchronizedDb db) {
        try (DbSync.SynchronizedStatement stmt = db.compileStatement(mExistsSql)) {
            stmt.bindString(1, getName());
            stmt.bindString(2, getName());
            return (stmt.count() > 0);
        }
    }

    public enum TableTypes {Standard, Temporary, FTS3, FTS4}

    /**
     * Class used to represent a foreign key reference
     *
     * @author Philip Warner
     */
    private class FkReference {
        /** Owner of primary key in FK reference */
        @NonNull
        final TableDefinition parent;
        /** Table owning FK */
        @NonNull
        final TableDefinition child;
        /** Domains in the FK that reference the parent PK */
        @NonNull
        final List<DomainDefinition> domains;

        /**
         * Constructor.
         *
         * @param parent  Parent table (one with PK that FK references)
         * @param child   Child table (owner of the FK)
         * @param domains Domains in child table that reference PK in parent
         */
        FkReference(@NonNull final TableDefinition parent,
                    @NonNull final TableDefinition child,
                    @NonNull final DomainDefinition... domains) {
            this.domains = new ArrayList<>(Arrays.asList(domains));
            this.parent = parent;
            this.child = child;
        }

        /**
         * Constructor
         *
         * @param parent  Parent table (one with PK that FK references)
         * @param child   Child table (owner of the FK)
         * @param domains Domains in child table that reference PK in parent
         */
        FkReference(@NonNull final TableDefinition parent,
                    @NonNull final TableDefinition child,
                    @NonNull final List<DomainDefinition> domains) {
            this.domains = new ArrayList<>(domains);
            this.parent = parent;
            this.child = child;
        }

        /**
         * Get an SQL fragment that matches the PK of the parent to the FK of the child.
         * eg. 'org.id = emp.organization_id' (but handles multi-domain keys)
         *
         * @return SQL fragment
         */
        @NonNull
        String getPredicate() {
            List<DomainDefinition> pk = parent.getPrimaryKey();
            StringBuilder sql = new StringBuilder();
            for (int i = 0; i < pk.size(); i++) {
                if (i > 0) {
                    sql.append(" AND ");
                }
                sql.append(parent.getAlias());
                sql.append(".");
                sql.append(pk.get(i).name);
                sql.append(" = ");
                sql.append(child.getAlias());
                sql.append(".");
                sql.append(domains.get(i).name);
            }
            return sql.toString();
        }
    }

}
