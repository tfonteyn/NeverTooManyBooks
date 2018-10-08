package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.entities.BookData;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

public class ISFDBBook extends AbstractBase {

    private static final Map<String,String> FORMAT_MAP = new HashMap<>();
    static {
        FORMAT_MAP.put("pb", "Paperback");
        FORMAT_MAP.put("tp", "Trade Paperback");
        FORMAT_MAP.put("hc", "Hardcover");
        FORMAT_MAP.put("ebook", "eBook");
        FORMAT_MAP.put("digest", "Digest");
        FORMAT_MAP.put("unknown", "Unknown");
    }

    private static final Map<String,String> TYPE_MAP = new HashMap<>();
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

    private final Bundle mBook;
    private final boolean mFetchThumbnail;

    /** ISFDB native book id */
    private long mPublicationRecord;

    /**
     * @param publicationRecord ISFDB native book id
     */
    public ISFDBBook(final long publicationRecord,
                     @NonNull final Bundle /* out */ book,
                     final boolean fetchThumbnail) {
        mPublicationRecord = publicationRecord;
        mPath = String.format(ISFDBManager.getBaseURL() + "/cgi-bin/pl.cgi?%s", mPublicationRecord);
        mBook = book;
        mFetchThumbnail = fetchThumbnail;
    }

