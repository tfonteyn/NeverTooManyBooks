/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Class to store table name and a list of domain definitions.
 */
@SuppressWarnings("FieldNotUsedInToString")
public class TableDefinition {

    private static final String _AS_ = " AS ";

    private static final String TABLE_EXISTS_SQL_STANDARD =
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?";
    private static final String TABLE_EXISTS_SQL_TEMP =
            "SELECT COUNT(*) FROM sqlite_temp_master WHERE type='table' AND name=?";

    /** List of index definitions for this table. */
    private final Collection<IndexDefinition> mIndexes = new ArrayList<>();
    /** Used for checking if an index has already been added. */
    private final Collection<String> mIndexNameCheck = new HashSet<>();

    /** List of domains in this table. */
    private final List<Domain> mDomains = new ArrayList<>();
    /** Used for checking if a domain has already been added. */
    private final Collection<Domain> mDomainCheck = new HashSet<>();
    /** Used for checking if a domain NAME has already been added. */
    private final Collection<Integer> mDomainNameCheck = new HashSet<>();

    /** List of domains forming primary key. */
    private final List<Domain> mPrimaryKey = new ArrayList<>();

    /** List of parent tables (tables referred to by foreign keys on this table). */
    private final Map<TableDefinition, FkReference> mParents =
            Collections.synchronizedMap(new HashMap<>());

    /** List of child tables (tables referring to by foreign keys to this table). */
    private final Map<TableDefinition, FkReference> mChildren =
            Collections.synchronizedMap(new HashMap<>());

    /** Table name. */
    @NonNull
    private String mName;
    /** Table alias. */
    @Nullable
    private String mAlias;
    /** Table type. */
    @NonNull
    private TableType mType = TableType.Standard;
    /** Cached table structure info. */
    @Nullable
    private TableInfo mTableInfo;

    /**
     * Constructor.
     *
     * @param name Table name; also used as alias unless the latter is overridden.
     */
    public TableDefinition(@NonNull final String name) {
        mName = name;
        mAlias = name;
    }

//    /** NOT IN USE - needs more work/testing on the references.
//     * Copy constructor.
//     *
//     * @param from object to copy
//     * @param name the NEW name to use
//     */
//    public TableDefinition(@NonNull final TableDefinition from,
//                           @NonNull final String name) {
//
//        boolean withRef = false;
//
//        mName = name;
//        mAlias = from.mAlias;
//        mType = from.mType;
//
//        // the domains are copied as is.
//        for (final Domain d : from.mDomains) {
//            // use the method, so mDomainNameCheck/mDomainCheck get populated properly
//            addDomain(d);
//        }
//        // the primary key is copied as is.
//        mPrimaryKey.addAll(from.mPrimaryKey);
//
//
//        if (withRef) {
//            // copy the foreign keys the 'from' table has to other 3rd-party tables.
//            // i.e. hook THIS table up with the same 3rd-party tables
//            for (final FkReference fromFK : from.mParents.values()) {
//                addReference(fromFK.mParent, fromFK.mDomains);
//            }
//
//            // tell the 3rd-party tables to stop referencing the 'from' table
//            // and that they should reference THIS table now.
//            for (final FkReference childFK : from.mChildren.values()) {
//                // unhook the foreign reference from the CHILDREN of the definition we're copying
//                childFK.mChild.removeReference(from);
//                // and create a new reference between the CHILDREN of the definition we're copying
//                // and THIS object.
//                childFK.mChild.addReference(this, childFK.mDomains);
//            }
//        }
//
//        // the indexes are copied as is.
//        for (final IndexDefinition fromIndex : from.mIndexes) {
//            // use the method, so mIndexNameCheck get populated properly
//            addIndex(fromIndex.getNameSuffix(), fromIndex.getUnique(), fromIndex.getDomains());
//        }
//    }

    /**
     * Given a list of tables, create the database (tables + indexes).
     * Constraints and references will be active.
     * <p>
     * This method is only called during
     * {@link android.database.sqlite.SQLiteOpenHelper#onCreate(SQLiteDatabase)}
     *
     * @param db     SQLiteDatabase
     * @param tables Table list
     */
    public static void onCreate(@NonNull final SQLiteDatabase db,
                                @NonNull final TableDefinition... tables) {
        for (final TableDefinition table : tables) {
            table.create(db, true);
            table.createIndices(db);
        }
    }

