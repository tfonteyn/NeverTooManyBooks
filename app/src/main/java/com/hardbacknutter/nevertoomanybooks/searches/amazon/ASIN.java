/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches.amazon;

import androidx.annotation.NonNull;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

/**
 * ASIN stands for Amazon Standard Identification Number.
 * Every product on Amazon has its own ASIN, a unique code used to identify it.
 * For books, the ASIN is the same as the ISBN-10 number, but for all other products a new ASIN
 * is created when the item is uploaded to their catalogue.
 */
public class ASIN {

    /**
     * Validate an Amazon ASIN.
     */
    public static boolean isValidAsin(@NonNull String asin) {

        // Book ASIN codes are always 10 characters.
        if (asin.length() != 10) {
            return false;
        }

        // An Book ASIN is basically an ISBN-10.
        if (ISBN.isValid(asin)) {
            return true;
        }

        // TODO: should we even check this ?
        boolean foundAlpha = false;
        asin = asin.toUpperCase(Locale.ENGLISH).trim();
        for (int i = 0; i < asin.length(); i++) {
            int pos = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(asin.charAt(i));
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
