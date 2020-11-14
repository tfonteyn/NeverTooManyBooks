/*
 * @Copyright 2020 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.booklist.groups.BooklistGroup;

/**
 * Defines a domain; name, type, ...
 * Immutable.
 * <p>
 * Parcelable: needed by {@link BooklistGroup}
 */
@SuppressWarnings("FieldNotUsedInToString")
public class Domain
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<Domain> CREATOR = new Creator<Domain>() {
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
    private final String mName;
    /** This domain represents a primary key. */
    private final boolean mIsPrimaryKey;

    @ColumnInfo.Type
    @NonNull
    private final String mType;

    /** {@code null} values not allowed. */
    private final boolean mIsNotNull;
    /** Blank ("", 0) values not allowed. */
    private final boolean mIsNotBlank;

    /** Holds a 'DEFAULT' clause (if any). */
    @Nullable
    private final String mDefaultClause;
    /** Holds a 'REFERENCES' clause (if any). */
    @Nullable
    private final String mReferences;

    /**
     * This domain is pre-prepared for sorting;
     * i.e. the values are stripped of spaces etc.. before being stored.
     */
    private final boolean mIsPrePreparedOrderBy;

    private final boolean mIsCollationLocalized;

    /**
     * Full, private constructor.
     *
     * @param builder to use
     */
    private Domain(@NonNull final Builder builder) {
        mName = builder.mName;
        mIsPrimaryKey = builder.mIsPrimaryKey;
        mType = builder.mType;
        mIsNotNull = builder.mIsNotNull;
        mDefaultClause = builder.mDefaultClause;
        mReferences = builder.mReferences;
        mIsCollationLocalized = builder.mIsCollationLocalized;
        mIsPrePreparedOrderBy = builder.mIsPrePreparedOrderBy;

        mIsNotBlank = mDefaultClause != null && !"''".equals(mDefaultClause);
    }

    /**
     * Copy constructor.
     *
     * @param from object to copy
     */
    public Domain(@NonNull final Domain from) {
        mName = from.mName;
        mIsPrimaryKey = from.mIsPrimaryKey;
        mType = from.mType;
        mIsNotNull = from.mIsNotNull;
        mDefaultClause = from.mDefaultClause;
        mReferences = from.mReferences;
        mIsCollationLocalized = from.mIsCollationLocalized;
        mIsPrePreparedOrderBy = from.mIsPrePreparedOrderBy;

        mIsNotBlank = from.mIsNotBlank;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Domain(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        mName = in.readString();
        //noinspection ConstantConditions
        mType = in.readString();
        mIsPrimaryKey = in.readByte() != 0;
        mIsNotNull = in.readByte() != 0;
        mDefaultClause = in.readString();
        mReferences = in.readString();
        mIsCollationLocalized = in.readByte() != 0;
        mIsPrePreparedOrderBy = in.readByte() != 0;

        mIsNotBlank = mDefaultClause != null && !"''".equals(mDefaultClause);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(mName);
        dest.writeString(mType);
        dest.writeByte((byte) (mIsPrimaryKey ? 1 : 0));
        dest.writeByte((byte) (mIsNotNull ? 1 : 0));
        dest.writeString(mDefaultClause);
        dest.writeString(mReferences);
        dest.writeByte((byte) (mIsCollationLocalized ? 1 : 0));
        dest.writeByte((byte) (mIsPrePreparedOrderBy ? 1 : 0));
    }

    /**
     * Get the name of this domain.
     *
     * @return name
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Get the type of this domain.
     *
     * @return one of ColumnInfo#TYPE*
     */
    @ColumnInfo.Type
    @NonNull
    public String getType() {
        return mType;
    }

    boolean isPrimaryKey() {
        return mIsPrimaryKey;
    }

    /**
     * {@code null} values are not allowed.
     */
    public boolean isNotNull() {
        return mIsNotNull;
    }

    /**
     * Blank values are not allowed.
     * <p>
     * This is basically domains which have a DEFAULT clause which is not the empty string.
     */
    public boolean isNotBlank() {
        return mIsNotBlank;
    }

    boolean isCollationLocalized() {
        return mIsCollationLocalized;
    }

    public boolean hasDefault() {
        return mDefaultClause != null;
    }

    @Nullable
    public String getDefault() {
        if (mDefaultClause == null) {
            return null;

        } else if (CURRENT_TIMESTAMP.equals(mDefaultClause)) {
            return LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        } else if (mDefaultClause.startsWith("'") && mDefaultClause.endsWith("'")) {
            return mDefaultClause.substring(1, mDefaultClause.length() - 1);
        }

        return mDefaultClause;
    }

    /**
     * Convenience method to check the type of this domain.
     * Can be used to check if collation clauses and/or lower/upper should be used.
     *
     * @return {@code true} if this domain is a TYPE_TEXT.
     */
    public boolean isText() {
        return ColumnInfo.TYPE_TEXT.equalsIgnoreCase(mType);
    }

    /**
     * Convenience method to check the type of this domain.
     *
     * @return {@code true} if this domain is a TYPE_BOOLEAN.
     */
    public boolean isBoolean() {
        return ColumnInfo.TYPE_BOOLEAN.equalsIgnoreCase(mType);
    }

    /**
     * Check if this domain is pre-prepared for sorting.
     *
     * @return {@code true} if this field should be used as-is for sorting.
     */
    public boolean isPrePreparedOrderBy() {
        return mIsPrePreparedOrderBy;
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * toString() <strong>NOT DEBUG, must only ever return the table name</strong>
     * <p>
     * useful for using the Domain in place of a domain name.
     *
     * @return the name of the domain.
     */
    @Override
    @NonNull
    public String toString() {
        return mName;
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
        final StringBuilder sql = new StringBuilder(mName + ' ' + mType);
        if (mIsPrimaryKey) {
            sql.append(" PRIMARY KEY AUTOINCREMENT");
        }

        if (withConstraints) {
            if (mIsNotNull) {
                sql.append(" NOT NULL");
            }
            if (mDefaultClause != null) {
                sql.append(" DEFAULT ").append(mDefaultClause);
            }
            if (mReferences != null) {
                sql.append(" REFERENCES ").append(mReferences);
            }
        }
        return sql.toString();
    }

    public static class Builder {

        @NonNull
        private final String mName;

        @ColumnInfo.Type
        @NonNull
        private final String mType;

        private boolean mIsPrimaryKey;
        private boolean mIsNotNull;
        @Nullable
        private String mDefaultClause;
        @Nullable
        private String mReferences;
        private boolean mIsPrePreparedOrderBy;
        private boolean mIsCollationLocalized;

        /**
         * Constructor.
         *
         * @param name column name
         * @param type column type (text, int, float, ...)
         */
        public Builder(@NonNull final String name,
                       @ColumnInfo.Type @NonNull final String type) {
            mName = name;
            mType = type;
        }

        /**
         * Define the domain to be a primary key.
         * Automatically adds a constraint for NOT NULL.
         *
         * @return Builder (for chaining)
         */
        @NonNull
        public Builder primaryKey() {
            mIsPrimaryKey = true;
            mIsNotNull = true;
            return this;
        }

        /**
         * Add a constraint for NOT NULL.
         *
         * @return Builder (for chaining)
         */
        @NonNull
        public Builder notNull() {
            mIsNotNull = true;
            return this;
        }

        /**
         * Add a numerical default constraint.
         *
         * @param value to use as default
         *
         * @return Builder (for chaining)
         */
        @NonNull
        public Builder withDefault(final long value) {
            mDefaultClause = String.valueOf(value);
            return this;
        }

        /**
         * Add a numerical default constraint.
         *
         * @param value to use as default
         *
         * @return Builder (for chaining)
         */
        @NonNull
        public Builder withDefault(final double value) {
            mDefaultClause = String.valueOf(value);
            return this;
        }

        /**
         * Add a string default constraint.
         *
         * @param value to add (a string default must include the quotes!)
         *
         * @return Builder (for chaining)
         */
        @NonNull
        public Builder withDefault(@NonNull final String value) {
            mDefaultClause = value;
            return this;
        }

        /**
         * Add a current UTC timestamp default constraint.
         *
         * @return Builder (for chaining)
         */
        @NonNull
        public Builder withDefaultCurrentTimeStamp() {
            mDefaultClause = CURRENT_TIMESTAMP;
            return this;
        }

        /**
         * Add a string default '' constraint.
         *
         * @return Builder (for chaining)
         */
        @NonNull
        public Builder withDefaultEmptyString() {
            mDefaultClause = "''";
            return this;
        }

        @NonNull
        public Builder localized() {
            mIsCollationLocalized = true;
            return this;
        }

        /**
         * if this domain is in fact pre-prepared for sorting.
         * i.e. the values are stripped of spaces etc.. before being stored.
         *
         * @return Builder (for chaining)
         */
        @NonNull
        public Builder prePreparedOrderBy() {
            mIsPrePreparedOrderBy = true;
            return this;
        }

        /**
         * (optional) a table and action reference (ON UPDATE... etc...).
         *
         * @param table   to reference
         * @param actions 'on delete...' etc...
         *
         * @return Builder (for chaining)
         */
        @NonNull
        public Builder references(@NonNull final TableDefinition table,
                                  @NonNull final String actions) {
//            if (BuildConfig.DEBUG /* always */) {
//                if (mReferences != null) {
//                    throw new IllegalStateException("can only be called once");
//                }
//            }
            mReferences = table.getName() + ' ' + actions;
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
