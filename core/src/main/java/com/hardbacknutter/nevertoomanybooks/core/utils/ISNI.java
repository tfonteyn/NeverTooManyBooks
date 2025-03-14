/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.core.utils;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

/**
 * International Standard Name Identifier.
 * <pre>
 * ISNI can be used to disambiguate named entities that might otherwise be confused,
 * and links the data about names that are collected and used in all sectors
 * of the media industries.
 * </pre>
 *
 * @see <a href="https://en.wikipedia.org/wiki/International_Standard_Name_Identifier">
 *         Wikipedia</a>
 */
public class ISNI {

    /** Remove '-' and space chars. */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[ -]");

    /** Leading {@code 0}'s <strong>MUST</strong> be present. */
    private static final String ISNI_URL = "https://isni.org/isni/%s";

    private final String isni;
    private final boolean valid;

    public ISNI(@NonNull final CharSequence isni) {
        final String str = WHITESPACE_PATTERN.matcher(isni).replaceAll("");

        if (str.length() < 2 || str.length() > 16) {
            this.isni = str;
            valid = false;
            return;
        }

        final String digits = str.substring(0, str.length() - 1);
        final String checksum = str.substring(str.length() - 1);

        valid = generateCheckDigit(digits).equals(checksum);
        this.isni = ("0000000000000000" + str).substring(str.length());
    }

    @NonNull
    public String getIsni() {
        return isni;
    }

    public boolean isValid() {
        return valid;
    }

    /**
     * Generates check digit as per ISO 7064 11,2.
     *
     * @param baseDigits the 15 digits without the checksum.
     *                   Missing leading digits are presumed to be {@code 0}.
     *
     * @return the checksum digit {@code 0..9 or X}
     */
    @NonNull
    public static String generateCheckDigit(@NonNull final CharSequence baseDigits) {
        final String cleanStr = WHITESPACE_PATTERN.matcher(baseDigits).replaceAll("");

        int total = 0;
        for (int i = 0; i < cleanStr.length(); i++) {
            final int digit = Character.getNumericValue(cleanStr.charAt(i));
            total = (total + digit) * 2;
        }
        final int remainder = total % 11;
        final int result = (12 - remainder) % 11;
        return result == 10 ? "X" : String.valueOf(result);
    }
}
