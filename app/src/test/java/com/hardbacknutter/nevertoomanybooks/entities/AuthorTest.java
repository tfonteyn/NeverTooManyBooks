/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.entities;

import androidx.annotation.NonNull;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test the regular expressions used by {@link DataHolderUtils#requireAuthor}.
 */
class AuthorTest {

    @NonNull
    static Stream<Arguments> readArgs() {
        return Stream.of(
                Arguments.of("Asimov, Isaac", "Asimov", "Isaac"),
                Arguments.of("Isaac Asimov", "Asimov", "Isaac"),
                Arguments.of("James Tiptree, Jr.", "Tiptree Jr.", "James"),
                Arguments.of("Ursula Le Guin", "Le Guin", "Ursula"),
                Arguments.of("Charles Emerson Winchester", "Winchester", "Charles Emerson"),
                Arguments.of("Charles Emerson Winchester (The Third one)",
                             "Winchester (The Third one)", "Charles Emerson"),
                Arguments.of("Charles Emerson Winchester III", "Winchester III", "Charles Emerson"),
                Arguments.of("Charles Emerson Winchester, jr.", "Winchester jr.",
                             "Charles Emerson"),
                Arguments.of("Charles Emerson Winchester jr.", "Winchester jr.", "Charles Emerson"),
                // yes, there REALLY is a book with an author named like this...
                Arguments.of("Don (*3)", "Don (*3)", ""),
                Arguments.of("(*3), Don", "(*3)", "Don"),
                Arguments.of("Robert Velter (Rob Vel)", "Velter (Rob Vel)", "Robert"),
                Arguments.of("Robert Velter (Rob-vel,Bozz)", "Velter (Rob-vel,Bozz)", "Robert"),
                Arguments.of("Robert Velter Jr. (Rob-vel,Bozz)", "Velter Jr. (Rob-vel,Bozz)",
                             "Robert"),

                /*
                 * https://en.wikipedia.org/wiki/List_of_Georgian_writers
                 * https://ka.wikipedia.org/wiki/%E1%83%A5%E1%83%90%E1%83%A0%E1%83%97%E1%83%95%E1%83%94%E1%83%9A%E1%83%98_%E1%83%9B%E1%83%AC%E1%83%94%E1%83%A0%E1%83%9A%E1%83%94%E1%83%91%E1%83%98%E1%83%A1_%E1%83%A1%E1%83%98%E1%83%90
                 * Alexander Abasheli
                 */
                Arguments.of("ალექსანდრე აბაშელი", "აბაშელი", "ალექსანდრე")
        );
    }

    @ParameterizedTest
    @MethodSource("readArgs")
    void fromString00(@NonNull final String source,
                      @NonNull final String familyName,
                      @NonNull final String givenNames) {
        final Author author = Author.from(source);
        assertNotNull(author);
        assertEquals(familyName, author.getFamilyName());
        assertEquals(givenNames, author.getGivenNames());
    }
}
