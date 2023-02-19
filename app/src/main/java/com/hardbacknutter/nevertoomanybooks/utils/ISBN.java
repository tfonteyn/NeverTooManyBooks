/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.content.Context;
import android.text.Editable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtTextWatcher;

/**
 * This class name is a bit of a misnomer by now.
 * See {@link Type} for all supported codes.
 * {@link Type#UpcA} numbers are converted to {@link Type#Isbn10} if possible.
 *
 * <p>
 * ISBN stands for International Standard Book Number.
 * Every book is assigned a unique ISBN-10 and ISBN-13 when published.
 * See <a href="http://en.wikipedia.org/wiki/International_Standard_Book_Number">ISBN</a>
 * See <a href="https://isbn-information.com">https://isbn-information.com</a>
 * <p>
 * An International Standard Serial Number (ISSN) is an eight-digit serial number used to uniquely
 * identify a serial publication, such as a magazine.
 * See <a href="https://en.wikipedia.org/wiki/International_Standard_Serial_Number">ISSN</a>
 * <p>
 * The International Standard Music Number or ISMN (ISO 10957) is a thirteen-character
 * alphanumeric identifier for printed music
 * See <a href="https://en.wikipedia.org/wiki/International_Standard_Music_Number">ISMN</a>
 * <p>
 * An Universal Product Code (UPC) is a barcode symbology that is widely used
 * worldwide for tracking trade items in stores.
 * See <a href="https://en.wikipedia.org/wiki/Universal_Product_Code">UPC_A</a>
 * <p>
 * An Amazon Standard Identification Number (ASIN) is a 10-character alphanumeric unique
 * identifier assigned by Amazon.com.
 * ISBN-10 codes are identical with ASIN codes (but not the reverse).
 * See <a href="https://en.wikipedia.org/wiki/Amazon_Standard_Identification_Number">ASIN</a>
 * <p>
 * The International Article Number (also known as European Article Number or EAN) is a
 * standard describing a barcode symbology and numbering system
 * See <a href="https://en.wikipedia.org/wiki/International_Article_Number">EAN</a>
 * and more specifically
 * <a href="https://en.wikipedia.org/wiki/International_Article_Number#GS1_prefix">
 * EAN GS1 prefix country code</a>
 * <p>
 * The EAN "country code" 978 (and later 979) has been allocated since the 1980s to reserve
 * a Unique Country Code (UCC) prefix for EAN identifiers of published books, regardless of
 * country of origin, so that the EAN space can catalog books by ISBNs rather than
 * maintaining a redundant parallel numbering system. This is informally known as "Bookland".
 * <p>
 * The prefix 979 with first digit 0 is used for International Standard Music Number
 * (ISMN a.k.a. "MusicLand")
 * <br>The prefix 977 indicates International Standard Serial Number (ISSN).
 */
public class ISBN {

    /** Log tag. */
    private static final String TAG = "ISBN";

    private static final String ERROR_WRONG_SIZE = "Wrong size: ";
    private static final String ERROR_UNABLE_TO_CONVERT =
            "Unable to convert type: %1$s to %2$s";
    private static final String ERROR_X_CAN_ONLY_BE_AT_THE_END_OF_AN_ISBN_10 =
            "X can only be at the end of an ISBN-10";

    /**
     * The extended barcode combined with the UPC_A vendor prefix can be used to
     * reconstruct the ISBN.
     * Example:
     * Del Rey edition of Larry Niven's "World of Ptavvs",
     * which says it's "Ninth Printing: September 1982" on the copyright page.
     * There is no ISBN/EAN barcode on the inside cover.
     * The back cover has an extended UPC_A code "0 70999 00225 5 30054".
     * <p>
     * "070999" in the first part of the UPC_A means that the ISBN starts with "0-345"
     * see <a href="https://www.eblong.com/zarf/bookscan/shelvescripts/upc-map">upc-map</a>
     * making it a Ballantine book.
     * "00225" indicates the price
     * "5" is the checksum on the 11 first digits (first 12-digits form the basic UPC code)
     * The extended part "30054" can be concatenated with the ISBN prefix "0-345"
     * <p>
     * That gets us a near complete ISBN-10 code: "0-345-30054-?"
     * The ISBN check digit is omitted from the bar code but can be calculated;
     * in this case it's 8, which makes the full ISBN "0-345-30054-8".
     *
     * @see <a href="https://getsatisfaction.com/deliciousmonster/topics/cant-scan-a-barcode-with-5-digit-extension-no-barcodes-inside">Info</a>
     */
    private static final Map<String, String> UPC_2_ISBN_PREFIX = new HashMap<>();
    /** Remove '-' and space chars. */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[ -]");

