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
package com.hardbacknutter.nevertoomanybooks.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test the regular expressions used by {@link DataHolderUtils#requireAuthor}.
 */
class AuthorTest {

    @Test
    void fromString00() {
        final Author author = Author.from("Asimov, Isaac");
        assertNotNull(author);
        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
    }

    @Test
    void fromString01() {
        final Author author = Author.from("Isaac Asimov");
        assertNotNull(author);
        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
    }

    @Test
    void fromString09() {
        final Author author = Author.from("James Tiptree, Jr.");
        assertNotNull(author);
        assertEquals("Tiptree Jr.", author.getFamilyName());
        assertEquals("James", author.getGivenNames());
    }

    @Test
    void fromString10() {
        final Author author = Author.from("Ursula Le Guin");
        assertNotNull(author);
        assertEquals("Le Guin", author.getFamilyName());
        assertEquals("Ursula", author.getGivenNames());
    }

    @Test
    void fromString11() {
        final Author author = Author.from("Charles Emerson Winchester");
        assertNotNull(author);
        assertEquals("Winchester", author.getFamilyName());
        assertEquals("Charles Emerson", author.getGivenNames());
    }

    @Test
    void fromString12() {
        final Author author = Author.from("Charles Emerson Winchester (The Third one)");
        assertNotNull(author);
        assertEquals("Winchester (The Third one)", author.getFamilyName());
        assertEquals("Charles Emerson", author.getGivenNames());
    }

    @Test
    void fromString20() {
        final Author author = Author.from("Charles Emerson Winchester III");
        assertNotNull(author);
        assertEquals("Winchester III", author.getFamilyName());
        assertEquals("Charles Emerson", author.getGivenNames());
    }

    @Test
    void fromString21() {
        final Author author = Author.from("Charles Emerson Winchester, jr.");
        assertNotNull(author);
        assertEquals("Winchester jr.", author.getFamilyName());
        assertEquals("Charles Emerson", author.getGivenNames());
    }

    @Test
    void fromString22() {
        final Author author = Author.from("Charles Emerson Winchester jr.");
        assertNotNull(author);
        assertEquals("Winchester jr.", author.getFamilyName());
        assertEquals("Charles Emerson", author.getGivenNames());
    }

    @Test
    void fromString30() {
        // yes, there REALLY is a book with an author named like this...
        final Author author = Author.from("Don (*3)");
        assertNotNull(author);
        assertEquals("Don (*3)", author.getFamilyName());
        assertEquals("", author.getGivenNames());
    }

    @Test
    void fromString31() {
        // yes, there REALLY is a book with an author named like this...
        final Author author = Author.from("(*3), Don");
        assertNotNull(author);
        assertEquals("(*3)", author.getFamilyName());
        assertEquals("Don", author.getGivenNames());
    }

    @Test
    void fromString40() {
        // real name (alias,alias,...)
        final Author author = Author.from("Robert Velter (Rob Vel)");
        assertNotNull(author);
        assertEquals("Velter (Rob Vel)", author.getFamilyName());
        assertEquals("Robert", author.getGivenNames());
    }

    @Test
    void fromString41() {
        // real name (alias,alias,...)
        final Author author = Author.from("Robert Velter (Rob-vel,Bozz)");
        assertNotNull(author);
        assertEquals("Velter (Rob-vel,Bozz)", author.getFamilyName());
        assertEquals("Robert", author.getGivenNames());
    }

    @Test
    void fromString42() {
        // real name (alias,alias,...)
        final Author author = Author.from("Robert Velter Jr. (Rob-vel,Bozz)");
        assertNotNull(author);
        assertEquals("Velter Jr. (Rob-vel,Bozz)", author.getFamilyName());
        assertEquals("Robert", author.getGivenNames());
    }
}
