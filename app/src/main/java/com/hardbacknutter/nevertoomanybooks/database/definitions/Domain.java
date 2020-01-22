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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;

/**
 * Defines a domain; name, type, ...
 * Cannot be modified after creation.
 * <p>
 * TODO: currently we only take reference copies of {@link Domain}.
 * Similarly, lists of domains are (sometimes) reference copies as well.
 * This is ok for now as we can't modify a Domain after initial creation.
 * So we <strong>SHOULD</strong> do a proper/full copy of the domain objects.
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

    /** Constraint string. */
    private static final String NOT_NULL = "NOT NULL";
    @NonNull
    private final String mName;
    @NonNull
    private final String mType;
    @NonNull
    private final List<String> mConstraints = new ArrayList<>();

    /** Holds a 'REFERENCES' clause (if any). */
    @Nullable
    private final String mReferences;

    /** This domain represents a primary key. */
    private final boolean mIsPrimaryKey;
    /**
     * This domain is pre-prepared for sorting;
     * i.e. the values are stripped of spaces etc.. before being stored.
     */
    private final boolean mIsPrePreparedOrderBy;

    /**
     * Full, private constructor.
     *
     * @param name               column name
     * @param isPrimaryKey       Flag
     * @param type               column type (text, int, float, ...)
     * @param constraints        {@code true} if this column should never be {@code null}
     * @param references         (optional) a table and action reference (ON UPDATE... etc...)
     * @param prePreparedOrderBy {@code true} if this domain is in fact pre-prepared for sorting.
     *                           i.e. the values are stripped of spaces etc.. before being stored.
     */
    private Domain(@NonNull final String name,
                   final boolean isPrimaryKey,
                   @NonNull final String type,
                   @NonNull final Collection<String> constraints,
                   @Nullable final String references,
                   final boolean prePreparedOrderBy) {
        mName = name;
        mType = type;
        mIsPrimaryKey = isPrimaryKey;
        mIsPrePreparedOrderBy = prePreparedOrderBy;
        mConstraints.addAll(constraints);
        mReferences = references;
    }

    /**
     * Copy constructor.
     *
     * @param from object to copy
     */
    public Domain(@NonNull final Domain from) {
        mName = from.mName;
        mType = from.mType;
        mIsPrimaryKey = from.mIsPrimaryKey;
        mIsPrePreparedOrderBy = from.mIsPrePreparedOrderBy;
        mConstraints.addAll(from.mConstraints);
        mReferences = from.mReferences;
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
        in.readList(mConstraints, getClass().getClassLoader());
        mReferences = in.readString();
        mIsPrimaryKey = in.readInt() == 1;
        mIsPrePreparedOrderBy = in.readInt() == 1;
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
     * Convenience method to check if this domain is TEXT based.
     *
     * @return {@code true} if this domain is a 'text' type.
     */
    public boolean isText() {
        return ColumnInfo.TYPE_TEXT.equalsIgnoreCase(mType);
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

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(mName);
        dest.writeString(mType);
        dest.writeList(mConstraints);
        dest.writeString(mReferences);
        dest.writeInt(mIsPrimaryKey ? 1 : 0);
        dest.writeInt(mIsPrePreparedOrderBy ? 1 : 0);
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
            for (String cs : mConstraints) {
                sql.append(' ').append(cs);
            }
            if (mReferences != null) {
                sql.append(" REFERENCES ").append(mReferences);
            }
        }
        return sql.toString();
    }

    public static class Builder {

        /** Multi-use; space at the end. */
        private static final String DEFAULT_ = "DEFAULT ";
        @NonNull
        private final String mName;
        @NonNull
        private final String mType;
        private final List<String> mConstraints = new ArrayList<>();

        private boolean mIsPrimaryKey;
        private boolean mIsPrePreparedOrderBy;

        @Nullable
        private String mReferences;

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
            mConstraints.add(NOT_NULL);
            return this;
        }

        /**
         * Add a constraint for NOT NULL.
         *
         * @return Builder (for chaining)
         */
        @NonNull
        public Builder notNull() {
            mConstraints.add(NOT_NULL);
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
            mConstraints.add(DEFAULT_ + value);
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
            mConstraints.add(DEFAULT_ + value);
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
            mConstraints.add(DEFAULT_ + value);
            return this;
        }

        /**
         * Add a string default '' constraint.
         *
         * @return Builder (for chaining)
         */
        @NonNull
        public Builder withDefaultEmptyString() {
            mConstraints.add(DEFAULT_ + "''");
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
            return new Domain(mName, mIsPrimaryKey, mType, mConstraints, mReferences,
                              mIsPrePreparedOrderBy);
        }
    }
}