    static {
        // UPC_A Prefix -- ISBN Prefix mapping file (may not be complete)
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
    @NonNull
    private final Type codeType;
    /** The code as a pure text string. The raw input string for invalid codes. */
    @NonNull
    private final String codeText;
    /** Kept for faster conversion between formats. {@code null} for invalid codes. */
    @Nullable
    private final List<Integer> codeDigits;

    /**
     * Constructor.
     * <p>
     * With {@code strictIsbn == false}: accepts as valid:
     * <ul>
     *      <li>{@link Type#Isbn10}</li>
     *      <li>{@link Type#Isbn13}</li>
     *      <li>{@link Type#Ean13}</li>
     *      <li>{@link Type#Issn8}</li>
     *      <li>{@link Type#Ismn}</li>
     *      <li>generic {@link Type#UpcA}</li>
     * </ul>
     * </p>
     *
     * <p>
     * With {@code strictIsbn == true}: accepts as valid:
     * <ul>
     *      <li>{@link Type#Isbn10}</li>
     *      <li>{@link Type#Isbn13}</li>
     *      <li>{@link Type#UpcA} <strong>if convertible to {@link Type#Isbn10}</strong></li>
     * </ul>
     * Rejects as invalid:
     * <ul>
     *      <li>{@link Type#Ean13}</li>
     *      <li>{@link Type#Issn8}</li>
     *      <li>{@link Type#Ismn}</li>
     *      <li>generic {@link Type#UpcA}</li>
     * </ul>
     * </p>
     * <p>
     * Accepts {@code null} which results in {@code Type.Invalid}.
     * <p>
     * Accepts ' ' and '-' separator characters.
     *
     * @param text       string to digest
     * @param strictIsbn Flag: {@code true} to strictly allow ISBN codes.
     *                   {@code false} to also accept any other valid code.
     */
    public ISBN(@Nullable final String text,
                final boolean strictIsbn) {

        List<Integer> digits = null;
        Type type = Type.Invalid;

        if (text != null && !text.isEmpty()) {
            // Remove whitespace first for easier parsing.
            final String cleanStr = WHITESPACE_PATTERN.matcher(text).replaceAll("");
            if (!cleanStr.isEmpty()) {
                try {
                    digits = toDigits(cleanStr, strictIsbn);
                    type = getType(digits);

                    if (type == Type.UpcA) {
                        // is this UPC_A convertible to ISBN-10 ?
                        final String isbnPrefix = UPC_2_ISBN_PREFIX.get(cleanStr.substring(0, 6));
                        if (isbnPrefix != null) {
                            // yes, convert to ISBN-10
                            digits = toDigits(isbnPrefix + cleanStr.substring(12), false);
                            digits.add(calculateIsbn10Checksum(digits));
                            type = Type.Isbn10;
                        }
                    }
                } catch (@NonNull final NumberFormatException e) {
                    if (BuildConfig.DEBUG /* always */) {
                        ServiceLocator.getInstance().getLogger().d(TAG, e, "text=`" + text + '`');
                    }
                }

                // strict ISBN required?
                if (strictIsbn && type != Type.Isbn10 && type != Type.Isbn13) {
                    type = Type.Invalid;
                }
            }
        }

        // Make sure the internal status is uniform.
        if (type == Type.Invalid) {
            codeDigits = null;
            codeText = text != null ? text : "";
            codeType = Type.Invalid;

        } else {
            codeDigits = digits;
            codeText = concat(codeDigits);
            codeType = type;
        }
    }

    /**
     * Filter a string keeping only digits and 'X'.
     *
     * @param text string to parse
     *
     * @return stripped string; can be empty
     */
    @NonNull
    public static String cleanText(@Nullable final String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (final char c : text.toCharArray()) {
            // Allow an X anywhere; We're not validating here.
            if (Character.isDigit(c)) {
                sb.append(c);
            } else if (c == 'X' || c == 'x') {
                sb.append('X');
            }
        }
        return sb.toString();
    }

    /**
     * Takes a string which (hopefully) contains a 10 or 13 digit ISBN number,
     * and formats it in the traditional way with '-' characters.
     * Any non valid string is returned as-is;  a {@code null} becomes {@code ""}
     *
     * @param s to format
     *
     * @return dash formatted isbn
     */
    @NonNull
    public static String formatIsbn(@Nullable final String s) {
        if (s == null) {
            return "";

        } else if (s.length() == 10) {
            return s.substring(0, 2) + '-'
                   + s.substring(2, 6) + '-'
                   + s.substring(6, 9) + '-'
                   + s.charAt(9);

        } else if (s.length() == 13) {
            return s.substring(0, 3) + '-'
                   + s.substring(3, 5) + '-'
                   + s.substring(5, 9) + '-'
                   + s.substring(9, 12) + '-'
                   + s.charAt(12);
        } else {
            return s;
        }
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
            return codeType == Type.Isbn13 || codeType == Type.Isbn10;
        } else {
            return codeType != Type.Invalid;
        }
    }

