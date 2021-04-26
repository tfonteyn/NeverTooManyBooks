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
package com.hardbacknutter.nevertoomanybooks.searchengines.amazon;

import androidx.annotation.NonNull;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

/**
 * ASIN stands for Amazon Standard Identification Number.
 * Every product on Amazon has its own ASIN, a unique code used to identify it.
 * For books, the ASIN is the same as the ISBN-10 number, but for all other products a new ASIN
 * is created when the item is uploaded to their catalogue.
 *
 * <a href="https://www.amazon.com/gp/seller/asin-upc-isbn-info.html">
 * https://www.amazon.com/gp/seller/asin-upc-isbn-info.html</a>
 */
public final class ASIN {

    private ASIN() {
    }

    /**
     * Validate an Amazon ASIN.
     */
    public static boolean isValidAsin(@NonNull String asin) {

        // ASIN codes are always 10 characters.
        if (asin.length() != 10) {
            return false;
        }

        // An Book ASIN is basically an ISBN-10.
        if (ISBN.isValidIsbn(asin)) {
            return true;
        }

        boolean foundAlpha = false;
        asin = asin.trim().toUpperCase(Locale.ENGLISH);
        for (int i = 0; i < asin.length(); i++) {
            final int pos = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(asin.charAt(i));
            // Make sure it's a valid char
            if (pos == -1) {
                return false;
            }
            // See if we got a non-numeric
            if (pos >= 10) {
                foundAlpha = true;
            }
        }
        return foundAlpha;
    }
}
