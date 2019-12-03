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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;

/**
 * Represents an ISBN-10, ISBN-13 or EAN-13 code.
 * Accepts UPC numbers if they can be converted to ISBN.
 * <p>
 * See <a href="http://en.wikipedia.org/wiki/International_Standard_Book_Number">
 * http://en.wikipedia.org/wiki/International_Standard_Book_Number</a>
 * <p>
 * ISBN stands for International Standard Book Number.
 * Every book is assigned a unique ISBN-10 and ISBN-13 when published.
 * <p>
 * <a href="https://www.amazon.com/gp/seller/asin-upc-isbn-info.html">
 * https://www.amazon.com/gp/seller/asin-upc-isbn-info.html</a>
 * <p>
 * Lots of info:
 * <a href="https://isbn-information.com">https://isbn-information.com</a>
 *
 * <a href="https://en.wikipedia.org/wiki/International_Article_Number#GS1_prefix">
 * https://en.wikipedia.org/wiki/International_Article_Number#GS1_prefix</a>
 * <p>
 * The EAN "country code" 978 (and later 979) has been allocated since the 1980s to reserve
 * a Unique Country Code (UCC) prefix for EAN identifiers of published books, regardless of
 * country of origin, so that the EAN space can catalog books by ISBNs[3] rather than
 * maintaining a redundant parallel numbering system. This is informally known as "Bookland".
 * The prefix 979 with first digit 0 is used for International Standard Music Number (ISMN)
 * and the prefix 977 indicates International Standard Serial Number (ISSN).
 */
public class ISBN {

    private static final String TAG = "ISBN";

    private static final String ERROR_UNABLE_TO_CONVERT = "Unable to convert invalid ISBN";

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
    /** Remove '-' and space chars. */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[ -]");

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

    /** {@code true} if this should be a strict ISBN number (and not an EAN-13 */
    private final boolean mStrictIsbn;

    /** kept for faster conversion between 10/13 formats. */
    @Nullable
    private List<Integer> mDigits;

    private boolean mIsUpc;

    /**
     * Constructor.
     * <p>
     * Accepts (and removes) ' ' and '-' characters.
     *
     * @param s the isbn string, 10 or 13, or the old UPC
     */
    public ISBN(@NonNull final String s) {
        this(s, true);
    }

