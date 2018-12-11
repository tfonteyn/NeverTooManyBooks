package com.eleybourn.bookcatalogue.database.definitions;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.io.Serializable;

/**
 * Class to store domain name and definition.
 *
 * @author Philip Warner
 */
public class DomainDefinition implements Parcelable, Serializable {
    public static final Creator<DomainDefinition> CREATOR = new Creator<DomainDefinition>() {
        @Override
        public DomainDefinition createFromParcel(Parcel in) {
            return new DomainDefinition(in);
        }

        @Override
        public DomainDefinition[] newArray(int size) {
            return new DomainDefinition[size];
        }
    };
    @NonNull
    public final String name;
    @NonNull
    private final String type;
    @NonNull
    private final String extra;
    @NonNull
    private final String constraint;

    /**
     * Create a PRIMARY KEY
     *
     * @param name    column name
     */
    public DomainDefinition(final @NonNull String name) {
        this.name = name;
        this.type = TableInfo.TYPE_INTEGER;
        this.extra = "PRIMARY KEY autoincrement";
        this.constraint = "NOT NULL";
    }

    /**
     * @param name    column name
     * @param type    column type (text, int, float, ...)
     * @param notNull can this column be null
     */
    public DomainDefinition(final @NonNull String name,
                            final @NonNull String type,
                            final boolean notNull) {
        this.name = name;
        this.type = type;
        this.constraint = (notNull ? "NOT NULL" : "");
        this.extra = "";
    }

    /**
     * @param name    column name
     * @param type    column type (text, int, float, ...)
     * @param notNull can this column be null
     * @param extra   (optional, but non null) for example "default 0'
     */
    public DomainDefinition(final @NonNull String name,
                            final @NonNull String type,
                            final boolean notNull,
                            final @NonNull String extra) {
        this.name = name;
        this.type = type;
        this.extra = extra;
        this.constraint = (notNull ? "NOT NULL" : "");
    }

    private DomainDefinition(Parcel in) {
        name = in.readString();
        type = in.readString();
        extra = in.readString();
        constraint = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(type);
        dest.writeString(extra);
        dest.writeString(constraint);
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
    public String def(boolean withConstraints) {
        String s = name + " " + type + " " + extra;
        if (withConstraints) {
            s += " " + constraint;
        }
        return s;
    }
}
