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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;

public abstract class BookFields {

    /**
     * All fields (domains) that are optionally shown on the Book level,
     * in an <strong>ordered</strong> map.
     */
    final Map<String, PBoolean> mFields = new LinkedHashMap<>();

    /**
     * Check if the given book-detail field should be displayed.
     *
     * @param context     Current context
     * @param preferences the <strong>GLOBAL</strong> preferences
     * @param key         to check
     *
     * @return {@code true} if in use
     */
    public boolean isShowField(@NonNull final Context context,
                               @NonNull final SharedPreferences preferences,
                               @ListScreenBookFields.Key @NonNull final String key) {

        // Disabled in the Global style overrules the local style
        if (!preferences.getBoolean(key, true)) {
            return false;
        }

        if (mFields.containsKey(key)) {
            final PBoolean value = mFields.get(key);
            return value != null && value.isTrue(context);
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

    /**
     * Set the <strong>value</strong> from the Parcel.
     *
     * @param in parcel to read from
     */
    public void set(@NonNull final Parcel in) {
        // the collection is ordered, so we don't need the keys.
        for (final PBoolean field : mFields.values()) {
            field.set(in);
        }
    }

    /**
     * Write the <strong>value</strong> to the Parcel.
     *
     * @param dest parcel to write to
     */
    public void writeToParcel(@NonNull final Parcel dest) {
        // the collection is ordered, so we don't write the keys.
        for (final PBoolean field : mFields.values()) {
            field.writeToParcel(dest);
        }
    }


    @Override
    @NonNull
    public String toString() {
        return "DetailScreenBookFields{"
               + "mFields=" + mFields
               + '}';
    }
}
