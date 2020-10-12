/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * This class name is a bit of a misnomer by now.
 * It represents an ISBN-10, ISBN-13, EAN-13 or UPC_A code.
 * UPC_A numbers are converted to ISB-10 if possible.
 *
 * <p>
 * See <a href="http://en.wikipedia.org/wiki/International_Standard_Book_Number">ISBN</a>
 * <p>
 * ISBN stands for International Standard Book Number.
 * Every book is assigned a unique ISBN-10 and ISBN-13 when published.
 * <p>
 * <a href="https://www.amazon.com/gp/seller/asin-upc-isbn-info.html">asin-upc-isbn</a>
 * <p>
 * Lots of info:
 * <a href="https://isbn-information.com">https://isbn-information.com</a>
 *
 * <a href="https://en.wikipedia.org/wiki/International_Article_Number#GS1_prefix">EAN</a>
 * <p>
 * The EAN "country code" 978 (and later 979) has been allocated since the 1980s to reserve
 * a Unique Country Code (UCC) prefix for EAN identifiers of published books, regardless of
 * country of origin, so that the EAN space can catalog books by ISBNs[3] rather than
 * maintaining a redundant parallel numbering system. This is informally known as "Bookland".
 * The prefix 979 with first digit 0 is used for International Standard Music Number (ISMN)
 * and the prefix 977 indicates International Standard Serial Number (ISSN).
 */
public class ISBN {

    private static final String ERROR_INVALID_ISBN = "isbn must be valid";

    public static final int VALIDITY_NONE = 0;
    public static final int VALIDITY_LOOSE = 1;
    public static final int VALIDITY_STRICT = 2;

    public static final int TYPE_ISBN10 = 1;
    /** ISBN-13 is a subtype of EAN-13. See {@link #isType(int)}. */
    public static final int TYPE_ISBN13 = 2;
    static final int TYPE_EAN13 = 3;
    static final int TYPE_UPC_A = 4;
    static final int TYPE_INVALID = 0;

    /** Log tag. */
    private static final String TAG = "ISBN";
    private static final String ERROR_WRONG_SIZE = "Wrong size: ";
    private static final String ERROR_INVALID_CHARACTER = "Invalid character: ";
    private static final String ERROR_UNABLE_TO_CONVERT =
            "Unable to convert type: %1$s to %2$s";
    private static final String ERROR_X_CAN_ONLY_BE_AT_THE_END_OF_AN_ISBN_10 =
            "X can only be at the end of an ISBN-10";

    /**
     * <a href="https://getsatisfaction.com/deliciousmonster/topics/cant-scan-a-barcode-with-5-digit-extension-no-barcodes-inside">Info</a>
     * <p>
     * The extended barcode combined with the UPC_A vendor prefix can be used to
     * reconstruct the ISBN.
     * Example:
     * Del Rey edition of Larry Niven's _World of Ptavvs_,
     * which says it's "Ninth Printing: September 1982" on the copyright page.
     * There is no ISBN/EAN barcode on the inside cover.
     * The back cover has an extended UPC_A code "0 70999 00225 5 30054".
     * <p>
     * "070999" in the first part of the UPC_A means that the ISBN starts with "0-345"
     * see <a href="https://www.eblong.com/zarf/bookscan/shelvescripts/upc-map">upc-map</a>
     * making it a Ballantine book
     * "00225" indicates the price
     * That gets us:
     * ISBN-10 is "0-345-30054-?"
     * The ISBN check digit is omitted from the bar code but can be calculated;
     * in this case it's 8
     * <p>
     * UPC_A Prefix -- ISBN Prefix mapping file (may not be complete)
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

    /** The type of code, determined at creation time. */
    @Type
    private final int mType;
    /** the code as a pure text string. The raw input string for invalid codes. */
    @NonNull
    private final String mAsText;
    /** kept for faster conversion between formats. {@code null} for invalid codes. */
    @Nullable
    private final List<Integer> mDigits;

    /**
     * Constructor for a strict ISBN.
     * Accepts {@code null}.
     * Accepts (and removes) ' ' and '-' characters.
     *
     * @param str string to digest
     */
    public ISBN(@Nullable final String str) {
        this(str, true);
    }

