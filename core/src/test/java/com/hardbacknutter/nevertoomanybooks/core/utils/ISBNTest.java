/*
 * @Copyright 2018-2025 HardBackNutter
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

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ISBNTest {

    @NonNull
    static Stream<Arguments> validIsbn13() {
        return Stream.of(
                Arguments.of("9782723481229"),
                Arguments.of("978-1-886778-17-7"),
                Arguments.of("9781886778177"),
                Arguments.of("978-0-684-18818-8"),
                Arguments.of("9780684188188"),

                Arguments.of("9791028102838")
        );
    }

    /**
     * Same as {@link #validIsbn13()}, but one digit changed so the checksum fails.
     */
    @NonNull
    static Stream<Arguments> invalidIsbn13() {
        return Stream.of(
                Arguments.of("9782823481229"),
                Arguments.of("978-1-986778-17-7"),
                Arguments.of("9781886878177"),
                Arguments.of("978-0-684-18818-7"),
                Arguments.of("9780684188178"),

                Arguments.of("9791028102848")
        );
    }

    /**
     * UPC codes which can be translated to ISBN-10.
     */
    @NonNull
    static Stream<Arguments> validUpcIsbn() {
        return Stream.of(
                Arguments.of("0-70999-00225-5 30054", "0345300548")
        );
    }

    /**
     * Generic UPC_A codes
     */
    @NonNull
    static Stream<Arguments> valid_upc() {
        return Stream.of(
                Arguments.of("7 12345 67890 4", "712345678904")
        );
    }

    ;

    /**
     * Generic EAN-13 codes.
     */
    @NonNull
    static Stream<Arguments> valid_ean13() {
        return Stream.of(
                Arguments.of("5410983535003", "5410983535003")
        );
    }

    ;

    /**
     * pure 8 digit ISSN codes.
     */
    @NonNull
    static Stream<Arguments> valid_issn8() {
        return Stream.of(
                Arguments.of("0378-5955", "03785955"),
                Arguments.of("0024-984X", "0024984X")
        );
    }

    ;

    /**
     * pure 13 digit ISSN codes.
     */
    @NonNull
    static Stream<Arguments> valid_issn13() {
        return Stream.of(
                Arguments.of("977 0378-595 12 5", "9770378595125"),
                Arguments.of("9770024984648", "9770024984648")
        );
    }

    ;

    /**
     * mixed 13 digit input; 8 digit expected ISSN codes.
     */
    @NonNull
    static Stream<Arguments> valid_issn138() {
        return Stream.of(
                Arguments.of("977 0378-595 12 5", "03785955"),
                Arguments.of("9770024984648", "0024984X")
        );
    }

    ;

    @ParameterizedTest
    @MethodSource("validIsbn13")
    void validIsbn13(@NonNull final String isbnStr) {
        final ISBN isbn = new ISBN(isbnStr, true);
        assertTrue(isbn.isValid(true));
        assertTrue(isbn.isType(ISBN.Type.Isbn13));
    }

    @ParameterizedTest
    @MethodSource("invalidIsbn13")
    void invalidIsbn13(@NonNull final String isbnStr) {
        final ISBN isbn = new ISBN(isbnStr, true);
        assertFalse(isbn.isValid(true));
    }

    @ParameterizedTest
    @MethodSource("validUpcIsbn")
    void validUpcIsbn(@NonNull final String upcStr,
                      @NonNull final String expected) {
        final ISBN isbn = new ISBN(upcStr, false);
        assertTrue(isbn.isType(ISBN.Type.Isbn10));
        assertTrue(isbn.isValid(false));
        assertEquals(expected, isbn.asText());
    }


    @ParameterizedTest
    @MethodSource("valid_upc")
    void valid_upc(@NonNull final String upcStr,
                   @NonNull final String expected) {
        final ISBN upc = new ISBN(upcStr, false);
        assertTrue(upc.isType(ISBN.Type.UpcA));
        assertTrue(upc.isValid(false));
        assertEquals(expected, upc.asText());
    }

    @ParameterizedTest
    @MethodSource("valid_ean13")
    void valid_ean13(@NonNull final String eanStr,
                     @NonNull final String expected) {
        final ISBN ean = new ISBN(eanStr, false);
        assertTrue(ean.isType(ISBN.Type.Ean13));
        assertTrue(ean.isValid(false));
        assertEquals(expected, ean.asText());
    }


    @ParameterizedTest
    @MethodSource("valid_issn8")
    void valid_issn8(@NonNull final String issnStr,
                     @NonNull final String expected) {
        final ISBN issn = new ISBN(issnStr, false);
        assertTrue(issn.isValid(false));
        assertTrue(issn.isType(ISBN.Type.Issn8));
        assertEquals(expected, issn.asText(ISBN.Type.Issn8));
    }

    @ParameterizedTest
    @MethodSource("valid_issn13")
    void valid_issn13(@NonNull final String issnStr,
                      @NonNull final String expected) {
        final ISBN issn = new ISBN(issnStr, false);
        assertTrue(issn.isValid(false));
        assertTrue(issn.isType(ISBN.Type.Issn13));
        assertEquals(expected, issn.asText(ISBN.Type.Issn13));
    }

    @ParameterizedTest
    @MethodSource("valid_issn138")
    void valid_issn138(@NonNull final String issnStr,
                       @NonNull final String expected) {
        final ISBN issn = new ISBN(issnStr, false);
        assertTrue(issn.isValid(false));
        assertTrue(issn.isType(ISBN.Type.Issn13));
        assertEquals(expected, issn.asText(ISBN.Type.Issn8));
    }

}