    @VisibleForTesting
    public boolean isType(@NonNull final Type type) {
        if (type == Type.Ean13) {
            // ISBN-13 and ISSN-13 are sub-types of EAN13
            return codeType == Type.Ean13 || codeType == Type.Isbn13 || codeType == Type.Issn13;
        }
        return codeType == type;
    }

    /**
     * Check if the ISBN code is either an ISBN-10,
     * or an ISBN-13 which can be converted to an ISBN-10.
     *
     * @return {@code true} if compatible; {@code false} if not compatible or not a valid ISBN
     */
    public boolean isIsbn10Compat() {
        // reminder: no need to check UPC_A here, as we would have converted it already
        return codeType == Type.Isbn10 || codeType == Type.Isbn13 && codeText.startsWith("978");
    }

    /**
     * Get the code as a text string based on the original type. No conversions are done.
     *
     * @return string
     */
    @NonNull
    public String asText() {
        return codeText;
    }

    /**
     * Get the ISBN as a text string converted to the given type.
     * <p>
     * <strong>WARNING:</strong> when converting an ISBN-13 to ISBN-10,
     * you must call {@link #isIsbn10Compat()} prior to avoid this method throwing an exception.
     *
     * @param type to convert to
     *
     * @return string
     *
     * @throws NumberFormatException on failure
     */
    @NonNull
    public String asText(@NonNull final Type type)
            throws NumberFormatException {

        if (type == Type.Invalid) {
            return codeText;
        }
        Objects.requireNonNull(codeDigits, "codeDigits");

        switch (type) {
            case Isbn13: {
                if (codeType == Type.Isbn13) {
                    return codeText;
                }

                // Must be ISBN-10 to convert to 13 digits.
                if (codeType == Type.Isbn10) {
                    final List<Integer> digits = new ArrayList<>();
                    // standard prefix 978
                    digits.add(9);
                    digits.add(7);
                    digits.add(8);

                    // copy the first 9 digits
                    for (int i = 0; i < 9; i++) {
                        digits.add(codeDigits.get(i));
                    }
                    // and add the new checksum
                    digits.add(calculateEan13Checksum(digits));

                    return concat(digits);
                }
                break;
            }
            case Isbn10: {
                if (codeType == Type.Isbn10) {
                    return codeText;
                }

                // Must be ISBN-13 and compatible with ISBN-10
                if (codeType == Type.Isbn13 && codeText.startsWith("978")) {
                    // drop the first 3 digits, and copy the next 9.
                    final List<Integer> digits = new ArrayList<>();
                    for (int i = 3; i < 12; i++) {
                        digits.add(codeDigits.get(i));
                    }
                    // and add the new checksum
                    digits.add(calculateIsbn10Checksum(digits));
                    return concat(digits);
                }
                break;
            }
            case Issn8: {
                if (codeType == Type.Issn8) {
                    return codeText;
                }

                // Must be ISSN-13 and compatible with ISSN-8
                // Note that the vendor 2-digits are dropped as they are not part of ISSN itself.
                if (codeType == Type.Issn13) {
                    // drop the first 3 digits, and copy the next 7.
                    final List<Integer> digits = new ArrayList<>();
                    for (int i = 3; i < 10; i++) {
                        digits.add(codeDigits.get(i));
                    }
                    // and add the new checksum
                    digits.add(calculateIssnChecksum(digits));
                    return concat(digits);
                }
                break;
            }
            case Issn13: {
                // No conversions possible
                if (codeType == Type.Issn13) {
                    return codeText;
                }
                break;
            }
            case Ismn: {
                // No conversions possible
                if (codeType == Type.Ismn) {
                    return codeText;
                }
                break;
            }
            case Ean13: {
                // No conversions possible but ISBN-13 and ISSN-13 are valid subtypes.
                if (codeType == Type.Ean13 || codeType == Type.Isbn13 || codeType == Type.Issn13) {
                    return codeText;
                }
                break;
            }
            case UpcA: {
                // No conversions possible. Any ISBN-10 compatible UPC number was already
                // converted in the class constructor.
                if (codeType == Type.UpcA) {
                    return codeText;
                }
                break;
            }
            default:
                break;
        }

        throw new NumberFormatException(
                String.format(ERROR_UNABLE_TO_CONVERT, codeType, type));
    }

