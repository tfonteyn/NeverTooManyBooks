package com.eleybourn.bookcatalogue.utils;

import com.eleybourn.bookcatalogue.UpdateFromInternet;

import java.util.LinkedHashMap;

/**
 * Class to manage a collection of fields and the rules for importing them.
 * Inherits from LinkedHashMap to guarantee iteration order.
 *
 * FIXME: Android Studio 3.1.4 failed to compile this, had to make {@link FieldUsages}+ {@link FieldUsage} standalone classes instead of internal to {@link UpdateFromInternet}
 * @author Philip Warner
 */
public class FieldUsages extends LinkedHashMap<String,FieldUsage> {
    private static final long serialVersionUID = 1L;

    public enum Usages { COPY_IF_BLANK, ADD_EXTRA, OVERWRITE };

    public FieldUsage put(FieldUsage usage) {
        this.put(usage.fieldName, usage);
        return usage;
    }

}