    /**
     * @param path example: "http://www.isfdb.org/cgi-bin/pl.cgi?230949"
     */
    public ISFDBBook(@NonNull final String path,
                     @NonNull final Bundle /* out */ book,
                     final boolean fetchThumbnail) {
        mPublicationRecord = stripNumber(path);
        mPath = path;
        mBook = book;
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
        //TODO: series could be fetched from Content (e.g. contextbox.last)

        Element contentBox = mDoc.select("div.contentbox").first();
        Elements lis = contentBox.select("li");
        String tmp;

        for (Element li : lis) {
            try {
                if (li.text().contains("Publication")) {
                    tmp = li.childNode(1).toString().trim();
                    mBook.putString(UniqueId.KEY_TITLE, tmp);

                    tmp = li.childNode(2).childNode(1).toString().trim();
                    mBook.putString("ISFDB_PUB_RECORD", tmp);

                } else if (li.text().contains("Author")) {
                    ArrayList<String> urls = new ArrayList<>();
                    Elements as = li.select("a");
                    for (Element a : as) {
                        urls.add(a.attr("href"));
                        ArrayUtils.appendOrAdd(mBook, UniqueId.BKEY_AUTHOR_DETAILS, a.text());
                    }
                    mBook.putStringArrayList("ISFDB_AUTHOR_URL", urls);

                } else if (li.text().contains("Date")) {
                    tmp = li.childNode(2).toString().trim();
                    mBook.putString(UniqueId.KEY_BOOK_DATE_PUBLISHED, tmp);

                } else if (li.text().contains("ISBN")) {
                    tmp = li.childNode(1).toString().trim();
                    mBook.putString("ISFDB_ISBN10", digits(tmp));
                    // second ISBN, 13
                    tmp = li.childNode(2).childNode(0).toString().trim();
                    mBook.putString(UniqueId.KEY_ISBN, digits(tmp));

                } else if (li.text().contains("Publisher")) {
                    tmp = li.childNode(3).attr("href");
                    mBook.putString("ISFDB_PUBLISHER_URL", tmp);

                    tmp = li.childNode(3).childNode(0).toString().trim();
                    mBook.putString(UniqueId.KEY_BOOK_PUBLISHER, tmp);

                } else if (li.text().contains("Price")) {
                    tmp = li.childNode(2).toString().trim();
                    mBook.putString(UniqueId.KEY_BOOK_LIST_PRICE, tmp);

                } else if (li.text().contains("Pages")) {
                    tmp = li.childNode(2).toString().trim();
                    mBook.putString(UniqueId.KEY_BOOK_PAGES, tmp);

                } else if (li.text().contains("Format")) {
                    tmp = li.childNode(3).childNode(0).toString().trim();
                    tmp = FORMAT_MAP.get(tmp);
                    if (tmp != null) {
                        mBook.putString(UniqueId.KEY_BOOK_FORMAT, tmp);
                    }

                } else if (li.text().contains("Type")) {
                    tmp = li.childNode(2).toString().trim();
                    mBook.putString("ISFDB_BOOK_TYPE", tmp); // original type

                    if ("a1".equals(TYPE_MAP.get(tmp))) {
                        mBook.putInt(BookData.IS_ANTHOLOGY,
                                DatabaseDefinitions.DOM_ANTHOLOGY_IS_AN_ANTHOLOGY);
                    } else if ("a2".equals(TYPE_MAP.get(tmp))) {
                        mBook.putInt(BookData.IS_ANTHOLOGY,
                                DatabaseDefinitions.DOM_ANTHOLOGY_IS_AN_ANTHOLOGY |
                                DatabaseDefinitions.DOM_ANTHOLOGY_WITH_MULTIPLE_AUTHORS);
                    }

                } else if (li.text().contains("Cover")) {
                    tmp = li.childNode(2).attr("href");
                    mBook.putString("ISFDB_BOOK_COVER_ART_URL", tmp);

                    tmp = li.childNode(2).childNode(0).toString().trim();
                    mBook.putString("ISFDB_BOOK_COVER_ART_TXT", tmp);

                    tmp = li.childNode(4).attr("href");
                    mBook.putString("ISFDB_BOOK_COVER_ARTIST_URL", tmp);

                    // Cover artist
                    tmp = li.childNode(4).childNode(0).toString().trim();
                    ArrayUtils.appendOrAdd(mBook, UniqueId.BKEY_AUTHOR_DETAILS, tmp);

                } else if (li.text().contains("Notes")) {
                    tmp = li.childNode(1).childNode(1).toString().trim();
                    mBook.putString(UniqueId.KEY_DESCRIPTION, tmp);

                } else if (li.text().contains("Editors")) {
                    ArrayList<String> urls = new ArrayList<>();
                    Elements as = li.select("a");
                    for (Element a : as) {
                        urls.add(a.attr("href"));
                        ArrayUtils.appendOrAdd(mBook, UniqueId.BKEY_AUTHOR_DETAILS, a.text());
                    }
                    mBook.putStringArrayList("ISFDB_EDITORS_URL", urls);

                }
            } catch (IndexOutOfBoundsException e) {
                // does not happen now, but could happen if ISFDB website changes
                Logger.logError(e,"path: " + mPath + "\n\nLI: " + li.toString());
            }
        }

        mBook.putSerializable(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY, this.getAnthologyTitles());

        if (mFetchThumbnail) {
            fetchCover();
        }
    }