    /**
     * Converts a string containing digits 0..9 and 'X'/'x' to a list of digits.
     * <p>
     * This method does NOT check on a specific length nor whether the input is a valid code.
     * <p>
     * As soon as an 'X' is found, we return the digits found up to then (including the 'X').
     * <p>
     * If an illegal character is found, we return the digits found up to then
     * (excluding the illegal character).
     *
     * @param text       to convert
     * @param strictIsbn enforces that the X character is only present at the end
     *                   of a 10 character string; i.e. for ISBN10 codes.
     *
     * @return list of digits
     *
     * @throws NumberFormatException on failure
     */
    @NonNull
    private List<Integer> toDigits(@NonNull final CharSequence text,
                                   final boolean strictIsbn)
            throws NumberFormatException {

        final List<Integer> digits = new ArrayList<>();

        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (Character.isDigit(c)) {
                digits.add(Integer.parseInt(Character.toString(c)));

            } else if (c == 'X' || c == 'x') {
                digits.add(10);

                if (strictIsbn && digits.size() != 10) {
                    throw new NumberFormatException(ERROR_X_CAN_ONLY_BE_AT_THE_END_OF_AN_ISBN_10);
                }
                // an X is only allowed at the end of the text
                // Whether we are at the end or not, just stop parsing here and return
                return digits;

            } else {
                // Invalid character found: don't throw; just return whatever we got up to now.
                return digits;
            }
        }
        return digits;
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
    private Type getType(@Nullable final List<Integer> digits)
            throws NumberFormatException {

        if (digits == null || digits.isEmpty()) {
            return Type.Invalid;
        }

        final int size = digits.size();

        if (size == 8) {
            if (calculateIssnChecksum(digits) == digits.get(size - 1)) {
                return Type.Issn8;
            }
        } else if (size == 10) {
            if (calculateIsbn10Checksum(digits) == digits.get(size - 1)) {
                return Type.Isbn10;
            }
        } else if (size == 13) {
            if (calculateEan13Checksum(digits) == digits.get(size - 1)) {
                // Prefix 978 is "Bookland"
                if (digits.get(0) == 9 && digits.get(1) == 7 && digits.get(2) == 8) {
                    return Type.Isbn13;

                } else if (digits.get(0) == 9 && digits.get(1) == 7 && digits.get(2) == 9) {
                    if (digits.get(3) == 0) {
                        // Prefix 979 with first digit 0 is "Musicland"
                        return Type.Ismn;
                    } else {
                        // non-0 is "Bookland"... we PRESUME, it's not entirely clear
                        // if these are simply 'reserved' or actual books.
                        return Type.Isbn13;
                    }
                } else if (digits.get(0) == 9 && digits.get(1) == 7 && digits.get(2) == 7) {
                    // Prefix 977 are periodicals; an ISSN packed in an EAN-13
                    return Type.Issn13;

                } else {
                    // it's a generic EAN-13
                    return Type.Ean13;
                }
            }
        } else if (size >= 12) {
            // a UPC barcode might be longer than 12 characters due to allowed extensions.
            // But only the first 12 characters are 'the' UPC_A code.
            if (calculateUpcAChecksum(digits.subList(0, 12)) == digits.get(11)) {
                return Type.UpcA;
            }
        }

        return Type.Invalid;
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
     * This calculation is valid for ISSN only
     *
     * @param digits list with the digits, either 8 or 7
     *
     * @return the check digit.
     *
     * @throws NumberFormatException on failure
     */
    private int calculateIssnChecksum(@NonNull final List<Integer> digits)
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
     * Get the concatenated digits. Digit 10 is always returned as '<strong>X</strong>'.
     *
     * @param digits the list of digits
     *
     * @return the code as a string.
     */
    @NonNull
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
               + "codeType=" + codeType
               + ", codeText=" + codeText
               + ", codeDigits=" + codeDigits
               + '}';
    }

