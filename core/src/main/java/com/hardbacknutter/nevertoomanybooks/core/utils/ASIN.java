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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * ASIN stands for Amazon Standard Identification Number.
 * <p>
 * It's a base 36 number (the letters of the alphabet plus the 10 digits).
 * <p>
 * Every product on Amazon has its own ASIN, a unique code used to identify it.
 * For books, the ASIN is the same as the ISBN-10 number, and NOT simply 978 stripped
 * off as the inventor tells in her article below.
 * <p>
 * For all other products a new ASIN is created when the item is uploaded to their catalogue.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Amazon_Standard_Identification_Number">ASIN</a>
 * @see <a href="https://inventlikeanowner.com/blog/the-story-behind-asins-amazon-standard-identification-numbers/">
 *         the-story-behind-asins</a>
 */
public final class ASIN
        implements ProductCode {

    /** ASIN codes are always 10 characters. */
    private static final int ASIN_LEN = 10;
    /** Alphanumeric. */
    private static final String VALID_CHARS = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    /**
     * The cleaned/converted text. May be invalid.
     *
     * @see #isValid()
     */
    @NonNull
    private final String codeText;
    private final boolean valid;

    /** If the code was a pure ISBN-10, we cache it. */
    @Nullable
    private final ProductCode isbn10;

    /**
     * Constructor.
     *
     * @param text string to parse
     */
    public ASIN(@NonNull final String text) {
        final String tmpCode = text.toUpperCase(Locale.ENGLISH);
        // Historically, a Book ASIN is just an ISBN-10
        // For leniency we also accept ISBN-13, and convert them to ISBN-10 if possible
        final ProductCode isbn = ISBN.parseISBN(tmpCode);
        if (isbn.isIsbn10Compat()) {
            this.codeText = isbn.asText(ProductCodeType.Isbn10);
            this.isbn10 = isbn;
            this.valid = true;
        } else {
            this.codeText = tmpCode;
            this.isbn10 = null;
            this.valid = isAlphaNumeric10(this.codeText);
        }
    }

    /**
     * Validate an Amazon ASIN as being a 10 character long, alpha-numeric string.
     *
     * @param asin to validate
     *
     * @return validity
     */
    private static boolean isAlphaNumeric10(@NonNull final String asin) {
        if (asin.length() != ASIN_LEN) {
            return false;
        }

        // But these days, it can also be a Kindle book, self-published without ISBN,...
        // In this case, it will/must have at least one letter (A-Z).
        // Typically it will start with a 'B' but no need to be that strict.
        boolean foundAlpha = false;
        final String ucAsin = asin.strip().toUpperCase(Locale.ENGLISH);
        for (int i = 0; i < ucAsin.length(); i++) {
            final int pos = VALID_CHARS.indexOf(ucAsin.charAt(i));
            // Make sure it's a valid char
            if (pos == -1) {
                return false;
            }
            // Must have at least one non-numeric character
            if (pos >= ASIN_LEN) {
                foundAlpha = true;
            }
        }
        return foundAlpha;
    }

    @Override
    @NonNull
    public ProductCodeType getType() {
        return ProductCodeType.Asin;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public boolean isIsbn() {
        return isbn10 != null;
    }

    @Override
    public boolean isIsbn10Compat() {
        return isbn10 != null;
    }

    @Override
    @NonNull
    public String asText() {
        return codeText;
    }

    @NonNull
    @Override
    public String asText(@NonNull final ProductCodeType toType)
            throws NumberFormatException {
        if (toType == ProductCodeType.Asin
            || toType == ProductCodeType.Isbn10 && isbn10 != null) {
            return codeText;
        }
        if (toType == ProductCodeType.Isbn13 && isbn10 != null) {
            return isbn10.asText(ProductCodeType.Isbn13);
        }
        throw new NumberFormatException("Unable to convert type: Asin to " + toType);
    }
}
