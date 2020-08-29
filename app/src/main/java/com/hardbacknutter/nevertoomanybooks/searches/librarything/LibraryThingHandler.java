/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches.librarything;

import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

/**
 * Parser Handler to collect the book data.
 * Search LibraryThing for an ISBN using the Web API.
 * <p>
 * Another good example: (response not shown here)
 * http://www.librarything.com/services/rest/1.1/?
 * method=librarything.ck.getwork&isbn=058603806X&apikey=x
 * <p>
 * A typical (and thorough) LibraryThing ISBN response looks like:
 * ('person' tag removed')
 *
 * <pre>
 *     http://www.librarything.com/services/rest/1.1/?
 *     method=librarything.ck.getwork&isbn=031285966X&apikey=x
 *     {@code
 *  <response stat="ok">
 *    <ltml xmlns="http://www.librarything.com/" version="1.1">
 *    <item id="81766" type="work">
 *    <author id="397" authorcode="vancejack">Jack Vance</author>
 *    <title>Alastor</title>
 *    <rating>7.6</rating>
 *    <url>http://www.librarything.com/work/81766</url>
 *
 *    <commonknowledge>
 *      <fieldList>
 *        <field type="25" name="firstwords" displayName="First words">
 *          <versionList>
 *            <version id="3207634" archived="0" lang="eng">
 *              <date timestamp="1294088287">Mon, 03 Jan 2011 15:58:07 -0500</date>
 *              <factList>
 *                <fact>
 *                  ![CDATA[ <b>Trullion: Alastor ...blah blah ]]>
 *                </fact>
 *                <fact>
 *                  ![CDATA[ <b>Marune: Alastor 933</b></br>Alastor Cl...blah blah ]]>
 *                </fact>
 *                <fact>
 *                  ![CDATA[ <b>Wyst: Alastor 1716</b></br>Alastor Cluster, ...blah blah ]]>
 *                </fact>
 *              </factList>
 *            </version>
 *          </versionList>
 *        </field>
 *        <field type="26" name="lastwords" displayName="Last words">
 *          <versionList>
 *            <version id="3207632" archived="0" lang="eng">
 *              <date timestamp="1294088250">Mon, 03 Jan 2011 15:57:30 -0500</date>
 *              <factList>
 *                <fact>
 *                  ![CDATA[ <b>Trullion: Alastor 2262</b></br>Glinnes moved after ...blah blah ]]>
 *                </fact>
 *                <fact>
 *                  ![CDATA[ <b>Marune: Alastor 933</b></br>"I don't know either." ]]>
 *                </fact>
 *                <fact>
 *                  ![CDATA[ <b>Wyst: Alastor 1716</b></br>For this reason, s...blah blah ]]>
 *                </fact>
 *              </factList>
 *            </version>
 *          </versionList>
 *        </field>
 *        <field type="14" name="description" displayName="Description">
 *          <versionList>
 *            <version id="3207629" archived="0" lang="eng">
 *              <date timestamp="1294087991">Mon, 03 Jan 2011 15:53:11 -0500</date>
 *              <factList>
 *                <fact>
 *                  ![CDATA[ Contains the following novels: <i></br>Trullion: ...blah blah ]]>
 *                </fact>
 *              </factList>
 *            </version>
 *          </versionList>
 *        </field>
 *        <field type="4" name="awards" displayName="Awards and honors">
 *          <versionList>
 *            <version id="2139252" archived="0" lang="eng">
 *              <date timestamp="1264492371">Tue, 26 Jan 2010 02:52:51 -0500</date>
 *              <factList>
 *                <fact>
 *                  100 Greatest Science Fiction or Fantasy ...blah blah
 *                </fact>
 *              </factList>
 *            </version>
 *          </versionList>
 *        </field>
 *        <field type="16" name="originalpublicationdate" displayName="Original publication date">
 *          <versionList>
 *            <version id="788105" archived="0" lang="eng">
 *              <date timestamp="1228946925">Wed, 10 Dec 2008 17:08:45 -0500</date>
 *              <factList>
 *                <fact>1978 (omnibus)</fact>
 *              </factList>
 *            </version>
 *          </versionList>
 *        </field>
 *        <field type="23" name="series" displayName="Series">
 *          <versionList>
 *            <version id="788102" archived="0" lang="eng">
 *              <date timestamp="1228946925">Wed, 10 Dec 2008 17:08:45 -0500</date>
 *              <factList>
 *                <fact>Alastor (4|Omnibus)</fact>
 *              </factList>
 *            </version>
 *          </versionList>
 *        </field>
 *      </fieldList>
 *    </commonknowledge>
 *  </item>
 *  <legal>
 * By using this data you agree to the LibraryThing API terms of service.
 * </legal>
 * </ltml>
 * </response>
 *     }
 * </pre>
 *
 * <pre>
 * {@code
 *  <?xml version="1.0" encoding="UTF-8"?>
 *    <response stat="ok">
 *    <ltml xmlns="http://www.librarything.com/" version="1.1">
 *    <item id="5196084" type="work">
 *      <author id="28" authorcode="asimovisaac">Isaac Asimov</author>
 *      <url>http://www.librarything.com/work/5196084</url>
 *      <commonknowledge>
 *        <fieldList>
 *          <field type="4" name="awards" displayName="Awards and honors">
 *            <versionList>
 *              <version id="3324305" archived="0" lang="eng">
 *                <date timestamp="1296476301">Mon, 31 Jan 2011 07:18:21 -0500</date>
 *                <factList>
 *                  <fact>1001 Books You Must Read Before You Die (2006/2008/2010 Edition)</fact>
 *                  <fact>Astounding/Analog Science Fiction and Fact All-Time Poll ...</fact>
 *                  <fact>Astounding/Analog Science Fiction and Fact All-Time Poll ...</fact>
 *                  <fact>501 Must-Read Books (Science Fiction)</fact>
 *                </factList>
 *              </version>
 *            </versionList>
 *          </field>
 *          <field type="37" name="movies" displayName="Related movies">
 *            <versionList>
 *              <version id="3120269" archived="0" lang="eng">
 *                <date timestamp="1292202792">Sun, 12 Dec 2010 20:13:12 -0500</date>
 *                <factList>
 *                  <fact>Robots (1988 | tt0174170)</fact>
 *                  <fact>I, Robot (2004 | tt0343818)</fact>
 *                  <fact>The Outer Limits: I Robot (1963 | tt0056777)</fact>
 *                </factList>
 *              </version>
 *            </versionList>
 *          </field>
 *          <field type="40" name="publisherseries" displayName="Publisher Series">
 *            <versionList>
 *              <version id="2971007" archived="0" lang="eng">
 *                <date timestamp="1289497446">Thu, 11 Nov 2010 12:44:06 -0500</date>
 *                <factList>
 *                  <fact>Voyager Classics</fact>
 *                </factList>
 *              </version>
 *            </versionList>
 *          </field>
 *          <field type="14" name="description" displayName="Description">
 *            <versionList>
 *              <version id="2756634" archived="0" lang="eng">
 *                <date timestamp="1281897478">Sun, 15 Aug 2010 14:37:58 -0400</date>
 *                <factList>
 *                  <fact>&lt;![CDATA[ Contents:&lt;br&gt;&lt;br&gt;Introduction&lt;br
 *                    &gt;Robbie&lt;br&gt;Runaround&lt;br&gt;Reason&lt;br&gt;Catch That Rabbit&lt;
 *                    br&gt;Liar!&lt;br&gt;Little Lost Robot&lt;br&gt;Escape!&lt;br
 *                    &gt;Evidence&lt;br&gt;The Evitable Conflict ]]&gt;
 *                  </fact>
 *                </factList>
 *              </version>
 *            </versionList>
 *          </field>
 *          <field type="23" name="series" displayName="Series">
 *            <versionList>
 *              <version id="2742329" archived="0" lang="eng">
 *                <date timestamp="1281338643">Mon, 09 Aug 2010 03:24:03 -0400</date>
 *                <factList>
 *                  <fact>Isaac Asimov's Robot Series (0.1)</fact>
 *                  <fact>Robot/Foundation</fact>
 *                  <fact>Robot/Empire/Foundation - Chronological (book 1)</fact>
 *                </factList>
 *              </version>
 *            </versionList>
 *          </field>
 *          <field type="16" name="originalpublicationdate" displayName="Original publication date">
 *            <versionList>
 *              <version id="2554955" archived="0" lang="eng">
 *                <date timestamp="1275746736">Sat, 05 Jun 2010 10:05:36 -0400</date>
 *                <factList>
 *                  <fact>1950 (Collection)</fact>
 *                  <fact>1944 (Catch that Rabbit)</fact>
 *                  <fact>1945 (Escape!)</fact>
 *                  <fact>1946 (Evidence)</fact>
 *                  <fact>1950 (The Evitable Conflict)</fact>
 *                  <fact>1941  (Liar)</fact>
 *                  <fact>1947  (Little Lost Robot)</fact>
 *                  <fact>1940  (Robbie)</fact>
 *                  <fact>1942  (Runaround)</fact>
 *                  <fact>1941  (Reason)</fact>
 *                </factList>
 *              </version>
 *            </versionList>
 *          </field>
 *          <field type="27" name="quotations" displayName="Quotations">
 *            <versionList>
 *              <version id="2503597" archived="0" lang="eng">
 *                <date timestamp="1274377341">Thu, 20 May 2010 13:42:21 -0400</date>
 *                <factList>
 *                  <fact>&lt;![CDATA[ The Three Laws of Robotics
 *                    1. A robot may not injure a human being, or, through inaction, allow a human
 *                    being to come to harm.
 *                    2. A robot must obey the orders given it by human beings except where such
 *                    orders would conflict with the First Law.
 *                    3. A robot must protect its own existence as long as such protection does not
 *                    conflict with the First or Second Law.  ]]&gt;
 *                  </fact>
 *                </factList>
 *              </version>
 *            </versionList>
 *          </field>
 *          <field type="30" name="dedication" displayName="Dedication">
 *            <versionList>
 *              <version id="2503596" archived="0" lang="eng">
 *                <date timestamp="1274377341">Thu, 20 May 2010 13:42:21 -0400</date>
 *                <factList>
 *                  <fact>
 *                      &lt;![CDATA[ To John W. Campbell, Jr., who god-fathered the robots]]&gt;
 *                  </fact>
 *                </factList>
 *            </version>
 *          </versionList>
 *        </field>
 *        <field type="26" name="lastwords" displayName="Last words">
 *          <versionList>
 *            <version id="2503594" archived="0" lang="eng">
 *              <date timestamp="1274377340">Thu, 20 May 2010 13:42:20 -0400</date>
 *              <factList>
 *                <fact>&lt;![CDATA[ Robbie:&lt;br&gt;"Well," said Mrs. Weston, at last,
 *                "I guess he can stay with us until he rusts." ]]&gt;</fact>
 *                <fact>&lt;![CDATA[ Runaround:&lt;br&gt;"Space Station," said Donovan,
 *                "here I come." ]]&gt;</fact>
 *                <fact>&lt;![CDATA[ Reason"&lt;br&gt;He grinned � and went into the ship.
 *                Muller would be here for several weeks � ]]&gt;</fact>
 *                <fact>&lt;![CDATA[ Liar:&lt;br&gt;"Liar!" ]]&gt;</fact>
 *                <fact>&lt;![CDATA[ Little Lost Robot:&lt;br&gt;"� His very superiority caught
 *                him.  Good-by General" ]]&gt;</fact>
 *                <fact>&lt;![CDATA[ &lt;i&gt;She died last month at the age of eighty-two.
 *                &lt;/i&gt; ]]&gt;</fact>
 *              </factList>
 *            </version>
 *          </versionList>
 *        </field>
 *        <field type="25" name="firstwords" displayName="First words">
 *          <versionList>
 *            <version id="2503593" archived="0" lang="eng">
 *              <date timestamp="1274377340">Thu, 20 May 2010 13:42:20 -0400</date>
 *              <factList>
 *                <fact>&lt;![CDATA[ Robbie:&lt;br&gt;"Ninety-eight � ninety-nine � &lt;
 *                i&gt;one hundred&lt;/i&gt;." ]]&gt;</fact>
 *                <fact>&lt;![CDATA[ Runaround:&lt;br&gt;It was one of Gregory Powell's favorite
 *                platitudes that nothing was to be gained from excitement, so when Mike Donovan
 *                came leaping down the stairs toward him, red hair matted with perspiration,
 *                Powell frowned. ]]&gt;</fact>
 *                <fact>&lt;![CDATA[ Reason:&lt;br&gt;Half a year later, the boys had changed
 *                their minds. ]]&gt;</fact>
 *                <fact>&lt;![CDATA[ Catch That Rabbit:&lt;br&gt;The vacation was longer than
 *                two weeks. ]]&gt;</fact>
 *                <fact>&lt;![CDATA[ Little Lost Robot:&lt;br&gt;When I did see Susan Calvin again,
 *                it was at the door of her office. ]]&gt;</fact>
 *                <fact>&lt;![CDATA[ Escape!:&lt;br&gt;When Susan Calvin returned from Hyper Base,
 *                Alfred Lanning was waiting for her. ]]&gt;</fact>
 *                <fact>&lt;![CDATA[ Evidence:&lt;br&gt;Francis Quinn was a politician of the new
 *                school. ]]&gt;</fact>
 *                <fact>&lt;![CDATA[ &lt;i&gt;I looked at my notes and I didn't like them.&lt;
 *                /i&gt; (Introduction) ]]&gt;</fact>
 *              </factList>
 *            </version>
 *          </versionList>
 *        </field>
 *        <field type="21" name="canonicaltitle" displayName="Canonical title">
 *          <versionList>
 *            <version id="2503590" archived="0" lang="eng">
 *              <date timestamp="1274377338">Thu, 20 May 2010 13:42:18 -0400</date>
 *              <factList>
 *                <fact>I, Robot</fact>
 *              </factList>
 *            </version>
 *          </versionList>
 *        </field>
 *        <field type="3" name="characternames" displayName="People/Characters">
 *          <versionList>
 *            <version id="2503589" archived="0" lang="eng">
 *              <date timestamp="1274377337">Thu, 20 May 2010 13:42:17 -0400</date>
 *              <factList>
 *                <fact>Susan Calvin</fact>
 *                <fact>Cutie (QT1)</fact>
 *                ...
 *              </factList>
 *            </version>
 *          </versionList>
 *        </field>
 *        <field type="2" name="placesmentioned" displayName="Important places">
 *          <versionList>
 *            <version id="2503588" archived="0" lang="eng">
 *              <date timestamp="1274377336">Thu, 20 May 2010 13:42:16 -0400</date>
 *              <factList>
 *                <fact>Mercury</fact>
 *                <fact>New York, New York, USA</fact>
 *                <fact>Roosevelt Building</fact>
 *                <fact>U.S. Robots and Mechanical Men factory</fact>
 *                <fact>Hyper Base</fact>
 *              </factList>
 *            </version>
 *          </versionList>
 *        </field>
 *      </fieldList>
 *    </commonknowledge>
 * </item>
 * <legal>By using this data you agree to the LibraryThing API terms of service.</legal>
 * </ltml>
 * </response>
 * }
 * </pre>
 * <p>
 * A less well-known work produces rather less data:
 *
 * <pre>
 *     {@code
 *   <?xml version="1.0" encoding="UTF-8"?>
 *   <response stat="ok">
 *     <ltml xmlns="http://www.librarything.com/" version="1.1">
 *       <item id="255375" type="work">
 *         <author id="359458" authorcode="fallonmary">Mary Fallon</author>
 *         <url>http://www.librarything.com/work/255375</url>
 *         <commonknowledge/>
 *       </item>
 *       <legal>By using this data you agree to the LibraryThing API terms of service.</legal>
 *     </ltml>
 *   </response>
 *   }
 * </pre>
 * but in both cases, it should be noted that the covers are still available.
 */
