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

package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("LongLine")
class SidTest
        extends BaseDBTest {

    private static final String TAG = "SidTest";

    private IsfdbSearchEngine searchEngine;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.Isfdb.getConfig().setLogHttpGetRequests(true);
        searchEngine = (IsfdbSearchEngine) EngineId.Isfdb.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    void amazon() {
        Identifier.Value iv;

        iv = searchEngine.parseSid("https://www.amazon.com/dp/1529082919" +
                                   "?ie=UTF8&tag=isfdb-20&linkCode=as2&camp=1789&creative=9325")
                         .orElseThrow();
        assertEquals(Identifier.SID_ASIN, iv.getKey());
        assertEquals("1529082919", iv.getSid());
        iv = searchEngine.parseSid("https://www.amazon.co.uk/dp/1529082919" +
                                   "?ie=UTF8&tag=isfdb-21")
                         .orElseThrow();
        assertEquals(Identifier.SID_ASIN, iv.getKey());
        assertEquals("1529082919", iv.getSid());
        iv = searchEngine.parseSid("https://www.amazon.de/dp/1529082919").orElseThrow();
        assertEquals(Identifier.SID_ASIN, iv.getKey());
        assertEquals("1529082919", iv.getSid());


        iv = searchEngine.parseSid("https://www.amazon.com/dp/B017V568SY" +
                                   "?ie=UTF8&tag=isfdb-20&linkCode=as2&camp=1789&creative=9325")
                         .orElseThrow();
        assertEquals(Identifier.SID_ASIN, iv.getKey());
        assertEquals("B017V568SY", iv.getSid());
        iv = searchEngine.parseSid("https://www.amazon.co.uk/dp/B017V568SY" +
                                   "?ie=UTF8&tag=isfdb-21")
                         .orElseThrow();
        assertEquals(Identifier.SID_ASIN, iv.getKey());
        assertEquals("B017V568SY", iv.getSid());
        iv = searchEngine.parseSid("https://www.amazon.de/dp/B017V568SY").orElseThrow();
        assertEquals(Identifier.SID_ASIN, iv.getKey());
        assertEquals("B017V568SY", iv.getSid());
    }

    @Test
    void librisSe() {
        Identifier.Value iv;
        iv = searchEngine.parseSid("https://libris.kb.se/bib/868526").orElseThrow();
        assertEquals(Identifier.SID_LIBRIS, iv.getKey());
        assertEquals("868526", iv.getSid());
        iv = searchEngine.parseSid("https://libris.kb.se/resource/bib/868526").orElseThrow();
        assertEquals(Identifier.SID_LIBRIS, iv.getKey());
        assertEquals("868526", iv.getSid());

        iv = searchEngine.parseSid("https://libris.kb.se/katalogisering/1jb6znkc0d94rjz")
                         .orElseThrow();
        assertEquals(Identifier.SID_LIBRIS_XL, iv.getKey());
        assertEquals("1jb6znkc0d94rjz", iv.getSid());
        iv = searchEngine.parseSid("https://libris.kb.se/1jb6znkc0d94rjz").orElseThrow();
        assertEquals(Identifier.SID_LIBRIS_XL, iv.getKey());
        assertEquals("1jb6znkc0d94rjz", iv.getSid());
    }

    @Test
    void preDefSids() {
        Identifier.Value iv;

        iv = searchEngine.parseSid("https://www.audible.com/pd/B017V568SY").orElseThrow();
        assertEquals(Identifier.SID_AUDIBLE, iv.getKey());
        assertEquals("B017V568SY", iv.getSid());

        iv = searchEngine.parseSid("https://www.barnesandnoble.com/s/1100340602").orElseThrow();
        assertEquals(Identifier.SID_BARNES_AND_NOBLE, iv.getKey());
        assertEquals("1100340602", iv.getSid());

        iv = searchEngine.parseSid("https://catalogue.bnf.fr/ark:/12148/cb47598555r").orElseThrow();
        assertEquals(Identifier.SID_BNF, iv.getKey());
        assertEquals("cb47598555r", iv.getSid());
        iv = searchEngine.parseSid("https://d-nb.info/1278243054").orElseThrow();
        assertEquals(Identifier.SID_DNB, iv.getKey());
        assertEquals("1278243054", iv.getSid());

        iv = searchEngine.parseSid("https://fantlab.ru/edition440557").orElseThrow();
        assertEquals(Identifier.SID_FANTLAB, iv.getKey());
        assertEquals("440557", iv.getSid());

        iv = searchEngine.parseSid("https://www.goodreads.com/book/show/209796181").orElseThrow();
        assertEquals(Identifier.SID_GOODREADS, iv.getKey());
        assertEquals("209796181", iv.getSid());

        iv = searchEngine.parseSid("http://picarta.pica.nl/xslt/DB=3.9/XMLPRS=Y/PPN?PPN=852323123")
                         .orElseThrow();
        assertEquals(Identifier.SID_KBNL, iv.getKey());
        assertEquals("852323123", iv.getSid());
        iv = searchEngine.parseSid("https://webggc.oclc.org/cbs/DB=2.37/XMLPRS=Y/PPN?PPN=852323123")
                         .orElseThrow();
        assertEquals(Identifier.SID_KBNL, iv.getKey());
        assertEquals("852323123", iv.getSid());

        iv = searchEngine.parseSid("https://opac.kbr.be/Library/doc/SYRACUSE/17274792/")
                         .orElseThrow();
        assertEquals(Identifier.SID_KBR, iv.getKey());
        assertEquals("17274792", iv.getSid());

        iv = searchEngine.parseSid("https://lccn.loc.gov/2021008848").orElseThrow();
        assertEquals(Identifier.SID_LCCN, iv.getKey());
        assertEquals("2021008848", iv.getSid());
        iv = searchEngine.parseSid("https://lccn.loc.gov/66-12593").orElseThrow();
        assertEquals(Identifier.SID_LCCN, iv.getKey());
        assertEquals("66-12593", iv.getSid());
        iv = searchEngine.parseSid("https://lccn.loc.gov/gm71002450").orElseThrow();
        assertEquals(Identifier.SID_LCCN, iv.getKey());
        assertEquals("gm71002450", iv.getSid());

        iv = searchEngine.parseSid("https://www.fantascienza.com/catalogo/volumi/NILF100327")
                         .orElseThrow();
        assertEquals(Identifier.SID_NILF, iv.getKey());
        assertEquals("100327", iv.getSid());

        iv = searchEngine.parseSid("https://www.noosfere.org/livres/niourf.asp?numlivre=2146640321")
                         .orElseThrow();
        assertEquals(Identifier.SID_NOOSFERE, iv.getKey());
        assertEquals("2146640321", iv.getSid());

        iv = searchEngine.parseSid("https://www.worldcat.org/oclc/00734863").orElseThrow();
        assertEquals(Identifier.SID_OCLC, iv.getKey());
        assertEquals("00734863", iv.getSid());

        iv = searchEngine.parseSid("https://openlibrary.org/books/OL25129006M").orElseThrow();
        assertEquals(Identifier.SID_OPEN_LIBRARY, iv.getKey());
        assertEquals("OL25129006M", iv.getSid());

        iv = searchEngine.parseSid("http://id.bnportugal.gov.pt/bib/porbase/1302921").orElseThrow();
        assertEquals(Identifier.SID_PORBASE, iv.getKey());
        assertEquals("1302921", iv.getSid());

        iv = searchEngine.parseSid("https://tercerafundacion.net/biblioteca/ver/libro/75978")
                         .orElseThrow();
        assertEquals(Identifier.SID_TERCERA_FUNDACION, iv.getKey());
        assertEquals("75978", iv.getSid());
    }

    @Test
    void other() {
        Identifier.Value iv;

        // String
        iv = searchEngine.parseSid(
                                 "http://www.philsp.com/homeville/FMI/ZZPERMLINK.ASP?NAME='P_1906ASMAPR'")
                         .orElseThrow();
        assertEquals("fmi", iv.getKey());
        assertEquals("P_1906ASMAPR", iv.getSid());

        // Long
        iv = searchEngine.parseSid("https://iss.ndl.go.jp/api/openurl?ndl_jpno=20087442&locale=en")
                         .orElseThrow();
        assertEquals("jpno", iv.getKey());
        assertEquals("20087442", iv.getSid());

        // Long
        iv = searchEngine.parseSid("https://id.ndl.go.jp/bib/030182294/eng").orElseThrow();
        assertEquals("ndl", iv.getKey());
        assertEquals("030182294", iv.getSid());

        // String
        iv = searchEngine.parseSid("http://www.sfbg.us/book/BARD-ISF-077X").orElseThrow();
        assertEquals("sfbg", iv.getKey());
        assertEquals("BARD-ISF-077X", iv.getSid());

        // Long
        iv = searchEngine.parseSid("http://www.sf-leihbuch.de/index.cfm?bid=1396").orElseThrow();
        assertEquals("sf-leihbuch", iv.getKey());
        assertEquals("1396", iv.getSid());

        // Long
        iv = searchEngine.parseSid("https://nla.gov.au/nla.cat-vn2263478").orElseThrow();
        assertEquals("nla", iv.getKey());
        assertEquals("2263478", iv.getSid());

        // Long
        iv = searchEngine.parseSid("https://biblioman.chitanka.info/books/837").orElseThrow();
        assertEquals("biblioman", iv.getKey());
        assertEquals("837", iv.getSid());
    }

    @Test
    void cobiss() {
        Identifier.Value iv;

        // Long
        // siteUrl: https://bg.cobiss.net/
        // bookUrl: https://plus.cobiss.net/cobiss/bg/en/bib/%s
        iv = searchEngine.parseSid("https://plus.bg.cobiss.net/opac7/bib/1296220#full")
                         .orElseThrow();
        assertEquals("cobiss.bg", iv.getKey());
        assertEquals("1296220", iv.getSid());
        // Long
        // siteUrl: https://sr.cobiss.net/
        // bookUrl: https://plus.cobiss.net/cobiss/sr/en/bib/%s
        iv = searchEngine.parseSid("https://plus.sr.cobiss.net/opac7/bib/155833351#full")
                         .orElseThrow();
        assertEquals("cobiss.sr", iv.getKey());
        assertEquals("155833351", iv.getSid());
    }
}
