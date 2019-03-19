package com.eleybourn.bookcatalogue.database.definitions;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class to store domain name and definition.
 *
 * @author Philip Warner
 */
public class DomainDefinition
        implements Parcelable, Serializable {

    /** {@link Parcelable}. */
    public static final Creator<DomainDefinition> CREATOR =
            new Creator<DomainDefinition>() {
                @Override
                public DomainDefinition createFromParcel(@NonNull final Parcel source) {
                    return new DomainDefinition(source);
                }

                @Override
                public DomainDefinition[] newArray(final int size) {
                    return new DomainDefinition[size];
                }
            };

    private static final long serialVersionUID = 3405337543505961871L;

    /** not null constraint string. */
    private static final String NOT_NULL = "NOT NULL";
    @NonNull
    public final String name;
    @NonNull
    private final String mType;
    @NonNull
    private final List<String> mConstraints = new ArrayList<>();
    /** Holds a 'REFERENCES' clause (if any). */
    @Nullable
    private String mReferences;

    private boolean isPrimaryKey;

    /**
     * Create a PRIMARY KEY column.
     *
     * @param name column name
     */
    public DomainDefinition(@NonNull final String name) {
        this.name = name;
        mType = ColumnInfo.TYPE_INTEGER;
        isPrimaryKey = true;
        mConstraints.add(NOT_NULL);
    }

    /**
     * Simple column without constraints.
     *
     * @param name column name
     * @param type column type (text, int, float, ...)
     */
    public DomainDefinition(@NonNull final String name,
                            @NonNull final String type) {
        this.name = name;
        mType = type;
    }

    /**
     * Simple column with optional NOT NULL constraint.
     *
     * @param name    column name
     * @param type    column type (text, int, float, ...)
     * @param notNull <tt>true</tt> if this column should never be null
     */
    public DomainDefinition(@NonNull final String name,
                            @NonNull final String type,
                            final boolean notNull) {
        this.name = name;
        mType = type;
        if (notNull) {
            mConstraints.add(NOT_NULL);
        }
    }

    /**
     * @param name        column name
     * @param type        column type (text, int, float, ...)
     * @param notNull     <tt>true</tt> if this column should never be null
     * @param constraints (optional) a list of generic constraints
     */
    public DomainDefinition(@NonNull final String name,
                            @NonNull final String type,
                            final boolean notNull,
                            @Nullable final String... constraints) {
        this.name = name;
        mType = type;
        if (notNull) {
            mConstraints.add(NOT_NULL);
        }
        if ((constraints != null) && (constraints.length > 0)) {
            mConstraints.addAll(Arrays.asList(constraints));
        }
    }

    private DomainDefinition(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        name = in.readString();
        //noinspection ConstantConditions
        mType = in.readString();
        in.readList(mConstraints, getClass().getClassLoader());
    }

    /**
     * add a numerical default constraint.
     *
     * @param value to use as default
     *
     * @return this for chaining.
     */
    @NonNull
    public DomainDefinition setDefault(final long value) {
        mConstraints.add("DEFAULT " + value);
        return this;
    }

    /**
     * add a string default constraint.
     *
     * @param value to add (a string default must include the quotes!)
     *
     * @return this for chaining.
     */
    @NonNull
    public DomainDefinition setDefault(@NonNull final String value) {
        mConstraints.add("DEFAULT " + value);
        return this;
    }

    /**
     * add a string default '' constraint.
     *
     * @return this for chaining.
     */
    @NonNull
    public DomainDefinition setDefaultEmptyString() {
        mConstraints.add("DEFAULT ''");
        return this;
    }

    /**
     * add a generic constraint.
     *
     * @param constraint to add
     *
     * @return this for chaining.
     */
    @SuppressWarnings("unused")
    @NonNull
    public DomainDefinition addConstraint(@NonNull final String constraint) {
        mConstraints.add(constraint);
        return this;
    }

    /**
     * Defines a foreign key for this column.
     * Only simple, primary key references supported for now.
     * <p>
     * No validation is done on the arguments.
     * <p>
     *
     * @param table   to reference
     * @param actions 'on delete...' etc...
     *
     * @return this for chaining.
     */
    @NonNull
    public DomainDefinition references(@NonNull final TableDefinition table,
                                       @NonNull final String actions) {
        mReferences = table.getName() + ' ' + actions;
        return this;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(name);
        dest.writeString(mType);
        dest.writeList(mConstraints);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @return <tt>true</tt> if this domain is a 'text' type.
     */
    public boolean isText() {
        return ColumnInfo.TYPE_TEXT.equalsIgnoreCase(mType);
    }

    boolean isPrimaryKey() {
        return isPrimaryKey;
    }

    /**
     * useful for using the DomainDefinition in place of a domain name.
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
        StringBuilder sql = new StringBuilder(name + ' ' + mType);
        if (isPrimaryKey) {
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
}