    @Override
    public int hashCode() {
        // only use the 'codeText' if we have no digits!
        return Objects.hash(codeType, Objects.requireNonNullElse(codeDigits, codeText));
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final ISBN cmp = (ISBN) obj;

        // Reminder: do not compare 'codeText' !

        // Either one is invalid ? No match.
        if (codeType == Type.Invalid || cmp.isType(Type.Invalid)) {
            return false;
        }

        // No digits ? No match.
        if (codeDigits == null || cmp.codeDigits == null) {
            return false;
        }

        // Same length ? they should match exactly. This covers all codes with the same length.
        if (codeDigits.size() == cmp.codeDigits.size()) {
            return Objects.equals(codeDigits, cmp.codeDigits);
        }

        // Lastly, different but compatible length/codes.

        // ISBN-10 and ISBN-13:
        // Compare the 9 significant digits:
        // ISBN10: don't include the checksum -> 0..9
        // ISBN13: skip the first 3 character, and don't include the checksum -> 3..12
        if (codeDigits.size() == 10 && cmp.codeDigits.size() == 13) {
            return codeDigits.subList(0, 9).equals(cmp.codeDigits.subList(3, 12));

        } else if (codeDigits.size() == 13 && cmp.codeDigits.size() == 10) {
            return codeDigits.subList(3, 12).equals(cmp.codeDigits.subList(0, 9));
        }

        // ISSN and EAN-13
        // Compare the 7 significant digits:
        // ISSN: don't include the checksum -> 0..7
        // EAN-13: skip the first 3 character, and don't include the remainder -> 3..11
        if (codeDigits.size() == 8 && cmp.codeDigits.size() == 13) {
            return codeDigits.subList(0, 7).equals(cmp.codeDigits.subList(3, 11));

        } else if (codeDigits.size() == 13 && cmp.codeDigits.size() == 8) {
            return codeDigits.subList(3, 11).equals(cmp.codeDigits.subList(0, 7));
        }

        return false;
    }

    public enum Type {
        Invalid,

        /** The original ISBN number. 10 digits. */
        Isbn10,
        /** ISBN-13 is a subtype of EAN-13. 13 digits. */
        Isbn13,
        /** Generic 13 digit barcode. */
        Ean13,
        /** Generic product barcode. Minimum 12 digits but can be any length. */
        UpcA,
        /** Periodicals. 8 digits. */
        Issn8,
        /** Periodicals. subtype of EAN-13. 13 digits. */
        Issn13,
        /** Sheet Music. 13 digits. */
        Ismn
    }

    /**
     * Describes how harsh we check for valid codes. This is a user-setting.
     */
    public enum Validity {
        None(0),
        Loose(1),
        Strict(2);

        private final int value;

        Validity(final int value) {
            this.value = value;
        }

        /**
         * Get the user preferred ISBN validity level check for (by the user) editing ISBN codes.
         *
         * @param context Current context
         *
         * @return Validity level
         */
        @NonNull
        public static Validity getLevel(@NonNull final Context context) {

            final int value = Prefs.getIntListPref(context, Prefs.pk_edit_book_isbn_checks,
                                                   Loose.value);
            switch (value) {
                case 2:
                    return Strict;
                case 1:
                    return Loose;
                case 0:
                default:
                    return None;
            }
        }
    }

