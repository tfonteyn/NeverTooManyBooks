/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.goodreads.api.ShelvesListApiHandler;

public class GoodreadsShelf {

    /** Virtual shelf names used in XML request/responses. */
    public static final String VIRTUAL_CURRENTLY_READING = "currently-reading";
    /** Virtual Goodreads shelf name. */
    public static final String VIRTUAL_READ = "read";
    /** Virtual Goodreads shelf name. */
    public static final String VIRTUAL_TO_READ = "to-read";

    /** The default shelf at Goodreads. */
    static final String DEFAULT_SHELF = "Default";

    /** Bundle with shelf related entries. */
    @NonNull
    private final Bundle mBundle;

    public GoodreadsShelf(@NonNull final Bundle bundle) {
        mBundle = bundle;
    }

    /**
     * Create canonical representation based on the best guess as to the Goodreads rules.
     *
     * @param locale to use
     * @param name   to bless
     *
     * @return blessed name
     */
    public static String canonicalizeName(@NonNull final Locale locale,
                                          @NonNull final String name) {
        final StringBuilder canonical = new StringBuilder();
        final String lcName = name.toLowerCase(locale);
        for (int i = 0; i < lcName.length(); i++) {
            final char c = lcName.charAt(i);
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
        return Objects.requireNonNull(mBundle.getString(ShelvesListApiHandler.ShelvesField.NAME));
    }

    boolean isExclusive() {
        return mBundle.getBoolean(ShelvesListApiHandler.ShelvesField.EXCLUSIVE);
    }
}