class LibraryThingHandler
        extends DefaultHandler {

    /** some common XML attributes. */
    private static final String XML_ATTR_ID = "id";
    private static final String XML_ATTR_TYPE = "type";
    private static final String XML_ATTR_NAME = "name";
    /** XML tags we look for. */
    //private static final String XML_RESPONSE = "response";
    private static final String XML_AUTHOR = "author";
    /** Highest level title. */
    private static final String XML_TITLE = "title";
    /** {@code <item id="5196084" type="work">}. */
    private static final String XML_ITEM = "item";
    /** a 'field' (see below). */
    private static final String XML_FIELD = "field";
    /** a 'fact' in a 'factlist' of a 'field'. */
    private static final String XML_FACT = "fact";
    /** fields. */
    private static final String XML_FIELD_21_CANONICAL_TITLE = "canonicaltitle";
    private static final String XML_FIELD_41_ORIG_TITLE = "originaltitle";
    private static final String XML_FIELD_42_ALT_TITLES = "alternativetitles";
    private static final String XML_FIELD_23_SERIES = "series";
    private static final String XML_FIELD_40_PUB_SERIES = "publisherseries";
    //    private static final String XML_FIELD_02_PLACES = "placesmentioned";
//    private static final String XML_FIELD_03_CHARACTERS = "characternames";
    private static final String XML_FIELD_14_DESCRIPTION = "description";
    private static final String XML_FIELD_16_ORIG_PUB_DATE = "originalpublicationdate";
    private static final String XML_FIELD_58_ORIG_LANG = "originallanguage";

    /**
     * Field types we are interested in.
     */
    private static final int FT_NONE = 0;
    private static final int FT_OTHER = 1;
    private static final int FT_TITLE = 2;
    private static final int FT_SERIES = 3;
    private static final int FT_PUB_SERIES = 4;
    private static final int FT_DESCRIPTION = 5;
    private static final int FT_ORIGINAL_PUB_DATE = 6;
    private static final int FT_ORIGINAL_LANGUAGE = 7;
    private static final int FT_ORIGINAL_TITLE = 8;
    private static final int FT_ALT_TITLE = 9;

    /** XML content. */
    @SuppressWarnings("StringBufferField")
    private final StringBuilder mBuilder = new StringBuilder();

    /**
     * Extracting the year from field "originalpublicationdate".
     * {@code
     * A short story collection:
     * <fact>1950 (Collection)</fact>
     * <fact>1944 (Catch that Rabbit)</fact>
     * <p>
     * An omnibus of multiple book:
     * <fact>1978 (omnibus)</fact>
     * }
     */
    private static final Pattern YEAR_PATTERN = Pattern.compile("([1|2]\\d\\d\\d)");

    /** Bundle to save results in. */
    @NonNull
    private final Bundle mBookData;

    /** accumulate all authors for this book. */
    @NonNull
    private final ArrayList<Author> mAuthors = new ArrayList<>();

    /** accumulate all series for this book. */
    @NonNull
    private final ArrayList<Series> mSeries = new ArrayList<>();
    /** Current Field we're in. We need this because the actual data is always in a 'fact' tag. */
    @FieldType
    private int mFieldType = FT_OTHER;

    /**
     * Constructor.
     *
     * @param bookData Bundle to update <em>(passed in to allow mocking)</em>
     */
    LibraryThingHandler(@NonNull final Bundle bookData) {
        mBookData = bookData;
    }

    /**
     * Get the results.
     *
     * @return Bundle with book data
     */
    @NonNull
    public Bundle getResult() {
        return mBookData;
    }

    /**
     * Store the accumulated data in the results.
     */
    @Override
    public void endDocument() {
        if (!mAuthors.isEmpty()) {
            mBookData.putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, mAuthors);
        }
        if (!mSeries.isEmpty()) {
            mBookData.putParcelableArrayList(Book.BKEY_SERIES_ARRAY, mSeries);
        }
    }

    /**
     * Start each XML element. Specifically identify when we are in the item
     * element and set the appropriate flag.
     */
    @Override
    @CallSuper
    public void startElement(@NonNull final String uri,
                             @NonNull final String localName,
                             @NonNull final String qName,
                             @NonNull final Attributes attributes) {

        // reset the string. See note in endElement() for a discussion.
        mBuilder.setLength(0);

        if (localName.equalsIgnoreCase(XML_FIELD)) {
            // FIELD's are the main things we want. Once we are in a field we wait for an XML_FACT;
            // these are read in the endElement() method.
            final String fieldName = attributes.getValue("", XML_ATTR_NAME);
            if (fieldName != null) {
                if (fieldName.equalsIgnoreCase(XML_FIELD_21_CANONICAL_TITLE)) {
                    mFieldType = FT_TITLE;
                } else if (fieldName.equalsIgnoreCase(XML_FIELD_41_ORIG_TITLE)) {
                    mFieldType = FT_ORIGINAL_TITLE;
                } else if (fieldName.equalsIgnoreCase(XML_FIELD_42_ALT_TITLES)) {
                    mFieldType = FT_ALT_TITLE;

                } else if (fieldName.equalsIgnoreCase(XML_FIELD_23_SERIES)) {
                    mFieldType = FT_SERIES;
                } else if (fieldName.equalsIgnoreCase(XML_FIELD_40_PUB_SERIES)) {
                    mFieldType = FT_PUB_SERIES;

                } else if (fieldName.equalsIgnoreCase(XML_FIELD_14_DESCRIPTION)) {
                    mFieldType = FT_DESCRIPTION;
                } else if (fieldName.equalsIgnoreCase(XML_FIELD_16_ORIG_PUB_DATE)) {
                    mFieldType = FT_ORIGINAL_PUB_DATE;
                } else if (fieldName.equalsIgnoreCase(XML_FIELD_58_ORIG_LANG)) {
                    mFieldType = FT_ORIGINAL_LANGUAGE;
                }
            }
        } else if (localName.equalsIgnoreCase(XML_ITEM)) {
            // <item id="1745230" type="work">
            final String type = attributes.getValue("", XML_ATTR_TYPE);
            // leave hardcoded, it's a value for the attribute.
            if ("work".equalsIgnoreCase(type)) {
                try {
                    final long id = Long.parseLong(attributes.getValue("", XML_ATTR_ID));
                    mBookData.putLong(DBDefinitions.KEY_EID_LIBRARY_THING, id);
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
//          } else if (localName.equalsIgnoreCase(XML_RESPONSE)){
//              // Not really much to do; we *could* look for the <err> element, then report it.
//              final String stat = attributes.get("", "stat");
        }
    }

    /**
     * Populate the results Bundle for each appropriate element.
     * Also download the thumbnail and store in a tmp location
     */
    @Override
    @CallSuper
    public void endElement(@NonNull final String uri,
                           @NonNull final String localName,
                           @NonNull final String qName) {

        if (localName.equalsIgnoreCase(XML_FIELD)) {
            // end of Field reached, reset the current field
            mFieldType = FT_NONE;

        } else if (localName.equalsIgnoreCase(XML_AUTHOR)) {
            mAuthors.add(Author.from(mBuilder.toString()));

        } else if (localName.equalsIgnoreCase(XML_TITLE)) {
            addIfNotPresent(DBDefinitions.KEY_TITLE, mBuilder.toString());

        } else if (localName.equalsIgnoreCase(XML_FACT)) {
            switch (mFieldType) {
                case FT_TITLE:
                    addIfNotPresent(DBDefinitions.KEY_TITLE, mBuilder.toString());
                    break;

                case FT_SERIES:
                    mSeries.add(Series.from(mBuilder.toString()));
                    break;

                case FT_PUB_SERIES:
                    // don't do this. The site does not differentiate between "this"
                    // edition of the book and all others.
//                    mSeries.add(Series.from(mBuilder.toString()));
                    break;

                case FT_DESCRIPTION:
                    addIfNotPresent(DBDefinitions.KEY_DESCRIPTION, mBuilder.toString());
                    break;

                case FT_ORIGINAL_PUB_DATE:
                    if (!mBookData.containsKey(DBDefinitions.KEY_DATE_FIRST_PUBLICATION)) {
                        final Matcher matcher = YEAR_PATTERN.matcher(mBuilder.toString());
                        if (matcher.find()) {
                            mBookData.putString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION,
                                                matcher.group(1));
                        }
                    }
                    break;

                case FT_ORIGINAL_LANGUAGE:
                case FT_ORIGINAL_TITLE:
                case FT_ALT_TITLE:
                    //ENHANCE FT_ORIGINAL_LANGUAGE, FT_ORIGINAL_TITLE, FT_ALT_TITLE
                    break;

                case FT_OTHER:
                case FT_NONE:
                default:
                    break;
            }
        }
        // Always reset the length. This is not entirely the right thing to do, but works
        // because we always want strings from the lowest level (leaf) XML elements.
        // To be completely correct, we should maintain a stack of builders that are pushed and
        // popped as each startElement/endElement is called. But lets not be pedantic for now.
        mBuilder.setLength(0);
    }

    @Override
    @CallSuper
    public void characters(final char[] ch,
                           final int start,
                           final int length) {
        mBuilder.append(ch, start, length);
    }

    /**
     * Add the value to the Bundle if not present or empty.
     *
     * @param key   to use
     * @param value to store
     */
    private void addIfNotPresent(@NonNull final String key,
                                 @NonNull final String value) {
        final String test = mBookData.getString(key);
        if (test == null || test.isEmpty()) {
            String v = value.trim();
            if (v.startsWith("![CDATA[")) {
                v = v.substring(8);
                // sanity check
                if (v.endsWith("]]>")) {
                    v = v.substring(0, v.length() - 3);
                }
                v = v.trim();
            }
            mBookData.putString(key, v);
        }
    }

    @IntDef({FT_NONE, FT_OTHER, FT_TITLE, FT_SERIES,
             FT_PUB_SERIES, FT_DESCRIPTION, FT_ORIGINAL_PUB_DATE,
             FT_ORIGINAL_LANGUAGE, FT_ORIGINAL_TITLE, FT_ALT_TITLE})
    @Retention(RetentionPolicy.SOURCE)
    private @interface FieldType {

    }
}