    public static void onCreate(@NonNull final SQLiteDatabase db,
                                @NonNull final Collection<TableDefinition> tables) {
        for (final TableDefinition table : tables) {
            table.create(db, true);
            table.createIndices(db);
        }
    }

    /**
     * Create this table. Don't forget to call {@link #createIndices(SQLiteDatabase)} if needed.
     *
     * @param db                    Database Access
     * @param withDomainConstraints Indicates if fields should have constraints applied
     */
    public void create(@NonNull final SQLiteDatabase db,
                       final boolean withDomainConstraints) {
        db.execSQL(def(mName, withDomainConstraints));
    }

    /**
     * Create all registered indexes for this table.
     *
     * @param db Database Access
     */
    public void createIndices(@NonNull final SQLiteDatabase db) {
        for (final IndexDefinition index : mIndexes) {
            index.create(db);
        }
    }

    /**
     * Remove all references and resources used by this table.
     */
    public void clear() {
        mDomains.clear();
        mDomainCheck.clear();
        mDomainNameCheck.clear();
        mIndexes.clear();
        mIndexNameCheck.clear();
        mPrimaryKey.clear();

        // Need to make local copies to avoid 'collection modified' errors
        final Collection<TableDefinition> tmpParents = new ArrayList<>();
        for (final FkReference fk : mParents.values()) {
            tmpParents.add(fk.mParent);
        }
        for (final TableDefinition parent : tmpParents) {
            removeReference(parent);
        }

        // Need to make local copies to avoid 'collection modified' errors
        final Collection<TableDefinition> tmpChildren = new ArrayList<>();
        for (final FkReference fk : mChildren.values()) {
            tmpChildren.add(fk.mChild);
        }
        for (final TableDefinition child : tmpChildren) {
            child.removeReference(this);
        }
    }

    /**
     * Set the type of the table.
     *
     * @param type type
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public TableDefinition setType(@NonNull final TableType type) {
        mType = type;
        return this;
    }

    /**
     * Get the table name.
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
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public TableDefinition setName(@NonNull final String newName) {
        mName = newName;
        return this;
    }

    /**
     * toString() <strong>NOT DEBUG, must only ever return the table name</strong>
     * <p>
     * useful for using the TableDefinition in place of a table name.
     *
     * @return the name of the table.
     */
    @Override
    @NonNull
    public String toString() {
        return mName;
    }


    @SuppressWarnings({"WeakerAccess", "unused"})
    @NonNull
    public String toDebugString() {
        return "TableDefinition{"
               + "mName='" + mName + '\''
               + ", mAlias='" + mAlias + '\''
               + ", mType=" + mType
               + ", mDomains=" + mDomains
               + ", mPrimaryKey=" + mPrimaryKey
               + "\nmParents=" + mParents
               + "\nmChildren=" + mChildren
               + "\nmIndexes=" + mIndexes
               + "\nmIndexNameCheck=" + mIndexNameCheck
               + "\nmDomainCheck=" + mDomainCheck
               + "\nmDomainNameCheck=" + mDomainNameCheck
               + "\nmTableInfo=" + mTableInfo
               + '}';
    }

    /**
     * Get the alias name.
     *
     * @return the table alias, or if blank, return the table name.
     */
    @NonNull
    public String getAlias() {
        if (mAlias == null || mAlias.isEmpty()) {
            return mName;
        } else {
            return mAlias;
        }
    }

