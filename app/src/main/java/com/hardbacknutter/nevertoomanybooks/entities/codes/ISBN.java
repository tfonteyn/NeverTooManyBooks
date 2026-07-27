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
package com.hardbacknutter.nevertoomanybooks.entities.codes;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * This class name is a bit of a misnomer by now.
 * It represents all <strong>numeric</strong> codes supported.
 * See {@link ProductCodeType} for all supported codes.
 * ISBN uses the digit 10 for 'X'.
 * <p>
 * ISBN stands for International Standard Book Number.
 * Every book is assigned a unique ISBN-10 and ISBN-13 when published.
 * See <a href="http://en.wikipedia.org/wiki/International_Standard_Book_Number">ISBN</a>
 * See <a href="https://isbn-information.com">https://isbn-information.com</a>
 * <p>
 * An International Standard Serial Number {@link ProductCodeType#Issn8} is an eight-digit serial
 * number used to uniquely identify a serial publication, such as a magazine.
 * See <a href="https://en.wikipedia.org/wiki/International_Standard_Serial_Number">ISSN</a>
 * The {@link ProductCodeType#Issn13} is an encoded version, with a {@code 977} "country code",
 * followed by the 7 main digits of the ISSN (the check digit is not included),
 * followed by 2 publisher-defined digits, followed by the EAN check digit.
 * <p>
 * The International Standard Music Number or {@link ProductCodeType#Ismn} (ISO 10957)
 * is a thirteen-character alphanumeric identifier for printed music
 * See <a href="https://en.wikipedia.org/wiki/International_Standard_Music_Number">ISMN</a>
 * <p>
 * A Universal Product Code {@link ProductCodeType#UpcA} is a barcode symbology that is widely used
 * worldwide for tracking trade items in stores.
 * See <a href="https://en.wikipedia.org/wiki/Universal_Product_Code">UPC_A</a>
 * and <a href="https://www.cbr.com/comic-book-covers-upc-meaning/">Specifics for comics</a>.
 * {@link ProductCodeType#UpcA} numbers are converted to {@link ProductCodeType#Isbn10} if possible.
 * <p>
 * An Amazon Standard Identification Number {@link ProductCodeType#Asin} is a 10-character
 * alphanumeric unique identifier assigned by Amazon.com.
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
 * The precursor of ISBN was known as the 9-digit Standard Book Numbering (SBN)
 * created in 1966.
 * {@link ProductCodeType#Sbn} is always converted to {@link ProductCodeType#Isbn10}
 * by prefixing with the digit {@code 0}.
 * <p>
 * The EAN "country code" 978 (and later 979) has been allocated since the 1980s to reserve
 * a Unique Country Code (UCC) prefix for EAN identifiers of published books, regardless of
 * country of origin, so that the EAN space can catalog books by ISBNs rather than
 * maintaining a redundant parallel numbering system. This is informally known as "Bookland".
 * <p>
 * The prefix 979 with first digit 0 is used for International Standard Music Number
 * (ISMN a.k.a. "MusicLand").
 * <br>The prefix 977 indicates International Standard Serial Number (ISSN).
 */
@SuppressWarnings("MagicNumber")
public final class ISBN
        implements ProductCode {

    /** Log tag. */
    private static final String TAG = "ISBN";

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
    /** Remove the '-' character and all whitespace. */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[-\\s+]");
    private static final String L977 = "977";
    private static final String L978 = "978";
    private static final String L979 = "979";

    private static final String ERROR_X_CAN_ONLY_BE_AT_THE_END_OF_AN_ISBN_10 =
            "X can only be at the end of an ISBN-10";
    /** debug. */
    private static final String ERROR_CODE_DIGITS_NULL = "codeDigits";

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
    private final ProductCodeType productCodeType;
    /**
     * The parsed code as a text string,
     * or the raw input string for invalid codes.
     */
    @NonNull
    private final String codeText;
    /**
     * The parsed code Kept for faster conversion between formats;
     * {@code null} for invalid codes.
     */
    @Nullable
    private final List<Integer> codeDigits;
    /**
     * Flag: when {@code true} all non-ISBN codes are rejected as invalid.
     */
    private final boolean strictIsbn;
    /**
     * The optional barcode information if available.
     */
    @Nullable
    private Barcode barcode;

    /**
     * Constructor.
     *
     * @param barcode    to parse
     * @param strictIsbn {@code true} to strictly allow ISBN codes.
     *                   {@code false} to also accept any other valid code.
     */
    private ISBN(@NonNull final Barcode barcode,
                 final boolean strictIsbn) {
        this(barcode.getText(), strictIsbn);
        this.barcode = barcode;
    }

    /**
     * Constructor.
     * <ul>
     *  <li>
     *      {@code strictIsbn == false}
     *      <br>accepts as valid:
     *      <ul>
     *          <li>{@link ProductCodeType#Isbn10}</li>
     *          <li>{@link ProductCodeType#Sbn}</li>
     *          <li>{@link ProductCodeType#Isbn13}</li>
     *          <li>{@link ProductCodeType#Ean13}</li>
     *          <li>{@link ProductCodeType#Issn8}</li>
     *          <li>{@link ProductCodeType#Ismn}</li>
     *          <li>generic {@link ProductCodeType#UpcA}</li>
     *      </ul>
     *  </li>
     *  <li>
     *      {@code strictIsbn == true}
     *      <br>accepts as valid:
     *      <ul>
     *          <li>{@link ProductCodeType#Isbn10}</li>
     *          <li>{@link ProductCodeType#Sbn}</li>
     *          <li>{@link ProductCodeType#Isbn13}</li>
     *          <li>{@link ProductCodeType#UpcA} <strong>if convertible
     *              to {@link ProductCodeType#Isbn10}</strong></li>
     *      </ul>
     *      Rejects as invalid:
     *      <ul>
     *          <li>{@link ProductCodeType#Ean13}</li>
     *          <li>{@link ProductCodeType#Issn8}</li>
     *          <li>{@link ProductCodeType#Ismn}</li>
     *          <li>generic {@link ProductCodeType#UpcA}</li>
     *      </ul>
     *  </li>
     *  <li>Accepts {@code null} which results in {@code Type.Invalid}.</li>
     *  <li>Accepts {@code ' '} and {@code '-'} separator characters.</li>
     *  </ul>
     *
     * @param text       string to parse
     * @param strictIsbn {@code true} to strictly allow ISBN codes.
     *                   {@code false} to also accept any other valid code.
     */
    private ISBN(@Nullable final String text,
                 final boolean strictIsbn) {
        this.strictIsbn = strictIsbn;

        List<Integer> digits = null;
        ProductCodeType type = ProductCodeType.Invalid;

        if (text != null && !text.isEmpty()) {
            // Remove whitespace first for easier parsing.
            final String cleanStr = WHITESPACE_PATTERN.matcher(text).replaceAll("");
            if (!cleanStr.isEmpty()) {
                try {
                    digits = toDigits(cleanStr, strictIsbn);
                    type = ProductCodeType.getType(digits);

                    if (type == ProductCodeType.UpcA) {
                        // is this UPC_A convertible to ISBN-10 ?
                        final String isbnPrefix = UPC_2_ISBN_PREFIX.get(cleanStr.substring(0, 6));
                        if (isbnPrefix != null) {
                            // yes, convert to ISBN-10
                            digits = toDigits(isbnPrefix + cleanStr.substring(12), false);
                            digits.add(ProductCodeType.Isbn10.checksum(digits));
                            type = ProductCodeType.Isbn10;
                        }
                    } else if (type == ProductCodeType.Sbn) {
                        // can always be converted to ISBN-10
                        digits = new ArrayList<>(digits.subList(0, 9));
                        digits.add(0, 0);
                        type = ProductCodeType.Isbn10;
                    }
                } catch (@NonNull final NumberFormatException e) {
                    if (BuildConfig.DEBUG /* always */) {
                        LoggerFactory.getLogger().e(TAG, e, "text=`" + text + '`');
                    }
                }

                // strict ISBN required?
                if (strictIsbn
                    && type != ProductCodeType.Isbn10
                    && type != ProductCodeType.Isbn13) {
                    type = ProductCodeType.Invalid;
                }
            }
        }

        // Make sure the internal status is uniform.
        if (type == ProductCodeType.Invalid) {
            codeDigits = null;
            codeText = text != null ? text : "";
            productCodeType = ProductCodeType.Invalid;

        } else {
            codeDigits = digits;
            codeText = concat(codeDigits);
            productCodeType = type;
        }
    }

    /**
     * Constructor.
     *
     * @param text string to parse
     *
     * @return new instance
     */
    @NonNull
    public static ProductCode parse(@Nullable final String text) {
        return new ISBN(text, false);
    }

    /**
     * Constructor.
     *
     * @param text       string to parse
     * @param strictIsbn {@code true} to strictly allow ISBN codes.
     *                   {@code false} to also accept any other valid code.
     *
     * @return new instance
     */
    @NonNull
    public static ProductCode parse(@Nullable final String text,
                                    final boolean strictIsbn) {
        return new ISBN(text, strictIsbn);
    }

    /**
     * Constructor.
     *
     * @param barcode    to parse
     * @param strictIsbn {@code true} to strictly allow ISBN codes.
     *                   {@code false} to also accept any other valid code.
     *
     * @return new instance
     */
    @NonNull
    public static ProductCode parse(@NonNull final Barcode barcode,
                                    final boolean strictIsbn) {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "barcode=" + barcode);
        }
        return new ISBN(barcode, strictIsbn);
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
     * Get the concatenated digits. Digit 10 is always returned as '<strong>X</strong>'.
     *
     * @param digits the list of digits
     *
     * @return the code as a string.
     */
    @NonNull
    private static String concat(@NonNull final Iterable<Integer> digits) {
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
    private static List<Integer> toDigits(@NonNull final CharSequence text,
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
     * Check if we have a valid code. Does not check for a specific type
     * unless the {@code strictIsbn} flag as set in the constructor is {@code true}.
     * <p>
     * Use {@link #getType()} or {@link #isIsbn()} by preference.
     *
     * @return validity
     */
    @Override
    public boolean isValid() {
        if (strictIsbn) {
            return isIsbn();
        } else {
            return productCodeType != ProductCodeType.Invalid;
        }
    }

    @Override
    @NonNull
    public ProductCodeType getType() {
        return productCodeType;
    }

    @Override
    public boolean isIsbn() {
        return productCodeType == ProductCodeType.Isbn10
               || productCodeType == ProductCodeType.Isbn13;
    }

    @Override
    public boolean isIsbn10Compat() {
        // reminder: no need to check UPC_A here, as we would have converted it already
        return productCodeType == ProductCodeType.Isbn10
               || productCodeType == ProductCodeType.Isbn13 && codeText.startsWith(L978);
    }

    /**
     * Get the code as a text string based on the original type.
     * It will have been cleaned and reduced to digits/x only.
     * No other conversions are done.
     *
     * @return string
     */
    @NonNull
    @Override
    public String asText() {
        return codeText;
    }

    /**
     * Get the code as a text string converted to the given type.
     * It will have been cleaned and reduced to digits/x only.
     * <p>
     * <strong>WARNING:</strong> when converting an
     * {@link ProductCodeType#Isbn13} to {@link ProductCodeType#Isbn10},
     * you must call {@link #isIsbn10Compat()} prior to avoid this method throwing an exception.
     *
     * @param toType to convert to
     *
     * @return string
     *
     * @throws NumberFormatException if a conversion is not possible.
     */
    @Override
    @NonNull
    public String asText(@NonNull final ProductCodeType toType)
            throws NumberFormatException {

        // Same type? No conversion duh
        if (productCodeType == toType) {
            return codeText;
        }

        // Both EAN-13 ? No conversion needed.
        if (productCodeType.isEan13Compat() && toType.isEan13Compat()) {
            return codeText;
        }

        // types not listed cannot be converted or would not make sense
        // e.g. we don't do isbn10 to ismn13 or similar non-sensical conversions.
        switch (productCodeType) {
            case Isbn10: {
                if (toType == ProductCodeType.Asin) {
                    return codeText;

                } else if (toType == ProductCodeType.Isbn13) {
                    Objects.requireNonNull(codeDigits, ERROR_CODE_DIGITS_NULL);
                    final List<Integer> digits = new ArrayList<>();
                    // Add the standard prefix 978.
                    // Copy the first 9 digits.
                    // Drop the original checksum digit.
                    digits.add(9);
                    digits.add(7);
                    digits.add(8);
                    for (int i = 0; i < 9; i++) {
                        digits.add(codeDigits.get(i));
                    }
                    // and add the new checksum
                    digits.add(ProductCodeType.Ean13.checksum(digits));

                    return concat(digits);
                }
                break;
            }
            case Isbn13: {
                if ((toType == ProductCodeType.Isbn10 || toType == ProductCodeType.Asin)
                    && codeText.startsWith(L978)) {
                    Objects.requireNonNull(codeDigits, ERROR_CODE_DIGITS_NULL);
                    // Drop the first 3 digits.
                    // Copy the next 9.
                    // Drop the original checksum digit.
                    final List<Integer> digits = new ArrayList<>();
                    for (int i = 3; i < 12; i++) {
                        digits.add(codeDigits.get(i));
                    }
                    // and add the new checksum
                    digits.add(ProductCodeType.Isbn10.checksum(digits));
                    return concat(digits);
                }
                break;
            }
            case Issn13: {
                if (toType == ProductCodeType.Issn8) {
                    Objects.requireNonNull(codeDigits, ERROR_CODE_DIGITS_NULL);
                    // Drop the first 3 digits.
                    // Copy the next 7.
                    // Drop the 2-digit vendor code and the trailing price digits.
                    final List<Integer> digits = new ArrayList<>();
                    for (int i = 3; i < 10; i++) {
                        digits.add(codeDigits.get(i));
                    }
                    // and add the new checksum
                    digits.add(ProductCodeType.Issn8.checksum(digits));
                    return concat(digits);
                }
                break;
            }
        }

        throw new NumberFormatException("Unable to convert type: "
                                        + productCodeType + " to " + toType);
    }

    /**
     * The optional barcode information if available.
     *
     * @return barcode; or {@code null} when not available.
     */
    @Nullable
    public Barcode getBarcode() {
        return barcode;
    }

    @Override
    @NonNull
    public String toString() {
        return "ISBN{"
               + "strictIsbn=" + strictIsbn
               + ", productCodeType=" + productCodeType
               + ", codeText=" + codeText
               + ", codeDigits=" + codeDigits
               + ", barcode=" + barcode
               + '}';
    }

    /**
     * <strong>The barcode is not included</strong>.
     */
    @Override
    public int hashCode() {
        // only use the 'codeText' if we have no digits!
        return Objects.hash(productCodeType, Objects.requireNonNullElse(codeDigits, codeText));
    }

    /**
     * <strong>The barcode is not included</strong>.
     */
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ISBN cmp = (ISBN) o;

        // Reminder: do not compare 'codeText' !

        // Either one is invalid ? No match.
        if (productCodeType == ProductCodeType.Invalid
            || cmp.getType() == ProductCodeType.Invalid) {
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

        // ISBN-10 and ISBN-13: Compare the 9 significant digits:
        // ISBN10: don't include the checksum -> 0..9
        // ISBN13: skip the first 3 character, and don't include the checksum -> 3..12
        if (codeDigits.size() == 10 && cmp.codeDigits.size() == 13) {
            return codeDigits.subList(0, 9).equals(cmp.codeDigits.subList(3, 12));

        } else if (codeDigits.size() == 13 && cmp.codeDigits.size() == 10) {
            return codeDigits.subList(3, 12).equals(cmp.codeDigits.subList(0, 9));
        }

        // ISSN-8 and ISSN-13: Compare the 7 significant digits:
        // ISSN-8: don't include the checksum -> 0..7
        // ISSN-13: skip the first 3 character, and don't include the remainder -> 3..11
        if (codeDigits.size() == 8 && cmp.codeDigits.size() == 13) {
            return codeDigits.subList(0, 7).equals(cmp.codeDigits.subList(3, 11));

        } else if (codeDigits.size() == 13 && cmp.codeDigits.size() == 8) {
            return codeDigits.subList(3, 11).equals(cmp.codeDigits.subList(0, 7));
        }

        return false;
    }

    public static class CleanupTextWatcher
            implements TextWatcher {

        @NonNull
        private final TextInputEditText editText;
        @NonNull
        private ProductCodeValidity validity;

        /**
         * Constructor.
         *
         * @param editText the view to watch
         * @param validity validity check-level for codes
         */
        public CleanupTextWatcher(@NonNull final TextInputEditText editText,
                                  @NonNull final ProductCodeValidity validity) {
            this.editText = editText;
            this.validity = validity;
        }

        /**
         * Update the validity level.
         *
         * @param validity validity check-level for codes
         */
        public void setValidityLevel(@NonNull final ProductCodeValidity validity) {
            this.validity = validity;
            clean(editText.getEditableText());
        }

        @Override
        public void afterTextChanged(@NonNull final Editable editable) {
            clean(editText.getEditableText());
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

        private void clean(@Nullable final Editable editable) {
            if (validity == ProductCodeValidity.NoChecks
                || editable == null || editable.length() == 0) {
                return;
            }

            final String text = editable.toString().strip();
            if (text.isEmpty()) {
                return;
            }

            if (validity == ProductCodeValidity.ValidCodes) {
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
                // to enter a custom code.
                // Even this 13/17 length rule might be too restrictive?)
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

            // Validity.Isbn, or we decided we can clean up anyhow.
            final String isbnText = cleanText(text);
            if (!isbnText.equals(text)) {
                editText.removeTextChangedListener(this);
                editable.replace(0, editable.length(), isbnText);
                editText.addTextChangedListener(this);
            }
        }
    }

    public static class ValidationTextWatcher
            implements TextWatcher {

        @NonNull
        private final TextInputLayout layout;
        @NonNull
        private final TextInputEditText editText;

        /** The alternative ISBN text - 10/13 opposite of editText. */
        @Nullable
        private String altIsbn;
        @NonNull
        private ProductCodeValidity isbnValidityCheck;

        /**
         * Constructor.
         *
         * @param layoutView        TIL layout View
         * @param editText          the View to watch
         * @param isbnValidityCheck validity check-level for ISBN codes
         */
        public ValidationTextWatcher(@NonNull final TextInputLayout layoutView,
                                     @NonNull final TextInputEditText editText,
                                     @NonNull final ProductCodeValidity isbnValidityCheck) {
            layout = layoutView;
            layout.setStartIconVisible(false);

            this.editText = editText;
            this.isbnValidityCheck = isbnValidityCheck;
        }

        /**
         * Update the validity level.
         *
         * @param isbnValidityCheck validity check-level for ISBN codes
         */
        public void setValidityLevel(@NonNull final ProductCodeValidity isbnValidityCheck) {
            this.isbnValidityCheck = isbnValidityCheck;
            validate(editText.getEditableText());
        }

        @Override
        public void afterTextChanged(@NonNull final Editable editable) {
            validate(editable);
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

        private void invalidate() {
            layout.setStartIconVisible(false);
            layout.setStartIconOnClickListener(null);
            LoggerFactory.getLogger().d(TAG, "invalidate");
        }

        /**
         * Validate the input, and set the start-icon visibility/OnClickListener as needed.
         * Does NOT modify the editable.
         *
         * @param editable to validate; will not be modified.
         */
        private void validate(@Nullable final Editable editable) {

            if (editable == null || editable.length() == 0) {
                // empty field
                invalidate();
                return;
            }

            final String str = editable.toString().strip();
            final int length = str.length();

            // Bail out if the code length is not recognised as potentially valid:
            // Valid lengths are: 8,9,10,12 or longer
            if (length < 8 || length == 11) {
                // not a recognised code-length and/or the user is still typing
                invalidate();
                return;
            }

            // Create it without forcing ISBN, we'll check the type in detail.
            final ProductCode productCode = parse(str);
            if (isbnValidityCheck == ProductCodeValidity.Isbn
                && productCode.getType() == ProductCodeType.Invalid) {
                // We're in strict mode, reject any invalid codes
                invalidate();
                return;
            }

            // ISBN-10 + any legacy SBN/UPC which was converted to ISBN-10
            if (productCode.getType() == ProductCodeType.Isbn10) {
                layout.setStartIconVisible(true);
                // ISBN-10, which can always be converted to ISBN-13
                altIsbn = productCode.asText(ProductCodeType.Isbn13);
                layout.setStartIconOnClickListener(v -> editText.setText(altIsbn));
                LoggerFactory.getLogger().d(TAG, productCode.getType());
                return;
            }

            if (productCode.getType() == ProductCodeType.Isbn13) {
                layout.setStartIconVisible(true);
                if (productCode.isIsbn10Compat()) {
                    // can be converted to ISBN-10
                    altIsbn = productCode.asText(ProductCodeType.Isbn10);
                    layout.setStartIconOnClickListener(v -> editText.setText(altIsbn));
                    LoggerFactory.getLogger().d(TAG, productCode.getType());
                } else {
                    // cannot be converted
                    layout.setStartIconOnClickListener(null);
                    LoggerFactory.getLogger().d(TAG, productCode.getType());
                }
                return;
            }

            if (isbnValidityCheck == ProductCodeValidity.Isbn) {
                // We're in strict mode, reject all other code (even when valid)
                invalidate();
                return;
            }

            // We're not in strict mode, just show validity status
            layout.setStartIconVisible(productCode.getType() != ProductCodeType.Invalid);
            layout.setStartIconOnClickListener(null);
            LoggerFactory.getLogger().d(TAG, productCode.getType());
        }
    }
}
