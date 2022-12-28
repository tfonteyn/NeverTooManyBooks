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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks._mocks.MockCancellable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorLookupTest
        extends JSoupBase {

    private static final String UTF_8 = "UTF-8";
    private AuthorResolver resolver;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        resolver = new AuthorResolver(context, new MockCancellable(), null);
    }

    @Test
    void parseList()
            throws IOException {
        final String locationHeader = "https://www.bedetheque.com/liste_auteurs_BD_L.html";
        final String filename = "/bedetheque/liste_auteurs_BD_L.html";

        final Document document;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            document = Jsoup.parse(is, UTF_8, locationHeader);

            assertNotNull(document);
            assertTrue(document.hasText());

            final List<AuthorResolver.BdtAuthor> list = resolver.parseAuthorList(document);
            assertEquals(2585, list.size());

        }
    }

    @Test
    void parseOneWithout()
            throws IOException {
        final String locationHeader = "https://www.bedetheque.com/auteur-96-BD-Leloup-Roger.html";
        final String filename = "/bedetheque/auteur-96-BD-Leloup-Roger.html";
        final AuthorResolver.BdtAuthor bdtAuthor =
                new AuthorResolver.BdtAuthor("Leloup, Roger", locationHeader);

        final Document document;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            document = Jsoup.parse(is, UTF_8, locationHeader);

            assertNotNull(document);
            assertTrue(document.hasText());

            final boolean modified = resolver.parseAuthor(document, bdtAuthor);
            assertTrue(modified);
            assertEquals("Leloup, Roger", bdtAuthor.getName());
            assertEquals("Leloup, Roger", bdtAuthor.getResolvedName());

        }
    }

    @Test
    void parseOneWith()
            throws IOException {
        final String locationHeader = "https://www.bedetheque.com/auteur-97-BD-Leo.html";
        final String filename = "/bedetheque/auteur-97-BD-Leo.html";
        final AuthorResolver.BdtAuthor bdtAuthor =
                new AuthorResolver.BdtAuthor("Leo", locationHeader);

        final Document document;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            document = Jsoup.parse(is, UTF_8, locationHeader);

            assertNotNull(document);
            assertTrue(document.hasText());

            final boolean modified = resolver.parseAuthor(document, bdtAuthor);
            assertTrue(modified);
            assertEquals("Leo", bdtAuthor.getName());
            assertEquals("De Oliveira, Luiz Eduardo", bdtAuthor.getResolvedName());

        }
    }
}