    /**
     * Set the table alias. Useful for cloned tables.
     *
     * @param newAlias New table alias
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public TableDefinition setAlias(@Nullable final String newAlias) {
        mAlias = newAlias;
        return this;
    }

    /**
     * Add a list of domains to this table.
     *
     * @param domains List of domains to add
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public TableDefinition addDomains(@NonNull final Domain... domains) {
        for (final Domain d : domains) {
            addDomain(d);
        }
        return this;
    }

    /**
     * Add a domain to this table. Domains already present are silently ignored.
     *
     * @param domain Domain object to add
     *
     * @return {@code true} if the domain was added, {@code false} if it was already present.
     */
    public boolean addDomain(@NonNull final Domain domain) {
        // Make sure it's not already in the table, silently ignore if it is.
        if (mDomainCheck.contains(domain)) {
            return false;
        }

        // avoid toLowerCase
        final int nameHash = domain.getName().hashCode();
        // Make sure one with the same name is not already in table, can't ignore that, go crash.
        if (mDomainNameCheck.contains(nameHash)) {
            throw new IllegalArgumentException("Duplicate domain=" + domain);
        }
        // Add it
        mDomains.add(domain);

        mDomainCheck.add(domain);
        mDomainNameCheck.add(nameHash);
        return true;
    }

    /**
     * Get the complete list of domains used by this table.
     *
     * @return list of domains.
     */
    @NonNull
    public List<Domain> getDomains() {
        return mDomains;
    }

    /**
     * Get the named domain.
     *
     * @param key name of domain to get
     *
     * @return the domain, or {@code null} if not found
     */
    @Nullable
    public Domain getDomain(@NonNull final String key) {
        return mDomains.stream()
                       .filter(domain -> domain.getName().equals(key))
                       .findFirst()
                       .orElse(null);
    }

    /**
     * Get the domains forming the PK of this table.
     *
     * @return Domain List
     */
    @NonNull
    private List<Domain> getPrimaryKey() {
        return mPrimaryKey;
    }

    /**
     * Set the primary key domains.
     *
     * @param domains List of domains in PK
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public TableDefinition setPrimaryKey(@NonNull final Domain... domains) {
        mPrimaryKey.clear();
        Collections.addAll(mPrimaryKey, domains);
        return this;
    }

    /**
     * Common code to add a foreign key (FK) references to another (parent) table.
     *
     * @param fk The FK object
     *
     * @return {@code this} (for chaining)
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
     * @return {@code this} (for chaining)
     */
    @NonNull
    public TableDefinition addReference(@NonNull final TableDefinition parent,
                                        @NonNull final Domain... domains) {
        final FkReference fk = new FkReference(parent, this, domains);
        return addReference(fk);
    }

    /**
     * Add a foreign key (FK) references to another (parent) table.
     *
     * @param parent  The referenced table (with the PK)
     * @param domains Domains in this table that reference Primary Key (PK) in parent
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings({"UnusedReturnValue", "unused"})
    @NonNull
    private TableDefinition addReference(@NonNull final TableDefinition parent,
                                         @NonNull final List<Domain> domains) {
        final FkReference fk = new FkReference(parent, this, domains);
        return addReference(fk);
    }

    /**
     * Add a foreign key (FK) references to another (parent) table.
     *
     * @param parent    The referenced table
     * @param parentKey single Domain key in the parent table
     * @param domain    single Domain in child table that references parentKey in parent
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("unused")
    @NonNull
    public TableDefinition addReference(@NonNull final TableDefinition parent,
                                        @NonNull final Domain parentKey,
                                        @NonNull final Domain domain) {
        final FkReference fk = new FkReference(parent, parentKey, this, domain);
        return addReference(fk);
    }

    /**
     * Remove FK reference to parent table.
     *
     * @param parent The referenced Table
     *
     * @return {@code this} (for chaining)
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
     * @return {@code this} (for chaining)
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
     * @return {@code this} (for chaining)
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
     * @param nameSuffix unique (for this table) suffix to add to the name of this index.
     *                   Alphanumeric Only.
     * @param unique     FLag indicating index is UNIQUE
     * @param domains    List of domains index
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public TableDefinition addIndex(@NonNull final String nameSuffix,
                                    final boolean unique,
                                    @NonNull final Domain... domains) {
        return addIndex(nameSuffix, unique, Arrays.asList(domains));
    }

    /**
     * Add an index to this table.
     *
     * @param nameSuffix unique (for this table) suffix to add to the name of this index.
     *                   Alphanumeric Only.
     * @param unique     FLag indicating index is UNIQUE
     * @param domains    List of domains index
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    private TableDefinition addIndex(@NonNull final String nameSuffix,
                                     final boolean unique,
                                     @NonNull final List<Domain> domains) {
        // Make sure not already defined
        if (mIndexNameCheck.contains(nameSuffix)) {
            throw new IllegalStateException("Index suffix '" + nameSuffix + "' already defined");
        }
        mIndexes.add(new IndexDefinition(this, nameSuffix + "_" + (mIndexes.size() + 1),
                                         unique, domains));
        mIndexNameCheck.add(nameSuffix);
        return this;
    }

    /**
     * Return an SQL fragment. Use this for columns in the where, join, order, etc... clause.
     * <p>
     * format: [table-alias].[domain-name]
     *
     * @param domain Domain name
     *
     * @return SQL fragment
     */
    @NonNull
    public String dot(@NonNull final String domain) {
        return getAlias() + '.' + domain;
    }

