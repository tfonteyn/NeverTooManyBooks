package com.eleybourn.bookcatalogue.database.definitions;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Class to store domain name and definition.
 *
 * TOMF FIXME: add support for 'references' clause (and/or in TableDefinition for table constraints to be added)
 *
 * @author Philip Warner
 */
public class DomainDefinition implements Parcelable, Serializable {

    public static final Creator<DomainDefinition> CREATOR = new Creator<DomainDefinition>() {
        @Override
        public DomainDefinition createFromParcel(@NonNull final Parcel source) {
            return new DomainDefinition(source);
        }

        @Override
        public DomainDefinition[] newArray(final int size) {
            return new DomainDefinition[size];
        }
    };
    private static final long serialVersionUID = 3635761831854862723L;
    @NonNull
    public final String name;
    @NonNull
    private final String type;
    @NonNull
    private final List<String> constraints = new ArrayList<>();

    /**
     * Create a PRIMARY KEY column
     *
     * @param name column name
     */
    public DomainDefinition(@NonNull final String name) {
        this.name = name;
        // a special case; the constraints are added to the type
        // as they should *always* be used even when we deliberately do not
        // apply constraints at creation time.
        this.type = TableInfo.TYPE_INTEGER + " PRIMARY KEY autoincrement NOT NULL";
    }

    /**
     * Simple column without constraints
     *
     * @param name    column name
     * @param type    column type (text, int, float, ...)
     */
    public DomainDefinition(@NonNull final String name,
                            @NonNull final String type) {
        this.name = name;
        this.type = type;
    }

    /**
     * Simple column with optional NOT NULL constraint
     *
     * @param name    column name
     * @param type    column type (text, int, float, ...)
     * @param notNull true if this column should never be null
     */
    public DomainDefinition(@NonNull final String name,
                            @NonNull final String type,
                            final boolean notNull) {
        this.name = name;
        this.type = type;
        if (notNull) {
            this.constraints.add("NOT NULL");
        }
    }

    /**
     * @param name        column name
     * @param type        column type (text, int, float, ...)
     * @param notNull     true if this column should never be null
     * @param constraints (optional) a list of generic constraints
     */
    public DomainDefinition(@NonNull final String name,
                            @NonNull final String type,
                            final boolean notNull,
                            @Nullable final String... constraints) {
        this.name = name;
        this.type = type;
        if (notNull) {
            this.constraints.add("NOT NULL");
        }
        if ((constraints != null) && (constraints.length > 0)) {
            this.constraints.addAll(Arrays.asList(constraints));
        }
    }

    private DomainDefinition(@NonNull final Parcel in) {
        name = in.readString();
        type = in.readString();
        in.readList(constraints, getClass().getClassLoader());
    }

    /**
     * add a numerical default constraint
     *
     * @param value to use as default
     *
     * @return this for chaining.
     */
    @NonNull
    public DomainDefinition setDefault(@NonNull final int value) {
        constraints.add("DEFAULT " + value);
        return this;
    }

    /**
     * add a string default constraint
     *
     * @param value to add (a string default must include the quotes!)
     *
     * @return this for chaining.
     */
    @NonNull
    public DomainDefinition setDefault(@NonNull final String value) {
        constraints.add("DEFAULT " + value);
        return this;
    }

    /**
     * add a generic constraint
     *
     * @param constraint to add
     *
     * @return this for chaining.
     */
    @NonNull
    public DomainDefinition addConstraint(@NonNull final String constraint) {
        constraints.add(constraint);
        return this;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(type);
        dest.writeList(constraints);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    public boolean isText() {
        return TableInfo.TYPE_TEXT.equals(type.toLowerCase());
    }

    /** useful for using the DomainDefinition in place of a domain name */
    @Override
    @NonNull
    public String toString() {
        return name;
    }

    /**
     * Get the SQL used to define this domain
     */
    @NonNull
    public String def() {
        return def(true);
    }

    /**
     * Get the SQL used to define this domain
     *
     * Are you sure you don't want to use {@link #def()} ?
     *
     * @param withConstraints when false, no constraints are applied
     */
    @NonNull
    String def(final boolean withConstraints) {
        StringBuilder sql = new StringBuilder(name + ' ' + type);

        if (withConstraints && !constraints.isEmpty()) {
            for (String cs : constraints) {
                sql.append(' ').append(cs);
            }
        }
        return sql.toString();
    }
}
