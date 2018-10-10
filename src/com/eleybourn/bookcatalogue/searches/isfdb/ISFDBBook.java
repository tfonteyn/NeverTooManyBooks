package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ISFDBBook extends AbstractBase {

    /** maps to translate ISFDB terminology with our own */
    private static final Map<String, String> FORMAT_MAP = new HashMap<>();
    private static final Map<String, String> TYPE_MAP = new HashMap<>();

    static {
        FORMAT_MAP.put("pb", "Paperback");
        FORMAT_MAP.put("tp", "Trade Paperback");
        FORMAT_MAP.put("hc", "Hardcover");
        FORMAT_MAP.put("ebook", UniqueId.BVAL_FORMAT_EBOOK);
        FORMAT_MAP.put("digest", "Digest");
        FORMAT_MAP.put("unknown", "Unknown");
    }

    static {
        // throw all these together into the Anthology bucket
        TYPE_MAP.put("coll", "a1"); // one author
        TYPE_MAP.put("COLLECTION", "a1");
        TYPE_MAP.put("anth", "a2"); // multiple authors?
        TYPE_MAP.put("ANTHOLOGY", "a2");
        TYPE_MAP.put("omni", "a2");  // multiple authors?
        TYPE_MAP.put("OMNIBUS", "a2");

        // don't really care about these, but at least unify them
        TYPE_MAP.put("novel", "Novel");
        TYPE_MAP.put("NOVEL", "Novel");

        TYPE_MAP.put("chap", "Chapbook");
        TYPE_MAP.put("CHAPBOOK", "Chapbook");

        TYPE_MAP.put("non-fic", "Non-Fiction");
        TYPE_MAP.put("NONFICTION", "Non-Fiction");
    }

    @NonNull
    private final Bundle mBookData;
    private final boolean mFetchThumbnail;

    /** set during book load, used during content table load */
    @Nullable
    private String mTitle;

    /** with some luck we'll get these as well */
    @Nullable
    private String mFirstPublication;
    @Nullable
    private String mSeries;

    /** "[some string]" TODO: make this more specific ? */
    private static final Pattern SERIES = Pattern.compile("\\[(.*)]");

    /** ISFDB native book id */
    private long mPublicationRecord;

    /**
     * @param publicationRecord ISFDB native book id
     */
    public ISFDBBook(final long publicationRecord,
                     @NonNull final Bundle /* out */ bookData,
                     final boolean fetchThumbnail) {
        mPublicationRecord = publicationRecord;
        mPath = String.format(ISFDBManager.getBaseURL() + "/cgi-bin/pl.cgi?%s", mPublicationRecord);
        mBookData = bookData;
        mFetchThumbnail = fetchThumbnail;
    }

    /**
     * @param path example: "http://www.isfdb.org/cgi-bin/pl.cgi?230949"
     */
    ISFDBBook(@NonNull final String path,
              @NonNull final Bundle /* out */ bookData,
              final boolean fetchThumbnail) {
        mPublicationRecord = stripNumber(path);
        mPath = path;
        mBookData = bookData;
        mFetchThumbnail = fetchThumbnail;
    }

    public long getPublicationRecord() {
        return mPublicationRecord;
    }

    /* First "ContentBox" contains all basic details

        <div class="ContentBox">
        <table>
        <tr class="scan">

        <td>
            <a href="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg">
            <img src="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg" alt="picture" class="scan"></a>
        </td>

        <td class="pubheader">
        <ul>
            <li><b>Publication:</b> The Days of Perky Pat <span class="recordID"><b>Publication Record # </b>230949</span>
            <li><b>Author:</b> <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
            <li><b>Date:</b> 1991-05-00
            <li><b>ISBN:</b> 0-586-20768-6 [<small>978-0-586-20768-0</small>]
            <li><b>Publisher:</b> <a href="http://www.isfdb.org/cgi-bin/publisher.cgi?62" dir="ltr">Grafton</a>
            <li><b>Price:</b> £5.99
            <li><b>Pages:</b> 494
            <li><b>Format:</b> <div class="tooltip">tp<sup class="mouseover">?</sup><span class="tooltiptext tooltipnarrow">Trade paperback. Any softcover book which is at least 7.25" (or 19 cm) tall, or at least 4.5" (11.5 cm) wide/deep.</span></div>
            <li><b>Type:</b> COLLECTION
            <li><b>Cover:</b><a href="http://www.isfdb.org/cgi-bin/title.cgi?737949" dir="ltr">The Days of Perky Pat</a>  by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?21338" dir="ltr">Chris Moore</a>
            <li>
                <div class="notes"><b>Notes:</b>
                "Published by Grafton Books 1991" on copyright page
                Artist credited on back cover
                "Top Stand-By Job" listed in contents and title page of story as "Stand-By"
                • Month from Locus1
                • Notes from page 487 to 494
                • OCLC <A href="http://www.worldcat.org/oclc/60047795">60047795</a>
                </div>
        </ul>
        </td>
        </table>
        Cover art supplied by <a href="http://www.isfdb.org/wiki/index.php/Image:THDSFPRKPT1991.jpg" target="_blank">ISFDB</a>
        </div>
     */

    public void fetchBook() {
        if (!loadPage()) {
            return;
        }

        Element contentBox = mDoc.select("div.contentbox").first();
        Elements lis = contentBox.select("li");
        String tmp;

        for (Element li : lis) {
            try {
                if (li.text().contains("Publication")) {
                    mTitle = li.childNode(1).toString().trim();
                    mBookData.putString(UniqueId.KEY_TITLE, mTitle);

                    tmp = li.childNode(2).childNode(1).toString().trim();
                    mBookData.putString("ISFDB_PUB_RECORD", tmp);

                } else if (li.text().contains("Author")) {
                    Elements as = li.select("a");
                    if (as != null) {
                        for (Element a : as) {
                            ArrayUtils.addOrAppend(mBookData, UniqueId.BKEY_AUTHOR_DETAILS, a.text());
                            ArrayUtils.addOrAppend(mBookData, "ISFDB_AUTHOR_ID", Long.toString(stripNumber(a.attr("href"))));
                        }
                    }
                } else if (li.text().contains("Date")) {
                    tmp = li.childNode(2).toString().trim();
                    mBookData.putString(UniqueId.KEY_BOOK_DATE_PUBLISHED, tmp);

                } else if (li.text().contains("ISBN")) {
                    // always use the first one, as that will be the one used at publication
                    tmp = li.childNode(1).toString().trim();
                    mBookData.putString(UniqueId.KEY_ISBN, digits(tmp));

                    tmp = li.childNode(2).childNode(0).toString().trim();
                    mBookData.putString("ISFDB_ISBN2", digits(tmp));

                } else if (li.text().contains("Publisher")) {
                    tmp = li.childNode(3).attr("href");
                    mBookData.putString("ISFDB_PUBLISHER_ID", Long.toString(stripNumber(tmp)));

                    tmp = li.childNode(3).childNode(0).toString().trim();
                    mBookData.putString(UniqueId.KEY_BOOK_PUBLISHER, tmp);

                } else if (li.text().contains("Pub. Series")) {
                    Elements as = li.select("a");
                    if (as != null) {
                        for (Element a : as) {
                            ArrayUtils.addOrAppend(mBookData, UniqueId.BKEY_SERIES_DETAILS, a.text());
                            ArrayUtils.addOrAppend(mBookData, "ISFDB_SERIES_ID", Long.toString(stripNumber(a.attr("href"))));
                        }
                    }

                } else if (li.text().contains("Price")) {
                    tmp = li.childNode(2).toString().trim();
                    mBookData.putString(UniqueId.KEY_BOOK_LIST_PRICE, tmp);

                } else if (li.text().contains("Pages")) {
                    tmp = li.childNode(2).toString().trim();
                    mBookData.putString(UniqueId.KEY_BOOK_PAGES, tmp);

                } else if (li.text().contains("Format")) {
                    tmp = li.childNode(3).childNode(0).toString().trim();
                    tmp = FORMAT_MAP.get(tmp);
                    if (tmp != null) {
                        mBookData.putString(UniqueId.KEY_BOOK_FORMAT, tmp);
                    }
                } else if (li.text().contains("Type")) {
                    tmp = li.childNode(2).toString().trim();
                    mBookData.putString("ISFDB_BOOK_TYPE", tmp);

                    if ("a1".equals(TYPE_MAP.get(tmp))) {
                        mBookData.putInt(Book.IS_ANTHOLOGY,
                                DatabaseDefinitions.DOM_ANTHOLOGY_IS_AN_ANTHOLOGY);
                    } else if ("a2".equals(TYPE_MAP.get(tmp))) {
                        mBookData.putInt(Book.IS_ANTHOLOGY,
                                DatabaseDefinitions.DOM_ANTHOLOGY_IS_AN_ANTHOLOGY |
                                        DatabaseDefinitions.DOM_ANTHOLOGY_WITH_MULTIPLE_AUTHORS);
                    }

                } else if (li.text().contains("Cover")) {
                    //TODO: if there are multiple art/artists... will this barf ?
                    tmp = li.childNode(2).attr("href");
                    mBookData.putString("ISFDB_BOOK_COVER_ART_URL", tmp);

                    tmp = li.childNode(2).childNode(0).toString().trim();
                    mBookData.putString("ISFDB_BOOK_COVER_ART_TXT", tmp);

                    // Cover artist, handle as author
                    Node node_a = li.childNode(4);
                    ArrayUtils.addOrAppend(mBookData, UniqueId.BKEY_AUTHOR_DETAILS, node_a.childNode(0).toString().trim());
                    ArrayUtils.addOrAppend(mBookData, "ISFDB_BOOK_COVER_ARTIST_ID", Long.toString(stripNumber(node_a.attr("href"))));

                } else if (li.text().contains("Notes")) {
                    tmp = li.childNode(1).childNode(1).toString().trim();
                    mBookData.putString(UniqueId.KEY_DESCRIPTION, tmp);

                } else if (li.text().contains("Editors")) {
                    // handle as authors
                    Elements as = li.select("a");
                    if (as != null) {
                        for (Element a : as) {
                            ArrayUtils.addOrAppend(mBookData, UniqueId.BKEY_AUTHOR_DETAILS, a.text());
                            ArrayUtils.addOrAppend(mBookData, "ISFDB_EDITORS_ID", Long.toString(stripNumber(a.attr("href"))));
                        }
                    }

                }
            } catch (IndexOutOfBoundsException e) {
                // does not happen now, but could happen if we come about non-standard entries, or if ISFDB website changes
                Logger.error(e, "path: " + mPath + "\n\nLI: " + li.toString());
            }
        }

        // ISFDB does not offer the books language on the main page (although they store it in their database)
        // default to a localised 'English" as ISFDB is after all (I presume) 99% english
        mBookData.putString(UniqueId.KEY_BOOK_LANGUAGE, Locale.ENGLISH.getDisplayName());

        // the content for local (below) processing. The actual entries are already added to the book data bundle
        ArrayList<AnthologyTitle> toc = getTableOfContentList();

        // indicate Anthology, just in case the ISDB book-type did not.
        if (toc.size() > 0) {
            int ant = mBookData.getInt(Book.IS_ANTHOLOGY, 0) | DatabaseDefinitions.DOM_ANTHOLOGY_IS_AN_ANTHOLOGY;
            mBookData.putInt(Book.IS_ANTHOLOGY, ant);
        }

        // try to deduce the first publication date
        if (toc.size() == 1) {
            // if the content table has only one entry, then this will have the first publication year for sure
            mBookData.putString(UniqueId.KEY_FIRST_PUBLICATION, digits(toc.get(0).getFirstPublication()));

        } else  if (toc.size() > 1){
            // we gamble and take what we found in the content
            if (mFirstPublication != null) {
                mBookData.putString(UniqueId.KEY_FIRST_PUBLICATION, digits(mFirstPublication));
            } // else take the book pub date ... but that might be wrong....
        }

        // another gamble for the series "name (nr)"
        if (mSeries != null) {
            ArrayUtils.addOrAppend(mBookData, UniqueId.BKEY_SERIES_DETAILS, mSeries);
        }

        // lastly, optional fetch of the cover.
        if (mFetchThumbnail) {
            fetchCover();
        }
    }

    @NonNull
    private String digits(@NonNull final String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public void fetchCover() {
        if (!loadPage()) {
            return;
        }
        fetchCover(mDoc.select("div.contentbox").first());
    }

    /* First "ContentBox" contains all basic details

    <div class="ContentBox">
    <table>
    <tr class="scan">
    <td>
        <a href="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg">
        <img src="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg" alt="picture" class="scan"></a>
    </td>
    */
    private void fetchCover(@NonNull final Element contentBox) {
        Element img = contentBox.selectFirst("img");
        String thumbnail = img.attr("src");
        String fileSpec = ImageUtils.saveThumbnailFromUrl(thumbnail, "_ISFDB");
        if (!fileSpec.isEmpty()) {
            ArrayUtils.addOrAppend(mBookData, UniqueId.BKEY_THUMBNAIL_USCORE, fileSpec);
        }
    }

    /*  Second ContentBox contains the TOC

        <div class="ContentBox">
        <span class="containertitle">Collection Title:</span>
        <a href="http://www.isfdb.org/cgi-bin/title.cgi?37576" dir="ltr">The Days of Perky Pat</a> &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?22461" dir="ltr">The Collected Stories of Philip K. Dick</a> &#8226; 4] &#8226; (1987) &#8226; collection by
        <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
        <h2>Contents <a href="http://www.isfdb.org/cgi-bin/pl.cgi?230949+c"><span class="listingtext">(view Concise Listing)</span></a></h2>
        <ul>

        <li>
        7 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?118799" dir="ltr">Introduction (The Days of Perky Pat)</a>
        &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?31226" dir="ltr">Introductions to the Collected Stories of Philip K. Dick</a> &#8226; 4]
        &#8226; (1987)
        &#8226; essay by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?57" dir="ltr">James Tiptree, Jr.</a>

        <li>
        11 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?53646" dir="ltr">Autofac</a>
        &#8226; (1955)
        &#8226; novelette by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>

        <li>
        395 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?58186" dir="ltr">The Days of Perky Pat</a>
        &#8226; (1963)
         &#8226; novelette by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>

        <li>
        423 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?95236" dir="ltr">Stand-By</a>
         &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?25841" dir="ltr">Jim Briskin</a>]
         &#8226; (1963)
         &#8226; short story by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
         (variant of <i><a href="http://www.isfdb.org/cgi-bin/title.cgi?58256" dir="ltr">Top Stand-By Job</a></i>)

        <li>
        487 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?1257883" dir="ltr">Notes (The Days of Perky Pat)</a>
        &#8226; (1987)
        &#8226; essay by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
        </ul>
        </div>

        This method returns the list for easy use, but ALSO adds the data to the book bundle !
     */
    @NonNull
    private ArrayList<AnthologyTitle> getTableOfContentList() {

        final ArrayList<AnthologyTitle> results = new ArrayList<>();

        if (!loadPage()) {
            return results;
        }
        // <div class="ContentBox"> but there are two, so get last one
        Element contentbox = mDoc.select("div.contentbox").last();
        Elements lis = contentbox.select("li");
        for (Element li : lis) {

            /* LI entries, 4 possibilities:

            7 &#8226;
            <a href="http://www.isfdb.org/cgi-bin/title.cgi?118799" dir="ltr">Introduction (The Days of Perky Pat)</a>
            &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?31226" dir="ltr">Introductions to the Collected Stories of Philip K. Dick</a> &#8226; 4]
            &#8226; (1987)
            &#8226; essay by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?57" dir="ltr">James Tiptree, Jr.</a>


            11 &#8226;
            <a href="http://www.isfdb.org/cgi-bin/title.cgi?53646" dir="ltr">Autofac</a>
            &#8226; (1955)
            &#8226; novelette by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>


            <a href="http://www.isfdb.org/cgi-bin/title.cgi?41613" dir="ltr">Beyond Lies the Wub</a>
            &#8226; (1952)
            &#8226; short story by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>


            <a href="http://www.isfdb.org/cgi-bin/title.cgi?118803" dir="ltr">Introduction (Beyond Lies the Wub)</a>
            &#8226; [ <a href="http://www.isfdb.org/cgi-bin/pe.cgi?31226" dir="ltr">Introductions to the Collected Stories of Philip K. Dick</a> &#8226; 1]
            &#8226; (1987)
            &#8226; essay by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?69" dir="ltr">Roger Zelazny</a>

            So the year is always previous from last, but there is a non-visible 'text' node at the end, hence 'len-3'

            A book belonging to a series will have one content entry with the same title as the book.
            TODO: And potentially have the series/nr in it:

            <a href="http://www.isfdb.org/cgi-bin/title.cgi?2210372" dir="ltr">The Delirium Brief</a>
            &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?23081" dir="ltr">Laundry Files</a> &#8226; 8]
            &#8226; (2017) &#8226; novel by
            <a href="http://www.isfdb.org/cgi-bin/ea.cgi?2200" dir="ltr">Charles Stross</a>

             */
            int len = li.childNodeSize();
            Node y = li.childNode(len - 3);
            Matcher matcher = AnthologyTitle.YEAR_FROM_STRING.matcher(y.toString());
            String year = matcher.find() ? matcher.group(1) : "";

            /* See above for LI examples. The title is the first a element, the author is the last a element */
            Elements a = li.select("a");
            String title = cleanUpName(a.get(0).text());
            Author author = new Author(cleanUpName(a.get(a.size() - 1).text()));

            AnthologyTitle anthologyTitle = new AnthologyTitle(author, title, year);
            results.add(anthologyTitle);
            ArrayUtils.addOrAppend(mBookData, UniqueId.BKEY_ANTHOLOGY_DETAILS, anthologyTitle.toString());

            // check for year & series. Note we don't store it here in mBookData
            // once found, don't retest (mFirstPublication == null)
            // is the book title the same as the content entry?
            if (mFirstPublication == null && title.equalsIgnoreCase(mTitle)) {
                // then we have the year
                mFirstPublication = year;

                // and potentially a series:
                if (a.size() == 3) {
                    // series don't always have a number
                    Matcher sm = SERIES.matcher(li.toString());
                    String tmp = sm.find() ? sm.group(1) : "";
                    String[] data = tmp.split("&#x2022;"); // yes, hex... chrome browser view-source shows &#8226;
                    // don't use data[0], we already have easy access to the a element
                    mSeries = a.get(1).text();
                    // now check if there was a series number
                    if (data.length > 1) {
                        //noinspection StringConcatenationInLoop
                        mSeries += " (" + data[1] + ")";
                    }
                }
            }
        }

        return results;
    }
}
