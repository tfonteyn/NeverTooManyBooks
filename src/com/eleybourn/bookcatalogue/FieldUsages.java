/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue;

import android.support.annotation.NonNull;

import java.util.LinkedHashMap;

/**
 * Class to manage a collection of fields and the rules for importing them.
 * Inherits from {@link LinkedHashMap} to guarantee iteration order.
 *
 * FIXME: Android Studio 3.1.4 failed to compile this, had to make a standalone
 * class instead of internal to {@link UpdateFromInternet}
 *
 * @author Philip Warner
 */
public class FieldUsages extends LinkedHashMap<String,FieldUsages.FieldUsage> {
    private static final long serialVersionUID = 1L;

    public enum Usages { COPY_IF_BLANK, ADD_EXTRA, OVERWRITE }

    @SuppressWarnings("UnusedReturnValue")
    public FieldUsage put(@NonNull final FieldUsage usage) {
        this.put(usage.fieldName, usage);
        return usage;
    }

    public static class FieldUsage {
        public final String fieldName;
        public final int stringId;
        public Usages usage;
        public boolean selected;
        public final boolean canAppend;

        public FieldUsage(@NonNull final String name, final int id, @NonNull final Usages usage, final boolean canAppend) {
            this.fieldName = name;
            this.stringId = id;
            this.usage = usage;
            this.selected = true;
            this.canAppend = canAppend;
        }
    }
}