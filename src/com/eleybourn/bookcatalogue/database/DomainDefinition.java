package com.eleybourn.bookcatalogue.database;

import android.support.annotation.NonNull;

/**
 * Class to store domain name and definition.
 *
 * @author Philip Warner
 */
public class DomainDefinition {
    public final String name;

    private final String type;
    private final String extra;

    private final String constraint;

    DomainDefinition(@NonNull final String name, @NonNull final String type) {
        this(name,type,"","");
    }
    DomainDefinition(@NonNull final String name, @NonNull final String type, @NonNull final String extra) {
        this(name,type,extra,"");
    }

    DomainDefinition(@NonNull final String name, @NonNull final String type, @NonNull final String extra, @NonNull final String constraint) {
        this.name = name;
        this.type = type;
        this.extra = extra;
        this.constraint = constraint;
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

    /** Get the SQL used to define this domain */
    @NonNull
    public String getDefinition(boolean withConstraints) {
        String s = name + " " + type + " " + extra;
        if (withConstraints)
            s += " " + constraint;
        return s;
    }
}
