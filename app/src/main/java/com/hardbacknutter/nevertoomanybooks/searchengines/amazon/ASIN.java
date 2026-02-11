/*
 * @Copyright 2018-2026 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;

/**
 * ASIN stands for Amazon Standard Identification Number.
 * <p>
 * It's a base 36 number (the letters of the alphabet plus the 10 digits).
 * <p>
 * Every product on Amazon has its own ASIN, a unique code used to identify it.
 * For books, the ASIN is the same as the ISBN-10 number, and NOT simply 978 stripped
 * off as the inventor tells in her article below.
 * <p>
 * For all other products a new ASIN is created when the item is uploaded to their catalogue.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Amazon_Standard_Identification_Number">ASIN</a>
 * @see <a href="https://inventlikeanowner.com/blog/the-story-behind-asins-amazon-standard-identification-numbers/">
 *         the-story-behind-asins</a>
 */
public final class ASIN {

    /** ASIN codes are always 10 characters. */
    private static final int ASIN_LEN = 10;
    /** The first 10 characters are numeric; this fact is used during validity checks. */
    private static final String VALID_CHARS = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private final String code;
    private final boolean valid;

    public ASIN(@NonNull final String code) {
        this.code = SearchEngineUtils.cleanText(code).toUpperCase(Locale.ENGLISH);
        valid = isValid(this.code);
    }

    public boolean isValid() {
        return valid;
    }

    public String asText() {
        return code;
    }

    /**
     * Validate an Amazon ASIN.
     *
     * @param asin to validate
     *
     * @return validity
     */
    static boolean isValid(@NonNull final String asin) {

        if (asin.length() != ASIN_LEN) {
            return false;
        }

        // Historically, a Book ASIN is just an ISBN-10.
        if (new ISBN(asin, true).isValid(true)) {
            return true;
        }

        // But these days, it can also be a Kindle book, self-published without ISBN,...
        // In this case, it will/must have at least one letter (A-Z).
        // Typically it will start with a 'B' but no need to be that strict.
        boolean foundAlpha = false;
        final String ucAsin = asin.strip().toUpperCase(Locale.ENGLISH);
        for (int i = 0; i < ucAsin.length(); i++) {
            final int pos = VALID_CHARS.indexOf(ucAsin.charAt(i));
            // Make sure it's a valid char
            if (pos == -1) {
                return false;
            }
            // Must have at least one non-numeric character
            if (pos >= ASIN_LEN) {
                foundAlpha = true;
            }
        }
        return foundAlpha;
    }
}