    private String digits(@NonNull final String s) {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i < s.length(); i++) {
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
            ArrayUtils.appendOrAdd(mBook, UniqueId.BKEY_THUMBNAIL_USCORE, fileSpec);
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
    7 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?118799" dir="ltr">Introduction (The Days of Perky Pat)</a> &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?31226" dir="ltr">Introductions to the Collected Stories of Philip K. Dick</a> &#8226; 4] &#8226; (1987) &#8226; essay by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?57" dir="ltr">James Tiptree, Jr.</a>
    <li>
    11 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?53646" dir="ltr">Autofac</a> &#8226; (1955) &#8226; novelette by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    37 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?58242" dir="ltr">Service Call</a> &#8226; (1955) &#8226; novelette by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    57 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?41568" dir="ltr">Captive Market</a> &#8226; (1955) &#8226; short story by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    76 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?58216" dir="ltr">The Mold of Yancy</a> &#8226; (1955) &#8226; novelette by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    99 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?58214" dir="ltr">The Minority Report</a> &#8226; (1956) &#8226; novelette by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    141 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?58238" dir="ltr">Recall Mechanism</a> &#8226; (1959) &#8226; short story by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    159 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?58259" dir="ltr">The Unreconstructed M</a> &#8226; (1957) &#8226; novelette by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    198 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?58190" dir="ltr">Explorers We</a> &#8226; (1959) &#8226; short story by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    209 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?43713" dir="ltr">War Game</a> &#8226; (1959) &#8226; short story by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    228 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?41292" dir="ltr">If There Were No Benny Cemoli</a> &#8226; (1963) &#8226; novelette by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    251 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?58220" dir="ltr">Novelty Act</a> &#8226; (1964) &#8226; novelette by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    285 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?58263" dir="ltr">Waterspider</a> &#8226; (1964) &#8226; novelette by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    321 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?58264" dir="ltr">What the Dead Men Say</a> &#8226; (1964) &#8226; novella by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    379 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?58224" dir="ltr">Orpheus with Clay Feet</a> &#8226; (1987) &#8226; short story by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    395 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?58186" dir="ltr">The Days of Perky Pat</a> &#8226; (1963) &#8226; novelette by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    423 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?95236" dir="ltr">Stand-By</a> &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?25841" dir="ltr">Jim Briskin</a>] &#8226; (1963) &#8226; short story by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
     (variant of <i><a href="http://www.isfdb.org/cgi-bin/title.cgi?58256" dir="ltr">Top Stand-By Job</a></i>)
    <li>
    443 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?58265" dir="ltr">What'll We Do with Ragland Park?</a> &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?25841" dir="ltr">Jim Briskin</a>] &#8226; (1963) &#8226; novelette by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    467 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?58223" dir="ltr">Oh, to Be a Blobel!</a> &#8226; (1964) &#8226; novelette by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    <li>
    487 &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?1257883" dir="ltr">Notes (The Days of Perky Pat)</a> &#8226; (1987) &#8226; essay by
    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
    </ul>
    </div>
     */
    public ArrayList<AnthologyTitle> getAnthologyTitles() {
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
                    &#8226; [
                    <a href="http://www.isfdb.org/cgi-bin/pe.cgi?31226" dir="ltr">Introductions to the Collected Stories of Philip K. Dick</a>
                    &#8226; 4] &#8226; (1987) &#8226; essay by
                    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?57" dir="ltr">James Tiptree, Jr.</a>


                    11 &#8226;
                    <a href="http://www.isfdb.org/cgi-bin/title.cgi?53646" dir="ltr">Autofac</a>
                    &#8226; (1955) &#8226; novelette by
                    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>


                    <a href="http://www.isfdb.org/cgi-bin/title.cgi?41613" dir="ltr">Beyond Lies the Wub</a>
                    &#8226; (1952) &#8226; short story by
                    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>


                    <a href="http://www.isfdb.org/cgi-bin/title.cgi?118803" dir="ltr">Introduction (Beyond Lies the Wub)</a>
                    &#8226; [
                    <a href="http://www.isfdb.org/cgi-bin/pe.cgi?31226" dir="ltr">Introductions to the Collected Stories of Philip K. Dick</a>
                    &#8226; 1] &#8226; (1987) &#8226; essay by
                    <a href="http://www.isfdb.org/cgi-bin/ea.cgi?69" dir="ltr">Roger Zelazny</a>

                    So the year is always previous from last, but there is a non-visible 'text' node at the end, hence 'len-3'
                     */
            int len = li.childNodeSize();
            Node y = li.childNode(len - 3);
            Matcher matcher = AnthologyTitle.YEAR_FROM_STRING.matcher(y.toString());
            String year = matcher.find() ? matcher.group(1) : "";

            /* See above for LI examples. The title is the first a element, the author is the last a element */
            Elements a = li.select("a");
            String title = cleanUpName(a.get(0).text());
            String author = cleanUpName(a.get(a.size() - 1).text());
            results.add(new AnthologyTitle(new Author(author), title, year, mPublicationRecord));
        }

        return results;
    }
}
