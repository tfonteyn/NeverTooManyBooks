package com.eleybourn.bookcatalogue.database.definitions;

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

    public DomainDefinition(@NonNull final String name,
                            @NonNull final String type) {
        this(name,type, "", "");
    }
    public DomainDefinition(@NonNull final String name,
                            @NonNull final String type,
                            @NonNull final String extra) {
        this(name,type, "", extra);
    }

    /**
     *  @param name         column name
     * @param type          column type (text, int, float, ...)
     * @param constraint    (optional but non null) for example "not null"
     * @param extra         (optional, but non null) for example "default 0'
     */
    public DomainDefinition(@NonNull final String name,
                            @NonNull final String type,
                            @NonNull final String constraint,
                            @NonNull final String extra) {
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
        if (withConstraints) {
            s += " " + constraint;
        }
        return s;
    }
}
