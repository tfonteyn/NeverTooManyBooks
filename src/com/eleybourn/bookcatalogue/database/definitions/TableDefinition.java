package com.eleybourn.bookcatalogue.database.definitions;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.database.DbSync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Class to store table name and a list of domain definitions.
 *
 * @author Philip Warner
 */
public class TableDefinition implements AutoCloseable {
    private final static String mExistsSql =
            "Select (SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?) + " +
            "(SELECT count(*) FROM sqlite_temp_master WHERE type='table' AND name=?)";
    /** List of index definitions for this table */
    private final Map<String, IndexDefinition> mIndexes = Collections.synchronizedMap(new HashMap<String, IndexDefinition>());
    /** List of domains in this table */
    private final ArrayList<DomainDefinition> mDomains;
    /** Used for checking if a domain has already been added */
    private final Set<DomainDefinition> mDomainCheck = new HashSet<>();
    /** Used for checking if a domain NAME has already been added */
    private final Map<String, DomainDefinition> mDomainNameCheck = Collections.synchronizedMap(new HashMap<String, DomainDefinition>());
    /** List of domains forming primary key */
    private final ArrayList<DomainDefinition> mPrimaryKey = new ArrayList<>();
    /** List of parent tables (tables referred to by foreign keys on this table) */
    private final Map<TableDefinition, FkReference> mParents = Collections.synchronizedMap(new HashMap<TableDefinition, FkReference>());
    /** List of child tables (tables referring to by foreign keys to this table) */
    private final Map<TableDefinition, FkReference> mChildren = Collections.synchronizedMap(new HashMap<TableDefinition, FkReference>());
    /** Table name */
    private String mName;
    /** Table alias */
    private String mAlias;
    /** Flag indicating table is temporary */
    private TableTypes mType = TableTypes.Standard;