    public static class CleanupTextWatcher
            implements ExtTextWatcher {

        @NonNull
        private final TextInputEditText editText;
        @NonNull
        private Validity isbnValidityCheck;

        /**
         * Constructor.
         *
         * @param editText          the view to watch
         * @param isbnValidityCheck validity check-level for ISBN codes
         */
        public CleanupTextWatcher(@NonNull final TextInputEditText editText,
                                  @NonNull final Validity isbnValidityCheck) {
            this.editText = editText;
            this.isbnValidityCheck = isbnValidityCheck;
        }

        public void setValidityLevel(@NonNull final Validity isbnValidityCheck) {
            this.isbnValidityCheck = isbnValidityCheck;
            clean(editText.getEditableText());
        }

        @Override
        public void afterTextChanged(@NonNull final Editable editable) {
            clean(editText.getEditableText());
        }

        private void clean(@Nullable final Editable editable) {
            if (isbnValidityCheck != Validity.None && editable != null && editable.length() > 0) {
                final String text = editable.toString().trim();
                if (text.isEmpty()) {
                    return;
                }

                if (isbnValidityCheck == Validity.Loose) {
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
                    // Note we DELIBERATELY do not attempt to clean other lengths.
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

                // Validity.Strict, or we decided we can clean up anyhow.
                final String cleaned = cleanText(text);
                if (!cleaned.equals(text)) {
                    editText.removeTextChangedListener(this);
                    editable.replace(0, editable.length(), cleaned);
                    editText.addTextChangedListener(this);
                }
            }
        }
    }

    public static class ValidationTextWatcher
            implements ExtTextWatcher {

        @NonNull
        private final TextInputLayout layout;
        @NonNull
        private final TextInputEditText editText;

        /** The alternative ISBN text - 10/13 opposite of editText. */
        @Nullable
        private String altIsbn;
        @NonNull
        private Validity isbnValidityCheck;

        /**
         * Constructor.
         *
         * @param layoutView        TIL layout View
         * @param editText          the View to watch
         * @param isbnValidityCheck validity check-level for ISBN codes
         */
        public ValidationTextWatcher(@NonNull final TextInputLayout layoutView,
                                     @NonNull final TextInputEditText editText,
                                     @NonNull final Validity isbnValidityCheck) {
            layout = layoutView;
            layout.setStartIconVisible(false);

            this.editText = editText;
            this.isbnValidityCheck = isbnValidityCheck;
        }

        public void setValidityLevel(@NonNull final Validity isbnValidityCheck) {
            this.isbnValidityCheck = isbnValidityCheck;
            validate(editText.getEditableText());
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
                        final ISBN isbn = new ISBN(str, isbnValidityCheck == Validity.Strict);
                        if (isbn.isIsbn10Compat()) {
                            altIsbn = isbn.asText(Type.Isbn10);
                            layout.setStartIconVisible(true);
                            layout.setStartIconOnClickListener(v -> editText.setText(altIsbn));
                            return;
                        }
                        break;
                    }

                    case 10: {
                        final ISBN isbn = new ISBN(str, isbnValidityCheck == Validity.Strict);
                        if (isbn.isValid(isbnValidityCheck == Validity.Strict)) {
                            altIsbn = isbn.asText(Type.Isbn13);
                            layout.setStartIconVisible(true);
                            layout.setStartIconOnClickListener(v -> editText.setText(altIsbn));
                            return;
                        }
                        break;
                    }

                    case 12: {
                        // UPC: indicate the code is valid, but disable the swap functionality
                        if (isbnValidityCheck != Validity.Strict) {
                            final ISBN isbn = new ISBN(str, false);
                            if (isbn.isType(Type.UpcA)) {
                                layout.setStartIconVisible(true);
                                layout.setStartIconOnClickListener(null);
                                return;
                            }
                        }
                        break;
                    }

                    case 8: {
                        // ISSN: indicate the code is valid, but disable the swap functionality
                        if (isbnValidityCheck != Validity.Strict) {
                            final ISBN isbn = new ISBN(str, false);
                            if (isbn.isType(Type.Issn8)) {
                                layout.setStartIconVisible(true);
                                layout.setStartIconOnClickListener(null);
                                return;
                            }
                        }
                        break;
                    }

                    default:
                        break;
                }
            }

            layout.setStartIconVisible(false);
            layout.setStartIconOnClickListener(null);
        }
    }
}