    /**
     * Constructor.
     * <p>
     * Accepts (and removes) ' ' and '-' characters.
     *
     * @param isbnStr the isbn string, 10 or 13, or the old UPC
     * @param strict  Flag: {@code true} to strictly test ISBN-13 codes,
     *                {@code false} to just check EAN-13. Ignored if there are only 10 digits.
     */
    public ISBN(@NonNull final String isbnStr,
                final boolean strict) {
        mStrictIsbn = strict;

        List<Integer> digits;
        // regular ISBN 10/13
        try {
            digits = isbnToDigits(isbnStr);
            if (isValid(digits)) {
                mDigits = digits;
                return;
            }
        } catch (@NonNull final NumberFormatException e) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISBN) {
                Log.d(TAG, "s=" + isbnStr, e);
            }
        }

        // old UPC
        try {
            digits = upcToDigits(isbnStr);
            if (isValid(digits)) {
                mIsUpc = true;
                mDigits = digits;
            }
        } catch (@NonNull final NumberFormatException e) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISBN) {
                Log.d(TAG, "s=" + isbnStr, e);
            }
        }
    }

    /**
     * Validate an ISBN.
     *
     * @param isbn to check
     *
     * @return {@code true} if valid
     */
    public static boolean isValid(@Nullable final String isbn) {
        return isValid(isbn, true);
    }

    /**
     * Validate an ISBN.
     *
     * @param isbnStr to check
     * @param strict  Flag: {@code true} to strictly test ISBN-13 codes,
     *                {@code false} to just check EAN-13. Ignored if there are only 10 digits.
     *
     * @return {@code true} if valid
     */
    public static boolean isValid(@Nullable final String isbnStr,
                                  final boolean strict) {
        if (isbnStr == null || isbnStr.isEmpty()) {
            return false;
        }
        try {
            return new ISBN(isbnStr, strict).isValid();
        } catch (@NonNull final NumberFormatException e) {
            return false;
        }
    }

    /**
     * (try to) convert a UPC number to a real ISBN.
     *
     * @param upcStr UPC, isbn. Can be blank
     *
     * @return either the valid ISBN equivalent, or the input string if conversion failed.
     */
    @NonNull
    public static String upc2isbn(@NonNull final String upcStr) {
        if (upcStr.isEmpty()) {
            return upcStr;
        }

        try {
            ISBN isbn = new ISBN(upcStr, true);
            if (isbn.isUpc()) {
                // if it's a UPC, convert to isbn 10 and return
                return isbn.to10();
            }
        } catch (@NonNull final NumberFormatException | StringIndexOutOfBoundsException ignore) {
        }

        // it's not UPC, just return as-is.
        return upcStr;
    }

    /**
     * Check if two ISBN codes are matching.
     *
     * @param isbnStr1  first code
     * @param isbnStr2  second code
     * @param strict Flag: {@code true} to strictly test ISBN-13 codes,
     *               {@code false} to just check EAN-13. Ignored if there are only 10 digits.
     *
     * @return {@code true} if the 2 codes match.
     */
    public static boolean matches(@Nullable final String isbnStr1,
                                  @Nullable final String isbnStr2,
                                  final boolean strict) {
        if (isbnStr1 == null || isbnStr2 == null) {
            return false;
        }

        // Full ISBN check needed ...if either one is invalid, we consider them different
        ISBN o1 = new ISBN(isbnStr1, strict);
        if (!o1.isValid()) {
            return false;
        }

        ISBN o2 = new ISBN(isbnStr2, strict);
        if (!o2.isValid()) {
            return false;
        }

        return o1.equals(o2);
    }

    /**
     * Changes format from 10->13 or 13->10.
     * <p>
     * If the isbn was invalid, simply returns the input string.
     *
     * @param isbnStr to transform
     *
     * @return transformed isbn
     */
    @NonNull
    public static String isbn2isbn(@NonNull final String isbnStr) {
        try {
            ISBN isbn = new ISBN(isbnStr, true);
            if (isbn.is10()) {
                return isbn.to13();
            } else {
                return isbn.to10();
            }
        } catch (@NonNull final NumberFormatException ignore) {
        }

        // might be invalid, but let the caller deal with that.
        return isbnStr;
    }

    /**
     * Check if this is a valid
     * ISBN-10 / ISBN-13 (strict==true) / EAN-13 (strict==false).
     *
     * @return validity
     */
    public boolean isValid() {
        return isValid(mDigits);
    }

    /**
     * Check if the passed digits form a valid
     * ISBN-10 / ISBN-13 (strict==true) / EAN-13 (strict==false).
     *
     * @param digits to check
     *
     * @return validity
     */
    private boolean isValid(@Nullable final List<Integer> digits) {
        if (digits == null) {
            return false;
        }
        int len = digits.size();
        if (len != 10 && len != 13) {
            return false;
        }

        if (mStrictIsbn && len == 13) {
            // Currently (2019), the only possible prefix elements for ISBNs are 978 and 979
            if (digits.get(0) != 9
                || digits.get(1) != 7 ||
                (digits.get(2) != 8 && digits.get(2) != 9)) {
                return false;
            }
        }

        return getCheckDigit(digits) == digits.get(digits.size() - 1);
    }

    public boolean is10() {
        return mDigits != null && (mDigits.size() == 10) && isValid();
    }

    public boolean is13() {
        return mDigits != null && (mDigits.size() == 13) && isValid();
    }

    @SuppressWarnings("WeakerAccess")
    public boolean isUpc() {
        return mIsUpc;
    }

    /**
     * use the internal stored digits to construct a valid ISBN-10.
     *
     * @return a valid ISBN-10
     *
     * @throws NumberFormatException if conversion fails
     */
    @NonNull
    @VisibleForTesting
    String to10()
            throws NumberFormatException {
        if (mDigits == null || !isValid()) {
            throw new NumberFormatException(ERROR_UNABLE_TO_CONVERT);
        }

        // already in ISBN-10 format, just return
        if (is10()) {
            return concat(mDigits);
        }

        // need to convert from ISBN-13, drop the first 3 digits, and copy the next 9.
        List<Integer> digits = new ArrayList<>();
        for (int i = 3; i < 12; i++) {
            digits.add(mDigits.get(i));
        }
        // and add the new checksum
        digits.add(getCheckDigit(digits));

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
    @VisibleForTesting
    String to13()
            throws NumberFormatException {
        if (mDigits == null || !isValid()) {
            throw new NumberFormatException(ERROR_UNABLE_TO_CONVERT);
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

        // copy the first 9 digits
        for (int i = 0; i < 9; i++) {
            digits.add(mDigits.get(i));
        }

        // and add the new checksum
        digits.add(getCheckDigit(digits));

        return concat(digits);
    }

    /**
     * Calculate the check-digit (checksum) for the given digits.
     * This calculation is valid for ISBN-10/13 and EAN-13 codes.
     *
     * @param digits list with the digits, either 13 (or 12) or 10 (or 9)
     *
     * @return the check digit.
     *
     * @throws NumberFormatException if the digits list has an incorrect length
     */
    private int getCheckDigit(@NonNull final List<Integer> digits)
            throws NumberFormatException {

        switch (digits.size()) {
            case 9:
            case 10: {
                // 1. Take the first 9 digits of the 10-digit ISBN.
                // 2. Multiply each number in turn, from left to right by a number.
                //    The first, leftmost, digit of the nine is multiplied by 10,
                //    then working from left to right, each successive digit is
                //    multiplied by one less than the one before.
                //    So the second digit is multiplied by 9, the third by 8,
                //    and so on to the ninth which is multiplied by 2.
                //
                // 3. Add all of the 9 products.
                int sum = 0;
                int multiplier = 10;
                for (int dig = 1; dig < 10; dig++) {
                    sum += digits.get(dig - 1) * multiplier;
                    multiplier--;
                }

                // 4. Do a modulo 11 division on the sum.
                int modulo = sum % 11;
                if (modulo == 0) {
                    return 0;
                } else {
                    return 11 - modulo;
                }
            }
            case 12:
            case 13: {
                // 1. Take the first 12 digits of the 13-digit ISBN
                // 2. Multiply each number in turn, from left to right by a number.
                //    The first, leftmost, digit is multiplied by 1, the second by 3,
                //    the third by 1 again, the fourth by 3 again, and so on to
                //    the eleventh which is multiplied by 1 and the twelfth by 3.
                //
                // 3. Add all of the 12 products.
                int sum = 0;
                for (int dig = 1; dig < 13; dig += 2) {
                    sum += digits.get(dig - 1);
                }
                for (int dig = 2; dig < 13; dig += 2) {
                    sum += digits.get(dig - 1) * 3;
                }

                // 4. Do a modulo 10 division on the sum.
                int modulo = sum % 10;

                if (modulo == 0) {
                    // If it's a zero, then the check digit is zero.
                    return 0;
                } else {
                    // Otherwise subtract the remainder from 10.
                    return 10 - modulo;
                }
            }
            default:
                throw new NumberFormatException("ISBN incorrect length: " + digits.size());
        }
    }

    /**
     * @param digits the isbn number as digits (10 or 13)
     *
     * @return the ISBN number as a string (10 or 13)
     */
    private String concat(@NonNull final Iterable<Integer> digits) {
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
     * Get the ISBN as a text string.
     *
     * @return string, or {@code null} if the code is not valid.
     */
    @Nullable
    public String asText() {
        if (mDigits == null || !isValid()) {
            return null;
        }
        return concat(mDigits);
    }

    @Override
    @NonNull
    public String toString() {
        return "ISBN{"
               + "mDigits=" + mDigits
               + ", mStrictIsbn=" + mStrictIsbn
               + ", mIsUpc=" + mIsUpc
               + '}';
    }

    /**
     * This method does NOT check if the actual digits form a valid ISBN.
     * <p>
     * Allows and ignore '-' and space characters.
     *
     * @param isbnStr to convert
     *
     * @return list of digits
     *
     * @throws NumberFormatException if conversion fails
     */
    @NonNull
    private List<Integer> isbnToDigits(@NonNull final CharSequence isbnStr)
            throws NumberFormatException {
        // the digit '10' represented as 'X' in an isbn indicates we got to the end
        boolean foundX = false;

        List<Integer> digits = new ArrayList<>();

        for (int i = 0; i < isbnStr.length(); i++) {
            final char c = isbnStr.charAt(i);
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

            } else if (c == '-' || c == ' ') {
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
     * @param upcStr UPC code, example: "070999 00225 530054", "00225" (price) and "5"
     *               will be discarded to construct the isbn.
     *
     * @return list of digits or empty on failure
     *
     * @throws NumberFormatException           on failure to analyse the string
     * @throws StringIndexOutOfBoundsException theoretically we should not get this, as we *should*
     *                                         not pass a string which is to short. But...
     */
    @NonNull
    private List<Integer> upcToDigits(@NonNull final CharSequence upcStr)
            throws NumberFormatException, StringIndexOutOfBoundsException {

        String upc = WHITESPACE_PATTERN.matcher(upcStr).replaceAll("");

        String isbnPrefix = UPC_2_ISBN_PREFIX.get(upc.substring(0, 6));
        if (isbnPrefix == null) {
            return new ArrayList<>();
        }

        List<Integer> digits = isbnToDigits(isbnPrefix + upc.substring(12));

        // and add the new checksum
        digits.add(getCheckDigit(digits));
        return digits;
    }

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
        if (mDigits == null || !isValid()
            || cmp.mDigits == null || !cmp.isValid()) {
            return false;
        }

        // same length ? they should match exactly
        if (mDigits.size() == cmp.mDigits.size()) {
            return Objects.equals(mDigits, cmp.mDigits);
        }

        // Both are valid ISBN codes and we know the lengths are either 10 or 13
        // when we get here. So ... compare the significant digits:
        // ISBN13: skip the first 3 character, and don't include the checksum.
        // ISBN10: don't include the checksum.
        if (mDigits.size() == 10) {
            return digitsMatch(mDigits, 0, cmp.mDigits, 3);
        } else {
            return digitsMatch(mDigits, 3, cmp.mDigits, 0);
        }
    }

    /**
     * Check if all digits are the same.
     */
    private boolean digitsMatch(@NonNull final List<Integer> digits1,
                                int posFrom1,
                                @NonNull final List<Integer> digits2,
                                int posFrom2) {
        for (int i = 0; i < 9; i++) {
            if (!digits1.get(posFrom1++).equals(digits2.get(posFrom2++))) {
                return false;
            }
        }
        return true;
    }
}