    /**
     * Constructor.
     * Accepts {@code null}.
     * Accepts (and removes) ' ' and '-' characters.
     *
     * @param str        string to digest
     * @param strictIsbn Flag: {@code true} to strictly allow ISBN codes.
     */
    public ISBN(@Nullable final String str,
                final boolean strictIsbn) {

        List<Integer> digits = null;
        @Type
        int type = TYPE_INVALID;

        if (str != null && !str.isEmpty()) {
            final String cleanStr = WHITESPACE_PATTERN.matcher(str).replaceAll("");
            if (!cleanStr.isEmpty()) {
                // Determine the digits and the type.
                try {
                    digits = toDigits(cleanStr);
                    type = getType(digits);

                    if (type == TYPE_UPC_A) {
                        // is this UPC_A convertible to ISBN ?
                        final String isbnPrefix = UPC_2_ISBN_PREFIX.get(cleanStr.substring(0, 6));
                        if (isbnPrefix != null) {
                            // yes, convert to ISBN-10
                            digits = toDigits(isbnPrefix + cleanStr.substring(12));
                            digits.add(calculateIsbn10Checksum(digits));
                            type = TYPE_ISBN10;
                        }
                    }
                } catch (@NonNull final NumberFormatException e) {
                    if (BuildConfig.DEBUG /* always */) {
                        Logger.d(TAG, e, "str=`" + str + '`');
                    }
                }

                // strict ISBN required?
                if (strictIsbn && type != TYPE_ISBN10 && type != TYPE_ISBN13) {
                    type = TYPE_INVALID;
                }
            }
        }

        // Make sure the internal status is uniform.
        if (type == TYPE_INVALID) {
            mDigits = null;
            mAsText = str != null ? str : "";
            mType = TYPE_INVALID;

        } else {
            mDigits = digits;
            mAsText = concat(mDigits);
            mType = type;
        }
    }

    /**
     * Constructor.
     *
     * <ul>Accepts as valid:
     *      <li>ISBN-10</li>
     *      <li>ISBN-13</li>
     *      <li>EAN-13</li>
     *      <li>generic UPC_A</li>
     * </ul>
     * <p>
     * Accepts (and removes) ' ' and '-' characters.
     *
     * @param str the string to digest
     *
     * @return instance
     */
    @NonNull
    public static ISBN create(@NonNull final String str) {
        return new ISBN(str, false);
    }

    /**
     * Constructor.
     *
     * <ul>Accepts as valid:
     *      <li>ISBN-10</li>
     *      <li>ISBN-13</li>
     *      <li>UPC_A <strong>if convertible to ISBN-10</strong></li>
     * </ul>
     * <ul>Rejects as invalid:
     *      <li>EAN-13</li>
     *      <li>generic UPC_A</li>
     * </ul>
     * <p>
     * Accepts (and removes) ' ' and '-' characters.
     *
     * @param isbnStr the string to digest
     *
     * @return instance
     */
    @NonNull
    public static ISBN createISBN(@NonNull final String isbnStr) {
        return new ISBN(isbnStr, true);
    }

    /**
     * Get the user preferred ISBN validity level check for (by the user) editing ISBN codes.
     *
     * @param preferences Global preferences
     *
     * @return {@link Validity} level
     */
    @Validity
    public static int getEditValidityLevel(@NonNull final SharedPreferences preferences) {
        return Prefs.getIntListPref(preferences, Prefs.pk_edit_book_isbn_checks, VALIDITY_LOOSE);
    }

    /**
     * Check if two codes are matching.
     *
     * @param isbnStr1   first code
     * @param isbnStr2   second code
     * @param strictIsbn Flag: {@code true} to strictly allow ISBN codes.
     *
     * @return {@code true} if the 2 codes match.
     */
    public static boolean matches(@Nullable final String isbnStr1,
                                  @Nullable final String isbnStr2,
                                  final boolean strictIsbn) {
        if (isbnStr1 == null || isbnStr2 == null) {
            return false;
        }

        // Full check needed ...if either one is invalid, we consider them different
        final ISBN o1 = new ISBN(isbnStr1, strictIsbn);
        if (!o1.isValid(strictIsbn)) {
            return false;
        }

        final ISBN o2 = new ISBN(isbnStr2, strictIsbn);
        if (!o2.isValid(strictIsbn)) {
            return false;
        }

        return o1.equals(o2);
    }

