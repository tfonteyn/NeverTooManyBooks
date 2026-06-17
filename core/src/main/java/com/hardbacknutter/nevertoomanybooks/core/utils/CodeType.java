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

package com.hardbacknutter.nevertoomanybooks.core.utils;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public enum CodeType {
    /** None of the below. */
    Invalid {
        @Override
        @IntRange(from = 0, to = 10)
        public int checksum(@NonNull final List<Integer> digits)
                throws NumberFormatException {
            throw new NumberFormatException(ERROR_NOT_APPLICABLE + name());
        }
    },
    /** The original ISBN. 10 digits. */
    Isbn10 {
        /**
         * Calculate the check-digit (checksum) for the given digits.
         *
         * @param digits list with the digits, either 10 or 9
         *
         * @return the check digit.
         *
         * @throws NumberFormatException on failure
         */
        @Override
        @IntRange(from = 0, to = 10)
        public int checksum(@NonNull final List<Integer> digits)
                throws NumberFormatException {
            final int len = digits.size();
            if (len < 9 || len > 10) {
                throw new NumberFormatException(ERROR_WRONG_SIZE + len);
            }
            int sum = 0;
            // 1. Take the first 9 digits of the 10-digit ISBN.
            // 2. Multiply each number in turn, from left to right by a number.
            //    The first, leftmost, digit of the nine is multiplied by 10,
            //    then working from left to right, each successive digit is
            //    multiplied by one less than the one before.
            //    So the second digit is multiplied by 9, the third by 8,
            //    and so on to the ninth which is multiplied by 2.
            //
            // 3. Add all of the 9 products.
            int multiplier = 10;
            for (int dig = 1; dig < 10; dig++) {
                sum += digits.get(dig - 1) * multiplier;
                multiplier--;
            }

            // 4. Do a modulo 11 division on the sum.
            final int modulo = sum % 11;
            if (modulo == 0) {
                return 0;
            } else {
                return 11 - modulo;
            }
        }
    },
    /** ISBN-13 is a subtype of EAN-13. 13 digits. */
    Isbn13 {
        @Override
        @IntRange(from = 0, to = 10)
        public int checksum(@NonNull final List<Integer> digits)
                throws NumberFormatException {
            return Ean13.checksum(digits);
        }
    },
    /** Generic 13 digit barcode. */
    Ean13 {
        /**
         * Calculate the check-digit (checksum) for the given digits.
         * This calculation is valid for EAN-13 and subtypes
         *
         * @param digits list with the digits, either 13 or 12
         *
         * @return the check digit.
         *
         * @throws NumberFormatException on failure
         */
        @Override
        @IntRange(from = 0, to = 10)
        public int checksum(@NonNull final List<Integer> digits)
                throws NumberFormatException {
            final int len = digits.size();
            if (len < 12 || len > 13) {
                throw new NumberFormatException(ERROR_WRONG_SIZE + len);
            }
            int sum = 0;
            // 1. Take the first 12 digits of the 13-digit EAN
            // 2. Multiply each number in turn, from left to right by a number.
            //    The first, leftmost, digit is multiplied by 1, the second by 3,
            //    the third by 1 again, the fourth by 3 again, and so on to
            //    the eleventh which is multiplied by 1 and the twelfth by 3.
            //
            // 3. Add all of the 12 products.

            for (int dig = 1; dig < 13; dig += 2) {
                sum += digits.get(dig - 1);
            }
            for (int dig = 2; dig < 13; dig += 2) {
                sum += digits.get(dig - 1) * 3;
            }

            // 4. Do a modulo 10 division on the sum.
            final int modulo = sum % 10;

            if (modulo == 0) {
                // If it's a zero, then the check digit is zero.
                return 0;
            } else {
                // Otherwise subtract the remainder from 10.
                return 10 - modulo;
            }
        }
    },
    /**
     * Generic product barcode. Minimum 12 digits but can be any length.
     * May be automatically converted in {@link ISBN} when it's {@link #Isbn10} compatible.
     */
    UpcA {
        /**
         * Calculate the check-digit (checksum) for the given digits.
         * This calculation is valid for UPC_A-12 only
         *
         * @param digits list with the digits, either 12 or 11
         *
         * @return the check digit.
         *
         * @throws NumberFormatException on failure
         */
        @Override
        @IntRange(from = 0, to = 10)
        public int checksum(@NonNull final List<Integer> digits)
                throws NumberFormatException {
            final int len = digits.size();
            if (len < 11 || len > 12) {
                throw new NumberFormatException(ERROR_WRONG_SIZE + len);
            }
            int sum = 0;
            // 1. Take the first 11 digits of the 12-digit UPC_A
            // 2. Sum the digits at odd-numbered positions (first, third, fifth,..., eleventh).
            // Multiply the result by 3.
            for (int dig = 1; dig < 12; dig += 2) {
                sum += digits.get(dig - 1) * 3;
            }
            // 3. Add the digit sum at even-numbered positions (second, fourth, sixth,..., tenth)
            // to the result.
            for (int dig = 2; dig < 12; dig += 2) {
                sum += digits.get(dig - 1);
            }

            // 4. Do a modulo 10 division on the sum.
            final int modulo = sum % 10;

            if (modulo == 0) {
                // If it's a zero, then the check digit is zero.
                return 0;
            } else {
                // Otherwise subtract the remainder from 10.
                return 10 - modulo;
            }
        }
    },
    /**
     * The precursor of {@link #Isbn10}.
     * Consists of a 9 digits number optionally followed by 3 digit price (in the US).
     * Can be converted to {@link #Isbn10} by taking the first 9 digits,
     * and prefix them with a {@code 0}.
     * <p>
     * Internal use only, as {@link ISBN} will always convert these to {@link #Isbn10}.
     */
    Sbn {
        @Override
        @IntRange(from = 0, to = 10)
        public int checksum(@NonNull final List<Integer> digits)
                throws NumberFormatException {
            throw new NumberFormatException(ERROR_NOT_APPLICABLE + name());
        }
    },
    /** Periodicals. 8 digits. */
    Issn8 {
        /**
         * Calculate the check-digit (checksum) for the given digits.
         *
         * @param digits list with the digits, either 8 or 7
         *
         * @return the check digit.
         *
         * @throws NumberFormatException on failure
         */
        @Override
        @IntRange(from = 0, to = 10)
        public int checksum(@NonNull final List<Integer> digits)
                throws NumberFormatException {
            final int len = digits.size();
            if (len < 7 || len > 8) {
                throw new NumberFormatException(ERROR_WRONG_SIZE + len);
            }
            int sum = 0;
            // 1. Take the first 7 digits of the 8-digit ISSN.
            // 2. Multiply each number in turn, from left to right by a number.
            //    The first, leftmost, digit of the seven is multiplied by 8,
            //    then working from left to right, each successive digit is
            //    multiplied by one less than the one before.
            //    So the second digit is multiplied by 7, the third by 6,
            //    and so on to the seventh which is multiplied by 2.
            //
            // 3. Add all of the 7 products.
            int multiplier = 8;
            for (int dig = 1; dig < 8; dig++) {
                sum += digits.get(dig - 1) * multiplier;
                multiplier--;
            }

            // 4. Do a modulo 11 division on the sum.
            final int modulo = sum % 11;
            if (modulo == 0) {
                return 0;
            } else {
                return 11 - modulo;
            }
        }
    },
    /** Periodicals. subtype of EAN-13. 13 digits. */
    Issn13 {
        @Override
        @IntRange(from = 0, to = 10)
        public int checksum(@NonNull final List<Integer> digits)
                throws NumberFormatException {
            return Ean13.checksum(digits);
        }
    },
    /** Sheet Music. 13 digits. */
    Ismn {
        @Override
        @IntRange(from = 0, to = 10)
        public int checksum(@NonNull final List<Integer> digits)
                throws NumberFormatException {
            return Ean13.checksum(digits);
        }
    },
    /**
     * Amazon ASIN.
     *
     * @see ASIN
     */
    Asin {
        @Override
        @IntRange(from = 0, to = 10)
        public int checksum(@NonNull final List<Integer> digits)
                throws NumberFormatException {
            throw new NumberFormatException(ERROR_NOT_APPLICABLE + name());
        }
    };

    private static final String ERROR_NOT_APPLICABLE = "N/A: ";
    private static final String ERROR_WRONG_SIZE = "Wrong size: ";

    /**
     * Calculate the check-digit (checksum) for the given digits.
     *
     * @param digits list with the digits, the length depends on the type
     *
     * @return the check digit.
     *
     * @throws NumberFormatException on failure
     */
    @IntRange(from = 0, to = 10)
    public abstract int checksum(@NonNull List<Integer> digits)
            throws NumberFormatException;

    /**
     * Convenience method to check all EAN-13 compatible codes.
     *
     * @return flag
     */
    public boolean isEan13Compat() {
        return this == CodeType.Ean13
               || this == CodeType.Isbn13
               || this == CodeType.Issn13
               || this == CodeType.Ismn;
    }

    /**
     * Determine the type of code.
     *
     * @param digits to check
     *
     * @return type
     *
     * @throws NumberFormatException if parsing totally failed
     */
    @NonNull
    static CodeType getType(@Nullable final List<Integer> digits)
            throws NumberFormatException {

        if (digits == null || digits.isEmpty()) {
            return Invalid;
        }

        final int size = digits.size();

        // Most common 13 digits
        if (size == 13 && Ean13.checksum(digits) == digits.get(12)) {
            // Prefix 978 is "Bookland"
            if (digits.get(0) == 9 && digits.get(1) == 7 && digits.get(2) == 8) {
                return Isbn13;

            } else if (digits.get(0) == 9 && digits.get(1) == 7 && digits.get(2) == 9) {
                if (digits.get(3) == 0) {
                    // Prefix 979 with first digit 0 is "Musicland"
                    return Ismn;
                } else {
                    // non-0 is "Bookland"... we PRESUME, it's not entirely clear
                    // if these are simply 'reserved' or actual books.
                    return Isbn13;
                }
            } else if (digits.get(0) == 9 && digits.get(1) == 7 && digits.get(2) == 7) {
                // Prefix 977 are periodicals; an ISSN packed in an EAN-13
                return Issn13;

            } else {
                // it's a generic EAN-13
                return Ean13;
            }
        }

        // Older ISBN-10
        if (size == 10 && Isbn10.checksum(digits) == digits.get(9)) {
            return Isbn10;
        }

        // Magazines/Serials with ISSN numbers
        if (size == 8 && Issn8.checksum(digits) == digits.get(7)) {
            return Issn8;
        }

        // Legacy UPC_A codes.
        // a UPC barcode might be longer than 12 characters due to allowed extensions.
        // But only the first 12 characters are 'the' UPC_A code.
        if (size >= 12 && UpcA.checksum(digits.subList(0, 12)) == digits.get(11)) {
            return UpcA;
        }

        // Legacy SBN with optional price digits.
        if (size == 9 || size == 12) {
            final List<Integer> sbn = new ArrayList<>(digits.subList(0, 9));
            sbn.add(0, 0);
            if (Isbn10.checksum(sbn) == sbn.get(9)) {
                return Sbn;
            }
        }

        return Invalid;
    }
}
