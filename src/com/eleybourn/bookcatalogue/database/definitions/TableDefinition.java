package com.eleybourn.bookcatalogue.database.definitions;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.Csv;

/**
 * Class to store table name and a list of domain definitions.
 *
 * @author Philip Warner
 */
public class TableDefinition
        implements Cloneable {

    private static final String EXISTS_SQL =
            "SELECT "
                    + "(SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?) + "
                    + "(SELECT COUNT(*) FROM sqlite_temp_master WHERE type='table' AND name=?)";

    /** List of index definitions for this table. */
    private final Map<String, IndexDefinition> mIndexes =
            Collections.synchronizedMap(new HashMap<>());

    /** List of domains in this table. */
    @NonNull
    private final List<DomainDefinition> mDomains;

    /** Used for checking if a domain has already been added. */
    private final Set<DomainDefinition> mDomainCheck = new HashSet<>();

    /** Used for checking if a domain NAME has already been added. */
    private final Map<String, DomainDefinition> mDomainNameCheck =
            Collections.synchronizedMap(new HashMap<>());

    /** List of domains forming primary key. */
    private final List<DomainDefinition> mPrimaryKey = new ArrayList<>();

    /** List of parent tables (tables referred to by foreign keys on this table). */
    private final Map<TableDefinition, FkReference> mParents =
            Collections.synchronizedMap(new HashMap<>());

    /** List of child tables (tables referring to by foreign keys to this table). */
    private final Map<TableDefinition, FkReference> mChildren =
            Collections.synchronizedMap(new HashMap<>());

    /** Table name. */
    private String mName;
    /** Table alias. */
    private String mAlias;
    /** Table type. */
    @NonNull
    private TableTypes mType = TableTypes.Standard;

    /**
     * Constructor (empty table). Used for cloning.
     */
    private TableDefinition() {
        mName = "";
        mDomains = new ArrayList<>();
    }

    /**
     * Constructor.
     *
     * @param name    Table name
     * @param domains List of domains in table
     */
    public TableDefinition(@NonNull final String name,
                           @NonNull final DomainDefinition... domains) {
        mName = name;
        mAlias = name;
        // take a COPY
        mDomains = new ArrayList<>(Arrays.asList(domains));
    }

    /**
     * Given a list of table, create the database (tables + indexes).
     *
     * @param db     Blank database
     * @param tables Table list
     */
    public static void createTables(@NonNull final SynchronizedDb db,
                                    @NonNull final TableDefinition... tables) {
        for (TableDefinition table : tables) {
            table.create(db, true);
            for (IndexDefinition index : table.getIndexes()) {
                index.create(db);
            }
        }
    }

    /**
     * Drop the passed table, if it exists.
     *
     * @param db   the database
     * @param name name of the table to drop
     */
    private static void drop(@NonNull final SynchronizedDb db,
                             @NonNull final String name) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DB_ADAPTER) {
            Logger.info(TableDefinition.class, "drop","TABLE:" + name);
        }
        db.execSQL("DROP TABLE IF EXISTS " + name);
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
    public TableDefinition create(@NonNull final SynchronizedDb db,
                                  final boolean withConstraints) {
        db.execSQL(getSqlCreateStatement(mName, withConstraints, true, false));
        return this;
    }

    /**
     * Create this table.
     *
     * @param db                  Database in which to create table
     * @param withConstraints     Indicates if fields should have constraints applied
     * @param withTableReferences Indicate if table should have constraints applied
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public TableDefinition create(@NonNull final SynchronizedDb db,
                                  final boolean withConstraints,
                                  final boolean withTableReferences) {
        db.execSQL(getSqlCreateStatement(mName, withConstraints, withTableReferences, false));
        return this;
    }

    /**
     * Create this table and related objects (indices).
     *
     * @param db Database in which to create table
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public TableDefinition createAll(@NonNull final SynchronizedDb db) {
        db.execSQL(getSqlCreateStatement(mName, true, true, false));
        createIndices(db);
        return this;
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
    public TableDefinition createIndices(@NonNull final SynchronizedDb db) {
        for (IndexDefinition index : getIndexes()) {
            index.create(db);
        }
        return this;
    }

    /**
     * Drop this table from the passed DB.
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public TableDefinition drop(@NonNull final SynchronizedDb db) {
        drop(db, mName);
        return this;
    }

    /**
     * Make a copy of this table.
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @NonNull
    public TableDefinition clone() {
        TableDefinition newTbl = new TableDefinition()
                .setName(mName)
                .setAlias(mAlias)
                .addDomains(mDomains)
                .setPrimaryKey(mPrimaryKey)
                .setType(mType);

        for (Map.Entry<TableDefinition, FkReference> fkEntry : mParents.entrySet()) {
            FkReference fk = fkEntry.getValue();
            newTbl.addReference(fk.mParent, fk.mDomains);
        }
        for (Map.Entry<TableDefinition, FkReference> fkEntry : mChildren.entrySet()) {
            FkReference fk = fkEntry.getValue();
            fk.mChild.addReference(newTbl, fk.mDomains);
        }
        for (Map.Entry<String, IndexDefinition> e : mIndexes.entrySet()) {
            IndexDefinition index = e.getValue();
            newTbl.addIndex(e.getKey(), index.getUnique(), index.getDomains());
        }
        return newTbl;
    }

    /**
     * Remove all references and resources used by this table.
     */
    public void clear() {
        mDomains.clear();
        mDomainCheck.clear();
        mDomainNameCheck.clear();
        mPrimaryKey.clear();
        mIndexes.clear();

        // Need to make local copies to avoid 'collection modified' errors
        List<TableDefinition> tmpParents = new ArrayList<>();
        for (Map.Entry<TableDefinition, FkReference> fkEntry : mParents.entrySet()) {
            FkReference fk = fkEntry.getValue();
            tmpParents.add(fk.mParent);
        }
        for (TableDefinition parent : tmpParents) {
            removeReference(parent);
        }

        // Need to make local copies to avoid 'collection modified' errors
        List<TableDefinition> tmpChildren = new ArrayList<>();
        for (Map.Entry<TableDefinition, FkReference> fkEntry : mChildren.entrySet()) {
            FkReference fk = fkEntry.getValue();
            tmpChildren.add(fk.mChild);
        }
        for (TableDefinition child : tmpChildren) {
            child.removeReference(this);
        }
    }

    /**
     * Set the type of the table.
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

    /**
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
        mName = newName;
        return this;
    }

    /**
     * toString() NOT DEBUG
     * <p>
     * useful for using the TableDefinition in place of a table name.
     */
    @Override
    @NonNull
    public String toString() {
        return mName;
    }

    /**
     * @return the table alias, or if blank, return the table name.
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
     * Add a domain to this table.
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
            throw new IllegalArgumentException("A domain '" + domain + "' has already been added");
        }
        // Add it
        mDomains.add(domain);
        mDomainCheck.add(domain);
        mDomainNameCheck.put(domain.name, domain);
        return this;
    }

    /**
     * @return list of domains.
     */
    @NonNull
    public List<DomainDefinition> getDomains() {
        return mDomains;
    }

    /**
     * Set the primary key domains.
     * <p>
     * Do not use this on a table which already has a standard column PK _id
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
     * Get the domains forming the PK of this table.
     *
     * @return Domain List
     */
    @NonNull
    private List<DomainDefinition> getPrimaryKey() {
        return mPrimaryKey;
    }

    /**
     * Set the primary key domains.
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
     * Common code to add a foreign key (FK) references to another (parent) table.
     *
     * @param fk The FK object
     *
     * @return TableDefinition (for chaining)
     */
    @NonNull
    private TableDefinition addReference(@NonNull final FkReference fk) {
        if (fk.mChild != this) {
            throw new IllegalArgumentException("Foreign key does not include this table as child");
        }
        mParents.put(fk.mParent, fk);
        fk.mParent.addChildReference(this, fk);
        return this;
    }

    /**
     * Add a foreign key (FK) references to another (parent) table.
     *
     * @param parent  The referenced table (with the PK)
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
     * @param parent  The referenced table (with the PK)
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
     * Remove FK reference to parent table.
     *
     * @param parent The referenced Table
     *
     * @return TableDefinition (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    private TableDefinition removeReference(@NonNull final TableDefinition parent) {
        mParents.remove(parent);
        parent.removeChildReference(this);
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
    private TableDefinition addChildReference(@NonNull final TableDefinition child,
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
    private TableDefinition removeChildReference(@NonNull final TableDefinition child) {
        mChildren.remove(child);
        return this;
    }

    /**
     * Add an index to this table.
     *
     * @param localName unique name, local for this table, to give this index. Alphanumeric Only.
     *                  The full name will become: tableName_IXi_localName
     * @param unique    FLag indicating index is UNIQUE
     * @param domains   List of domains index
     *
     * @return TableDefinition (for chaining)
     */
    @NonNull
    public TableDefinition addIndex(@NonNull final String localName,
                                    final boolean unique,
                                    @NonNull final DomainDefinition... domains) {
        return addIndex(localName, unique, Arrays.asList(domains));
    }

    /**
     * Add an index to this table.
     *
     * @param domain  domain to name the index.
     *                The full name will become: tableName_IXi_{domain.name}
     * @param unique  FLag indicating index is UNIQUE
     * @param domains List of domains index
     *
     * @return TableDefinition (for chaining)
     */
    @NonNull
    public TableDefinition addIndex(@NonNull final DomainDefinition domain,
                                    final boolean unique,
                                    @NonNull final DomainDefinition... domains) {
        return addIndex(domain.name, unique, Arrays.asList(domains));
    }

    /**
     * Add an index to this table.
     *
     * @param localName unique name, local for this table, to give this index. Alphanumeric Only.
     *                  The full name will become: tableName_IXi_localName
     * @param unique    FLag indicating index is UNIQUE
     * @param domains   List of domains index
     *
     * @return TableDefinition (for chaining)
     */
    @NonNull
    private TableDefinition addIndex(@NonNull final String localName,
                                     final boolean unique,
                                     @NonNull final List<DomainDefinition> domains) {
        // Make sure not already defined
        if (mIndexes.containsKey(localName)) {
            throw new IllegalStateException(
                    "Index with local name '" + localName + "' already defined");
        }
        // Construct the full index name
        String name = mName + "_IX" + (mIndexes.size() + 1) + '_' + localName;
        mIndexes.put(localName, new IndexDefinition(name, unique, this, domains));
        return this;
    }

    /**
     * @return indexes on this table.
     */
    @NonNull
    private Collection<IndexDefinition> getIndexes() {
        return mIndexes.values();
    }


    /**
     * Return an SQL fragment.
     * <p>
     * format: [table-alias].[domain-name]
     *
     * @param domain Domain
     *
     * @return SQL fragment
     */
    @NonNull
    public String dot(@NonNull final DomainDefinition domain) {
        return getAlias() + '.' + domain.name;
    }

    /**
     * Return an SQL fragment.
     * <p>
     * format: [table-alias].[name]
     *
     * @param name Domain name
     *
     * @return SQL fragment
     */
    @NonNull
    public String dot(@NonNull final String name) {
        return getAlias() + '.' + name;
    }

    /**
     * Return an SQL fragment.
     * <p>
     * format: [table-alias].[domain-name] AS [domain_name]
     * <p>
     * This is useful in older SQLite installations that add make the alias part of the output
     * column name.
     *
     * @param domain Domain
     *
     * @return SQL fragment
     */
    @NonNull
    public String dotAs(@NonNull final DomainDefinition domain) {
        return getAlias() + '.' + domain.name + " AS " + domain.name;
    }

    /**
     * Return an SQL fragment.
     * <p>
     * format: [table-alias].[domain-name] AS [asDomain]
     * <p>
     * This is useful when multiple differing versions of a domain are retrieved.
     * (i.e. same domain name, different table)
     *
     * @param domain   Domain
     * @param asDomain the alias to override the table alias
     *
     * @return SQL fragment
     */
    @NonNull
    public String dotAs(@NonNull final DomainDefinition domain,
                        @NonNull final DomainDefinition asDomain) {
        return getAlias() + '.' + domain.name + " AS " + asDomain.name;
    }

    /**
     * Return a base list of ALL registered domain fields for this table.
     * <p>
     * format: [alias].[domain-1], ..., [alias].[domain-n]
     * <p>
     * Keep in mind that not all tables are fully registered in {@link DBDefinitions}
     * Add domains there when needed. Eventually the lists will get complete.
     *
     * @param withAS set to <tt>true</tt> if you need 'AS domain-x' added.
     *
     * @return SQL fragment
     *
     * @see #dot
     */
    @NonNull
    public String csvColumns(final boolean withAS) {
        return Csv.join(",", mDomains, element -> {
            if (withAS) {
                return dotAs(element);
            } else {
                return dot(element);
            }
        });
    }

    /**
     * Return a base list of fields for this table using the passed list of domains.
     * <p>
     * format: [alias].[domain-1] AS [domain-1], ..., [alias].[domain-n] AS [domain-n]
     *
     * @param withAS  set to <tt>true</tt> if you need 'AS domain-x' added.
     * @param domains List of domains to use
     *
     * @return SQL fragment
     *
     * @see #dotAs
     */
    @NonNull
    public String csvColumns(final boolean withAS,
                             @NonNull final DomainDefinition... domains) {
        return Csv.join(",", Arrays.asList(domains), element -> {
            if (withAS) {
                return dotAs(element);
            } else {
                return dot(element);
            }
        });
    }


    /**
     * Return a base INSERT statement for this table using the passed list of domains.
     * <p>
     * format: INSERT into [table-name] ( [domain-list] )
     * or
     * format: INSERT into [table-name] ( [domain-list] ) VALUES(?,...?)
     *
     * @param withValues set to <tt>true</tt> to add the VALUES part
     * @param domains    List of domains to use
     *
     * @return SQL fragment
     */
    @NonNull
    public String getInsert(final boolean withValues,
                            @NonNull final DomainDefinition... domains) {
        StringBuilder s = new StringBuilder("INSERT INTO ")
                .append(mName)
                .append(" (")
                .append(Csv.join(",", Arrays.asList(domains)))
                .append(')');

        if (withValues) {
            s.append(" VALUES (")
             .append(Csv.join(",", domains.length, "?"))
             .append(')');
        }
        return s.toString();
    }

    /**
     * Return a base UPDATE statement for this table using the passed list of domains.
     * <p>
     * format: UPDATE [table-name] SET [domain-1]=?, ..., [domain-n]=?
     *
     * @param domains List of domains to use
     *
     * @return SQL fragment
     */
    @NonNull
    public String getUpdate(@NonNull final DomainDefinition... domains) {
        return "UPDATE " + mName + " SET " +
                Csv.join(",", Arrays.asList(domains), element -> element + "=?");
    }


    /**
     * Return the SQL that can be used to define this table.
     *
     * @param name                Name to use for table
     * @param withConstraints     Flag indicating DOMAIN constraints should be applied.
     * @param withTableReferences Flag indicating TABLE constraints (foreign keys) should be applied.
     * @param ifNotExists         Flag indicating that creation should not be done if the table exists
     *
     * @return SQL to create table
     */
    @NonNull
    private String getSqlCreateStatement(@NonNull final String name,
                                         final boolean withConstraints,
                                         final boolean withTableReferences,
                                         @SuppressWarnings("SameParameterValue") final boolean ifNotExists) {
        StringBuilder sql = new StringBuilder("CREATE")
                .append(mType.getCreateModifier())
                .append(" TABLE");
        if (ifNotExists) {
            if (mType.isVirtual()) {
                throw new IllegalStateException(
                        "'if not exists' can not be used when creating virtual tables");
            }
            sql.append(" if not exists");
        }

        sql.append(' ')
           .append(name)
           .append(mType.getUsingModifier())
           .append("\n(");

        // add the columns
        boolean hasPrimaryKey = false;
        boolean first = true;
        for (DomainDefinition domain : mDomains) {
            if (first) {
                first = false;
            } else {
                sql.append(',');
            }
            sql.append(domain.def(withConstraints));
            // remember if we added a primary key column.
            hasPrimaryKey = hasPrimaryKey || domain.isPrimaryKey();
        }

        // add the primary key if not already added / needed.
        if (!hasPrimaryKey && !mPrimaryKey.isEmpty()) {
            sql.append("\n,PRIMARY KEY (")
               .append(Csv.join(",", mPrimaryKey))
               .append(')');
        }

        // add foreign key TABLE constraints if allowed/needed.
        if (withTableReferences && !mParents.isEmpty()) {
            sql.append("\n,")
               .append(Csv.join("\n,", mParents.values(), FkReference::def));
        }

        // end of column/constraint list
        sql.append(')');

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SQL_CREATE_TABLE) {
            Logger.info(this, Tracker.State.Exit, "getSqlCreateStatement",
                        sql.toString());
        }
        return sql.toString();
    }

    /**
     * Return a join and condition from this table to another using foreign keys.
     * <p>
     * format: JOIN [to-name] [to-alias] ON [pk/fk match]
     *
     * @param to Table this table will be joined with
     *
     * @return SQL fragment
     */
    @NonNull
    public String join(@NonNull final TableDefinition to) {
        return " JOIN " + to.ref() + " ON (" + fkMatch(to) + ')';
    }

    /**
     * Return the FK condition that applies between this table and the 'to' table.
     * <p>
     * format: [to-alias].[to-pk] = [from-alias].[from-pk]
     *
     * @param to Table that is other part of FK/PK
     *
     * @return SQL fragment
     */
    @NonNull
    public String fkMatch(@NonNull final TableDefinition to) {
        FkReference fk;
        if (mChildren.containsKey(to)) {
            fk = mChildren.get(to);
        } else {
            fk = mParents.get(to);
        }
        Objects.requireNonNull(fk, "No foreign key between `" + getName()
                + "` and `" + to.getName() + '`');

        return fk.getPredicate();
    }

    /**
     * Return an aliased table name.
     * <p>
     * format: [table-name] [table-alias]
     * <p>
     * eg. 'books b'.
     *
     * @return SQL Fragment
     */
    @NonNull
    public String ref() {
        return mName + ' ' + getAlias();
    }

    /**
     * @return <tt>true</tt> if the table exists
     */
    public boolean exists(@NonNull final SynchronizedDb db) {
        try (SynchronizedStatement stmt = db.compileStatement(EXISTS_SQL)) {
            stmt.bindString(1, getName());
            stmt.bindString(2, getName());
            return (stmt.count() > 0);
        }
    }

    /**
     * Supported/used table types.
     * <p>
     * https://sqlite.org/fts3.html
     */
    public enum TableTypes {
        Standard, Temporary, FTS3, FTS4;

        boolean isVirtual() {
            return this == FTS3 || this == FTS4;
        }

        String getCreateModifier() {
            switch (this) {
                case FTS3:
                case FTS4:
                    return " Virtual";
                case Temporary:
                    return " Temporary";
                default:
                    return "";
            }
        }

        String getUsingModifier() {
            switch (this) {
                case FTS3:
                    return " USING fts3";
                case FTS4:
                    return " USING fts4";
                default:
                    return "";
            }
        }
    }

    /**
     * Class used to represent a foreign key reference.
     */
    private static class FkReference {

        /** Owner of primary key that the FK references. */
        @NonNull
        private final TableDefinition mParent;
        /** Table owning FK. */
        @NonNull
        private final TableDefinition mChild;
        /** Domains in the FK that reference the parent PK. */
        @NonNull
        private final List<DomainDefinition> mDomains;

        /**
         * Constructor.
         *
         * @param parent  Parent table (one with PK that the FK references)
         * @param child   Child table (owner of the FK)
         * @param domains Domains in child table that reference PK in parent
         */
        FkReference(@NonNull final TableDefinition parent,
                    @NonNull final TableDefinition child,
                    @NonNull final DomainDefinition... domains) {
            // take a COPY
            mDomains = new ArrayList<>(Arrays.asList(domains));
            mParent = parent;
            mChild = child;
        }

        /**
         * Constructor.
         *
         * @param parent  Parent table (one with PK that FK references)
         * @param child   Child table (owner of the FK)
         * @param domains Domains in child table that reference PK in parent
         */
        FkReference(@NonNull final TableDefinition parent,
                    @NonNull final TableDefinition child,
                    @NonNull final List<DomainDefinition> domains) {
            // take a COPY
            mDomains = new ArrayList<>(domains);
            mParent = parent;
            mChild = child;
        }

        /**
         * Get an SQL fragment that matches the PK of the parent to the FK of the child.
         * <p>
         * format: [parent alias].[column] = [child alias].[column]
         *
         * @return SQL fragment
         */
        @NonNull
        String getPredicate() {
            List<DomainDefinition> pk = mParent.getPrimaryKey();
            if (BuildConfig.DEBUG /* always debug */) {
                if (pk.isEmpty()) {
                    throw new IllegalStateException("no primary key on table: " + mParent);
                }
            }
            StringBuilder sql = new StringBuilder();
            for (int i = 0; i < pk.size(); i++) {
                if (i > 0) {
                    sql.append(" AND ");
                }
                sql.append(mParent.getAlias());
                sql.append('.');
                sql.append(pk.get(i).name);
                sql.append('=');
                sql.append(mChild.getAlias());
                sql.append('.');
                sql.append(mDomains.get(i).name);
            }
            return sql.toString();
        }

        /**
         * Construct the definition string. Format:
         * "FOREIGN KEY ([domain-list]) REFERENCES [pkTable] ([pk-list])"
         *
         * @return the definition
         */
        @NonNull
        String def() {
            return "FOREIGN KEY (" + Csv.join(",", mDomains) +
                    ") REFERENCES " + mParent +
                    '(' + Csv.join(",", mParent.getPrimaryKey()) + ')';
        }

        @Override
        @NonNull
        public String toString() {
            return "FkReference{"
                    + "mParent=" + mParent
                    + ", mChild=" + mChild
                    + ", mDomains=" + mDomains
                    + '}';
        }
    }
}
