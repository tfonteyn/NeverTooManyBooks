/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.utils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class IsbnUtils {
    private IsbnUtils() {
    }

    /**
     * Validate an ISBN
     */
    public static boolean isValid(@NonNull final String isbn) {
        try {
            return new ISBNNumber(isbn).isValid();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * (try to) convert a UPC number to a real ISBN
     *
     * @param input UPC, isbn
     *
     * @return either the valid ISBN equivalent, or the input string if conversion failed.
     */
    @NonNull
    public static String upc2isbn(@NonNull final String input) {
        int len = input.length();
        if (len == 10 || len == 13) {
            return input;
        }

        // if it's a UPC, convert to 10 and return
        try {
            return new ISBNNumber(input).to10();
        } catch (NumberFormatException ignore) {
        }

        // might be invalid, but let the caller deal with that.
        return input;
    }

    /**
     * matches.... but not necessarily valid !
     */
    public static boolean matches(@Nullable final String isbn1, @Nullable final String isbn2) {
        if (isbn1 == null || isbn2 == null) {
            return false;
        }
        if (isbn1.length() == isbn2.length()) {
            return isbn1.equalsIgnoreCase(isbn2);
        }

        // Full check needed ...if either is invalid, we consider them different
        final ISBNNumber info1 = new ISBNNumber(isbn1);
        if (!info1.isValid())
            return false;

        ISBNNumber info2 = new ISBNNumber(isbn2);
        return info2.isValid() && info1.equals(info2);
    }

    /**
     * Changes format from 10->13 or 13->10
     *
     * If the isbn was invalid, simply returns the same
     */
    public static String isbn2isbn(@NonNull final String isbn) {
        try {
            ISBNNumber info = new ISBNNumber(isbn);
            if (info.is10()) {
                return info.to13();
            } else {
                return info.to10();
            }
        } catch (NumberFormatException ignore) {
        }

        // might be invalid, but let the caller deal with that.
        return isbn;
    }

    /**
     * Validate an ISBN
     * See http://en.wikipedia.org/wiki/International_Standard_Book_Number
     */
    private static class ISBNNumber {
        /**
         * https://getsatisfaction.com/deliciousmonster/topics/cant-scan-a-barcode-with-5-digit-extension-no-barcodes-inside
         *
         * The extended barcode combined with the UPC vendor prefix can be used to reconstruct the ISBN.
         * Example:
         * Del Rey edition of Larry Niven's _World of Ptavvs_,
         * which says it's "Ninth Printing: September 1982" on the copyright page.
         * There is no ISBN/EAN barcode on the inside cover.
         * The back cover has an extended UPC code "0 70999 00225 5 30054".
         *
         * "070999" in the first part of the UPC means that the ISBN starts with "0-345"
         * see https://www.eblong.com/zarf/bookscan/shelvescripts/upc-map
         * making it a Ballantine book
         * That "00225" indicates the price
         * That gets us:
         * ISBN-10 is "0-345-30054-?"
         * The ISBN check digit is omitted from the bar code but can be calculated; in this case it's 8
         *
         * UPC Prefix -- ISBN Prefix mapping file (may not be complete)
         */
        private static final HashMap<String, String> UPC_2_ISBN_PREFIX = new HashMap<>();

        static {
            UPC_2_ISBN_PREFIX.put("014794", "08041");
            UPC_2_ISBN_PREFIX.put("018926", "0445");
            UPC_2_ISBN_PREFIX.put("027778", "0449");
            UPC_2_ISBN_PREFIX.put("037145", "0812");
            UPC_2_ISBN_PREFIX.put("042799", "0785");
            UPC_2_ISBN_PREFIX.put("043144", "0688");
            UPC_2_ISBN_PREFIX.put("044903", "0312");
            UPC_2_ISBN_PREFIX.put("045863", "0517");
            UPC_2_ISBN_PREFIX.put("046594", "0064");
            UPC_2_ISBN_PREFIX.put("047132", "0152");
            UPC_2_ISBN_PREFIX.put("051487", "08167");
            UPC_2_ISBN_PREFIX.put("051488", "0140");
            UPC_2_ISBN_PREFIX.put("060771", "0002");
            UPC_2_ISBN_PREFIX.put("065373", "0373");
            UPC_2_ISBN_PREFIX.put("070992", "0523");
            UPC_2_ISBN_PREFIX.put("070993", "0446");
            UPC_2_ISBN_PREFIX.put("070999", "0345");
            UPC_2_ISBN_PREFIX.put("071001", "0380");
            UPC_2_ISBN_PREFIX.put("071009", "0440");
            UPC_2_ISBN_PREFIX.put("071125", "088677");
            UPC_2_ISBN_PREFIX.put("071136", "0451");
            UPC_2_ISBN_PREFIX.put("071149", "0451");
            UPC_2_ISBN_PREFIX.put("071152", "0515");
            UPC_2_ISBN_PREFIX.put("071162", "0451");
            UPC_2_ISBN_PREFIX.put("071268", "08217");
            UPC_2_ISBN_PREFIX.put("071831", "0425");
            UPC_2_ISBN_PREFIX.put("071842", "08439");
            UPC_2_ISBN_PREFIX.put("072742", "0441");
            UPC_2_ISBN_PREFIX.put("076714", "0671");
            UPC_2_ISBN_PREFIX.put("076783", "0553");
            UPC_2_ISBN_PREFIX.put("076814", "0449");
            UPC_2_ISBN_PREFIX.put("078021", "0872");
            UPC_2_ISBN_PREFIX.put("079808", "0394");
            UPC_2_ISBN_PREFIX.put("090129", "0679");
            UPC_2_ISBN_PREFIX.put("099455", "0061");
            UPC_2_ISBN_PREFIX.put("099769", "0451");
        }

        /** kept for faster conversion between 10/13 formats */
        @SuppressWarnings("FieldMayBeFinal")
        private List<Integer> mDigits;

        /**
         * 0345330137
         * 0709990049 523 3013
         *
         * @param s the isbn string, 10 or 13, or the old UPC
         */
        ISBNNumber(@NonNull final String s) {
            List<Integer> digits;
            // regular ISBN 10/13
            try {
                digits = isbnToDigits(s);
                if (isValid(digits)) {
                    mDigits = digits;
                    return;
                }
            } catch (NumberFormatException e) {
                if (BuildConfig.DEBUG) {
                    Logger.logError(e);
                }
            }

            // old UPC
            try {
                digits = upcToDigits(s);
                if (isValid(digits)) {
                    mDigits = digits;
                    return;
                }
            } catch (NumberFormatException e) {
                if (BuildConfig.DEBUG) {
                    Logger.logError(e);
                }
            }
            throw new NumberFormatException();
        }

        public boolean isValid() {
            return isValid(mDigits);
        }

        private boolean isValid(List<Integer> digits) {
            switch (digits.size()) {
                case 10:
                    return (getChecksum(digits) == 0);
                case 13:
                    // Start with 978 or 979
                    return digits.get(0) == 9 && digits.get(1) == 7 && (digits.get(2) == 8 || digits.get(2) == 9)
                            && (getChecksum(digits) == 0);
                default:
                    return false;
            }
        }

        public boolean is10() {
            return isValid() && (mDigits.size() == 10);
        }

        public boolean is13() {
            return isValid() && (mDigits.size() == 13);
        }

        /**
         * @param digits the isbn number as digits (10 or 13)
         *
         * @return the ISBN number as a string (10 or 13)
         */
        private String concat(@NonNull final List<Integer> digits) {
            StringBuilder sb = new StringBuilder();
            for (int d : digits) {
                if (d == 10) {
                    sb.append('X');
                } else {
                    sb.append(d);
                }
            }
            return sb.toString();
        }

        /**
         * use the internal stored digits to construct a valid ISBN-10
         *
         * @return a valid ISBN-10
         */
        @NonNull
        String to10() throws NumberFormatException {
            if (!isValid()) {
                throw new NumberFormatException("Unable to convert invalid ISBN");
            }

            // already in ISBN-10 format, just return
            if (is10()) {
                return concat(mDigits);
            }

            // need to convert from ISBN-13
            List<Integer> digits = new ArrayList<>();
            for (int i = 3; i < 12; i++) {
                digits.add(mDigits.get(i));
            }
            // but replace the last one with the new checksum
            digits.set(digits.size() - 1, (11 - getChecksum(digits)) % 11);

            return concat(digits);
        }

        /**
         * use the internal stored digits to construct a valid ISBN-13
         *
         * @return a valid ISBN-13
         */
        @NonNull
        String to13() throws NumberFormatException {
            if (!isValid()) {
                throw new NumberFormatException("Unable to convert invalid ISBN");
            }

            // already in ISBN-13 format, just return
            if (is13()) {
                return concat(mDigits);
            }

            List<Integer> digits = new ArrayList<>();
            // standard prefix 978
            digits.add(9);
            digits.add(7);
            digits.add(8);

            // copy
            for (int i = 0; i < 9; i++) {
                digits.add(mDigits.get(i));
            }

            // and set the checksum digit
            digits.set(digits.size() - 1, (10 - getChecksum(digits)) % 10);

            return concat(digits);
        }

        /**
         * @param digits list with the digits, either 13 or 10
         *
         * @return 0 for valid, or the (10 - c) value, where (10 - getChecksum()) IS the checksum digit
         */
        private int getChecksum(@NonNull final List<Integer> digits) throws NumberFormatException {
            int sum = 0;
            switch (digits.size()) {
                case 10:
                    int multiplier = 10;
                    for (int d : digits) {
                        sum += d * multiplier;
                        multiplier--;
                    }
                    return (sum % 11);

                case 13:
                    for (int i = 0; i <= 12; i += 2) {
                        sum += digits.get(i);
                    }
                    for (int i = 1; i < 12; i += 2) {
                        sum += digits.get(i) * 3;
                    }
                    return (sum % 10);

                default:
                    throw new java.lang.NumberFormatException("ISBN incorrect length");
            }
        }


        /**
         * This method does NOT check if the actual digits form a valid ISBN
         *
         * @return list of digits
         */
        @NonNull
        private List<Integer> isbnToDigits(@NonNull final String isbn) throws NumberFormatException {
            // the digit '10' represented as 'X' in an isbn indicates we got to the end
            boolean foundX = false;

            List<Integer> digits = new ArrayList<>();

            for (int i = 0; i < isbn.length(); i++) {
                final Character c = isbn.charAt(i);
                int digit;
                if (Character.isDigit(c)) {
                    if (foundX) {
                        throw new NumberFormatException(); // X can only be at end of an ISBN10
                    }
                    digit = Integer.parseInt(c.toString());

                } else if (Character.toUpperCase(c) == 'X' && digits.size() == 9) {
                    if (foundX) {
                        throw new NumberFormatException(); // X can only be at end of an ISBN10
                    }
                    digit = 10; // 'X'
                    foundX = true;
                } else {
                    throw new NumberFormatException(); // Invalid character
                }

                // Check if too long
                if (digits.size() >= 13) {
                    throw new NumberFormatException();
                }
                digits.add(digit);
            }
            return digits;
        }

        /**
         * @param upc UPC code, example: "070999 00225 530054", "00225" (price) and "5" will be discarded to construct the isbn
         *
         * @return list of digits
         */
        @NonNull
        private List<Integer> upcToDigits(@NonNull final String upc) throws NumberFormatException {
            String isbnPrefix = UPC_2_ISBN_PREFIX.get(upc.substring(0, 6));
            if (isbnPrefix == null) {
                throw new NumberFormatException();
            }

            List<Integer> tmp = isbnToDigits(isbnPrefix + upc.substring(12));
            tmp.add(10); // bogus

            int c = getChecksum(tmp);
            if (c != 0) {
                c = 10 - c;
            }
            tmp.set(tmp.size() - 1, c);
            return tmp;
        }

        /**
         * do a digit wise compare, even on invalid data!
         */
        public boolean equals(@NonNull final ISBNNumber cmp) {

            if (!this.isValid() || !cmp.isValid()) {
                // If either is invalid, require they simply match exactly
                return this.mDigits.size() == cmp.mDigits.size()
                        && digitsMatch(this.mDigits.size(), 0, cmp, 0);
            }

            // same length ? simple check
            if (this.mDigits.size() == cmp.mDigits.size()) {
                return digitsMatch(this.mDigits.size(), 0, cmp, 0);
            }

            // We know the lengths are either 10 or 13 when we get here. So ... compare
            if (this.mDigits.size() == 10) {
                return digitsMatch(9, 0, cmp, 3);
            } else {
                return digitsMatch(9, 3, cmp, 0);
            }
        }

        /**
         * simple check if all digits are the same
         */
        private boolean digitsMatch(final int lenToCheck, int posFrom1, @NonNull final ISBNNumber dig2, int posFrom2) {
            for (int i = 0; i < lenToCheck; i++) {
                if (!this.mDigits.get(posFrom1++).equals(dig2.mDigits.get(posFrom2++))) {
                    return false;
                }
            }
            return true;
        }

    }
}
