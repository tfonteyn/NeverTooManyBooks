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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import android.util.Log;

import java.io.IOException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.BedethequeCacheDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorParseTest
        extends BaseDBTest {

    private static final String TAG = "AuthorParseTest";

    private static final String UTF_8 = "UTF-8";

    private BedethequeAuthorResolver resolver;

    private BedethequeSearchEngine searchEngine;
    private BedethequeCacheDao bedethequeCacheDao;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.Bedetheque.getConfig().setLogHttpGetRequests(true);
        searchEngine = EngineId.Bedetheque.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        resolver = new BedethequeAuthorResolver(context, new TestProgressListener(TAG));

        bedethequeCacheDao = ServiceLocator.getInstance().getBedethequeCacheDao();
        bedethequeCacheDao.clearCache();
    }

    @Test
    void parseOnly_no_pseudonym01()
            throws IOException {
        final String locationHeader = "https://www.bedetheque.com/auteur-96-BD-Leloup-Roger.html";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bedetheque_auteur_96_bd_leloup_roger;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Author author = resolver.parse(context, document);
        assertNotNull(author);
        Log.d(TAG, author.toString());
        assertEquals("Leloup", author.getFamilyName());
        assertEquals("Roger", author.getGivenNames());
        assertEquals("1933-11-17", author.getBirthDate().orElse(null));

        final Optional<String> oIv;
        oIv = author.getIdentifierValue(Identifier.SID_BEDETHEQUE);
        assertTrue(oIv.isPresent());
        assertEquals("96", oIv.get());

        final String pic = author.getTmpPictureFileSpec().orElse(null);
        assertNotNull(pic);
        assertTrue(pic.endsWith("_bedetheque_96_0_.jpg"));
    }

    @Test
    void parseOnly_with_pseudonym01()
            throws IOException {
        final String locationHeader = "https://www.bedetheque.com/auteur-97-BD-Leo.html";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bedetheque_auteur_97_bd_leo;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Author author = resolver.parse(context, document);
        assertNotNull(author);
        assertEquals("Leo", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals("1944-12-13", author.getBirthDate().orElse(null));

        Optional<String> oIv;
        oIv = author.getIdentifierValue(Identifier.SID_BEDETHEQUE);
        assertTrue(oIv.isPresent());
        assertEquals("97", oIv.get());

        final Author realAuthor = author.getRealAuthor();
        assertNotNull(realAuthor);
        assertEquals("De Oliveira", realAuthor.getFamilyName());
        assertEquals("Luiz Eduardo", realAuthor.getGivenNames());
        assertEquals("1944-12-13", realAuthor.getBirthDate().orElse(null));

        oIv = realAuthor.getIdentifierValue(Identifier.SID_BEDETHEQUE);
        assertTrue(oIv.isPresent());
        // SAME as the pen-name!
        assertEquals("97", oIv.get());
    }

    @Test
    void parse_downloaded_L_page()
            throws IOException {
        final String locationHeader = "https://www.bedetheque.com/liste_auteurs_BD_L.html";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bedetheque_liste_auteurs_bd_l;

        final Document document = loadDocument(resId, UTF_8, locationHeader);

        final AuthorListLoader authorListLoader = new AuthorListLoader(context, searchEngine);
        final boolean ok = authorListLoader.parseAuthorList(document);
        assertTrue(ok);

        // There should be 3138 authors, which we have verified during parsing to be correct.
        // However, there are only 3124 stored in the database.
        // This is caused by the site data sometimes listing the same person
        // but under a variation name:
        // - La Rosa, Bud
        // - Larosa, Bud
        // Nothing we can do about that... the site needs to fix their lists.
        final int countAuthors = bedethequeCacheDao.countAuthors();
        assertEquals(3124, countAuthors);
    }

    @Test
    void liveLookup01()
            throws SearchException, CredentialsException {

        final boolean modified;
        final Author author;
        final Author realAuthor;

        author = new Author("Leloup", "Roger");
        modified = resolver.resolve(context, author);
        assertTrue(modified);
        assertEquals("Leloup", author.getFamilyName());
        assertEquals("Roger", author.getGivenNames());
        realAuthor = author.getRealAuthor();
        assertNull(realAuthor);
    }

    @Test
    void liveLookup02()
            throws SearchException, CredentialsException {

        final boolean modified;
        final Author author;
        final Author realAuthor;

        author = new Author("<Indéterminé>", "");
        modified = resolver.resolve(context, author);
        // TODO: if not found in the local cache, modified will be 'false'
        //  otherwise it will be 'true'.
        assertTrue(modified);
        assertEquals("<Indéterminé>", author.getFamilyName());
        realAuthor = author.getRealAuthor();
        assertNull(realAuthor);
    }

    @Test
    void liveLookup03()
            throws SearchException, CredentialsException {

        final boolean modified;
        final Author author;
        final Author realAuthor;

        author = new Author("61Chi", "");
        modified = resolver.resolve(context, author);
        assertTrue(modified);
        assertEquals("61Chi", author.getFamilyName());
        realAuthor = author.getRealAuthor();
        assertNotNull(realAuthor);
        assertEquals("Liu", realAuthor.getFamilyName());
        assertEquals("Yi-chi", realAuthor.getGivenNames());
        assertNull(realAuthor.getRealAuthor());
    }

    @Test
    void liveLookup_no_pseudonym01()
            throws SearchException, CredentialsException {

        final Author author = new Author("Giraud", "Jean");

        final boolean resolved = resolver.resolve(context, author);
        assertTrue(resolved);

        Log.d(TAG, author.toString());

        assertEquals("Giraud", author.getFamilyName());
        assertEquals("Jean", author.getGivenNames());
        assertEquals("1938-05-08", author.getBirthDate().orElse(null));
        assertEquals("2012-03-10", author.getDeathDate().orElse(null));
        assertNull(author.getRealAuthor());

        final Optional<String> oIv;
        oIv = author.getIdentifierValue(Identifier.SID_BEDETHEQUE);
        assertTrue(oIv.isPresent());
        assertEquals("6231", oIv.get());
    }

    @Test
    void liveLookup_with_pseudonym01()
            throws SearchException, CredentialsException {

        final Author author = new Author("Moebius", "");

        final boolean resolved = resolver.resolve(context, author);
        assertTrue(resolved);

        Log.d(TAG, author.toString());

        assertEquals("Moebius", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals("1938-05-08", author.getBirthDate().orElse(null));
        assertEquals("2012-03-10", author.getDeathDate().orElse(null));

        Optional<String> oIv;
        oIv = author.getIdentifierValue(Identifier.SID_BEDETHEQUE);
        assertTrue(oIv.isPresent());
        assertEquals("70", oIv.get());

        final Author realAuthor = author.getRealAuthor();
        assertNotNull(realAuthor);

        assertEquals("Giraud", realAuthor.getFamilyName());
        assertEquals("Jean", realAuthor.getGivenNames());
        assertEquals("1938-05-08", realAuthor.getBirthDate().orElse(null));
        assertEquals("2012-03-10", realAuthor.getDeathDate().orElse(null));

        oIv = realAuthor.getIdentifierValue(Identifier.SID_BEDETHEQUE);
        assertTrue(oIv.isPresent());
        // 70, same as the pen-name, AND NOT 6231 which is Jean Giraud himself.
        // The site actually has TWO records for these situations.
        // One for "pen+real", and ANOTHER for "real-name-on-book".
        assertEquals("70", oIv.get());
    }

    @Test
    void liveLookup_with_pseudonym02()
            throws SearchException, CredentialsException {

        // WRONG diacritic on purpose. Actual is "Jijé"
        final Author author = new Author("Jije", "");

        final boolean resolved = resolver.resolve(context, author);
        assertTrue(resolved);

        Log.d(TAG, author.toString());

        // Diacritic should be corrected
        assertEquals("Jijé", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals("1914-01-13", author.getBirthDate().orElse(null));
        assertEquals("1980-06-19", author.getDeathDate().orElse(null));

        Optional<String> oIv;
        oIv = author.getIdentifierValue(Identifier.SID_BEDETHEQUE);
        assertTrue(oIv.isPresent());
        assertEquals("367", oIv.get());

        final Author realAuthor = author.getRealAuthor();
        assertNotNull(realAuthor);

        assertEquals("Gillain", realAuthor.getFamilyName());
        assertEquals("Joseph", realAuthor.getGivenNames());
        assertEquals("1914-01-13", realAuthor.getBirthDate().orElse(null));
        assertEquals("1980-06-19", realAuthor.getDeathDate().orElse(null));

        oIv = realAuthor.getIdentifierValue(Identifier.SID_BEDETHEQUE);
        assertTrue(oIv.isPresent());
        assertEquals("367", oIv.get());
    }
}
