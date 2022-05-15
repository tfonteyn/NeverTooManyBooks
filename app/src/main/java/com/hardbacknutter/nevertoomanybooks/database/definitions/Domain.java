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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Defines a domain; name, type, ...
 * Immutable.
 * <p>
 * Parcelable: needed by BooklistGroup
 */
@SuppressWarnings({"FieldNotUsedInToString", "NegativelyNamedBooleanVariable"})
public class Domain
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<Domain> CREATOR = new Creator<>() {
        @Override
        public Domain createFromParcel(@NonNull final Parcel source) {
            return new Domain(source);
        }

        @Override
        public Domain[] newArray(final int size) {
            return new Domain[size];
        }
    };

    /** standard SQL keyword. **/
    private static final String CURRENT_TIMESTAMP = "current_timestamp";

    @NonNull
    private final String name;
    /** This domain represents a primary key. */
    private final boolean primaryKey;

    @ColumnInfo.Type
    @NonNull
    private final String type;

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

    /**
     * This domain is pre-prepared for sorting;
     * i.e. the values are stripped of spaces etc.. before being stored.
     */
    private final boolean prePreparedOrderBy;

    private final boolean collationLocalized;

    /**
     * Full, private constructor.
     *
     * @param builder to use
     */
    private Domain(@NonNull final Builder builder) {
        name = builder.name;
        primaryKey = builder.primaryKey;
        type = builder.type;
        notNull = builder.notNull;
        defaultClause = builder.defaultClause;
        references = builder.references;
        collationLocalized = builder.collationLocalized;
        prePreparedOrderBy = builder.prePreparedOrderBy;

        notBlank = defaultClause != null && !"''".equals(defaultClause);
    }

    /**
     * Copy constructor.
     *
     * @param from object to copy
     */
    public Domain(@NonNull final Domain from) {
        name = from.name;
        primaryKey = from.primaryKey;
        type = from.type;
        notNull = from.notNull;
        defaultClause = from.defaultClause;
        references = from.references;
        collationLocalized = from.collationLocalized;
        prePreparedOrderBy = from.prePreparedOrderBy;

        notBlank = from.notBlank;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Domain(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        name = in.readString();
        //noinspection ConstantConditions
        type = in.readString();
        primaryKey = in.readByte() != 0;
        notNull = in.readByte() != 0;
        defaultClause = in.readString();
        references = in.readString();
        collationLocalized = in.readByte() != 0;
        prePreparedOrderBy = in.readByte() != 0;

        notBlank = defaultClause != null && !"''".equals(defaultClause);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(name);
        dest.writeString(type);
        dest.writeByte((byte) (primaryKey ? 1 : 0));
        dest.writeByte((byte) (notNull ? 1 : 0));
        dest.writeString(defaultClause);
        dest.writeString(references);
        dest.writeByte((byte) (collationLocalized ? 1 : 0));
        dest.writeByte((byte) (prePreparedOrderBy ? 1 : 0));
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
     * Get the type of this domain.
     *
     * @return one of ColumnInfo#TYPE*
     */
    @ColumnInfo.Type
    @NonNull
    public String getType() {
        return type;
    }

    boolean isPrimaryKey() {
        return primaryKey;
    }

    /**
     * {@code null} values are not allowed.
     */
    public boolean isNotNull() {
        return notNull;
    }

    /**
     * Blank values are not allowed.
     * <p>
     * This is basically domains which have a DEFAULT clause which is not the empty string.
     */
    public boolean isNotBlank() {
        return notBlank;
    }

    boolean isCollationLocalized() {
        return collationLocalized;
    }

    public boolean hasDefault() {
        return defaultClause != null;
    }

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
     * Convenience method to check the type of this domain.
     * Can be used to check if collation clauses and/or lower/upper should be used.
     *
     * @return {@code true} if this domain is a TYPE_TEXT.
     */
    public boolean isText() {
        return ColumnInfo.TYPE_TEXT.equalsIgnoreCase(type);
    }

    /**
     * Convenience method to check the type of this domain.
     *
     * @return {@code true} if this domain is a TYPE_BOOLEAN.
     */
    public boolean isBoolean() {
        return ColumnInfo.TYPE_BOOLEAN.equalsIgnoreCase(type);
    }

    /**
     * Check if this domain is pre-prepared for sorting.
     *
     * @return {@code true} if this field should be used as-is for sorting.
     */
    public boolean isPrePreparedOrderBy() {
        return prePreparedOrderBy;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * toString() <strong>NOT DEBUG, must only ever return the table name</strong>
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
        final StringBuilder sql = new StringBuilder(name + ' ' + type);
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
               && prePreparedOrderBy == domain.prePreparedOrderBy
               && collationLocalized == domain.collationLocalized
               && name.equals(domain.name)
               && type.equals(domain.type)
               && Objects.equals(defaultClause, domain.defaultClause)
               && Objects.equals(references, domain.references);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(name, primaryKey, type, notNull, notBlank, defaultClause,
                      references,
                      prePreparedOrderBy, collationLocalized);
    }

    public static class Builder {

        @NonNull
        private final String name;

        @ColumnInfo.Type
        @NonNull
        private final String type;

        private boolean primaryKey;
        private boolean notNull;
        @Nullable
        private String defaultClause;
        @Nullable
        private String references;
        private boolean prePreparedOrderBy;
        private boolean collationLocalized;

        /**
         * Constructor.
         *
         * @param name column name
         * @param type column type (text, int, float, ...)
         */
        public Builder(@NonNull final String name,
                       @ColumnInfo.Type @NonNull final String type) {
            this.name = name;
            this.type = type;
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
            defaultClause = "''";
            return this;
        }

        @NonNull
        public Builder localized() {
            collationLocalized = true;
            return this;
        }

        /**
         * if this domain is in fact pre-prepared for sorting.
         * i.e. the values are stripped of spaces etc.. before being stored.
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder prePreparedOrderBy() {
            prePreparedOrderBy = true;
            return this;
        }

        /**
         * (optional) a table and action reference (ON UPDATE... etc...).
         *
         * @param table   to reference
         * @param actions 'on delete...' etc...
         *
         * @return {@code this} (for chaining)
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
