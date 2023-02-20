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
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

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

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;


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

    /** List of domains in this table. */
    private final List<Domain> domains = new ArrayList<>();
    /** List of index definitions for this table. */
    private final Collection<IndexDefinition> indexes = new ArrayList<>();
    /** List of domains forming primary key. */
    private final List<Domain> primaryKey = new ArrayList<>();
    /** List of parent tables (tables referred to by foreign keys on this table). */
    private final Map<TableDefinition, FkReference> parents =
            Collections.synchronizedMap(new HashMap<>());
    /** List of child tables (tables referring to by foreign keys to this table). */
    private final Map<TableDefinition, FkReference> children =
            Collections.synchronizedMap(new HashMap<>());
    @NonNull
    private final String name;
    @NonNull
    private final String alias;
    @Nullable
    private final DebugHelper debugHelper;
    @NonNull
    private TableType type = TableType.Standard;
    /** Cached table structure info. */
    @Nullable
    private TableInfo tableInfo;

    /**
     * Constructor.
     *
     * @param name  Table name
     * @param alias name for the table
     */
    public TableDefinition(@NonNull final String name,
                           @NonNull final String alias) {
        this.name = name;
        this.alias = alias;

        if (BuildConfig.DEBUG /* always */) {
            debugHelper = new DebugHelper();
        } else {
            debugHelper = null;
        }
    }

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
                                final boolean collationCaseSensitive,
                                @NonNull final Collection<TableDefinition> tables) {
        for (final TableDefinition table : tables) {
            table.create(db, true);
            table.createIndices(db, collationCaseSensitive);
        }
    }

    /**
     * Create this table.
     * Don't forget to call {@link #createIndices(SQLiteDatabase, boolean)} if needed.
     *
     * @param db                    Database Access
     * @param withDomainConstraints Indicates if fields should have constraints applied
     */
    public void create(@NonNull final SQLiteDatabase db,
                       final boolean withDomainConstraints) {
        db.execSQL(getCreateStatement(name, withDomainConstraints));
    }

    /**
     * Create all registered indexes for this table.
     *
     * @param db Database Access
     */
    public void createIndices(@NonNull final SQLiteDatabase db,
                              final boolean collationCaseSensitive) {
        for (final IndexDefinition index : indexes) {
            index.create(db, collationCaseSensitive);
        }
    }

    /**
     * Remove all references and resources used by this table.
     */
    public void clear() {
        domains.clear();
        indexes.clear();
        primaryKey.clear();

        if (debugHelper != null) {
            debugHelper.clear();
        }

        parents.values().stream()
               .map(FkReference::getPrimaryKeyTable)
               .forEach(this::removeReference);

        children.values().stream()
                .map(FkReference::getForeignKeyTable)
                .forEach(child -> child.removeReference(this));
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
        this.type = type;
        return this;
    }

    /**
     * Get the alias name.
     *
     * @return the table alias name
     */
    @VisibleForTesting
    @NonNull
    public String getAlias() {
        return alias;
    }

    /**
     * Get the table name.
     *
     * @return table name.
     */
    @NonNull
    public String getName() {
        return name;
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
        return name;
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
        if (debugHelper != null) {
            debugHelper.addDomains(domains);
        }
        Collections.addAll(this.domains, domains);
        return this;
    }

    /**
     * Check if a domain is present.
     *
     * @param domain to check
     *
     * @return {@code true} if present.
     */
    public boolean contains(@NonNull final Domain domain) {
        return domains.contains(domain);
    }

    /**
     * Get the complete list of domains used by this table.
     *
     * @return list of domains.
     */
    @NonNull
    public List<Domain> getDomains() {
        return domains;
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
        return domains.stream()
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
        return primaryKey;
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
        primaryKey.clear();
        Collections.addAll(primaryKey, domains);
        return this;
    }

    /**
     * Add a FOREIGN KEY (FK) references to another (parent) table.
     *
     * @param primaryKeyTable   The referenced table (with the PK)
     * @param foreignKeyDomains Domains in <strong>this</strong> table that reference
     *                          the PK in the primaryKeyTable
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public TableDefinition addReference(@NonNull final TableDefinition primaryKeyTable,
                                        @NonNull final Domain... foreignKeyDomains) {
        final FkReference fk = new FkReference(primaryKeyTable, this, foreignKeyDomains);

        if (fk.getForeignKeyTable() != this) {
            throw new IllegalStateException("Foreign key does not include this table as child");
        }

        parents.put(fk.primaryKeyTable, fk);
        if (!fk.primaryKeyTable.children.containsKey(this)) {
            fk.primaryKeyTable.children.put(this, fk);
        }
        return this;
    }

    /**
     * Remove FK reference to parent table.
     *
     * @param parent The referenced Table
     */
    private void removeReference(@NonNull final TableDefinition parent) {
        parents.remove(parent);
        parent.children.remove(this);
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
        if (debugHelper != null) {
            debugHelper.addIndex(nameSuffix);
        }

        indexes.add(new IndexDefinition(this, nameSuffix + "_" + (indexes.size() + 1),
                                        unique, Arrays.asList(domains)));
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
        return alias + '.' + domain;
    }

    @NonNull
    public String dot(@NonNull final Domain domain) {
        return alias + '.' + domain.getName();
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
                     .map(key -> alias + '.' + key + _AS_ + key)
                     .collect(Collectors.joining(","));
    }

    @NonNull
    public String dotAs(@NonNull final List<Domain> domains) {
        return domains.stream()
                      .map(Domain::getName)
                      .map(key -> alias + '.' + key + _AS_ + key)
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
        return name + _AS_ + alias;
    }

    /**
     * Staring with the current table, join with the given list of tables one by one.
     *
     * @param tables to join
     *
     * @return SQL fragment
     */
    @NonNull
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
        return " JOIN " + to.ref() + " ON " + fkMatch(to);
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
        return " LEFT OUTER JOIN " + to.ref() + " ON " + fkMatch(to);
    }

    /**
     * Return the FK condition that applies between this table and the 'to' table.
     *
     * @param to Table that is other part of FK/PK
     *
     * @return SQL fragment
     */
    @NonNull
    public String fkMatch(@NonNull final TableDefinition to) {
        final FkReference fk;
        if (children.containsKey(to)) {
            fk = children.get(to);
        } else {
            fk = parents.get(to);
        }

        Objects.requireNonNull(fk, () -> "No foreign key between `" + name
                                         + "` and `" + to.getName() + '`');

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
        if (type == TableType.Standard) {
            sql = TABLE_EXISTS_SQL_STANDARD;
        } else {
            sql = TABLE_EXISTS_SQL_TEMP;
        }

        try (SQLiteStatement stmt = db.compileStatement(sql)) {
            stmt.bindString(1, name);
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
            if (tableInfo == null) {
                tableInfo = new TableInfo(db, name);
            }
        }
        return tableInfo;
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
            db.execSQL("ALTER TABLE " + name + " ADD " + domain.def(true));
        }
    }

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

        // Note: don't use the 'domains' to check for columns no longer there,
        // we'd be removing columns that need to be renamed as well.
        // Build the source column list, removing columns we no longer want.
        final Collection<String> removals = toRemove != null ? toRemove : new ArrayList<>();
        final TableInfo sourceTable = getTableInfo(db);
        final List<String> srcColumns = sourceTable
                .getColumns()
                .stream()
                .map(ColumnInfo::getName)
                .filter(s -> !removals.contains(s))
                .collect(Collectors.toList());

        // Build the destination column list, renaming columns as needed
        final Map<String, String> renames = toRename != null ? toRename : new HashMap<>();
        final List<String> dstColumns = srcColumns
                .stream()
                .map(column -> renames.getOrDefault(column, column))
                .collect(Collectors.toList());

        final String sql =
                "INSERT INTO " + destination + " (" + String.join(",", dstColumns) + ")"
                + " SELECT " + String.join(",", srcColumns) + " FROM " + name;
        db.execSQL(sql);
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
//        final String dstTableName = "copyOf" + name;
//        // With constraints... sqlite does not allow to add constraints later.
//        // Without indexes.
//        db.execSQL(def(dstTableName, true));
//
//        // This handles re-ordered fields etc.
//        copyTableSafely(db, dstTableName, toRemove, toRename);
//
//        db.execSQL("DROP TABLE " + name);
//        db.execSQL("ALTER TABLE " + dstTableName + " RENAME TO " + name);
//    }

    /**
     * Return the SQL that can be used to define this table.
     *
     * @param tableName             to use (passed in to be able to create copies of the tables)
     * @param withDomainConstraints Flag indicating DOMAIN constraints should be applied.
     *
     * @return SQL to create table
     */
    @NonNull
    private String getCreateStatement(@NonNull final String tableName,
                                      final boolean withDomainConstraints) {

        final StringBuilder sql;
        switch (type) {
            case FTS:
                sql = new StringBuilder("CREATE VIRTUAL TABLE " + tableName + " USING fts4");
                break;

            case Temporary:
                sql = new StringBuilder("CREATE TEMPORARY TABLE " + tableName);
                break;

            case Standard:
            default:
                sql = new StringBuilder("CREATE TABLE " + tableName);
                break;
        }

        sql.append("\n(");

        // add the columns
        boolean hasPrimaryKey = false;
        final StringJoiner columns = new StringJoiner(",");
        for (final Domain domain : domains) {
            columns.add(domain.def(withDomainConstraints));
            // remember if we added a primary key column.
            hasPrimaryKey = hasPrimaryKey || domain.isPrimaryKey();
        }
        sql.append(columns);

        // add the primary key if not already added / needed.
        if (!hasPrimaryKey && !primaryKey.isEmpty()) {
            sql.append("\n,PRIMARY KEY (")
               .append(primaryKey.stream()
                                 .map(Domain::getName)
                                 .collect(Collectors.joining(",")))
               .append(')');
        }

        // add foreign key TABLE constraints if any.
        if (!parents.isEmpty()) {
            sql.append("\n,")
               .append(parents.values().stream()
                              .map(FkReference::def)
                              .collect(Collectors.joining(",")));
        }

        // end of column/constraint list
        sql.append(')');

        return sql.toString();
    }

    /**
     * Supported/used table types.
     *
     * @see <a href=https://sqlite.org/fts3.html">https://sqlite.org/fts3.html</a>
     */
    public enum TableType {
        Standard,
        Temporary,
        FTS
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

    private static class DebugHelper {
        /** Used for checking if a domain has already been added. Only used in DEBUG. */
        private final Collection<Integer> debugDomainNameDuplicates = new HashSet<>();
        /** Used for checking if an index has already been added. Only used in DEBUG. */
        private final Collection<Integer> debugIndexNameDuplicates = new HashSet<>();

        void clear() {
            debugDomainNameDuplicates.clear();
            debugIndexNameDuplicates.clear();
        }

        void addDomains(@NonNull final Domain... domains) {
            Arrays.stream(domains).forEach(domain -> {
                final int nameHash = domain.getName().hashCode();
                if (debugDomainNameDuplicates.contains(nameHash)) {
                    throw new IllegalStateException("Duplicate domain: " + domain);
                }
                debugDomainNameDuplicates.add(nameHash);
            });
        }

        void addIndex(@NonNull final String nameSuffix) {
            // Make sure not already defined
            final int nameHash = nameSuffix.hashCode();
            if (debugIndexNameDuplicates.contains(nameHash)) {
                throw new IllegalStateException("Duplicate nameSuffix: " + nameSuffix);
            }
            debugIndexNameDuplicates.add(nameHash);
        }
    }

    /**
     * Represents a FOREIGN KEY reference.
     */
    private static class FkReference {

        /** Owner of PRIMARY KEY that the FK references. */
        @NonNull
        private final TableDefinition primaryKeyTable;
        /** Table owning FOREIGN KEY. */
        @NonNull
        private final TableDefinition foreignKeyTable;
        /** Domains in the FOREIGN KEY that reference the parent PRIMARY KEY. */
        @NonNull
        private final List<Domain> foreignKeyDomains;

        /**
         * Constructor.
         *
         * @param primaryKeyTable   Parent table with PRIMARY KEY that the FOREIGN KEY references
         * @param foreignKeyTable   Child table which owns the FOREIGN KEY
         * @param foreignKeyDomains Domains in child table that reference PRIMARY KEY in parent
         */
        FkReference(@NonNull final TableDefinition primaryKeyTable,
                    @NonNull final TableDefinition foreignKeyTable,
                    @NonNull final Domain... foreignKeyDomains) {
            this.primaryKeyTable = primaryKeyTable;
            this.foreignKeyTable = foreignKeyTable;
            // take a COPY
            this.foreignKeyDomains = new ArrayList<>(Arrays.asList(foreignKeyDomains));
        }

        @NonNull
        TableDefinition getPrimaryKeyTable() {
            return primaryKeyTable;
        }

        @NonNull
        TableDefinition getForeignKeyTable() {
            return foreignKeyTable;
        }

        /**
         * Get an SQL fragment that matches the PRIMARY KEY of the parent
         * to the FOREIGN KEY of the child.
         * <p>
         * format: ([parent alias].[column] = [child alias].[column] [ AND ...])
         *
         * @return SQL fragment
         */
        @NonNull
        String getPredicate() {
            final List<Domain> pk = primaryKeyTable.getPrimaryKey();

            if (pk.isEmpty()) {
                throw new IllegalStateException("No primary key for table: " + primaryKeyTable);
            }

            final StringJoiner sql = new StringJoiner(" AND ", "(", ")");
            for (int i = 0; i < pk.size(); i++) {
                sql.add(primaryKeyTable.dot(pk.get(i))
                        + '=' + foreignKeyTable.dot(foreignKeyDomains.get(i)));
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
            return "FOREIGN KEY ("
                   + foreignKeyDomains.stream()
                                      .map(Domain::getName)
                                      .collect(Collectors.joining(","))
                   + ") REFERENCES " + primaryKeyTable.getName() + '('
                   + primaryKeyTable.getPrimaryKey().stream()
                                    .map(Domain::getName)
                                    .collect(Collectors.joining(","))
                   + ')';
        }

        @Override
        @NonNull
        public String toString() {
            return "FkReference{"
                   + "parent=" + primaryKeyTable
                   + ", child=" + foreignKeyTable
                   + ", domains=" + foreignKeyDomains
                   + '}';
        }
    }
}
