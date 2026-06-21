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

package com.hardbacknutter.nevertoomanybooks.searchengines.wikidata;

import android.util.Log;

import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IisnParseTest
        extends BaseDBTest {

    private static final String TAG = "IisnParseTest";

    private WikidataSearchEngine searchEngine;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine =
                (WikidataSearchEngine) EngineId.Wikidata.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);
    }

    @Test
    void fsndsf()
            throws IOException {

        final ProductCode pc = ISBN.parse("0024984X");
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.wikidata_issn_fsf);
        final Book book = new Book();
        searchEngine.parseIssn(context, document, pc, book);

        Log.d(TAG, book.toString());

        assertEquals("The Magazine of Fantasy & Science Fiction",
                     book.getString(DBKey.TITLE, null));
        assertEquals("eng", book.getString(DBKey.LANGUAGE, null));
        assertEquals("digest size", book.getString(DBKey.FORMAT, null));

        assertEquals("2 monthly", book.getString(DBKey.DESCRIPTION, null));

        assertEquals("Q937202", book.getIdentifierValue(Identifier.SID_WIKIDATA).orElse(null));

        assertEquals("179825835", book.getIdentifierValue(Identifier.SID_VIAF).orElse(null));
        assertEquals("20325", book.getIdentifierValue(Identifier.SID_ISFDB).orElse(null));

        assertEquals("4678619-3", book.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        assertEquals("n80014289", book.getIdentifierValue(Identifier.SID_LCCN).orElse(null));
        assertEquals("18737979", book.getIdentifierValue(Identifier.SID_OCLC).orElse(null));
        // and many more...

        // description=2 monthly, language=eng, format=digest size,
        // title=The Magazine of Fantasy & Science Fiction,
        // identifier_list=[
        // Value{key=wikidata, sid=`Q937202`},
        // Value{key=P12048, sid=`e33af626-c9ba-4649-86b0-d8403f706841`},
        // Value{key=P8189, sid=`987007406505005171`},
        // Value{key=P5396, sid=`magoffandsf`},
        // Value{key=P5357, sid=`fsf`},
        // Value{key=P3417, sid=`The-Magazine-of-Fantasy-Science-Fiction`},
        // Value{key=P2735, sid=`FantasySF`},
        // Value{key=P2163, sid=`1359328`},
        // Value{key=P1695, sid=`a0000001839514`},
        // Value{key=P1417, sid=`topic/The-Magazine-of-Fantasy-andand-Science-Fiction`},
        // Value{key=P13137, sid=`20325`},
        // Value{key=P724, sid=`fantasyandsciencefiction`},
        // Value{key=P646, sid=`/m/024c8c`},
        // Value{key=P409, sid=`35802831`},
        // Value{key=P244, sid=`n80014289`},
        // Value{key=P243, sid=`18737979`},
        // Value{key=P236, sid=`1095-8258`},
        // Value{key=P227, sid=`4678619-3`},
        // Value{key=P214, sid=`179825835`}
    }

   @Test
    void guardian()
            throws IOException {

        final ProductCode pc = ISBN.parse("0261-3077");
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.wikidata_issn_guardian);
        final Book book = new Book();
        searchEngine.parseIssn(context, document, pc, book);

        Log.d(TAG, book.toString());

        assertEquals("The Guardian",
                     book.getString(DBKey.TITLE, null));
        assertEquals("eng", book.getString(DBKey.LANGUAGE, null));
        assertEquals("tabloid", book.getString(DBKey.FORMAT, null));
        assertEquals("tabloid", book.getString(DBKey.FORMAT, null));

        assertEquals("Daily", book.getString(DBKey.DESCRIPTION));

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(1, publishers.size());

        assertEquals("Guardian News and Media Ltd.", publishers.get(0).getName());

       assertEquals("Q11148", book.getIdentifierValue(Identifier.SID_WIKIDATA).orElse(null));

       assertEquals("4158503-3", book.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        assertEquals("60623878", book.getIdentifierValue(Identifier.SID_OCLC).orElse(null));
        // and many more...

       // 0description=1 daily, language=eng, format=tabloid, title=The Guardian,
       // publisher_list=[Publisher{id=0, name=`Guardian News and Media Ltd.`}],
       // identifier_list=[
       // Value{key=wikidata, sid=`Q11148`},
       // Value{key=P214, sid=`4860158188266720260004`},
       // Value{key=P14541, sid=`47hm8I`},
       // Value{key=P13337, sid=`guardianunlimited.com`},
       // Value{key=P12942, sid=`1598`},
       // Value{key=P12361, sid=`theguardian.com`},
       // Value{key=P12251, sid=`632`},
       // Value{key=P12204, sid=`2105`},
       // Value{key=P12126, sid=`The_Guardian`},
       // Value{key=P12079, sid=`guardian`},
       // Value{key=P11615, sid=`for-profit/guardian`},
       // Value{key=P11223, sid=`27`},
       // Value{key=P10804, sid=`208818013`},
       // Value{key=P10565, sid=`36516`},
       // Value{key=P10283, sid=`S2764508504`},
       // Value{key=P10006, sid=`guardian`},
       // Value{key=P9852, sid=`the-guardian`},
       // Value{key=P9035, sid=`guardian`},
       // Value{key=P8903, sid=`120743`},
       // Value{key=P8408, sid=`TheGuardian-TheNewspaper`},
       // Value{key=P8313, sid=`The_Guardian`},
       // Value{key=P8189, sid=`987007421245705171`},
       // Value{key=P7775, sid=`The_Guardian`},
       // Value{key=P7363, sid=`0307-756X`},
       // Value{key=P7305, sid=`3908648`},
       // Value{key=P7259, sid=`5077`},
       // Value{key=P7199, sid=`356207932`},
       // Value{key=P6981, sid=`66012`},
       // Value{key=P6849, sid=`the-guardian`},
       // Value{key=P6200, sid=`czpxkvk2qxpt`},
       // Value{key=P6157, sid=`CAAqBwgKMMKOqgswwpnCAw`},
       // Value{key=P6136, sid=`UK_TG`},
       // Value{key=P6058, sid=`oeuvre/The_Guardian/122481`},
       // Value{key=P5554, sid=`the-guardian`},
       // Value{key=P5396, sid=`manukguardian`},
       // Value{key=P4527, sid=`10559`},
       // Value{key=P4342, sid=`The_Guardian`},
       // Value{key=P4015, sid=`guardianvideo`},
       // Value{key=P3789, sid=`guardian`},
       // Value{key=P3482, sid=`The Guardian`},
       // Value{key=P3479, sid=`6caa40c4cbc18d281472aa7be6cbcf2b36c805de`},
       // Value{key=P3417, sid=`The-Guardian-newspaper-1`},
       // Value{key=P3222, sid=`guardian`},
       // Value{key=P3219, sid=`the-guardian`},
       // Value{key=P3106, sid=`media/theguardian`},
       // Value{key=P2942, sid=`Guardian`},
       // Value{key=P2924, sid=`2344643`},
       // Value{key=P2847, sid=`+TheGuardian`},
       // Value{key=P2581, sid=`01206062n`},
       // Value{key=P2397, sid=`UCHpw8xwDNhU9gdohEcJu4aA`},
       // Value{key=P2088, sid=`citizen-media`},
       // Value{key=P2013, sid=`theguardian`},
       // Value{key=P2002, sid=`guardian`},
       // Value{key=P1711, sid=`99601`},
       // Value{key=P1617, sid=`f349e97e-d232-403c-aead-a024c3522ba3`},
       // Value{key=P1417, sid=`topic/The-Guardian-British-newspaper`},
       // Value{key=P1258, sid=`critics/source/205`},
       // Value{key=P966, sid=`ef4f9c2d-598a-4aef-88c5-2f749438d3bf`},
       // Value{key=P646, sid=`/m/0cnn5`},
       // Value{key=P345, sid=`co0064503`},
       // Value{key=P269, sid=`152903445`},
       // Value{key=P268, sid=`12342909v`},
       // Value{key=P243, sid=`60623878`},
       // Value{key=P236, sid=`0261-3077`},
       // Value{key=P227, sid=`4158503-3`}]}]}
    }
}
