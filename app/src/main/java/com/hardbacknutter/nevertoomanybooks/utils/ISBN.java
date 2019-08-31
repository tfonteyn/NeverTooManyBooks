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
package com.hardbacknutter.nevertoomanybooks.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * See <a href="http://en.wikipedia.org/wiki/International_Standard_Book_Number">
 * http://en.wikipedia.org/wiki/International_Standard_Book_Number</a>
 * <p>
 * ISBN stands for International Standard Book Number.
 * Every book is assigned a unique ISBN-10 and ISBN-13 when published.
 * <p>
 * ASIN stands for Amazon Standard Identification Number.
 * Every product on Amazon has its own ASIN, a unique code used to identify it.
 * For books, the ASIN is the same as the ISBN-10 number, but for all other products a new ASIN
 * is created when the item is uploaded to their catalogue.
 */
public class ISBN {

    private static final String UNABLE_TO_CONVERT_ERROR = "Unable to convert invalid ISBN";

    /**
     * <a href="https://getsatisfaction.com/deliciousmonster/topics/cant-scan-a-barcode-with-5-digit-extension-no-barcodes-inside">
     * https://getsatisfaction.com/deliciousmonster/topics/cant-scan-a-barcode-with-5-digit-extension-no-barcodes-inside</a>
     * <p>
     * The extended barcode combined with the UPC vendor prefix can be used to
     * reconstruct the ISBN.
     * Example:
     * Del Rey edition of Larry Niven's _World of Ptavvs_,
     * which says it's "Ninth Printing: September 1982" on the copyright page.
     * There is no ISBN/EAN barcode on the inside cover.
     * The back cover has an extended UPC code "0 70999 00225 5 30054".
     * <p>
     * "070999" in the first part of the UPC means that the ISBN starts with "0-345"
     * see <a href="https://www.eblong.com/zarf/bookscan/shelvescripts/upc-map">
     * https://www.eblong.com/zarf/bookscan/shelvescripts/upc-map</a>
     * making it a Ballantine book
     * That "00225" indicates the price
     * That gets us:
     * ISBN-10 is "0-345-30054-?"
     * The ISBN check digit is omitted from the bar code but can be calculated;
     * in this case it's 8
     * <p>
     * UPC Prefix -- ISBN Prefix mapping file (may not be complete)
     */
    private static final Map<String, String> UPC_2_ISBN_PREFIX = new HashMap<>();

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

    /** kept for faster conversion between 10/13 formats. */
    private List<Integer> mDigits;

    /**
     * Constructor.
     * <p>
     * 0345330137
     * 0709990049 523 3013
     *
     * @param s the isbn string, 10 or 13, or the old UPC
     */
    public ISBN(@NonNull final String s) {
        List<Integer> digits;
        // regular ISBN 10/13
        try {
            digits = isbnToDigits(s);
            if (isValid(digits)) {
                mDigits = digits;
                return;
            }
        } catch (@NonNull final NumberFormatException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debug(this, "Constructor", "s=" + s, e);
            }
        }