    /**
     * Convenience method to check validity when there is no need to keep the object.
     *
     * @param isbnStr to check
     *
     * @return validity
     */
    public static boolean isValidIsbn(@Nullable final String isbnStr) {
        if (isbnStr == null || isbnStr.isEmpty()) {
            return false;
        }
        return new ISBN(isbnStr, true).isValid(true);
    }

    /**
     * Check the validity of an ISBN string.
     *
     * @param isbnStr to check
     *
     * @throws IllegalStateException if invalid
     */
    public static void requireValidIsbn(@Nullable final String isbnStr) {
        if (!isValidIsbn(isbnStr)) {
            throw new IllegalStateException(ERROR_INVALID_ISBN);
        }
    }

    /**
     * Convenience method to check validity when there is no need to keep the object.
     *
     * @param code to check
     *
     * @return validity
     */
    public static boolean isValid(@Nullable final String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        return new ISBN(code, false).isValid(false);
    }

    /**
     * Check if we have a valid code. Does not check for a specific type.
     *
     * @param strictIsbn Flag: {@code true} to strictly allow ISBN codes.
     *
     * @return validity
     */
    public boolean isValid(final boolean strictIsbn) {
        if (strictIsbn) {
            return mType == TYPE_ISBN13 || mType == TYPE_ISBN10;
        } else {
            return mType != TYPE_INVALID;
        }
    }

    @VisibleForTesting
    boolean isType(@Type final int type) {
        if (type == TYPE_EAN13) {
            // ISBN 13 is a sub-type of EAN13
            return mType == TYPE_EAN13 || mType == TYPE_ISBN13;
        }
        return mType == type;
    }

    /**
     * Check if the ISBN code is either an ISBN-10,
     * or an ISBN-13 which can be converted to an ISBN-10.
     *
     * @return {@code true} if compatible; {@code false} if not compatible or not a valid ISBN
     */
    public boolean isIsbn10Compat() {
        return mType == TYPE_ISBN10 || (mType == TYPE_ISBN13 && mAsText.startsWith("978"));
    }

    /**
     * Get the code as a text string based on the original type. No conversions are done.
     *
     * @return string
     */
    @NonNull
    public String asText() {
        return mAsText;
    }

    /**
     * Get the ISBN as a text string converted to the given type.
     *
     * <strong>WARNING:</strong> when converting an ISBN-13 to ISBN-10,
     * you must call {@link #isIsbn10Compat()} prior to avoid this method throwing an exception.
     *
     * @param type to convert to
     *
     * @return string
     *
     * @throws NumberFormatException on failure
     */
    @SuppressLint("SwitchIntDef")
    @NonNull
    public String asText(@Type final int type)
            throws NumberFormatException {

        if (type == TYPE_INVALID) {
            return mAsText;
        }
        Objects.requireNonNull(mDigits, "mDigits");

        switch (type) {
            case TYPE_ISBN13: {
                // already in ISBN-13 format?
                if (mType == TYPE_ISBN13) {
                    return mAsText;
                }

                // Must be ISBN10 or we cannot convert
                if (mType == TYPE_ISBN10) {
                    final List<Integer> digits = new ArrayList<>();
                    // standard prefix 978
                    digits.add(9);
                    digits.add(7);
                    digits.add(8);

                    // copy the first 9 digits
                    for (int i = 0; i < 9; i++) {
                        digits.add(mDigits.get(i));
                    }
                    // and add the new checksum
                    digits.add(calculateEan13Checksum(digits));

                    return concat(digits);
                }
                break;
            }
            case TYPE_ISBN10: {
                // already in ISBN-10 format?
                if (mType == TYPE_ISBN10) {
                    return mAsText;
                }

                // must be ISBN13 *AND* compatible with converting to ISBN10
                if (mType == TYPE_ISBN13 || !mAsText.startsWith("978")) {
                    // drop the first 3 digits, and copy the next 9.
                    final List<Integer> digits = new ArrayList<>();
                    for (int i = 3; i < 12; i++) {
                        digits.add(mDigits.get(i));
                    }
                    // and add the new checksum
                    digits.add(calculateIsbn10Checksum(digits));
                    return concat(digits);
                }
                break;
            }

            case TYPE_EAN13:
                // No conversion
                if (mType == TYPE_EAN13) {
                    return mAsText;
                }
                break;

            case TYPE_UPC_A:
                // No conversion
                if (mType == TYPE_UPC_A) {
                    return mAsText;
                }
                break;

            default:
                break;
        }

        throw new NumberFormatException(
                String.format(ERROR_UNABLE_TO_CONVERT, mType, type));
    }

