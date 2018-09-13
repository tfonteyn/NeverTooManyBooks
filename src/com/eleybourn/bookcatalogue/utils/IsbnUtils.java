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

import java.util.HashMap;

public class IsbnUtils {
	private IsbnUtils() {
	}

    /**
	 * Validate an ISBN
	 */
	public static boolean isValid(@NonNull final String isbn) {
		try {
			return new IsbnInfo(isbn).isValid;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public static boolean matches(@Nullable final String isbn1, @Nullable final String isbn2) {
		if (isbn1 == null || isbn2 == null) {
			return false;
		}
		if (isbn1.length() == isbn2.length()) {
            return isbn1.equalsIgnoreCase(isbn2);
        }

		// Different lengths; sanity check...if either is invalid, we consider them different
		IsbnInfo info1 = new IsbnInfo(isbn1);
		if (!info1.isValid)
			return false;

		IsbnInfo info2 = new IsbnInfo(isbn2);
		return info2.isValid && info1.equals(info2);
	}

    /**
     * switches an ISBN-10 to ISBN-13 and reverse
     */
    public static String isbn2isbn(@NonNull final String isbn) throws NumberFormatException {
        IsbnInfo info = new IsbnInfo(isbn);
        if (!info.isValid)
            throw new NumberFormatException("Unable to convert invalid ISBN");

        if (isbn.length() == 10) {
            return info.getIsbn13();
        } else {
            return info.getIsbn10();
        }
    }

	/**
     * Validate an ISBN
     * See http://en.wikipedia.org/wiki/International_Standard_Book_Number
     */
    public static class IsbnInfo {
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
        private static final HashMap<String, String> upc2isbnPrefix = new HashMap<>();

        static {
            upc2isbnPrefix.put("014794", "08041");
            upc2isbnPrefix.put("018926", "0445");
            upc2isbnPrefix.put("027778", "0449");
            upc2isbnPrefix.put("037145", "0812");
            upc2isbnPrefix.put("042799", "0785");
            upc2isbnPrefix.put("043144", "0688");
            upc2isbnPrefix.put("044903", "0312");
            upc2isbnPrefix.put("045863", "0517");
            upc2isbnPrefix.put("046594", "0064");
            upc2isbnPrefix.put("047132", "0152");
            upc2isbnPrefix.put("051487", "08167");
            upc2isbnPrefix.put("051488", "0140");
            upc2isbnPrefix.put("060771", "0002");
            upc2isbnPrefix.put("065373", "0373");
            upc2isbnPrefix.put("070992", "0523");
            upc2isbnPrefix.put("070993", "0446");
            upc2isbnPrefix.put("070999", "0345");
            upc2isbnPrefix.put("071001", "0380");
            upc2isbnPrefix.put("071009", "0440");
            upc2isbnPrefix.put("071125", "088677");
            upc2isbnPrefix.put("071136", "0451");
            upc2isbnPrefix.put("071149", "0451");
            upc2isbnPrefix.put("071152", "0515");
            upc2isbnPrefix.put("071162", "0451");
            upc2isbnPrefix.put("071268", "08217");
            upc2isbnPrefix.put("071831", "0425");
            upc2isbnPrefix.put("071842", "08439");
            upc2isbnPrefix.put("072742", "0441");
            upc2isbnPrefix.put("076714", "0671");
            upc2isbnPrefix.put("076783", "0553");
            upc2isbnPrefix.put("076814", "0449");
            upc2isbnPrefix.put("078021", "0872");
            upc2isbnPrefix.put("079808", "0394");
            upc2isbnPrefix.put("090129", "0679");
            upc2isbnPrefix.put("099455", "0061");
            upc2isbnPrefix.put("099769", "0451");
        }

        public final boolean isValid;

        private final int[] mDigits = new int[13];
        private int size = 0;

        /**
         * @param s the isbn string, 10 or 13, or the old UPC
         */
        IsbnInfo(@NonNull final String s) {
            isValid =  isnbToDigits(s) || upcToDigits(s);
        }

        private boolean isValid10(@NonNull final int[] digits) {
            return (getChecksum10(digits) == 0);
        }

        private boolean isValid13(@NonNull final int[] digits) {
            // Start with 978 or 979
            return digits[0] == 9 && digits[1] == 7 && (digits[2] == 8 || digits[2] == 9)
                    && (getChecksum13(digits) == 0);
        }

        /**
         * @param digits the isbn number as digits (10 or 13)
         *
         * @return the ISBN number as a string (10 or 13)
         */
        private String concat(@NonNull final int[] digits) {
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
		String getIsbn10() throws NumberFormatException {
            if (!isValid) {
                throw new NumberFormatException("Unable to convert invalid ISBN");
            }

            // already in ISBN-10 format, just return
            if (size == 10) {
                return concat(mDigits);
            }

            // need to convert from ISBN-13
            int[] digits;
            int pos = 0;
            digits = new int[10];
            for (int i = 3; i < 12; i++) {
                digits[pos++] = mDigits[i];
            }
            // but replace the last one with the checksum
            digits[9] = (11 - getChecksum10(digits)) % 11;

            return concat(digits);
        }

        /**
         * @return digit 10, the checksum
         */
        private int getChecksum10(@NonNull final int[] digits) {
            int multiplier = 10;
            int sum = 0;
            for (int i = 0; i < 10; i++) {
                sum += digits[i] * multiplier;
                multiplier--;
            }
            return (sum % 11);
        }

        /**
         * use the internal stored digits to construct a valid ISBN-13
         *
         * @return a valid ISBN-13
         */
        @NonNull
		String getIsbn13() throws NumberFormatException {
            if (!isValid) {
                throw new NumberFormatException("Unable to convert invalid ISBN");
            }

            // already in ISBN-13 format, just return
            if (size == 13) {
                return concat(mDigits);
            }

            int[] digits;
            digits = new int[13];
            // standard prefix 978
            digits[0] = 9;
            digits[1] = 7;
            digits[2] = 8;
            int pos = 3;

            // now simply get the remaining 10
            for (int i = 0; i < 9; i++) {
                digits[pos++] = mDigits[i];
            }
            // but replace the last one with the checksum
            digits[12] = (10 - getChecksum13(digits)) % 10;

            return concat(digits);
        }

        /**
         * @return digit 13, the checksum
         */
        private int getChecksum13(@NonNull final int[] digits) {
            int sum = 0;
            for (int i = 0; i <= 12; i += 2) {
                sum += digits[i];
            }
            for (int i = 1; i < 12; i += 2) {
                sum += digits[i] * 3;
            }
            return (sum % 10);
        }

        /**
         * Converts the string input to the global mDigits.
         * This method does NOT check if the actual digits form a valid ISBN
         *
         * @return true if conversion successful
         */
        private boolean isnbToDigits(@NonNull final String isbn) {
            // the digit '10' represented as 'X' in an isbn indicates we got to the end
            boolean foundX = false;

            for (int i = 0; i < isbn.length(); i++) {
                final Character c = isbn.charAt(i);
                int digit;
                if (Character.isDigit(c)) {
                    // X can only be at end of an ISBN10
                    if (foundX) {
                        return false;
                    }
                    digit = Integer.parseInt(c.toString());
                } else if (Character.toUpperCase(c) == 'X' && size == 9) {
                    // X can only be at end of an ISBN10
                    if (foundX) {
                        return false;
                    }
                    digit = 10; // 'X'
                    foundX = true;
                } else {
                    // Invalid character
                    return false;
                }

                // Check if too long
                if (size >= 13) {
                    return false;
                }
                mDigits[size] = digit;
                size++;
            }

            switch (size) {
                case 10:
                    return isValid10(mDigits);
                case 13:
                    return isValid13(mDigits);
            }
            return false;
        }

        /**
         *
         * Converts the string input to the global mDigits.
         *
         * @param upc UPC code, example: "070999 00225 530054", "00225" is the price and will be discarded
         *
         * @return true if conversion successful
         */
        private boolean upcToDigits(@NonNull final String upc) {
            String isbnPrefix = upc2isbnPrefix.get(upc.substring(0, 5));

            String isbnMain = upc.substring(11);

            String possible = isbnPrefix + isbnMain + 'X'; // last one is BOGUS checksum

            if (!isnbToDigits(possible)) {
                return false;
            }

            // we now have mDigits populated, but with a wrong checksum. So calc one
            mDigits[9] = getChecksum10(mDigits);
            size = 10;
            // we just calculated the checksum, so yes, it's valid.
            return true;
        }

        /**
         * do a digit wise compare, even on invalid data
         */
        public boolean equals(@NonNull final IsbnInfo cmp) {
            // If either is an invalid ISBN, require they match exactly
            if (!this.isValid || !cmp.isValid) {
                return this.size == cmp.size && digitsMatch(this.size, 0, cmp.mDigits, 0);
            }

            // We know the lengths are either 10 or 13 when we get here. So ... compare
            if (this.size == 10) {
                if (cmp.size == 10) {
                    return digitsMatch(9, 0, cmp.mDigits, 0);
                } else {
                    return digitsMatch(9, 0, cmp.mDigits, 3);
                }
            } else {
                if (cmp.size == 13) {
                    return digitsMatch(13, 0, cmp.mDigits, 0);
                } else {
                    return digitsMatch(9, 3, cmp.mDigits, 0);
                }

            }
        }

        private boolean digitsMatch(final int len, int pos1, @NonNull final int[] dig2, int pos2) {
            for (int i = 0; i < len; i++) {
                if (this.mDigits[pos1++] != dig2[pos2++])
                    return false;
            }
            return true;
        }
    }
}
