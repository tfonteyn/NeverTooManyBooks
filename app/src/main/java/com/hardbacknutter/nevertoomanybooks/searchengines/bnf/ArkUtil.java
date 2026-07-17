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

package com.hardbacknutter.nevertoomanybooks.searchengines.bnf;

import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * ARK (Archival Resource Key) related helper methods. Specific to the use at the BnF.fr
 *
 * @see <a href="https://arks.org/">arks.org</a>
 */
public final class ArkUtil {
    /**
     * A raw ark number must be 8 digits exactly.
     */
    public static final Pattern ARK_DIGITS_PATTERN = Pattern.compile("\\d{8}");

    private ArkUtil() {
    }

    /**
     * Transform the given raw bnf identifier value to a "cbXXXXXXXXc" format
     * suitable for creating urls.
     *
     * @param sid for bnf
     *
     * @return sid with 'cb' prefix and checksum digit suffix
     */
    @Nullable
    public static String createCBNumberString(@Nullable final CharSequence sid) {
        // Sanity check, it must be 8 digits.
        if (sid == null || !ARK_DIGITS_PATTERN.matcher(sid).matches()) {
            return null;
        }

        // Modulo 11.
        int sum = 0;
        int weight = 8;

        for (int i = 0; i < 8; i++) {
            final int digit = Character.getNumericValue(sid.charAt(i));
            sum += digit * weight;
            weight--;
        }

        final int remainder = sum % 11;

        // BnF's custom alphanumeric check character sequence
        final String bnfAlphabet = "0123456789xbcdefghjkmnpqrstvwxyz";
        final char checkChar = bnfAlphabet.charAt(remainder);

        //noinspection StringConcatenationMissingWhitespace
        return "cb" + sid + checkChar;
    }
}
