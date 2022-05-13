/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;

public abstract class BookFields {

    /**
     * All fields (domains) that are optionally shown on the Book level,
     * in an <strong>ordered</strong> map.
     */
    private final Map<String, PBoolean> fields = new LinkedHashMap<>();

    /**
     * Constructor.
     */
    BookFields() {
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param bookFields       to copy from
     */
    BookFields(final boolean isPersistent,
               @NonNull final StylePersistenceLayer persistenceLayer,
               @NonNull final BookFields bookFields) {
        for (final PBoolean field : bookFields.fields.values()) {
            final PBoolean clonedField = new PBoolean(isPersistent, persistenceLayer, field);
            fields.put(clonedField.getKey(), clonedField);
        }
    }

    void addField(@NonNull final PBoolean field) {
        fields.put(field.getKey(), field);
    }

    boolean isInUse(@NonNull final String key) {
        //noinspection ConstantConditions
        return fields.get(key).isTrue();
    }

    /**
     * Check if the given book-detail field should be displayed.
     *
     * @param global the <strong>GLOBAL</strong> preferences
     * @param key    to check
     *
     * @return {@code true} if in use
     */
    public boolean isShowField(@NonNull final SharedPreferences global,
                               @Key @NonNull final String key) {

        // Disabled in the Global style overrules the local style
        if (!global.getBoolean(key, true)) {
            return false;
        }

        if (fields.containsKey(key)) {
            return getValue(key);
        }
        return false;
    }

    public boolean getValue(@Key @NonNull final String key) {
        return Objects.requireNonNull(fields.get(key), key)
                      .getValue();
    }

    /**
     * Used by built-in styles only. Set by user via preferences screen.
     *
     * @param key  for the field
     * @param show value to set
     */
    public void setValue(@Key @NonNull final String key,
                         final boolean show) {
        Objects.requireNonNull(fields.get(key), key)
               .set(show);
    }

    /**
     * Get a flat list with accumulated preferences for this object and it's children.<br>
     * Provides low-level access to all preferences.<br>
     * This should only be called for export/import.
     *
     * @return list
     */
    @NonNull
    public Collection<PPref<?>> getRawPreferences() {
        return new ArrayList<>(fields.values());
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BookFields that = (BookFields) o;
        return Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields);
    }

    @Override
    @NonNull
    public String toString() {
        return "fields=" + fields;
    }

    @StringDef({ListScreenBookFields.PK_COVERS,
                ListScreenBookFields.PK_AUTHOR,
                ListScreenBookFields.PK_PUBLISHER,
                ListScreenBookFields.PK_PUB_DATE,
                ListScreenBookFields.PK_ISBN,
                ListScreenBookFields.PK_FORMAT,
                ListScreenBookFields.PK_LOCATION,
                ListScreenBookFields.PK_RATING,
                ListScreenBookFields.PK_BOOKSHELVES,
//                DetailScreenBookFields.PK_COVER[0],
//                DetailScreenBookFields.PK_COVER[1]
    })
    private @interface Key {

    }
}