    /**
     * Converts a string containing digits 0..9 and 10 == 'X'/'x' to a list of digits.
     * It enforces that the X character is only present at the end of a 10 character string.
     * <p>
     * This method does NOT check on a specific length (except as above) and whether
     * the actual digits form a valid code.
     *
     * @param str to convert
     *
     * @return list of digits
     *
     * @throws NumberFormatException on failure
     */
    @NonNull
    private List<Integer> toDigits(@NonNull final CharSequence str)
            throws NumberFormatException {
        // the digit '10' is represented as 'X'
        boolean foundX = false;

        final List<Integer> digits = new ArrayList<>();

        for (int i = 0; i < str.length(); i++) {
            final char c = str.charAt(i);
            final int digit;
            if (Character.isDigit(c)) {
                if (foundX) {
                    throw new NumberFormatException(ERROR_X_CAN_ONLY_BE_AT_THE_END_OF_AN_ISBN_10);
                }
                digit = Integer.parseInt(Character.toString(c));

            } else if ((c == 'X' || c == 'x') && digits.size() == 9) {
                if (foundX) {
                    throw new NumberFormatException(ERROR_X_CAN_ONLY_BE_AT_THE_END_OF_AN_ISBN_10);
                }
                // 'X'
                digit = 10;
                foundX = true;

            } else {
                throw new NumberFormatException(ERROR_INVALID_CHARACTER + c);
            }

            digits.add(digit);
        }
        return digits;
    }

    /**
     * Check if the passed digits form a valid code.
     *
     * @param digits to check
     *
     * @return type
     */
    @Type
    private int getType(@Nullable final List<Integer> digits)
            throws NumberFormatException {
        // don't test the type here, this test is used to determine the type!

        if (digits == null) {
            return TYPE_INVALID;
        }

        final int size = digits.size();

        if (size == 10) {
            if (calculateIsbn10Checksum(digits) == digits.get(digits.size() - 1)) {
                return TYPE_ISBN10;
            }

        } else if (size == 13) {
            if (calculateEan13Checksum(digits) == digits.get(digits.size() - 1)) {
                // check if it starts with 978 or 979
                if (digits.size() == 13
                    && digits.get(0) == 9
                    && digits.get(1) == 7
                    && (digits.get(2) == 8 || digits.get(2) == 9)) {
                    return TYPE_ISBN13;
                } else {
                    return TYPE_EAN13;
                }
            }
        } else if (digits.size() > 11) {
            // a UPC_A barcode might be longer than 12 characters due to allowed extensions.
            // But only 12 characters are 'the' UPC_A code.
            if (calculateUpcAChecksum(digits.subList(0, 12)) == digits.get(11)) {
                return TYPE_UPC_A;
            }
        }

        return TYPE_INVALID;
    }

    /**
     * Calculate the check-digit (checksum) for the given digits.
     * This calculation is valid for ISBN-10 only
     *
     * @param digits list with the digits, either 10 or 9
     *
     * @return the check digit.
     *
     * @throws NumberFormatException on failure
     */
    private int calculateIsbn10Checksum(@NonNull final List<Integer> digits)
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

    /**
     * Calculate the check-digit (checksum) for the given digits.
     * This calculation is valid for EAN-13 / ISBN-13 only
     *
     * @param digits list with the digits, either 13 or 12
     *
     * @return the check digit.
     *
     * @throws NumberFormatException on failure
     */
    private int calculateEan13Checksum(@NonNull final List<Integer> digits)
            throws NumberFormatException {
        final int len = digits.size();
        if (len < 12 || len > 13) {
            throw new NumberFormatException(ERROR_WRONG_SIZE + len);
        }
        int sum = 0;
        // 1. Take the first 12 digits of the 13-digit EAN/ISBN
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
    private int calculateUpcAChecksum(@NonNull final List<Integer> digits)
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

    /**
     * Get the concatenated digits. Digit 10 is always returned as <strong>uppercase</strong> 'X'.
     *
     * @param digits the list of digits
     *
     * @return the code as a string.
     */
    private String concat(@NonNull final Iterable<Integer> digits) {
        final StringBuilder sb = new StringBuilder();
        for (final int d : digits) {
            if (d == 10) {
                sb.append('X');
            } else {
                sb.append(d);
            }
        }
        return sb.toString();
    }

    @Override
    @NonNull
    public String toString() {
        return "ISBN{"
               + "mType=" + mType
               + ", mAsText=" + mAsText
               + ", mDigits=" + mDigits
               + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDigits);
    }

