package com.eleybourn.bookcatalogue.utils;

/**
 * read {@link FieldUsages}
 */
public class FieldUsage {
    public final String fieldName;
    public final int stringId;
    public FieldUsages.Usages usage;
    public boolean selected;
    public final boolean canAppend;

    public FieldUsage(String name, int id, FieldUsages.Usages usage, boolean canAppend) {
        this.fieldName = name;
        this.stringId = id;
        this.usage = usage;
        this.selected = true;
        this.canAppend = canAppend;
    }
}