    /**
     * Constructor
     *
     * @param name    Table name
     * @param domains List of domains in table
     */
    public TableDefinition(String name, DomainDefinition... domains) {
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
    public static void drop(DbSync.SynchronizedDb db, String name) {
        if (BuildConfig.DEBUG) {
            System.out.println("Drop Table If Exists " + name);
        }
        db.execSQL("Drop Table If Exists " + name);
    }

    /**
     * @return list of domains.
     */
    public ArrayList<DomainDefinition> getDomains() {
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
        ArrayList<TableDefinition> tmpParents = new ArrayList<>();
        for (Map.Entry<TableDefinition, FkReference> fkEntry : mParents.entrySet()) {
            FkReference fk = fkEntry.getValue();
            tmpParents.add(fk.parent);
        }
        for (TableDefinition parent : tmpParents) {
            removeReference(parent);
        }

        // Need to make local copies to avoid 'collection modified' errors
        ArrayList<TableDefinition> tmpChildren = new ArrayList<>();
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
    public Collection<IndexDefinition> getIndexes() {
        return mIndexes.values();
    }

    /**
     * Accessor. Get the table name.
     *
     * @return table name.
     */
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
    public TableDefinition setName(String newName) {
        this.mName = newName;
        return this;
    }

    /**
     * Accessor. Get the table alias, or if blank, return the table name.
     *
     * @return Alias
     */
    public String getAlias() {
        if (mAlias == null || mAlias.isEmpty())
            return getName();
        else
            return mAlias;
    }

    /**
     * Set the table alias. Useful for cloned tables.
     *
     * @param newAlias New table alias
     *
     * @return TableDefinition (for chaining)
     */
    public TableDefinition setAlias(String newAlias) {
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
    public String dot(DomainDefinition d) {
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
    public String dotAs(DomainDefinition d) {
        return getAlias() + "." + d.name + " as " + d.name;
    }

    /**
     * Utility routine to return [table-alias].[domain-name] as [asDomain]; this format
     * is useful when multiple differing versions of a domain are retrieved.
     *
     * @param d Domain
     *
     * @return SQL fragment
     */
    @SuppressWarnings("unused")
    public String dotAs(DomainDefinition d, DomainDefinition asDomain) {
        return getAlias() + "." + d.name + " as " + asDomain.name;
    }

    /**
     * Utility routine to return <table-alias>.<name>.
     *
     * @param s Domain name
     *
     * @return SQL fragment
     */
    public String dot(String s) {
        return getAlias() + "." + s;
    }

    /**
     * Set the primary key domains
     *
     * @param domains List of domains in PK
     *
     * @return TableDefinition (for chaining)
     */
    public TableDefinition setPrimaryKey(DomainDefinition... domains) {
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
    private TableDefinition addReference(FkReference fk) {
        if (fk.child != this)
            throw new RuntimeException("Foreign key does not include this table as child");
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
    public TableDefinition addReference(TableDefinition parent, DomainDefinition... domains) {
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
    private TableDefinition addReference(TableDefinition parent, ArrayList<DomainDefinition> domains) {
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
    private TableDefinition removeReference(TableDefinition parent) {
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
    @SuppressWarnings("UnusedReturnValue")
    private TableDefinition addChild(TableDefinition child, FkReference fk) {
        if (!mChildren.containsKey(child))
            mChildren.put(child, fk);
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
    private TableDefinition removeChild(TableDefinition child) {
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
    public TableDefinition addDomain(DomainDefinition domain) {
        // Make sure it's not already in the table
        if (mDomainCheck.contains(domain))
            return this;
        // Make sure one with same name is not already in table
        if (mDomainNameCheck.containsKey(domain.name.toLowerCase()))
            throw new RuntimeException("A domain with that name has already been added");
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
    public TableDefinition addDomains(DomainDefinition... domains) {
        for (DomainDefinition d : domains)
            addDomain(d);
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
    private TableDefinition addDomains(ArrayList<DomainDefinition> domains) {
        for (DomainDefinition d : domains)
            addDomain(d);
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
    public TableDefinition addIndex(String localKey, boolean unique, DomainDefinition... domains) {
        // Make sure not already defined
        if (mIndexes.containsKey(localKey))
            throw new RuntimeException("Index with local name '" + localKey + "' already defined");
        // Construct the full index name
        String name = this.mName + "_IX" + (mIndexes.size() + 1) + "_" + localKey;
        mIndexes.put(localKey, new IndexDefinition(name, unique, this, domains));
        return this;
    }

    /**
     * Drop this table from the passed DB.
     */
    @SuppressWarnings("UnusedReturnValue")
    public TableDefinition drop(DbSync.SynchronizedDb db) {
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
    public TableDefinition create(DbSync.SynchronizedDb db, boolean withConstraints) {
        if (BuildConfig.DEBUG) {
            System.out.println(this.getSql(mName, withConstraints, false));
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
    public TableDefinition createAll(DbSync.SynchronizedDb db, boolean withConstraints) {
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
    public TableDefinition createIfNecessary(DbSync.SynchronizedDb db, boolean withConstraints) {
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
    public String getInsert(DomainDefinition... domains) {
        StringBuilder s = new StringBuilder("Insert Into ");
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
     * Get a base list of fields for this table using the passed list of domains. Returns partial
     * SQL of the form: '[alias].[domain-1], ..., [alias].[domain-n]'.
     *
     * @param domains List of domains to use
     *
     * @return SQL fragment
     */
    public String ref(DomainDefinition... domains) {
        if (domains == null || domains.length == 0)
            return "";

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
    public String getUpdate(DomainDefinition... domains) {
        StringBuilder s = new StringBuilder("Update ");
        s.append(mName);
        s.append(" Set\n");

        s.append("	");
        s.append(domains[0]);
        s.append(" = ?");
        for (int i = 1; i < domains.length; i++) {
            s.append(",\n	");
            s.append(domains[i]);
            s.append(" = ?");
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
    public String getInsertOrReplaceValues(DomainDefinition... domains) {
        StringBuilder s = new StringBuilder("Insert or Replace Into ");
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
    public TableDefinition setType(TableTypes type) {
        mType = type;
        return this;
    }

    /** useful for using the TableDefinition in place of a table name */
    @Override
    public String toString() {
        return mName;
    }

    /**
     * Return the SQL that can be used to define this table.
     *
     * @param name            Name to use for table
     * @param withConstraints Flag indicating domain constraints should be applied
     * @param ifNecessary     Flag indicating if creation should not be done if table exists
     *
     * @return SQL to create table
     */
    private String getSql(String name, boolean withConstraints, boolean ifNecessary) {
        StringBuilder sql = new StringBuilder("Create ");
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

        sql.append("Table ");
        if (ifNecessary) {
            if (mType == TableTypes.FTS3 || mType == TableTypes.FTS4)
                throw new RuntimeException("'if not exists' can not be used when creating virtual tables");
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
    public String join(TableDefinition to) {
        return " join " + to.ref() + " On (" + fkMatch(to) + ")";
    }

    /**
     * Return the FK condition that applies between this table and the 'to' table
     *
     * @param to Table that is other part of FK/PK
     *
     * @return SQL fragment (eg. <to-alias>.<to-pk> = <from-alias>.<from-pk>').
     */
    public String fkMatch(TableDefinition to) {
        FkReference fk;
        if (mChildren.containsKey(to)) {
            fk = mChildren.get(to);
        } else {
            fk = mParents.get(to);
        }
        if (fk == null)
            throw new RuntimeException("No foreign key between '" + this.getName() + "' and '" + to.getName() + "'");

        return fk.getPredicate();
    }

    /**
     * Accessor. Get the domains forming the PK of this table.
     *
     * @return Domain List
     */
    private ArrayList<DomainDefinition> getPrimaryKey() {
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
    private TableDefinition setPrimaryKey(ArrayList<DomainDefinition> domains) {
        mPrimaryKey.clear();
        mPrimaryKey.addAll(domains);
        return this;
    }

    /**
     * Utility routine to return an SQL fragment of the form '<table-name> <table-alias>', eg. 'employees e'.
     *
     * @return SQL Fragment
     */
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
    public TableDefinition createIndices(DbSync.SynchronizedDb db) {
        for (IndexDefinition i : getIndexes()) {
            db.execSQL(i.getSql());
        }
        return this;
    }

    /**
     * Check if the table exists within the passed DB
     */
    public boolean exists(@NonNull final DbSync.SynchronizedDb db) {
        DbSync.SynchronizedStatement stmt = db.compileStatement(mExistsSql);
        try {
            stmt.bindString(1, getName());
            stmt.bindString(2, getName());
            // count, so no SQLiteDoneException
            return (stmt.simpleQueryForLong() > 0);
        } finally {
            stmt.close();
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
        final TableDefinition parent;
        /** Table owning FK */
        final TableDefinition child;
        /** Domains in the FK that reference the parent PK */
        final ArrayList<DomainDefinition> domains;

        /**
         * Constructor.
         *
         * @param parent  Parent table (one with PK that FK references)
         * @param child   Child table (owner of the FK)
         * @param domains Domains in child table that reference PK in parent
         */
        FkReference(TableDefinition parent, TableDefinition child, DomainDefinition... domains) {
            this.domains = new ArrayList<>();
            this.domains.addAll(Arrays.asList(domains));
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
        FkReference(TableDefinition parent, TableDefinition child, ArrayList<DomainDefinition> domains) {
            this.domains = new ArrayList<>();
            this.domains.addAll(domains);
            this.parent = parent;
            this.child = child;
        }

        /**
         * Get an SQL fragment that matches the PK of the parent to the FK of the child.
         * eg. 'org.id = emp.organization_id' (but handles multi-domain keys)
         *
         * @return SQL fragment
         */
        String getPredicate() {
            ArrayList<DomainDefinition> pk = parent.getPrimaryKey();
            StringBuilder sql = new StringBuilder();
            for (int i = 0; i < pk.size(); i++) {
                if (i > 0)
                    sql.append(" and ");
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
