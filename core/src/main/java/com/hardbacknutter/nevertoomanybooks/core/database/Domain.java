/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Defines a domain; name, type, ...
 * Immutable.
 */
@SuppressWarnings({"FieldNotUsedInToString", "NegativelyNamedBooleanVariable"})
public class Domain {

    /** standard SQL keyword. **/
    private static final String CURRENT_TIMESTAMP = "current_timestamp";
    private static final String SQL_EMPTY_STRING = "''";

    @NonNull
    private final String name;
    /** This domain represents a primary key. */
    private final boolean primaryKey;

    @NonNull
    private final SqLiteDataType sqLiteDataType;

    /** {@code null} values not allowed. */
    private final boolean notNull;
    /** Blank ("", 0) values not allowed. */
    private final boolean notBlank;

    /** Holds a 'DEFAULT' clause (if any). */
    @Nullable
    private final String defaultClause;
    /** Holds a 'REFERENCES' clause (if any). */
    @Nullable
    private final String references;

    @NonNull
    private final String collationClause;

    /**
     * Full, private constructor.
     *
     * @param builder to use
     */
    private Domain(@NonNull final Builder builder) {
        name = builder.name;
        primaryKey = builder.primaryKey;
        sqLiteDataType = builder.sqLiteDataType;
        notNull = builder.notNull;
        defaultClause = builder.defaultClause;
        references = builder.references;

        if (builder.collationLocalized) {
            collationClause = " COLLATE LOCALIZED";
        } else {
            collationClause = "";
        }

        notBlank = defaultClause != null && !SQL_EMPTY_STRING.equals(defaultClause);
    }

    /**
     * Copy constructor.
     *
     * @param from object to copy
     */
    public Domain(@NonNull final Domain from) {
        name = from.name;
        primaryKey = from.primaryKey;
        sqLiteDataType = from.sqLiteDataType;
        notNull = from.notNull;
        defaultClause = from.defaultClause;
        references = from.references;
        collationClause = from.collationClause;

        notBlank = from.notBlank;
    }

    /**
     * Get the name of this domain.
     *
     * @return name
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Get the collation clause.
     *
     * @return either " COLLATE LOCALIZED", or the empty String but never {@code null}
     */
    @NonNull
    public String getCollationClause() {
        return collationClause;
    }

    /**
     * Create the {@code ORDER BY} clause for this domain.
     *
     * @param sort                   flag (ascending, descending, not-set)
     * @param collationCaseSensitive flag; whether the database uses case-sensitive collation
     *
     * @return column sort SQL fragment
     */
    @NonNull
    public String getOrderByString(@NonNull final Sort sort,
                                   final boolean collationCaseSensitive) {
        if (collationCaseSensitive) {
            // Lowercase the DATA from the name column
            // but not the column name itself!
            // This should never happen, but see the DAO method docs.
            return "LOWER(" + name + ')' + collationClause + sort.getExpression();
        } else {
            return name + collationClause + sort.getExpression();
        }
    }

    /**
     * Get the type of this domain.
     *
     * @return one of {@link SqLiteDataType}
     */
    @NonNull
    public SqLiteDataType getSqLiteDataType() {
        return sqLiteDataType;
    }

    boolean isPrimaryKey() {
        return primaryKey;
    }

    /**
     * Check if {@code NULL} values are allowed.
     *
     * @return {@code true} if {@code NULL} values are NOT allowed.
     */
    public boolean isNotNull() {
        return notNull;
    }

    /**
     * Check if blank values are allowed.
     * <p>
     * This is basically domains which have a DEFAULT clause which is not the empty string.
     *
     * @return {@code true} if blank values are NOT allowed.
     */
    public boolean isNotBlank() {
        return notBlank;
    }


    /**
     * Check if this domain has a default set.
     *
     * @return {@code true} if is has.
     */
    public boolean hasDefault() {
        return defaultClause != null;
    }

    /**
     * Get the default value for this domain.
     *
     * @return default
     */
    @Nullable
    public String getDefault() {
        if (defaultClause == null) {
            return null;

        } else if (CURRENT_TIMESTAMP.equals(defaultClause)) {
            return LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        } else if (defaultClause.startsWith("'") && defaultClause.endsWith("'")) {
            return defaultClause.substring(1, defaultClause.length() - 1);
        }

        return defaultClause;
    }