        // old UPC
        try {
            digits = upcToDigits(s);
            if (isValid(digits)) {
                mDigits = digits;
            }
        } catch (@NonNull final NumberFormatException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debug(this, "Constructor", "s=" + s, e);
            }
        }
    }

    /**
     * Validate an ISBN.
     */
    public static boolean isValid(@Nullable final String isbn) {
        if (isbn == null || isbn.isEmpty()) {
            return false;
        }
        try {
            return new ISBN(isbn).isValid();
        } catch (@NonNull final NumberFormatException e) {
            return false;
        }
    }

    /**
     * (try to) convert a UPC number to a real ISBN.
     *
     * @param input UPC, isbn. Can be blank
     *
     * @return either the valid ISBN equivalent, or the input string if conversion failed.
     */
    @NonNull
    public static String upc2isbn(@NonNull final String input) {
        if (input.isEmpty()) {
            return input;
        }

        // if it's a UPC, convert to 10 and return
        try {
            return new ISBN(input).to10();
        } catch (@NonNull final NumberFormatException | StringIndexOutOfBoundsException ignore) {
        }

        // might be invalid, but let the caller deal with that.
        return input;
    }

    /**
     * Check if two ISBN codes are matching.
     * In order we check:
     * <ol>
     * <li>Either or both == null ? no match</li>
     * <li>Same length and equalsIgnoreCase ? MATCH</li>
     * <li>Either one invalid ? no match</li>
     * <li>ISBN/UPC as objects equal ? MATCH</li>
     * </ol>
     * <p>
     * TODO: SIMPLIFY ISBN matches...
     *
     * @return {@code true} if the 2 codes match.
     */
    public static boolean matches(@Nullable final String isbn1,
                                  @Nullable final String isbn2) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
            Logger.debug(ISBN.class, "matches", isbn1 + " ?= " + isbn2);
        }
        if (isbn1 == null || isbn2 == null) {
            return false;
        }
        if (isbn1.length() == isbn2.length()) {
            // this covers comparing 2 UPC codes, of in fact any code whatsoever.
            return isbn1.equalsIgnoreCase(isbn2);
        }

        // Full check needed ...if either one is invalid, we consider them different
        ISBN o1 = new ISBN(isbn1);
        if (!o1.isValid()) {
            return false;
        }

        ISBN o2 = new ISBN(isbn2);
        if (!o2.isValid()) {
            return false;
        }

        return o1.equals(o2);
    }

    /**
     * Changes format from 10->13 or 13->10.
     * <p>
     * If the isbn was invalid, simply returns the input string.
     */
    @NonNull
    public static String isbn2isbn(@NonNull final String isbn) {
        try {
            ISBN info = new ISBN(isbn);
            if (info.is10()) {
                return info.to13();
            } else {
                return info.to10();
            }
        } catch (@NonNull final NumberFormatException ignore) {
        }

        // might be invalid, but let the caller deal with that.
        return isbn;
    }

    /**
     * Validate an Amazon ASIN.
     */
    public static boolean isValidAsin(@NonNull String asin) {

        // Book ASIN codes are always 10 characters.
        if (asin.length() != 10) {
            return false;
        }

        // An ASIN is basically an ISBN-10.
        if (isValid(asin)) {
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

    public boolean isValid() {
        return isValid(mDigits);
    }

    private boolean isValid(@Nullable final List<Integer> digits) {
        if (digits == null) {
            return false;
        }

        switch (digits.size()) {
            case 10:
                return getChecksum(digits) == 0;
            case 13:
                // Start with 978 or 979
                return digits.get(0) == 9 && digits.get(1) == 7
                       && (digits.get(2) == 8 || digits.get(2) == 9)
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
     * use the internal stored digits to construct a valid ISBN-10.
     *
     * @return a valid ISBN-10
     *
     * @throws NumberFormatException if conversion fails
     */
    @NonNull
    private String to10()
            throws NumberFormatException {
        if (!isValid()) {
            throw new NumberFormatException(UNABLE_TO_CONVERT_ERROR);
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
     * use the internal stored digits to construct a valid ISBN-13.
     *
     * @return a valid ISBN-13
     *
     * @throws NumberFormatException if conversion fails
     */
    @NonNull
    private String to13()
            throws NumberFormatException {
        if (!isValid()) {
            throw new NumberFormatException(UNABLE_TO_CONVERT_ERROR);
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
     * @return 0 for valid, or the (10 - c) value,
     * where (10 - getChecksum()) IS the checksum digit
     *
     * @throws NumberFormatException if the digits list has an incorrect length
     */
    private int getChecksum(@NonNull final List<Integer> digits)
            throws NumberFormatException {
        int sum = 0;
        switch (digits.size()) {
            case 10:
                int multiplier = 10;
                for (int d : digits) {
                    sum += d * multiplier;
                    multiplier--;
                }
                return sum % 11;

            case 13:
                for (int i = 0; i <= 12; i += 2) {
                    sum += digits.get(i);
                }
                for (int i = 1; i < 12; i += 2) {
                    sum += digits.get(i) * 3;
                }
                return sum % 10;

            default:
                throw new java.lang.NumberFormatException("ISBN incorrect length");
        }
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
     * This method does NOT check if the actual digits form a valid ISBN.
     * <p>
     * Allows and ignore '-' characters.
     *
     * @return list of digits
     *
     * @throws NumberFormatException if conversion fails
     */
    @NonNull
    private List<Integer> isbnToDigits(@NonNull final String isbn)
            throws NumberFormatException {
        // the digit '10' represented as 'X' in an isbn indicates we got to the end
        boolean foundX = false;

        List<Integer> digits = new ArrayList<>();

        for (int i = 0; i < isbn.length(); i++) {
            final char c = isbn.charAt(i);
            int digit;
            if (Character.isDigit(c)) {
                if (foundX) {
                    // X can only be at end of an ISBN10
                    throw new NumberFormatException();
                }
                digit = Integer.parseInt(Character.toString(c));

            } else if (Character.toUpperCase(c) == 'X' && digits.size() == 9) {
                if (foundX) {
                    // X can only be at end of an ISBN10
                    throw new NumberFormatException();
                }
                // 'X'
                digit = 10;
                foundX = true;

            } else if (c == '-') {
                continue;

            } else {
                // Invalid character
                throw new NumberFormatException();
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
     * @param upc UPC code, example: "070999 00225 530054", "00225" (price) and "5"
     *            will be discarded to construct the isbn.
     *
     * @return list of digits or empty on failure
     *
     * @throws NumberFormatException           on failure to analyse the string
     * @throws StringIndexOutOfBoundsException theoretically we should not get this, as we *should*
     *                                         not pass a string which is to short. But...
     */
    @NonNull
    private List<Integer> upcToDigits(@NonNull final String upc)
            throws NumberFormatException, StringIndexOutOfBoundsException {
        String isbnPrefix = UPC_2_ISBN_PREFIX.get(upc.substring(0, 6));
        if (isbnPrefix == null) {
            return new ArrayList<>();
        }

        List<Integer> tmp = isbnToDigits(isbnPrefix + upc.substring(12));
        // add bogus checksum
        tmp.add(10);

        // calc and add real checksum
        int c = getChecksum(tmp);
        if (c != 0) {
            c = 10 - c;
        }
        tmp.set(tmp.size() - 1, c);
        return tmp;
    }


//    public boolean equals(@NonNull final ISBN that) {
//
//        // If either is invalid, require they match exactly
//        if (!isValid() || !that.isValid()) {
//            return mDigits.size() == that.mDigits.size()
//                    && digitsMatch(mDigits.size(), 0, that, 0);
//        }
//
//        // same length ? simple check
//        if (mDigits.size() == that.mDigits.size()) {
//            return digitsMatch(mDigits.size(), 0, that, 0);
//        }
//
//        // Both are valid ISBN codes and we know the lengths are either 10 or 13
//        // when we get here. So ... compare the significant digits.
//        if (mDigits.size() == 10) {
//            return digitsMatch(9, 0, that, 3);
//        } else {
//            return digitsMatch(9, 3, that, 0);
//        }
//    }

    @Override
    public int hashCode() {
        return Objects.hash(mDigits);
    }

    /**
     * Two ISBN objects are equal if they match these checks (in this order).
     * <ol>
     * <li>objects being '==' ? MATCH</li>
     * <li>Either or both == null ? no match</li>
     * <li>Incoming object is not an ISBN ? no match</li>
     * <li>Either or both invalid, compare their digits for exact match</li>
     * <li>Same length, compare their digits for exact match</li>
     * <li>compare the 9 significant isbn digits for equality.</li>
     * </ol>
     *
     * <strong>Note:</strong> hash codes are done over the {@link #mDigits} objects.
     * <p>
     * TODO: SIMPLIFY ISBN equals...
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ISBN cmp = (ISBN) obj;

        // If either is invalid, no match
        if (!isValid() || !cmp.isValid()) {
            return false;
        }

        // same length ? they should match exactly
        if (mDigits.size() == cmp.mDigits.size()) {
            return Objects.equals(mDigits, cmp.mDigits);
        }

        // Both are valid ISBN codes and we know the lengths are either 10 or 13
        // when we get here. So ... compare the significant digits:
        // ISBN13: skip the first 3 character, and don't use the checksum.
        // ISBN10: don't use the checksum.
        // len is always 9, but allow future extensions (non-isbn?)
        if (mDigits.size() == 10) {
            return digitsMatch(9, 0, cmp, 3);
        } else {
            return digitsMatch(9, 3, cmp, 0);
        }
    }

    /**
     * Check if all digits are the same.
     */
    private boolean digitsMatch(@SuppressWarnings("SameParameterValue") final int lenToCheck,
                                int posFrom1,
                                @NonNull final ISBN dig2,
                                int posFrom2) {
        for (int i = 0; i < lenToCheck; i++) {
            if (!mDigits.get(posFrom1++).equals(dig2.mDigits.get(posFrom2++))) {
                return false;
            }
        }
        return true;
    }
}
