/*
 * @Copyright 2020 HardBackNutter
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
    final Map<String, PBoolean> mFields = new LinkedHashMap<>();

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
        for (final PBoolean field : bookFields.mFields.values()) {
            final PBoolean clonedField = new PBoolean(isPersistent, persistenceLayer, field);
            mFields.put(clonedField.getKey(), clonedField);
        }
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
                               @ListScreenBookFields.Key @NonNull final String key) {

        // Disabled in the Global style overrules the local style
        if (!global.getBoolean(key, true)) {
            return false;
        }

        if (mFields.containsKey(key)) {
            final PBoolean value = mFields.get(key);
            return value != null && value.isTrue();
        }
        return false;
    }

    /**
     * Used by built-in styles only. Set by user via preferences screen.
     *
     * @param key  for the field
     * @param show value to set
     */
    void setShowField(@ListScreenBookFields.Key @NonNull final String key,
                      final boolean show) {
        //noinspection ConstantConditions
        mFields.get(key).set(show);
    }

    void addToMap(@NonNull final Map<String, PPref> map) {
        for (final PBoolean field : mFields.values()) {
            map.put(field.getKey(), field);
        }
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
        return Objects.equals(mFields, that.mFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFields);
    }

    @Override
    @NonNull
    public String toString() {
        return "BookFields{"
               + ", mFields=" + mFields
               + '}';
    }

}