    @NonNull
    public String dot(@NonNull final Domain domain) {
        return getAlias() + '.' + domain.getName();
    }

    /**
     * Return an SQL fragment. Use this for columns in the select-clause.
     * <p>
     * format: [table-alias].[domain-name] AS [domain_name] [,...]
     * <p>
     * Some SQLite versions make the alias part of the output column name which
     * breaks the easy fetching by pure column name.
     *
     * @param domainKeys list of domain names
     *
     * @return SQL fragment
     */
    @NonNull
    public String dotAs(@NonNull final String... domainKeys) {
        return Arrays.stream(domainKeys)
                     .map(key -> getAlias() + '.' + key + _AS_ + key)
                     .collect(Collectors.joining(","));
    }

    @NonNull
    public String dotAs(@NonNull final List<Domain> domains) {
        return domains.stream()
                      .map(Domain::getName)
                      .map(key -> getAlias() + '.' + key + _AS_ + key)
                      .collect(Collectors.joining(","));
    }

    /**
     * Return an aliased table name.
     * <p>
     * format: [table-name] AS [table-alias]
     * <p>
     * e.g. 'books AS b'.
     *
     * @return SQL Fragment
     */
    @NonNull
    public String ref() {
        return mName + _AS_ + getAlias();
    }

    /**
     * Staring with the current table, join with the given list of tables one by one.
     *
     * @param tables to join
     *
     * @return SQL fragment
     */
    public String startJoin(@NonNull final TableDefinition... tables) {
        // optimization
        if (tables.length == 1) {
            return ref() + join(tables[0]);
        }

        final ArrayList<TableDefinition> list = new ArrayList<>(Arrays.asList(tables));
        list.add(0, this);

        final StringBuilder sb = new StringBuilder(ref());
        for (int i = 0; i < list.size() - 1; i++) {
            sb.append(list.get(i).join(list.get(i + 1)));
        }
        return sb.toString();
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
     * Return a left outer join and condition from this table to another using foreign keys.
     * <p>
     * format: LEFT OUTER JOIN [to-name] [to-alias] ON [pk/fk match]
     *
     * @param to Table this table will be joined with
     *
     * @return SQL fragment
     */
    @NonNull
    public String leftOuterJoin(@NonNull final TableDefinition to) {
        return " LEFT OUTER JOIN " + to.ref() + " ON (" + fkMatch(to) + ')';
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
        final FkReference fk;
        if (mChildren.containsKey(to)) {
            fk = mChildren.get(to);
        } else {
            fk = mParents.get(to);
        }

        // note the use of a Supplier
        Objects.requireNonNull(fk, () ->
                "No foreign key between `" + mName + "` and `" + to.getName() + '`');

        return fk.getPredicate();
    }

    /**
     * Check if this table exists.
     *
     * @param db Database Access
     *
     * @return {@code true} if this table exists
     */
    public boolean exists(@NonNull final SQLiteDatabase db) {
        final String sql;
        if (mType == TableType.Standard) {
            sql = TABLE_EXISTS_SQL_STANDARD;
        } else {
            sql = TABLE_EXISTS_SQL_TEMP;
        }
        try (SQLiteStatement stmt = db.compileStatement(sql)) {
            stmt.bindString(1, mName);
            return stmt.simpleQueryForLong() > 0;
        } catch (@NonNull final SQLiteDoneException ignore) {
            return false;
        }
    }

    /**
     * Get a description/info structure for this table describing the columns etc.
     *
     * @param db Database Access
     *
     * @return info object
     */
    @NonNull
    public TableInfo getTableInfo(@NonNull final SQLiteDatabase db) {
        synchronized (this) {
            if (mTableInfo == null) {
                mTableInfo = new TableInfo(db, mName);
            }
        }
        return mTableInfo;
    }

    /**
     * Alter the physical table in the database: add the given domains.
     *
     * @param db      Database Access
     * @param domains to add
     */
    public void alterTableAddColumns(@NonNull final SQLiteDatabase db,
                                     @NonNull final Domain... domains) {
        for (final Domain domain : domains) {
            db.execSQL("ALTER TABLE " + getName() + " ADD " + domain.def(true));
        }
    }

//    /**
//     * The use of recreateAndReload is dangerous right now and can break updates.
//     *
//     * More specifically: the recreateAndReload routine can only be used ONCE per table.
//     * We'll need to keep previous table definitions as BC used to do.
//     * <p>
//     * Alter the physical table in the database.
//     * Takes care of newly added (based on TableDefinition),
//     * removes obsolete, and renames columns. The latter based on a list/map passed in.
//     *
//     * <strong>DOES NOT CREATE INDEXES - those MUST be recreated afterwards by the caller</strong>
//     *
//     * <a href="https://www.sqlite.org/lang_altertable.html#making_other_kinds_of_table_schema_changes">
//     * SQLite - making_other_kinds_of_table_schema_changes</a>
//     * <p>
//     * The 12 steps in summary:
//     * <ol>
//     *  <li>If foreign key constraints are enabled,
//     *      disable them using PRAGMA foreign_keys=OFF.</li>
//     *  <li>Create new table</li>
//     *  <li>Copy data</li>
//     *  <li>Drop old table</li>
//     *  <li>Rename new into old</li>
//     *  <li>If foreign keys constraints were originally enabled, re-enable them now.</li>
//     * </ol>
//     *
//     * @param db       Database Access
//     * @param toRename (optional) Map of fields to be renamed
//     * @param toRemove (optional) List of fields to be removed
//     */
//    public void recreateAndReload(@NonNull final SQLiteDatabase db,
//                                  @SuppressWarnings("SameParameterValue")
//                                  @Nullable final Map<String, String> toRename,
//                                  @Nullable final Collection<String> toRemove) {
//
//        final String dstTableName = "copyOf" + mName;
//        // With constraints... sqlite does not allow to add constraints later.
//        // Without indexes.
//        db.execSQL(def(dstTableName, true));
//
//        // This handles re-ordered fields etc.
//        copyTableSafely(db, dstTableName, toRemove, toRename);
//
//        db.execSQL("DROP TABLE " + mName);
//        db.execSQL("ALTER TABLE " + dstTableName + " RENAME TO " + mName);
//    }

    /**
     * Provide a safe table copy method that is insulated from risks associated with
     * column order. This method will copy all columns from the source to the destination;
     * if columns do not exist in the destination, an error will occur. Columns in the
     * destination that are not in the source will be defaulted or set to {@code null}
     * if no default is defined.
     *
     * <ul>
     *     <li>toRemove: columns already gone are ignored</li>
     *     <li>toRename: columns already renamed are ignored</li>
     * </ul>
     *
     * @param db          Database Access
     * @param destination to table
     * @param toRemove    (optional) List of fields to be removed
     * @param toRename    (optional) Map of fields to be renamed
     */
    private void copyTableSafely(@NonNull final SQLiteDatabase db,
                                 @SuppressWarnings("SameParameterValue")
                                 @NonNull final String destination,
                                 @Nullable final Collection<String> toRemove,
                                 @Nullable final Map<String, String> toRename) {

        // Note: don't use the mDomains to check for columns no longer there,
        // we'd be removing columns that need to be renamed as well.
        // Build the source column list, removing columns we no longer want.
        final Collection<String> removals = toRemove != null ? toRemove : new ArrayList<>();
        final TableInfo sourceTable = getTableInfo(db);
        final List<String> srcColumns = sourceTable
                .getColumns()
                .stream()
                .map(columnInfo -> columnInfo.name)
                .filter(name -> !removals.contains(name))
                .collect(Collectors.toList());

        // Build the destination column list, renaming columns as needed
        final Map<String, String> renames = toRename != null ? toRename : new HashMap<>();
        final List<String> dstColumns = srcColumns
                .stream()
                .map(column -> renames.getOrDefault(column, column))
                .collect(Collectors.toList());

        final String sql =
                "INSERT INTO " + destination + " (" + TextUtils.join(",", dstColumns) + ")"
                + " SELECT " + TextUtils.join(",", srcColumns) + " FROM " + mName;
        db.execSQL(sql);
    }

    /**
     * Return the SQL that can be used to define this table.
     *
     * @param tableName             to use (passed in to be able to create copies of the tables)
     * @param withDomainConstraints Flag indicating DOMAIN constraints should be applied.
     *
     * @return SQL to create table
     */
    @NonNull
    private String def(@NonNull final String tableName,
                       final boolean withDomainConstraints) {

        final StringBuilder sql = new StringBuilder("CREATE")
                .append(mType.getCreateModifier())
                .append(" TABLE ")
                .append(tableName)
                .append(mType.getUsingModifier())
                .append("\n(");

        // add the columns
        boolean hasPrimaryKey = false;
        final StringJoiner columns = new StringJoiner(",");
        for (final Domain domain : mDomains) {
            columns.add(domain.def(withDomainConstraints));
            // remember if we added a primary key column.
            hasPrimaryKey = hasPrimaryKey || domain.isPrimaryKey();
        }
        sql.append(columns);

        // add the primary key if not already added / needed.
        if (!hasPrimaryKey && !mPrimaryKey.isEmpty()) {
            sql.append("\n,PRIMARY KEY (")
               .append(TextUtils.join(",", mPrimaryKey))
               .append(')');
        }

        // add foreign key TABLE constraints if any.
        if (!mParents.isEmpty()) {
            sql.append("\n,")
               .append(mParents.values().stream()
                               .map(FkReference::def)
                               .collect(Collectors.joining("\n,")));
        }

        // end of column/constraint list
        sql.append(')');

        return sql.toString();
    }

//    /**
//     * NOT USED RIGHT NOW. BEFORE USING SHOULD BE ENHANCED WITH PREPROCESS_TITLE IF NEEDED
//     * <p>
//     * Create and populate the 'order by' column.
//     * This method is used/meant for use during upgrades.
//     * <p>
//     * Note this is a lazy approach using the users preferred Locale,
//     * as compared to the DAO code where we take the book's language/Locale into account.
//     * The overhead here would be huge.
//     * If the user has any specific book issue, a simple update of the book will fix it.
//     */
//    private void addOrderByColumn(@NonNull final Context context,
//                                  @NonNull final SQLiteDatabase db,
//                                  @NonNull final Domain source,
//                                  @NonNull final Domain destination) {
//
//        db.execSQL("ALTER TABLE " + getName()
//                   + " ADD " + destination.getName() + " text NOT NULL default ''");
//
//        final String updateSql =
//                "UPDATE " + getName() + " SET " + destination.getName() + "=?"
//                + " WHERE " + DBKey.PK_ID + "=?";
//
//        try (SQLiteStatement update = db.compileStatement(updateSql);
//             Cursor cursor = db.rawQuery(
//                     "SELECT " + DBKey.PK_ID
//                     + ',' + source.getName() + " FROM " + getName(),
//                     null)) {
//
//            final Locale userLocale = AppLocale.getInstance().getUserLocale(context);
//            while (cursor.moveToNext()) {
//                final long id = cursor.getLong(0);
//                final String in = cursor.getString(1);
//                update.bindString(1, SqlEncode.orderByColumn(in, userLocale));
//                update.bindLong(2, id);
//                update.executeUpdateDelete();
//            }
//        }
//    }

    /**
     * Supported/used table types.
     * <p>
     * <a href=https://sqlite.org/fts3.html">https://sqlite.org/fts3.html</a>
     */
    public enum TableType {
        Standard, Temporary, FTS3, FTS4;

        @SuppressWarnings("unused")
        boolean isVirtual() {
            return this == FTS3 || this == FTS4;
        }

        String getCreateModifier() {
            switch (this) {
                case FTS3:
                case FTS4:
                    return " VIRTUAL";
                case Standard:
                    return "";
                case Temporary:
                    return " TEMPORARY";
            }
            return "";
        }

        String getUsingModifier() {
            switch (this) {
                case Standard:
                case Temporary:
                    return "";

                case FTS3:
                    return " USING fts3";
                case FTS4:
                    return " USING fts4";
            }
            return "";
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
        private final List<Domain> mDomains;
        /** Optional key in the parent to use instead of the PK. */
        @Nullable
        private Domain mParentKey;

        /**
         * Constructor.
         * <p>
         * Create a reference to a parent table's Primary Key
         *
         * @param parent  Parent table (with PK that the FK references)
         * @param child   Child table (owner of the FK)
         * @param domains Domains in child table that reference PK in parent
         */
        FkReference(@NonNull final TableDefinition parent,
                    @NonNull final TableDefinition child,
                    @NonNull final Domain... domains) {
            // take a COPY
            mDomains = new ArrayList<>(Arrays.asList(domains));
            mParent = parent;
            mChild = child;
        }

        /**
         * Constructor.
         * <p>
         * Create a reference to a parent table's Primary Key
         *
         * @param parent  Parent table (with PK that FK references)
         * @param child   Child table (owner of the FK)
         * @param domains Domains in child table that reference PK in parent
         */
        FkReference(@NonNull final TableDefinition parent,
                    @NonNull final TableDefinition child,
                    @NonNull final List<Domain> domains) {
            // take a COPY
            mDomains = new ArrayList<>(domains);
            mParent = parent;
            mChild = child;
        }

        /**
         * Constructor.
         * <p>
         * Create a 1:1 reference to a parent table's specific domain.
         *
         * @param parent    Parent table
         * @param parentKey single Domain key in the parent table
         * @param child     Child table (owner of the FK)
         * @param domain    single Domain in child table that references parentKey in parent
         */
        FkReference(@NonNull final TableDefinition parent,
                    @NonNull final Domain parentKey,
                    @NonNull final TableDefinition child,
                    @NonNull final Domain domain) {
            mDomains = new ArrayList<>();
            mDomains.add(domain);
            mParent = parent;
            mParentKey = parentKey;
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
            if (mParentKey != null) {
                return mParent.getAlias() + '.' + mParentKey.getName()
                       + '=' + mChild.getAlias() + '.' + mDomains.get(0).getName();

            } else {
                final List<Domain> pk = mParent.getPrimaryKey();
                if (pk.isEmpty()) {
                    // Should never happen... flw
                    throw new IllegalStateException("No primary key for table: " + mParent);
                }
                final StringBuilder sql = new StringBuilder();
                for (int i = 0; i < pk.size(); i++) {
                    if (i > 0) {
                        sql.append(" AND ");
                    }
                    sql.append(mParent.getAlias()).append('.').append(pk.get(i).getName());
                    sql.append('=');
                    sql.append(mChild.getAlias()).append('.').append(mDomains.get(i).getName());
                }
                return sql.toString();
            }
        }

        /**
         * Construct the definition string. Format:
         * "FOREIGN KEY ([domain-list]) REFERENCES [pkTable] ([pk-list])"
         *
         * @return the definition
         */
        @NonNull
        String def() {
            if (mParentKey != null) {
                return "FOREIGN KEY (" + mDomains.get(0)
                       + ") REFERENCES " + mParent + '(' + mParentKey + ')';
            } else {
                return "FOREIGN KEY (" + TextUtils.join(",", mDomains)
                       + ") REFERENCES " + mParent
                       + '(' + TextUtils.join(",", mParent.getPrimaryKey()) + ')';
            }
        }

        @Override
        @NonNull
        public String toString() {
            return "FkReference{"
                   + "mParent=" + mParent
                   + ", mParentKey=" + mParentKey
                   + ", mChild=" + mChild
                   + ", mDomains=" + mDomains
                   + ", def=\n" + def()
                   + "\n}";
        }
    }
}