    /**
     * Two ISBN objects are equal if they match these checks (in this order).
     * <ol>
     *      <li>objects being '==' ? MATCH</li>
     *      <li>Either or both == null ? no match</li>
     *      <li>Incoming object is not an ISBN ? no match</li>
     *      <li>Either or both invalid, compare their digits for exact match</li>
     *      <li>Same length, compare their digits for exact match</li>
     *      <li>compare the 9 significant isbn digits for equality.</li>
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
        final ISBN cmp = (ISBN) obj;

        // If either is invalid, no match
        if (mType == TYPE_INVALID || cmp.isType(TYPE_INVALID)) {
            return false;
        }
        if (mDigits == null || cmp.mDigits == null) {
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
            return digitsMatch(9, mDigits, 0, cmp.mDigits, 3);
        } else {
            return digitsMatch(9, mDigits, 3, cmp.mDigits, 0);
        }
    }

    /**
     * Check if all relevant digits are the same.
     */
    private boolean digitsMatch(@SuppressWarnings("SameParameterValue") final int length,
                                @NonNull final List<Integer> digits1,
                                int posFrom1,
                                @NonNull final List<Integer> digits2,
                                int posFrom2) {
        for (int i = 0; i < length; i++) {
            if (!digits1.get(posFrom1++).equals(digits2.get(posFrom2++))) {
                return false;
            }
        }
        return true;
    }

    @IntDef({TYPE_INVALID, TYPE_ISBN10, TYPE_ISBN13, TYPE_EAN13, TYPE_UPC_A})
    @Retention(RetentionPolicy.SOURCE)
    @interface Type {

    }

