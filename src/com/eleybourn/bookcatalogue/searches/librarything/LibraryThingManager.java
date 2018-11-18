/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.searches.librarything;

import android.content.Context;
import android.net.ParseException;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.BundleUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.Utils;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Handle all aspects of searching (and ultimately synchronizing with) LibraryThing.
 *
 * The basic URLs are:
 *
 * Covers via ISBN: http://covers.librarything.com/devkey/<DEVKEY>/large/isbn/<ISBN>
 *
 *
 * Editions via ISBN: http://www.librarything.com/api/thingISBN/%s
 *
 * <idlist>
 * <isbn>0441172717</isbn>
 * <isbn>0441013597</isbn>
 * <isbn>0340839937</isbn>
 * ...
 *
 * REST api: http://www.librarything.com/services/rest/documentation/1.1/
 *
 * Details via ISBN: http://www.librarything.com/services/rest/1.1/?method=librarything.ck.getwork&apikey=<DEVKEY>&isbn=<ISBN>
 *
 * xml see {@link #search} header
 *
 * ENHANCE: extend the use of LibraryThing:
 * - Lookup title using keywords: http://www.librarything.com/api/thingTitle/hand oberon
 *
 * - consider scraping html for covers: http://www.librarything.com/work/18998/covers
 * with 18998 being the 'work' identifier.
 *
 * selector:
 * #coverlist_customcovers
 * then all 'img'
 * and use the href.
 *
 * @author Philip Warner
 */
public class LibraryThingManager {
    /** Name of preference that controls display of alert about LibraryThing */
    public static final String PREFS_LT_HIDE_ALERT = "lt_hide_alert";

    /** file suffix for cover files */
    public static final String FILENAME_SUFFIX = "_LT";

    /** Name of preference that contains the dev key for the user */
    static final String PREFS_LT_DEV_KEY = "lt_devkey";

    /** LibraryThing extra fields in the results for potential future usage */
    private static final String LT_PLACES = "__places";
    private static final String LT_CHARACTERS = "__characters";

    /** some common XML attributes */
    private static final String XML_ATTR_ID = "id";
    private static final String XML_ATTR_TYPE = "type";
    private static final String XML_ATTR_NAME = "name";

    /** XML tags we look for */
    //private static final String XML_RESPONSE = "response";
    private static final String XML_AUTHOR = "author";
    /** <item id="5196084" type="work"> */
    private static final String XML_ITEM = "item";
    /** a 'field' (see below) */
    private static final String XML_FIELD = "field";
    /** a 'fact' in a 'factlist' of a 'field' */
    private static final String XML_FACT = "fact";
    /** fields */
    private static final String XML_FIELD_SERIES = "series";
    private static final String XML_FIELD_CANONICAL_TITLE = "canonicaltitle";
    private static final String XML_FIELD_CHARACTERS = "characternames";
    private static final String XML_FIELD_PLACES = "placesmentioned";

    /** isbn tag in an editions xml response */
    private static final String XML_EDITIONS_ISBN = "isbn";

    /** base urls */
    private static final String BASE_URL = "https://www.librarything.com";
    private static final String BASE_URL_COVERS = "https://covers.librarything.com";
    /** book details urls */
    private static final String DETAIL_URL = BASE_URL + "/services/rest/1.1/?method=librarything.ck.getwork&apikey=%1$s&isbn=%2$s";
    /** fetches all isbn's from editions related to the requested isbn */
    private static final String EDITIONS_URL = BASE_URL + "/api/thingISBN/%s";
    /** cover size specific urls */
    private static final String COVER_URL_LARGE = BASE_URL_COVERS + "/devkey/%1$s/large/isbn/%2$s";
    private static final String COVER_URL_MEDIUM = BASE_URL_COVERS + "/devkey/%1$s/medium/isbn/%2$s";
    private static final String COVER_URL_SMALL = BASE_URL_COVERS + "/devkey/%1$s/small/isbn/%2$s";

    /** to control access to mLastRequestTime, we synchronize on this final Object */
    @NonNull
    private static final Object LAST_REQUEST_TIME_LOCK = new Object();
    /** Stores the last time an API request was made to avoid breaking API rules.
     * Only modify this value from inside a synchronized (LAST_REQUEST_TIME_LOCK) */
    @NonNull
    private static Long mLastRequestTime = 0L;

    public LibraryThingManager() {
    }

    @NonNull
    public static String getBaseURL() {
        return BASE_URL;
    }

    /**
     * Use mLastRequestTime to determine how long until the next request is allowed; and
     * update mLastRequestTime this needs to be synchronized across threads.
     *
     * Note that as a result of this approach mLastRequestTime may in fact be
     * in the future; callers to this routine effectively allocate time slots.
     *
     * This method will sleep() until it can make a request; if ten threads call this
     * simultaneously, one will return immediately, one will return 1 second later, another
     * two seconds etc.
     */
    private static void waitUntilRequestAllowed() {
        long now = System.currentTimeMillis();
        long wait;
        synchronized (LAST_REQUEST_TIME_LOCK) {
            wait = 1000 - (now - mLastRequestTime);
            //
            // mLastRequestTime must be updated while synchronized. As soon as this
            // block is left, another block may perform another update.
            //
            if (wait < 0) {
                wait = 0;
            }
            mLastRequestTime = now + wait;
        }

        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Search for edition data.
     *
     * @return a list of isbn's of alternative editions of our original isbn
     */
    @NonNull
    public static List<String> searchEditions(final @NonNull String isbn) {
        // Base path for an ISBN search
        String path = String.format(EDITIONS_URL, isbn);
        // static publicEntry point to LT, so check
        if (!IsbnUtils.isValid(isbn)) {
            throw new RTE.IsbnInvalidException(isbn);
        }

        List<String> editions = new ArrayList<>();
        // add the original isbn, as there might be more images at the time this search is done.
        editions.add(isbn);

        // Setup the parser
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SearchLibraryThingEditionHandler entryHandler = new LibraryThingManager.SearchLibraryThingEditionHandler(editions);

        // Make sure we follow LibraryThing ToS (no more than 1 request/second).
        waitUntilRequestAllowed();

        // Get it
        try {
            URL url = new URL(path);
            SAXParser parser = factory.newSAXParser();
            parser.parse(Utils.getInputStreamWithTerminator(url), entryHandler);
            // Don't bother catching general exceptions, they will be caught by the caller.
        } catch (@NonNull ParserConfigurationException | IOException | SAXException e) {
            Logger.error(e);
        }

        return editions;
    }

    public static void showLtAlertIfNecessary(final @NonNull Context context, final boolean always, final @NonNull String suffix) {
        LibraryThingManager ltm = new LibraryThingManager();
        if (!ltm.isAvailable()) {
            StandardDialogs.needLibraryThingAlert(context, always, suffix);
        }
    }

    /**
     * Search LibraryThing for an ISBN using the Web API.
     *
     * A typical (and thorough) LibraryThing ISBN response looks like:
     *
     * <pre>
     * <?xml version="1.0" encoding="UTF-8"?>
     * <response stat="ok">
     * <ltml xmlns="http://www.librarything.com/" version="1.1">
     * <item id="5196084" type="work">
     * <author id="28" authorcode="asimovisaac">Isaac Asimov</author>
     * <url>http://www.librarything.com/work/5196084</url>
     * <commonknowledge>
     * <fieldList>
     * <field type="4" name="awards" displayName="Awards and honors">
     * <versionList>
     * <version id="3324305" archived="0" lang="eng">
     * <date timestamp="1296476301">Mon, 31 Jan 2011 07:18:21 -0500</date>
     * <person id="325052"><name>Cecrow</name><url>http://www.librarything.com/profile/Cecrow</url></person>
     * <factList>
     * <fact>1001 Books You Must Read Before You Die (2006/2008/2010 Edition)</fact>
     * <fact>Astounding/Analog Science Fiction and Fact All-Time Poll (placed 23, 1952)</fact>
     * <fact>Astounding/Analog Science Fiction and Fact All-Time Poll (placed 21, 1956)</fact>
     * <fact>Harenberg Buch der 1000 B�cher (1. Ausgabe)</fact>
     * <fact>501 Must-Read Books (Science Fiction)</fact>
     * </factList>
     * </version>
     * </versionList>
     * </field>
     * <field type="37" name="movies" displayName="Related movies">
     * <versionList>
     * <version id="3120269" archived="0" lang="eng">
     * <date timestamp="1292202792">Sun, 12 Dec 2010 20:13:12 -0500</date>
     * <person id="656066">
     * <name>Scottneumann</name>
     * <url>http://www.librarything.com/profile/Scottneumann</url>
     * </person>
     * <factList>
     * <fact>Robots (1988 | tt0174170)</fact>
     * <fact>I, Robot (2004 | tt0343818)</fact>
     * <fact>The Outer Limits: I Robot (1963 | tt0056777)</fact>
     * </factList>
     * </version>
     * </versionList>
     * </field>
     * <field type="40" name="publisherseries" displayName="Publisher Series">
     * <versionList>
     * <version id="2971007" archived="0" lang="eng">
     * <date timestamp="1289497446">Thu, 11 Nov 2010 12:44:06 -0500</date>
     * <person id="3929">
     * <name>sonyagreen</name>
     * <url>http://www.librarything.com/profile/sonyagreen</url>
     * </person>
     * <factList>
     * <fact>Voyager Classics</fact>
     * </factList>
     * </version>
     * </versionList>
     * </field>
     * <field type="14" name="description" displayName="Description">
     * <versionList>
     * <version id="2756634" archived="0" lang="eng">
     * <date timestamp="1281897478">Sun, 15 Aug 2010 14:37:58 -0400</date>
     * <person id="203279">
     * <name>jseger9000</name>
     * <url>http://www.librarything.com/profile/jseger9000</url>
     * </person>
     * <factList>
     * <fact>&lt;![CDATA[ Contents:&lt;br&gt;&lt;br&gt;Introduction&lt;br&gt;Robbie&lt;br&gt;Runaround&lt
     * ;br&gt;Reason&lt;br&gt;Catch That Rabbit&lt;br&gt;Liar!&lt;br&gt;Little Lost Robot&lt
     * ;br&gt;Escape!&lt;br&gt;Evidence&lt;br&gt;The Evitable Conflict ]]&gt;
     * </fact>
     * </factList>
     * </version>
     * </versionList>
     * </field>
     * <field type="23" name="series" displayName="Series">
     * <versionList>
     * <version id="2742329" archived="0" lang="eng">
     * <date timestamp="1281338643">Mon, 09 Aug 2010 03:24:03 -0400</date>
     * <person id="1162290">
     * <name>larry.auld</name>
     * <url>http://www.librarything.com/profile/larry.auld</url>
     * </person>
     * <factList>
     * <fact>Isaac Asimov's Robot Series (0.1)</fact>
     * <fact>Robot/Foundation</fact>
     * <fact>Robot/Empire/Foundation - Chronological (book 1)</fact>
     * </factList>
     * </version>
     * </versionList>
     * </field>
     * <field type="16" name="originalpublicationdate" displayName="Original publication date">
     * <versionList>
     * <version id="2554955" archived="0" lang="eng">
     * <date timestamp="1275746736">Sat, 05 Jun 2010 10:05:36 -0400</date>
     * <person id="125174">
     * <name>paulhurtley</name>
     * <url>http://www.librarything.com/profile/paulhurtley</url>
     * </person>
     * <factList>
     * <fact>1950 (Collection)</fact>
     * <fact>1944 (Catch that Rabbit)</fact>
     * <fact>1945 (Escape!)</fact>
     * <fact>1946 (Evidence)</fact>
     * <fact>1950 (The Evitable Conflict)</fact>
     * <fact>1941  (Liar)</fact>
     * <fact>1947  (Little Lost Robot)</fact>
     * <fact>1940  (Robbie)</fact>
     * <fact>1942  (Runaround)</fact>
     * <fact>1941  (Reason)</fact>
     * </factList>
     * </version>
     * </versionList>
     * </field>
     * <field type="27" name="quotations" displayName="Quotations">
     * <versionList>
     * <version id="2503597" archived="0" lang="eng">
     * <date timestamp="1274377341">Thu, 20 May 2010 13:42:21 -0400</date>
     * <person id="1797">
     * <name>lorax</name>
     * <url>http://www.librarything.com/profile/lorax</url>
     * </person>
     * <factList>
     * <fact>&lt;![CDATA[ The Three Laws of Robotics
     * 1. A robot may not injure a human being, or, through inaction, allow a human being to come to harm.
     * 2. A robot must obey the orders given it by human beings except where such orders would conflict
     * with the First Law.
     * 3. A robot must protect its own existence as long as such protection does not conflict with the
     * First or Second Law.  ]]&gt;
     * </fact>
     * </factList>
     * </version>
     * </versionList>
     * </field>
     * <field type="30" name="dedication" displayName="Dedication">
     * <versionList>
     * <version id="2503596" archived="0" lang="eng">
     * <date timestamp="1274377341">Thu, 20 May 2010 13:42:21 -0400</date>
     * <person id="1797">
     * <name>lorax</name>
     * <url>http://www.librarything.com/profile/lorax</url>
     * </person>
     * <factList>
     * <fact>&lt;![CDATA[ To John W. Campbell, Jr., who godfathered the robots ]]&gt;</fact>
     * </factList>
     * </version>
     * </versionList>
     * </field>
     * <field type="26" name="lastwords" displayName="Last words">
     * <versionList>
     * <version id="2503594" archived="0" lang="eng">
     * <date timestamp="1274377340">Thu, 20 May 2010 13:42:20 -0400</date>
     * <person id="1797">
     * <name>lorax</name>
     * <url>http://www.librarything.com/profile/lorax</url>
     * </person>
     * <factList>
     * <fact>&lt;![CDATA[ Robbie:&lt;br&gt;"Well," said Mrs. Weston, at last, "I guess he can stay with us until he rusts." ]]&gt;</fact>
     * <fact>&lt;![CDATA[ Runaround:&lt;br&gt;"Space Station," said Donovan, "here I come." ]]&gt;</fact>
     * <fact>&lt;![CDATA[ Reason"&lt;br&gt;He grinned � and went into the ship.  Muller would be here for several weeks � ]]&gt;</fact>
     * <fact>&lt;![CDATA[ Catch That Rabbit:&lt;br&gt;****&lt;br&gt;**** too spoilerish! ]]&gt;</fact>
     * <fact>&lt;![CDATA[ Liar:&lt;br&gt;"Liar!" ]]&gt;</fact>
     * <fact>&lt;![CDATA[ Little Lost Robot:&lt;br&gt;"� His very superiority caught him.  Good-by General" ]]&gt;</fact>
     * <fact>&lt;![CDATA[ Escape:&lt;br&gt;To which Bogert added absently, "Strictly according to the contract, too." ]]&gt;</fact>
     * <fact>&lt;![CDATA[ Evidence:&lt;br&gt;Stephen Byerley chuckled.  "I must reply that that is a somewhat farfetched idea."&lt;br&gt;The door closed behind her. ]]&gt;</fact>
     * <fact>&lt;![CDATA[ The Evitable Conflict:&lt;br&gt;And the fire behind the quartz went out and only a curl of smoke was left to indicate its place. ]]&gt;</fact>
     * <fact>&lt;![CDATA[ &lt;i&gt;She died last month at the age of eighty-two.&lt;/i&gt; ]]&gt;</fact>
     * </factList>
     * </version>
     * </versionList>
     * </field>
     * <field type="25" name="firstwords" displayName="First words">
     * <versionList>
     * <version id="2503593" archived="0" lang="eng">
     * <date timestamp="1274377340">Thu, 20 May 2010 13:42:20 -0400</date>
     * <person id="1797">
     * <name>lorax</name>
     * <url>http://www.librarything.com/profile/lorax</url>
     * </person>
     * <factList>
     * <fact>&lt;![CDATA[ Robbie:&lt;br&gt;"Ninety-eight � ninety-nine � &lt;i&gt;one hundred&lt;/i&gt;." ]]&gt;</fact>
     * <fact>&lt;![CDATA[ Runaround:&lt;br&gt;It was one of Gregory Powell's favorite platitudes that nothing was to
     * be gained from excitement, so when Mike Donovan came leaping down the stairs toward him, red hair matted
     * with perspiration, Powell frowned. ]]&gt;
     * </fact>
     * <fact>&lt;![CDATA[ Reason:&lt;br&gt;Half a year later, the boys had changed their minds. ]]&gt;</fact>
     * <fact>&lt;![CDATA[ Catch That Rabbit:&lt;br&gt;The vacation was longer than two weeks. ]]&gt;</fact>
     * <fact>&lt;![CDATA[ Liar!&lt;br&gt;Alfred Lanning lit his cigar carefully, but the tips of his fingers were trembling slightly. ]]&gt;</fact>
     * <fact>&lt;![CDATA[ Little Lost Robot:&lt;br&gt;When I did see Susan Calvin again, it was at the door of her office. ]]&gt;</fact>
     * <fact>&lt;![CDATA[ Escape!:&lt;br&gt;When Susan Calvin returned from Hyper Base, Alfred Lanning was waiting for her. ]]&gt;</fact>
     * <fact>&lt;![CDATA[ Evidence:&lt;br&gt;Francis Quinn was a politician of the new school. ]]&gt;</fact>
     * <fact>&lt;![CDATA[ The Evitable Conflict:&lt;br&gt;The Co-ordinator, in his private study, had that medieval curiosity, a fireplace. ]]&gt;</fact>
     * <fact>&lt;![CDATA[ &lt;i&gt;I looked at my notes and I didn't like them.&lt;/i&gt; (Introduction) ]]&gt;</fact>
     * </factList>
     * </version>
     * </versionList>
     * </field>
     * <field type="21" name="canonicaltitle" displayName="Canonical title">
     * <versionList>
     * <version id="2503590" archived="0" lang="eng">
     * <date timestamp="1274377338">Thu, 20 May 2010 13:42:18 -0400</date>
     * <person id="1797">
     * <name>lorax</name>
     * <url>http://www.librarything.com/profile/lorax</url>
     * </person>
     * <factList>
     * <fact>I, Robot</fact>
     * </factList>
     * </version>
     * </versionList>
     * </field>
     * <field type="3" name="characternames" displayName="People/Characters">
     * <versionList>
     * <version id="2503589" archived="0" lang="eng">
     * <date timestamp="1274377337">Thu, 20 May 2010 13:42:17 -0400</date>
     * <person id="1797">
     * <name>lorax</name>
     * <url>http://www.librarything.com/profile/lorax</url>
     * </person>
     * <factList>
     * <fact>Susan Calvin</fact>
     * <fact>Cutie (QT1)</fact>
     * <fact>Gregory Powell</fact>
     * <fact>Mike Donovan</fact>
     * <fact>Robbie (RB-series)</fact>
     * <fact>Mr. Weston</fact>
     * <fact>Gloria Weston</fact>
     * <fact>Mrs. Weston</fact>
     * <fact>SPD-13 (Speedy)</fact>
     * <fact>Speedy (SPD-13)</fact>
     * <fact>QT1 (Cutie)</fact>
     * <fact>The Master</fact>
     * <fact>Prophet of the Master</fact>
     * <fact>Ren� Descartes</fact>
     * <fact>DV-5 (Dave)</fact><fact>Dave (DV-5)</fact>
     * <fact>HRB-34 (Herbie)</fact>
     * <fact>Herbie (HRB-34)</fact>
     * <fact>Gerald Black</fact>
     * <fact>NS-2 (Nestor)</fact>
     * <fact>Nestor (NS-2)</fact>
     * <fact>Peter Bogert</fact>
     * <fact>The Brain (computer)</fact>
     * <fact>Stephen Byerley</fact>
     * <fact>Francis Quinn</fact>
     * </factList>
     * </version>
     * </versionList>
     * </field>
     * <field type="2" name="placesmentioned" displayName="Important places">
     * <versionList>
     * <version id="2503588" archived="0" lang="eng">
     * <date timestamp="1274377336">Thu, 20 May 2010 13:42:16 -0400</date>
     * <person id="1797">
     * <name>lorax</name>
     * <url>http://www.librarything.com/profile/lorax</url>
     * </person>
     * <factList>
     * <fact>Mercury</fact>
     * <fact>New York, New York, USA</fact>
     * <fact>Roosevelt Building</fact>
     * <fact>U.S. Robots and Mechanical Men factory</fact>
     * <fact>Hyper Base</fact>
     * </factList>
     * </version>
     * </versionList>
     * </field>
     * </fieldList>
     * </commonknowledge>
     * </item>
     * <legal>By using this data you agree to the LibraryThing API terms of service.</legal>
     * </ltml>
     * </response>
     *
     * </pre>
     *
     * A less well-known work produces rather less data:
     *
     * <?xml version="1.0" encoding="UTF-8"?>
     * <response stat="ok">
     * <ltml xmlns="http://www.librarything.com/" version="1.1">
     * <item id="255375" type="work">
     * <author id="359458" authorcode="fallonmary">Mary Fallon</author>
     * <url>http://www.librarything.com/work/255375</url>
     * <commonknowledge/>
     * </item>
     * <legal>By using this data you agree to the LibraryThing API terms of service.</legal>
     * </ltml>
     * </response>
     *
     * but in both cases, it should be noted that the covers are still available.
     *
     * @param isbn ISBN to lookup
     * @param book Collection to save results in
     *
     *             call {@link #isAvailable()} before calling this method
     */
    void search(final @NonNull String isbn,
                final @NonNull Bundle /* out */ book,
                final boolean fetchThumbnail) throws IOException {

        String devKey = getDevKey();
        if (devKey.isEmpty()) {
            throw new RTE.DeveloperKeyMissingException();
        }

        // Base path for an ISBN search
        String path = String.format(DETAIL_URL, devKey, isbn);

        if (!IsbnUtils.isValid(isbn)) {
            throw new RTE.IsbnInvalidException(isbn);
        }

        URL url;

        // Setup the parser
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser;
        SearchLibraryThingEntryHandler entryHandler = new LibraryThingManager.SearchLibraryThingEntryHandler(book);

        try {
            url = new URL(path);
            parser = factory.newSAXParser();

            // Make sure we follow LibraryThing ToS (no more than 1 request/second).
            waitUntilRequestAllowed();

            parser.parse(Utils.getInputStreamWithTerminator(url), entryHandler);

            // only catch exceptions related to the parsing, others will be caught by the caller.
        } catch (ParserConfigurationException | ParseException | SAXException e) {
            Logger.error(e);
        }

        if (fetchThumbnail) {
            File file = getCoverImage(isbn, ImageSizes.LARGE);
            if (file != null) {
                StringList.addOrAppend(book, UniqueId.BKEY_THUMBNAIL_FILE_SPEC, file.getAbsolutePath());
            }
        }
    }

    /**
     * Get the cover image using the ISBN
     *
     * call {@link #isAvailable()} before calling this method
     */
    @NonNull
    private String prepareCoverImageUrl(final @NonNull String isbn, final @NonNull ImageSizes size) {
        String devKey = getDevKey();
        if (devKey.isEmpty()) {
            throw new RTE.DeveloperKeyMissingException();
        }

        String path;
        switch (size) {
            case SMALL:
                path = COVER_URL_SMALL;
                break;
            case MEDIUM:
                path = COVER_URL_MEDIUM;
                break;
            case LARGE:
                path = COVER_URL_LARGE;
                break;
            default:
                path = COVER_URL_SMALL;
                break;
        }
        // Get the 'large' version
        return String.format(path, devKey, isbn);
    }

    /**
     * @param isbn for book cover to find
     * @param size the LT {@link ImageSizes} size to get
     *
     * @return found/saved File, or null when none found (or any other failure)
     */
    @Nullable
    public File getCoverImage(final @NonNull String isbn, final @NonNull ImageSizes size) {
        String url = prepareCoverImageUrl(isbn, size);

        // Make sure we follow LibraryThing ToS (no more than 1 request/second).
        waitUntilRequestAllowed();

        // Save it with a suffix
        String fileSpec = ImageUtils.saveThumbnailFromUrl(url, FILENAME_SUFFIX + "_" + isbn + "_" + size);

        if (fileSpec.isEmpty()) {
            return null;
        }

        return new File(fileSpec);
    }

    /**
     * external users (to this class) should call this before doing any searches
     *
     * @return <tt>true</tt>if there is a non-empty dev key
     */
    public boolean isAvailable() {
        return !getDevKey().isEmpty();
    }

    /**
     * @return the dev key, CAN BE EMPTY but won't be null
     */
    @NonNull
    private String getDevKey() {
        String key = BookCatalogueApp.getStringPreference(PREFS_LT_DEV_KEY, null);
        if (key != null && !key.isEmpty()) {
            return key.replaceAll("[\\r\\t\\n\\s]*", "");
        }
        return "";
    }

    // Field types we are interested in.
    private enum FieldTypes {
        NONE, AUTHOR, TITLE, SERIES, PLACES, CHARACTERS, OTHER
    }

    // Sizes of thumbnails
    public enum ImageSizes {
        SMALL, MEDIUM, LARGE
    }

    /**
     * Parser Handler to collect the edition data.
     *
     * Typical request output:
     *
     * <?xml version="1.0" encoding="utf-8"?>
     * <idlist>
     * <isbn>0380014300</isbn>
     * <isbn>0839824270</isbn>
     * <isbn>0722194390</isbn>
     * <isbn>0783884257</isbn>
     * ...etc...
     * <isbn>2207301907</isbn>
     * </idlist>
     *
     * @author Philip Warner
     */
    static private class SearchLibraryThingEditionHandler extends DefaultHandler {
        private final StringBuilder mBuilder = new StringBuilder();
        @NonNull
        private final List<String> mEditions;

        SearchLibraryThingEditionHandler(final @NonNull List<String> editions) {
            mEditions = editions;
        }

        @Override
        @CallSuper
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
            super.characters(ch, start, length);
            mBuilder.append(ch, start, length);
        }

        @Override
        @CallSuper
        public void endElement(final String uri, final @NonNull String localName, final String name) throws SAXException {
            super.endElement(uri, localName, name);

            if (localName.equalsIgnoreCase(XML_EDITIONS_ISBN)) {
                // Add the isbn
                String isbn = mBuilder.toString();
                mEditions.add(isbn);
            }
            // Note:
            // Always reset the length. This is not entirely the right thing to do, but works
            // because we always want strings from the lowest level (leaf) XML elements.
            // To be completely correct, we should maintain a stack of builders that are pushed and
            // popped as each startElement/endElement is called. But lets not be pedantic for now.
            mBuilder.setLength(0);
        }

    }

    /**
     * Parser Handler to collect the book data.
     *
     * @author Philip Warner
     */
    private class SearchLibraryThingEntryHandler extends DefaultHandler {
        @NonNull
        private final Bundle mBookData;
        private final StringBuilder mBuilder = new StringBuilder();

        @NonNull
        private FieldTypes mFieldType = FieldTypes.OTHER;

        SearchLibraryThingEntryHandler(final @NonNull Bundle bookData) {
            mBookData = bookData;
        }

        @Override
        @CallSuper
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
            super.characters(ch, start, length);
            mBuilder.append(ch, start, length);
        }

        @Override
        @CallSuper
        public void startElement(final String uri, final @NonNull String localName, final String name, final @NonNull Attributes attributes) throws SAXException {
            super.startElement(uri, localName, name, attributes);

            // reset the string. See note in endElement() for a discussion.
            mBuilder.setLength(0);

            if (localName.equalsIgnoreCase(XML_FIELD)) {
                // FIELDs are the main things we want. Once we are in a field we wait for a XML_FACT; these
                // are read in the endElement() method.
                String fieldName = attributes.getValue("", XML_ATTR_NAME);
                if (fieldName != null) {
                    if (fieldName.equalsIgnoreCase(XML_FIELD_CANONICAL_TITLE)) {
                        mFieldType = FieldTypes.TITLE;
                    } else if (fieldName.equalsIgnoreCase(XML_FIELD_SERIES)) {
                        mFieldType = FieldTypes.SERIES;
                    } else if (fieldName.equalsIgnoreCase(XML_FIELD_PLACES)) {
                        mFieldType = FieldTypes.PLACES;
                    } else if (fieldName.equalsIgnoreCase(XML_FIELD_CHARACTERS)) {
                        mFieldType = FieldTypes.CHARACTERS;
                    }
                }
            } else if (localName.equalsIgnoreCase(XML_ITEM)) {
                String type = attributes.getValue("", XML_ATTR_TYPE);
                if (type != null && type.equalsIgnoreCase("work")) { // leave hardcoded, it's a value.
                    try {
                        long id = Long.parseLong(attributes.getValue("", XML_ATTR_ID));
                        mBookData.putLong(UniqueId.KEY_BOOK_LIBRARY_THING_ID, id );
                    } catch (NumberFormatException ignore) {
                    }
                }
//          } else if (localName.equalsIgnoreCase(XML_RESPONSE)){
//			    // Not really much to do; we *could* look for the <err> element, then report it.
//				String stat = attributes.getValue("", "stat");
            }
        }

        @Override
        @CallSuper
        public void endElement(final String uri, final @NonNull String localName, final String name) throws SAXException {
            super.endElement(uri, localName, name);

            if (localName.equalsIgnoreCase(XML_FIELD)) {
                // Reset the current field
                mFieldType = FieldTypes.NONE;

            } else if (localName.equalsIgnoreCase(XML_AUTHOR)) {
                StringList.addOrAppend(mBookData, UniqueId.BKEY_AUTHOR_STRING_LIST, mBuilder.toString());

            } else if (localName.equalsIgnoreCase(XML_FACT)) {
                // Process the XML_FACT according to the active XML_FIELD type.

                switch (mFieldType) {

                    case TITLE:
                        BundleUtils.addIfNotPresent(mBookData, UniqueId.KEY_TITLE, mBuilder.toString());
                        break;

                    case SERIES:
                        StringList.addOrAppend(mBookData, UniqueId.BKEY_SERIES_STRING_LIST, mBuilder.toString());
                        break;

                    case PLACES:
                        StringList.addOrAppend(mBookData, LT_PLACES, mBuilder.toString());
                        break;

                    case CHARACTERS:
                        StringList.addOrAppend(mBookData, LT_CHARACTERS, mBuilder.toString());
                        break;
                }
            }
            // Note:
            // Always reset the length. This is not entirely the right thing to do, but works
            // because we always want strings from the lowest level (leaf) XML elements.
            // To be completely correct, we should maintain a stack of builders that are pushed and
            // popped as each startElement/endElement is called. But lets not be pedantic for now.
            mBuilder.setLength(0);
        }
    }
}
