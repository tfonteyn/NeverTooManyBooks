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
package com.hardbacknutter.nevertoomanybooks.entities.merging;

import android.content.Context;

import androidx.test.filters.MediumTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MediumTest
class AuthorTest
        extends BaseDBTest {

    private static final String ISAAC_ASIMOV = "Isaac Asimov";
    private static final String PAUL_FRENCH = "Paul French";

    private static final String PHILIP_JOSE_FARMER = "Philip Jose Farmer";
    private static final String PHILIP_JOSE_FARMER_VARIANT = "Philip José Farmer";

    private static final String PHILIP_DICK = "Philip K. Dick";

    private static final String GERMAN_GROSS = "Groß";
    private static final String GERMAN_GROSS_1 = "Jan Groß";
    private static final String GERMAN_GROSS_2 = "Jan Gross";

    private static final long FAKE_ID_0 = 2_000_100;
    private static final long FAKE_ID_1 = 2_000_200;
    private static final long FAKE_ID_2 = 2_000_300;
    private static final long FAKE_ID_3 = 2_000_400;

    @Test
    void pruneAuthorList01() {
        final Context context = createContext();

        final Locale bookLocale = Locale.getDefault();

        final List<Author> authorList = new ArrayList<>();
        Author author;

        // Keep, position 0
        author = Author.from(ISAAC_ASIMOV);
        author.setId(FAKE_ID_0);
        author.setComplete(false);
        authorList.add(author);

        // same name, with a 0 id: merge with position 0
        author = Author.from(ISAAC_ASIMOV);
        author.setId(0);
        author.setComplete(true);
        authorList.add(author);

        // discard
        author = Author.from(ISAAC_ASIMOV);
        author.setId(FAKE_ID_0);
        authorList.add(author);

        // keep, position 1
        final Author author2 = Author.from(PAUL_FRENCH);
        author2.setId(FAKE_ID_3);
        author2.setRealAuthor(author);
        authorList.add(author2);

        // keep, position 2
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(FAKE_ID_1);
        authorList.add(author);

        // discard
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(FAKE_ID_1);
        authorList.add(author);

        // discard
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(FAKE_ID_1);
        authorList.add(author);

        // keep, position 3
        author = Author.from(PHILIP_DICK);
        author.setId(FAKE_ID_2);
        author.setRole(AuthorRole.WRITER);
        authorList.add(author);

        // discard
        author = Author.from(PHILIP_DICK);
        author.setId(FAKE_ID_2);
        author.setRole(AuthorRole.UNKNOWN);
        authorList.add(author);

        // discard, but add role to existing author in position 3
        author = Author.from(PHILIP_DICK);
        author.setId(FAKE_ID_2);
        author.setRole(AuthorRole.CONTRIBUTOR);
        authorList.add(author);

        final Pruner<Author> authorPruner = AuthorPruner.forTest();
        final boolean modified = authorPruner.prune(context, authorList, item -> bookLocale);

        assertTrue(modified, authorList.toString());
        assertEquals(4, authorList.size(), authorList.toString());

        author = authorList.get(0);
        assertEquals(FAKE_ID_0, author.getId());
        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        assertTrue(author.isComplete());
        assertEquals(AuthorRole.UNKNOWN, author.getRole());

        author = authorList.get(1);
        assertEquals(FAKE_ID_3, author.getId());
        assertEquals("French", author.getFamilyName());
        assertEquals("Paul", author.getGivenNames());
        assertNotNull(author.getRealAuthor());
        assertEquals("Asimov", author.getRealAuthor().getFamilyName());
        assertEquals("Isaac", author.getRealAuthor().getGivenNames());
        assertEquals(AuthorRole.UNKNOWN, author.getRole());

        author = authorList.get(2);
        assertEquals(FAKE_ID_1, author.getId());
        assertEquals("Farmer", author.getFamilyName());
        assertEquals("Philip Jose", author.getGivenNames());
        assertEquals(AuthorRole.UNKNOWN, author.getRole());

        author = authorList.get(3);
        assertEquals(FAKE_ID_2, author.getId());
        assertEquals("Dick", author.getFamilyName());
        assertEquals("Philip K.", author.getGivenNames());
        assertEquals(AuthorRole.WRITER | AuthorRole.CONTRIBUTOR, author.getRole());
    }

    @Test
    void pruneAuthorList02() {
        final Context context = createContext();

        final Locale bookLocale = Locale.getDefault();

        final List<Author> authorList = new ArrayList<>();
        Author author;

        // keep, position 0
        author = Author.from(PHILIP_JOSE_FARMER_VARIANT);
        author.setId(FAKE_ID_1);
        author.setRole(AuthorRole.UNKNOWN);
        authorList.add(author);

        // merge role with position 1
        author = Author.from(PHILIP_JOSE_FARMER);
        author.setId(FAKE_ID_1);
        author.setRole(AuthorRole.WRITER);
        authorList.add(author);

        // merge role with position 1
        author = Author.from(PHILIP_JOSE_FARMER_VARIANT);
        author.setId(FAKE_ID_1);
        author.setRole(AuthorRole.AFTERWORD);
        authorList.add(author);

        final Pruner<Author> authorPruner = AuthorPruner.forTest();
        final boolean modified = authorPruner.prune(context, authorList, item -> bookLocale);

        assertTrue(modified);
        assertEquals(1, authorList.size());

        author = authorList.get(0);
        assertEquals(FAKE_ID_1, author.getId());
        assertEquals("Farmer", author.getFamilyName());
        // Note the "José" because we added PHILIP_JOSE_FARMER_VARIANT as the first in the list
        assertEquals("Philip José", author.getGivenNames());
        assertEquals(AuthorRole.WRITER | AuthorRole.AFTERWORD, author.getRole());
    }

    @Test
    void pruneAuthorList03() {
        final Context context = createContext();

        final Locale bookLocale = Locale.getDefault();

        final List<Author> authorList = new ArrayList<>();
        Author author;

        // to be removed
        authorList.add(Author.createUnknownAuthor(context));

        // keep, position 0
        author = Author.from(PHILIP_JOSE_FARMER_VARIANT);
        author.setId(FAKE_ID_1);
        authorList.add(author);

        // to be removed
        authorList.add(Author.createUnknownAuthor(context));

        final Pruner<Author> authorPruner = AuthorPruner.forTest();
        final boolean modified = authorPruner.prune(context, authorList, item -> bookLocale);

        assertTrue(modified);
        assertEquals(1, authorList.size());

        author = authorList.get(0);
        assertEquals(FAKE_ID_1, author.getId());
        assertEquals("Farmer", author.getFamilyName());
        assertEquals("Philip José", author.getGivenNames());
    }

    @Test
    void pruneAuthorList04() {
        final Context context = createContext();

        final Locale bookLocale = Locale.getDefault();

        final List<Author> authorList = new ArrayList<>();
        authorList.add(Author.createUnknownAuthor(context));
        authorList.add(Author.createUnknownAuthor(context));
        authorList.add(Author.createUnknownAuthor(context));

        final Pruner<Author> authorPruner = AuthorPruner.forTest();
        final boolean modified = authorPruner.prune(context, authorList, item -> bookLocale);
        assertTrue(modified);
        assertEquals(1, authorList.size());

        final String unknown = context.getString(R.string.unknown_author);
        final Author author = authorList.get(0);
        assertEquals(unknown, author.getFamilyName());
    }

    @Test
    void pruneGeorgianNames01() {
        final Context context = createContext();

        // Georgian / Georgia
        @SuppressWarnings("deprecation")
        final Locale bookLocale = new Locale("ka", "GE");

        final List<Author> authorList = new ArrayList<>();
        Author author;

        // https://en.wikipedia.org/wiki/Alexander_Abasheli
        author = Author.from("ალექსანდრე აბაშელი");
        authorList.add(author);

        // https://en.wikipedia.org/wiki/Irakli_Abashidze
        author = Author.from("ირაკლი აბაშიძე");
        authorList.add(author);

        // https://en.wikipedia.org/wiki/Alexander_Amilakhvari
        author = Author.from("ალექსანდრე ამილახვარი");
        authorList.add(author);

        final Pruner<Author> authorPruner = AuthorPruner.forTest();
        final boolean modified = authorPruner.prune(context, authorList, item -> bookLocale);
        assertFalse(modified);
    }

    @Test
    void pruneGermanEszett() {
        final Context context = createContext();

        final Locale bookLocale = Locale.GERMANY;

        final List<Author> authorList = new ArrayList<>();
        Author author;

        author = Author.from(GERMAN_GROSS_1);
        authorList.add(author);

        author = Author.from(GERMAN_GROSS_2);
        authorList.add(author);

        final Pruner<Author> authorPruner = AuthorPruner.forTest();
        final boolean modified = authorPruner.prune(context, authorList, item -> bookLocale);
        assertTrue(modified);

        assertEquals(1, authorList.size());
        assertEquals(GERMAN_GROSS, authorList.get(0).getFamilyName());

    }

    @Test
    void pruneWithDash() {
        final Context context = createContext();

        final Locale bookLocale = Locale.GERMANY;

        final String familyName = "Larsson";
        final String dashAbsent = "Lars Olof";
        final String dashPresent = "Lars-Olof";

        final List<Author> authorList = new ArrayList<>();
        authorList.add(new Author(familyName, dashAbsent));
        authorList.add(new Author(familyName, dashPresent));

        final Pruner<Author> authorPruner = AuthorPruner.forTest();
        final boolean modified = authorPruner.prune(context, authorList, item -> bookLocale);
        assertTrue(modified);

        assertEquals(1, authorList.size());
        final Author author = authorList.get(0);
        assertEquals(familyName, author.getFamilyName());
        assertEquals(dashAbsent, author.getGivenNames());
    }

    @Test
    void pruneWithDash2() {
        final Context context = createContext();

        final Locale bookLocale = Locale.US;

        final String familyName = "Pelt";
        final String dashAbsent = "JeanMarie";
        final String dashPresent = "Jean-Marie";

        final List<Author> authorList = new ArrayList<>();
        authorList.add(new Author(familyName, dashAbsent));
        authorList.add(new Author(familyName, dashPresent));

        final Pruner<Author> authorPruner = AuthorPruner.forTest();
        final boolean modified = authorPruner.prune(context, authorList, item -> bookLocale);
        assertTrue(modified);

        assertEquals(1, authorList.size());
        final Author author = authorList.get(0);
        assertEquals(familyName, author.getFamilyName());
        assertEquals(dashAbsent, author.getGivenNames());
    }
}
