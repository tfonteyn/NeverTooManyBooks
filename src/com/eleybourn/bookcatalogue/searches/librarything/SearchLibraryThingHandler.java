package com.eleybourn.bookcatalogue.searches.librarything;

import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Series;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;

/**
 * Parser Handler to collect the book data.
 * Search LibraryThing for an ISBN using the Web API.
 * <p>
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
 * <p>
 * A less well-known work produces rather less data:
 *
 * <pre>
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
 * </pre>
 * but in both cases, it should be noted that the covers are still available.
 *
 * @author Philip Warner
 */
class SearchLibraryThingHandler
        extends DefaultHandler {

    /**
     * LibraryThing extra field "placesmentioned".
     * <p>
     * * <fact>Mercury</fact>
     * * <fact>New York, New York, USA</fact>
     * * <fact>Roosevelt Building</fact>
     */
    private static final String LT_PLACES = "__places";
    /**
     * LibraryThing extra field "characternames".
     * <p>
     * * <fact>Susan Calvin</fact>
     * * <fact>Cutie (QT1)</fact>
     * * <fact>Gregory Powell</fact>
     */
    private static final String LT_CHARACTERS = "__characters";
    /**
     * LibraryThing extra field "originalpublicationdate".
     * <p>
     * The format of these entries is probably not a standard. TODO: get more examples first.
     * <p>
     * * <fact>1950 (Collection)</fact>
     * * <fact>1944 (Catch that Rabbit)</fact>
     * * <fact>1945 (Escape!)</fact>
     * * <fact>1946 (Evidence)</fact>
     * * <fact>1950 (The Evitable Conflict)</fact>
     */
    private static final String LT_ORIG_PUB_DATE = "__originalpublicationdate";

    /** some common XML attributes. */
    private static final String XML_ATTR_ID = "id";
    private static final String XML_ATTR_TYPE = "type";
    private static final String XML_ATTR_NAME = "name";

    /** XML tags we look for. */
    //private static final String XML_RESPONSE = "response";
    private static final String XML_AUTHOR = "author";
    /** <item id="5196084" type="work">. */
    private static final String XML_ITEM = "item";
    /** a 'field' (see below). */
    private static final String XML_FIELD = "field";
    /** a 'fact' in a 'factlist' of a 'field'. */
    private static final String XML_FACT = "fact";
    /** fields. */
    private static final String XML_FIELD_23_SERIES = "series";
    private static final String XML_FIELD_21_CANONICAL_TITLE = "canonicaltitle";

    private static final String XML_FIELD_02_PLACES = "placesmentioned";
    private static final String XML_FIELD_03_CHARACTERS = "characternames";
    private static final String XML_FIELD_16_ORIG_PUB_DATE = "originalpublicationdate";
    /** Bundle to save results in. */
    @NonNull
    private final Bundle mBookData;
    /** accumulate all authors for this book. */
    @NonNull
    private final ArrayList<Author> mAuthors = new ArrayList<>();
    /** accumulate all series for this book. */
    @NonNull
    private final ArrayList<Series> mSeries = new ArrayList<>();
    /** XML content. */
    private final StringBuilder mBuilder = new StringBuilder();
    /** Current Field we're in. We need this because the actual data is always in a 'fact' tag. */
    @NonNull
    private FieldTypes mFieldType = FieldTypes.Other;

    /**
     * Constructor.
     *
     * @param bookData Bundle to save results in
     */
    SearchLibraryThingHandler(@NonNull final Bundle /* out */bookData) {
        mBookData = bookData;
    }

    /**
     * Add the value to the Bundle if not present.
     *
     * @param bundle to check
     * @param key    for data to add
     * @param value  to use
     */
    private static void addIfNotPresent(@NonNull final Bundle bundle,
                                        @NonNull final String key,
                                        @NonNull final String value) {
        String test = bundle.getString(key);
        if (test == null || test.isEmpty()) {
            bundle.putString(key, value.trim());
        }
    }

    @Override
    @CallSuper
    public void characters(final char[] ch,
                           final int start,
                           final int length)
            throws SAXException {
        super.characters(ch, start, length);
        mBuilder.append(ch, start, length);
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
                             @NonNull final Attributes attributes)
            throws SAXException {
        super.startElement(uri, localName, qName, attributes);

        // reset the string. See note in endElement() for a discussion.
        mBuilder.setLength(0);

        if (localName.equalsIgnoreCase(XML_FIELD)) {
            // FIELD's are the main things we want. Once we are in a field we wait for a XML_FACT;
            // these are read in the endElement() method.
            String fieldName = attributes.getValue("", XML_ATTR_NAME);
            if (fieldName != null) {
                if (fieldName.equalsIgnoreCase(XML_FIELD_21_CANONICAL_TITLE)) {
                    mFieldType = FieldTypes.Title;
                } else if (fieldName.equalsIgnoreCase(XML_FIELD_23_SERIES)) {
                    mFieldType = FieldTypes.Series;
                } else if (fieldName.equalsIgnoreCase(XML_FIELD_02_PLACES)) {
                    mFieldType = FieldTypes.Places;
                } else if (fieldName.equalsIgnoreCase(XML_FIELD_03_CHARACTERS)) {
                    mFieldType = FieldTypes.Characters;
                } else if (fieldName.equalsIgnoreCase(XML_FIELD_16_ORIG_PUB_DATE)) {
                    mFieldType = FieldTypes.OriginalPubDate;
                }
            }
        } else if (localName.equalsIgnoreCase(XML_ITEM)) {
            String type = attributes.getValue("", XML_ATTR_TYPE);
            // leave hardcoded, it's a value for the attribute.
            if ("work".equalsIgnoreCase(type)) {
                try {
                    long id = Long.parseLong(attributes.getValue("", XML_ATTR_ID));
                    mBookData.putLong(UniqueId.KEY_BOOK_LIBRARY_THING_ID, id);
                } catch (NumberFormatException ignore) {
                }
            }
//          } else if (localName.equalsIgnoreCase(XML_RESPONSE)){
//              // Not really much to do; we *could* look for the <err> element, then report it.
//              String stat = attributes.get("", "stat");
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
                           @NonNull final String qName)
            throws SAXException {
        super.endElement(uri, localName, qName);

        if (localName.equalsIgnoreCase(XML_FIELD)) {
            // end of Field reached, reset the current field
            mFieldType = FieldTypes.None;

        } else if (localName.equalsIgnoreCase(XML_AUTHOR)) {
            mAuthors.add(Author.fromString(mBuilder.toString()));

        } else if (localName.equalsIgnoreCase(XML_FACT)) {
            // Process the XML_FACT according to the active XML_FIELD type.
            switch (mFieldType) {

                case Title:
                    addIfNotPresent(mBookData, UniqueId.KEY_TITLE, mBuilder.toString());
                    break;

                case Series:
                    mSeries.add(Series.fromString(mBuilder.toString()));
                    break;

//                case Places:
//                    StringList.addOrAppend(mBookData, LT_PLACES, mBuilder.toString());
//                    break;
//
//                case Characters:
//                    StringList.addOrAppend(mBookData, LT_CHARACTERS, mBuilder.toString());
//                    break;
//
//                case OriginalPubDate:
//                    StringList.addOrAppend(mBookData, LT_ORIG_PUB_DATE, mBuilder.toString());
//                    break;
            }
        }
        // Note:
        // Always reset the length. This is not entirely the right thing to do, but works
        // because we always want strings from the lowest level (leaf) XML elements.
        // To be completely correct, we should maintain a stack of builders that are pushed and
        // popped as each startElement/endElement is called. But lets not be pedantic for now.
        mBuilder.setLength(0);
    }

    /**
     * Store the accumulated data in the results.
     */
    @Override
    public void endDocument()
            throws SAXException {
        super.endDocument();

        mBookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, mAuthors);
        mBookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, mSeries);

    }

    /**
     * Field types we are interested in.
     */
    private enum FieldTypes {
        None, Author, Title, Series, Places, Characters, OriginalPubDate, Other
    }
}
