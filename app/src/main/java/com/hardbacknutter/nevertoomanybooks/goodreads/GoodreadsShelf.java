/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.goodreads.api.ShelvesListApiHandler;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

public class GoodreadsShelf {

    /** Virtual shelf names used in XML request/responses. */
    public static final String VIRTUAL_CURRENTLY_READING = "currently-reading";
    public static final String VIRTUAL_READ = "read";
    public static final String VIRTUAL_TO_READ = "to-read";

    /** Bundle with shelf related entries. */
    @NonNull
    private final Bundle mBundle;

    public GoodreadsShelf(@NonNull final Bundle bundle) {
        mBundle = bundle;
    }

    /**
     * Create canonical representation based on the best guess as to the Goodreads rules.
     *
     * @param name to bless
     *
     * @return blessed name
     */
    public static String canonicalizeName(@NonNull final String name) {

        StringBuilder canonical = new StringBuilder();
        String lcName = name.toLowerCase(LocaleUtils.getPreferredLocale());
        for (int i = 0; i < lcName.length(); i++) {
            char c = lcName.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                canonical.append(c);
            } else {
                canonical.append('-');
            }
        }
        return canonical.toString();
    }

    @NonNull
    public String getName() {
        //noinspection ConstantConditions
        return mBundle.getString(ShelvesListApiHandler.ShelvesField.NAME);
    }

    boolean isExclusive() {
        return mBundle.getBoolean(ShelvesListApiHandler.ShelvesField.EXCLUSIVE);
    }
}
