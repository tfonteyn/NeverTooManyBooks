/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;

/**
 * Defines a domain; name, type, ...
 * Cannot be modified after creation.
 * <p>
 * TODO: currently we only take reference copies of {@link Domain}.
 * Similarly, lists of domains are (sometimes) reference copies as well.
 * This is ok for now as we can't modify a Domain after initial creation.
 * But we <strong>SHOULD</strong> do a proper/full copy of the domain objects.
 * This will need some time/work to hunt down all the places in the code though.
 * <p>
 * Parcelable: needed by {@link BooklistGroup}
 */
@SuppressWarnings("FieldNotUsedInToString")
public class Domain
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<Domain> CREATOR =
            new Creator<Domain>() {
                @Override
                public Domain createFromParcel(@NonNull final Parcel source) {
                    return new Domain(source);
                }

                @Override
                public Domain[] newArray(final int size) {
                    return new Domain[size];
                }
            };

    @NonNull
    private final String mName;
    /** This domain represents a primary key. */
    private final boolean mIsPrimaryKey;
    @NonNull
    private final String mType;
    /** {@code null} values not allowed. */
    private final boolean mIsNotNull;

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
    private final boolean mIsNotBlank;

    /**
     * Full, private constructor.
     *
     * @param name               column name
     * @param isPrimaryKey       Flag
     * @param type               column type (text, int, float, ...)
     * @param isNotNull          {@code true} if this column should never be {@code null}
     * @param defaultClause      (optional) a DEFAULT clause
     * @param references         (optional) a table and action reference (ON UPDATE... etc...)
     * @param prePreparedOrderBy {@code true} if this domain is in fact pre-prepared for sorting.
     *                           i.e. the values are stripped of spaces etc.. before being stored.
     */
    private Domain(@NonNull final String name,
                   final boolean isPrimaryKey,
                   @NonNull final String type,
                   final boolean isNotNull,
                   @Nullable final String defaultClause,
                   @Nullable final String references,
                   final boolean prePreparedOrderBy) {
        mName = name;
        mIsPrimaryKey = isPrimaryKey;
        mType = type;
        mIsNotNull = isNotNull;
        mDefaultClause = defaultClause;
        mReferences = references;
        mIsPrePreparedOrderBy = prePreparedOrderBy;

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
        mIsPrimaryKey = in.readInt() == 1;
        //noinspection ConstantConditions
        mType = in.readString();
        mIsNotNull = in.readInt() == 1;
        mDefaultClause = in.readString();
        mReferences = in.readString();
        mIsPrePreparedOrderBy = in.readInt() == 1;

        mIsNotBlank = mDefaultClause != null && !"''".equals(mDefaultClause);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(mName);
        dest.writeInt(mIsPrimaryKey ? 1 : 0);
        dest.writeString(mType);
        dest.writeInt(mIsNotNull ? 1 : 0);
        dest.writeString(mDefaultClause);
        dest.writeString(mReferences);
        dest.writeInt(mIsPrePreparedOrderBy ? 1 : 0);
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
     *
     * This is basically domains which have a DEFAULT clause which is not the empty string.
     */
    public boolean isNotBlank() {
        return mIsNotBlank;
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
        StringBuilder sql = new StringBuilder(mName + ' ' + mType);
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

    public boolean hasDefault() {
        return mDefaultClause != null;
    }

    public static class Builder {

        @NonNull
        private final String mName;
        @NonNull
        private final String mType;
        private boolean mIsPrimaryKey;
        private boolean mIsNotNull;
        @Nullable
        private String mDefaultClause;
        @Nullable
        private String mReferences;
        private boolean mIsPrePreparedOrderBy;

        public Builder(@NonNull final String name,
                       @NonNull final String type) {
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
        public Builder prePreparedOrderBy() {
            mIsPrePreparedOrderBy = true;
            return this;
        }

        /**
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
            return new Domain(mName, mIsPrimaryKey, mType,
                              mIsNotNull, mDefaultClause, mReferences,
                              mIsPrePreparedOrderBy);
        }
    }
}