    /**
     * toString() <strong>NOT DEBUG, must only ever return the column name</strong>.
     *
     * @return the name of the domain.
     */
    @Override
    @NonNull
    public String toString() {
        return name;
    }

    /**
     * Get the SQL used to define this domain.
     *
     * @param withConstraints when false, no constraints are applied
     *
     * @return the column creation clause
     */
    @NonNull
    String def(final boolean withConstraints) {
        final StringBuilder sql = new StringBuilder(name + ' ' + sqLiteDataType.getName());
        if (primaryKey) {
            sql.append(" PRIMARY KEY AUTOINCREMENT");
        }

        if (withConstraints) {
            if (notNull) {
                sql.append(" NOT NULL");
            }
            if (defaultClause != null) {
                sql.append(" DEFAULT ").append(defaultClause);
            }

            sql.append(collationClause);

            if (references != null) {
                sql.append(" REFERENCES ").append(references);
            }
        }
        return sql.toString();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Domain domain = (Domain) o;
        return primaryKey == domain.primaryKey
               && notNull == domain.notNull
               && notBlank == domain.notBlank
               && collationClause.equals(domain.collationClause)
               && name.equals(domain.name)
               && sqLiteDataType == domain.sqLiteDataType
               && Objects.equals(defaultClause, domain.defaultClause)
               && Objects.equals(references, domain.references);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(name, primaryKey, sqLiteDataType, notNull, notBlank, defaultClause,
                      references,
                      collationClause);
    }

    public static class Builder {

        @NonNull
        private final String name;

        @NonNull
        private final SqLiteDataType sqLiteDataType;

        private boolean primaryKey;
        private boolean notNull;
        @Nullable
        private String defaultClause;
        @Nullable
        private String references;
        private boolean collationLocalized;

        /**
         * Constructor.
         *
         * @param name           column name
         * @param sqLiteDataType column type (text, int, float, ...)
         */
        public Builder(@NonNull final String name,
                       @NonNull final SqLiteDataType sqLiteDataType) {
            this.name = name;
            this.sqLiteDataType = sqLiteDataType;
        }

        /**
         * Define the domain to be a primary key.
         * Automatically adds a constraint for NOT NULL.
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder primaryKey() {
            primaryKey = true;
            notNull = true;
            return this;
        }

        /**
         * Add a constraint for NOT NULL.
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder notNull() {
            notNull = true;
            return this;
        }

        /**
         * Add a boolean default constraint.
         *
         * @param value to use as default
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder withDefault(final boolean value) {
            defaultClause = value ? "1" : "0";
            return this;
        }

        /**
         * Add a numerical default constraint.
         *
         * @param value to use as default
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder withDefault(final long value) {
            defaultClause = String.valueOf(value);
            return this;
        }

        /**
         * Add a numerical default constraint.
         *
         * @param value to use as default
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder withDefault(final double value) {
            defaultClause = String.valueOf(value);
            return this;
        }

        /**
         * Add a string default constraint.
         *
         * @param value to add (a string default must include the quotes!)
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder withDefault(@NonNull final String value) {
            defaultClause = value;
            return this;
        }

        /**
         * Add a current UTC timestamp default constraint.
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder withDefaultCurrentTimeStamp() {
            defaultClause = CURRENT_TIMESTAMP;
            return this;
        }

        /**
         * Add a string default '' constraint.
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder withDefaultEmptyString() {
            defaultClause = SQL_EMPTY_STRING;
            return this;
        }

        /**
         * Specify this domain is Localized.
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder localized() {
            collationLocalized = true;
            return this;
        }

        /**
         * (optional) a table and action reference (ON UPDATE... etc...).
         *
         * @param table   to reference
         * @param actions 'on delete...' etc...
         *
         * @return {@code this} (for chaining)
         *
         * @throws IllegalStateException if this method is called more than once
         */
        @NonNull
        public Builder references(@NonNull final TableDefinition table,
                                  @NonNull final String actions) {
            if (references != null) {
                throw new IllegalStateException("can only be called once");
            }
            references = table.getName() + ' ' + actions;
            return this;
        }

        /**
         * Construct the Domain object.
         *
         * @return domain
         */
        @NonNull
        public Domain build() {
            return new Domain(this);
        }
    }
}