    @IntDef({VALIDITY_NONE, VALIDITY_LOOSE, VALIDITY_STRICT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Validity {

    }

    public static class CleanupTextWatcher
            implements TextWatcher {

        @NonNull
        private final TextInputEditText mEditText;
        @Validity
        private int mIsbnValidityCheck;

        /**
         * Constructor.
         *
         * @param editText          the view to watch
         * @param isbnValidityCheck validity check-level for ISBN codes
         */
        public CleanupTextWatcher(@NonNull final TextInputEditText editText,
                                  @Validity final int isbnValidityCheck) {
            mEditText = editText;
            mIsbnValidityCheck = isbnValidityCheck;
        }

        public void setValidityLevel(@Validity final int isbnValidityCheck) {
            mIsbnValidityCheck = isbnValidityCheck;
            clean(mEditText.getEditableText());
        }

        @Override
        public void beforeTextChanged(@NonNull final CharSequence s,
                                      final int start,
                                      final int count,
                                      final int after) {

        }

        @Override
        public void onTextChanged(@NonNull final CharSequence s,
                                  final int start,
                                  final int before,
                                  final int count) {

        }

        @Override
        public void afterTextChanged(@NonNull final Editable editable) {
            clean(mEditText.getEditableText());
        }

        private void clean(@Nullable final Editable editable) {
            if (mIsbnValidityCheck != VALIDITY_NONE && editable != null && editable.length() > 0) {
                final String text = editable.toString().trim();
                if (text.isEmpty()) {
                    return;
                }

                if (mIsbnValidityCheck == VALIDITY_LOOSE) {
                    // Text representation of ISBN-13/10 string is often
                    // split in groups of digits with '-' in between.
                    // This is, as observed, usually 10 + 3 '-' (or 10 + 2 '-' + 'x'),
                    // or 13 + 4 '-' characters.
                    // Examples of this pattern:
                    // 978-1-23456-789-0
                    // 978-1-2345-6789-0
                    // 978-1-234-56789-0
                    // 978-1-23-456789-0
                    // 1-234-56789-x
                    //
                    // Note we DELIBERATELY do not attempt to clean other length.
                    // (at first we did... this proved to be annoying to the user who wanted
                    // to enter a custom code. Even this 13/17 length rule might be to restrictive?)
                    if (text.length() != 13 && text.length() != 17) {
                        return;
                    }
                    for (final char c : text.toCharArray()) {
                        if (!Character.isDigit(c) && c != '-' && c != 'x' && c != 'X') {
                            // non isbn character, leave it.
                            return;
                        }
                    }
                }

                // strict, or we decided we can clean up anyhow.
                boolean x = false;
                final StringBuilder sb = new StringBuilder();
                // strip out all but digits and the 'x' character
                for (final char c : text.toCharArray()) {
                    if (Character.isDigit(c)) {
                        sb.append(c);
                    } else if (c == 'X' || c == 'x') {
                        x = true;
                    }
                }

                if (x) {
                    sb.append('X');
                }

                // case sensitive, so 'x' can be replaced with 'X'
                if (!sb.toString().equals(text)) {
                    mEditText.removeTextChangedListener(this);
                    editable.replace(0, editable.length(), sb.toString());
                    mEditText.addTextChangedListener(this);
                }
            }
        }
    }

    public static class ValidationTextWatcher
            implements TextWatcher {

        @NonNull
        private final TextInputLayout mLayout;
        @NonNull
        private final TextInputEditText mEditText;

        @Nullable
        private String mAltIsbn;
        @Validity
        private int mIsbnValidityCheck;

        /**
         * Constructor.
         *
         * @param layoutView        envelope layout
         * @param editText          the view to watch
         * @param isbnValidityCheck validity check-level for ISBN codes
         */
        public ValidationTextWatcher(@NonNull final TextInputLayout layoutView,
                                     @NonNull final TextInputEditText editText,
                                     @Validity final int isbnValidityCheck) {
            mLayout = layoutView;
            mLayout.setStartIconVisible(false);

            mEditText = editText;
            mIsbnValidityCheck = isbnValidityCheck;
        }

        public void setValidityLevel(@Validity final int isbnValidityCheck) {
            mIsbnValidityCheck = isbnValidityCheck;
            validate(mEditText.getEditableText());
        }

        @Override
        public void beforeTextChanged(@NonNull final CharSequence s,
                                      final int start,
                                      final int count,
                                      final int after) {

        }

        @Override
        public void onTextChanged(@NonNull final CharSequence s,
                                  final int start,
                                  final int before,
                                  final int count) {

        }

        @Override
        public void afterTextChanged(@NonNull final Editable editable) {
            validate(editable);
        }

        /**
         * Validate the input, and set the start-icon visibility/OnClickListener as needed.
         * Does NOT modify the editable.
         *
         * @param editable to validate; will not be modified.
         */
        private void validate(@Nullable final Editable editable) {
            if (editable != null && editable.length() > 0) {
                final String str = editable.toString().trim();
                switch (str.length()) {
                    case 13: {
                        final ISBN isbn = new ISBN(str, mIsbnValidityCheck == VALIDITY_STRICT);
                        if (isbn.isIsbn10Compat()) {
                            mAltIsbn = isbn.asText(TYPE_ISBN10);
                            mLayout.setStartIconVisible(true);
                            mLayout.setStartIconOnClickListener(
                                    view -> mEditText.setText(mAltIsbn));
                            return;
                        }
                        break;
                    }

                    case 10: {
                        final ISBN isbn = new ISBN(str, mIsbnValidityCheck == VALIDITY_STRICT);
                        if (isbn.isValid(mIsbnValidityCheck == VALIDITY_STRICT)) {
                            mAltIsbn = isbn.asText(TYPE_ISBN13);
                            mLayout.setStartIconVisible(true);
                            mLayout.setStartIconOnClickListener(
                                    view -> mEditText.setText(mAltIsbn));
                            return;
                        }
                        break;
                    }

                    case 12: {
                        // UPC: indicate the code is valid, but disable the swap functionality
                        if (mIsbnValidityCheck != VALIDITY_STRICT) {
                            final ISBN isbn = new ISBN(str, false);
                            if (isbn.isValid(false)) {
                                mLayout.setStartIconVisible(true);
                                mLayout.setStartIconOnClickListener(null);
                                return;
                            }
                        }
                        break;

                    }

                    default:
                        break;
                }
            }

            mLayout.setStartIconVisible(false);
            mLayout.setStartIconOnClickListener(null);
        }
    }
}
