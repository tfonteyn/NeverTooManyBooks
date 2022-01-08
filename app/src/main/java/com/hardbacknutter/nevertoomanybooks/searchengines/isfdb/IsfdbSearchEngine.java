/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.network.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * See notes in the package-info.java file.
 * <p>
 * 2020-01-04: "http://www.isfdb.org" is not available on https.
 * see "src/main/res/xml/network_security_config.xml"
 */
public class IsfdbSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByText,
                   SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId,
                   SearchEngine.ViewBookByExternalId,
                   SearchEngine.CoverByIsbn,
                   SearchEngine.AlternativeEditions {

    /**
     * The site claims to use ISO-8859-1.
     * <pre>
     * {@code <meta http-equiv="content-type" content="text/html; charset=iso-8859-1">}
     * </pre>
     * but the real encoding seems to be Windows-1252.
     * For example, a books list price with a specific currency symbol (e.g. dutch guilders)
     * fails to be decoded unless we force Windows-1252
     * (tested with UTF-8 similarly fails to decode those symbols)
     */
    static final String CHARSET_DECODE_PAGE = "Windows-1252";
    /** But to encode the search url (a GET), the charset must be 8859-1. */
    @SuppressWarnings("WeakerAccess")
    static final String CHARSET_ENCODE_URL = "iso-8859-1";
    /** Map ISFDB book types to {@link Book.ContentType}. */
    static final Map<String, Book.ContentType> TYPE_MAP = new HashMap<>();
    /** Preferences prefix. */
    private static final String PREF_KEY = "isfdb";
    /** Type: {@code boolean}. */
    public static final String PK_USE_PUBLISHER = PREF_KEY + ".search.uses.publisher";
    /** Type: {@code boolean}. */
    static final String PK_SERIES_FROM_TOC = PREF_KEY + ".search.toc.series";
    public static final CharSequence PK_CONNECT_TIMEOUT_IN_SECONDS =
            PREF_KEY + Prefs.pk_suffix_timeout_connect;
    public static final CharSequence PK_READ_TIMEOUT_IN_SECONDS =
            PREF_KEY + Prefs.pk_suffix_timeout_read;
    /**
     * As proposed by another user on the ISFDB wiki,
     * we're only going to send one request a second.
     *
     * @see <a href="http://www.isfdb.org/wiki/index.php/ISFDB:Help_desk#Some_Downloading_Questions_and_a_Request">throttling</a>
     */
    private static final Throttler THROTTLER = new Throttler(1_000);
    /** Common CGI directory. */
    private static final String CGI_BIN = "/cgi-bin";
    /** bibliographic information for one title. */
    private static final String CGI_TITLE = "/title.cgi";
    /** bibliographic information for one publication. */
    private static final String CGI_PL = "/pl.cgi";
    /** ISFDB bibliography for one author. */
    private static final String CGI_EA = "/ea.cgi";
    /** titles associated with a particular Series. */
    private static final String CGI_PE = "/pe.cgi";
    /** Search by type; e.g.  arg=%s&type=ISBN. */
    private static final String CGI_SE = "/se.cgi";
    /** Advanced search FORM submission (using GET), and the returned results page url. */
    private static final String CGI_ADV_SEARCH_RESULTS = "/adv_search_results.cgi";
    /** Log tag. */
    private static final String TAG = "IsfdbSearchEngine";
    /** Param 1: external book ID. */
    private static final String CGI_BY_EXTERNAL_ID = CGI_BIN + CGI_PL + "?%1$s";
    /** Search URL template. */
    private static final String CGI_EDITIONS = CGI_BIN + CGI_SE + "?arg=%s&type=ISBN";

    private static final String REST_BIN = CGI_BIN + "/rest";
    private static final String REST_BY_EXTERNAL_ID = REST_BIN + "/getpub_by_internal_ID.cgi?%1$s";

    /**
     * Either the Web page itself, and/or the JSoup parser has used both decimal and hex
     * representation for the "•" character. Capturing all 3 possibilities here.
     */
    private static final String DOT = "(&#x2022;|&#8226;|•)";
    /** Character used by the site as string divider/splitter. */
    // private static final Pattern DOT_PATTERN = Pattern.compile(DOT);
    private static final Pattern YEAR_PATTERN = Pattern.compile(DOT + " \\(([1|2]\\d\\d\\d)\\)");
    /** ISFDB uses 00 for the day/month when unknown. We cut that out. */
    private static final Pattern UNKNOWN_M_D_LITERAL = Pattern.compile("-00", Pattern.LITERAL);
    /** A CSS select query. */
    private static final String CSS_Q_DIV_CONTENTBOX = "div.contentbox";

    /*
     * <a href="http://www.isfdb.org/wiki/index.php/Help:Screen:NewPub#Publication_Type">
     Publication_Type</a>
     *
     * ANTHOLOGY. A publication containing fiction by more than one author,
     * not written in collaboration
     *
     * COLLECTION. A publication containing two or more works
     * by a single author or authors writing in collaboration
     *
     * OMNIBUS. A publication may be classified as an omnibus if it contains multiple works
     * that have previously been published independently
     * generally this category should not be used
     *
     * Boxed sets. Boxed sets which have additional data elements (ISBNs, cover art, etc)
     * not present in the individual books that they collect should be entered
     * as OMNIBUS publications
     *
     * Put all these together into the Anthology bucket.
     *
     * Reminder: a "Digest" is a format, not a type.
     */
    static {
        // multiple works, one author
        TYPE_MAP.put("coll", Book.ContentType.Collection);
        TYPE_MAP.put("COLLECTION", Book.ContentType.Collection);

        // multiple works, multiple authors
        TYPE_MAP.put("anth", Book.ContentType.Anthology);
        TYPE_MAP.put("ANTHOLOGY", Book.ContentType.Anthology);

        // multiple works that have previously been published independently
        TYPE_MAP.put("omni", Book.ContentType.Collection);
        TYPE_MAP.put("OMNIBUS", Book.ContentType.Collection);

        // we assume magazines have multiple authors; i.e. they are considered anthologies
        TYPE_MAP.put("MAGAZINE", Book.ContentType.Anthology);

        // others, treated as a standard book.
        // TYPE_MAP.put("novel", Book.ContentType.Book);
        // TYPE_MAP.put("NOVEL", Book.ContentType.Book);
        //
        // TYPE_MAP.put("chap", 0);
        // TYPE_MAP.put("CHAPBOOK", 0);
        //
        // TYPE_MAP.put("non-fic", 0);
        // TYPE_MAP.put("NONFICTION", 0);
    }

    /** set during book load, used during content table load. */
    @Nullable
    private String mTitle;
    /** with some luck we'll get these as well. */
    @Nullable
    private String mFirstPublicationYear;
    /** The ISBN we searched for. Not guaranteed to be identical to the book we find. */
    private String mIsbn;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param config the search engine configuration
     */
    @Keep
    public IsfdbSearchEngine(@NonNull final SearchEngineConfig config) {
        super(config, CHARSET_DECODE_PAGE);
    }

    public static SearchEngineConfig createConfig() {
        return new SearchEngineConfig.Builder(IsfdbSearchEngine.class,
                                              SearchSites.ISFDB,
                                              R.string.site_isfdb,
                                              PREF_KEY,
                                              "http://www.isfdb.org")
                .setFilenameSuffix("ISFDB")

                .setDomainKey(DBKey.SID_ISFDB)
                .setDomainViewId(R.id.site_isfdb)
                .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_ISFDB)

                .setConnectTimeoutMs(20_000)
                .setReadTimeoutMs(60_000)
                .setStaticThrottler(THROTTLER)
                .build();
    }


    @NonNull
    @Override
    public String createBrowserUrl(@NonNull final String externalId) {
        return getSiteUrl() + CGI_BIN + CGI_PL + "?" + externalId;
    }

    @NonNull
    @Override
    public Bundle searchByExternalId(@NonNull final Context context,
                                     @NonNull final String externalId,
                                     @NonNull final boolean[] fetchCovers)
            throws DiskFullException, CoverStorageException, SearchException, CredentialsException {

        final Bundle bookData = newBundleInstance();

        final String url = getSiteUrl() + String.format(CGI_BY_EXTERNAL_ID, externalId);
        final Document document = loadDocument(context, url);
        if (!isCancelled()) {
            parse(context, document, fetchCovers, bookData);
        }
        return bookData;
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context context,
                               @NonNull final String validIsbn,
                               @NonNull final boolean[] fetchCovers)
            throws DiskFullException, CoverStorageException, SearchException, CredentialsException {

        final Bundle bookData = newBundleInstance();

        final List<Edition> editions = fetchEditionsByIsbn(context, validIsbn);
        if (!editions.isEmpty()) {
            fetchByEdition(context, editions.get(0), fetchCovers, bookData);
        }
        return bookData;
    }

    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@NonNull final Context context,
                         @Nullable final /* not supported */ String code,
                         @Nullable final String author,
                         @Nullable final String title,
                         @Nullable final String publisher,
                         @NonNull final boolean[] fetchCovers)
            throws DiskFullException, CoverStorageException, SearchException, CredentialsException {

        final String url = getSiteUrl() + CGI_BIN + CGI_ADV_SEARCH_RESULTS + "?"
                           + "ORDERBY=pub_title"
                           + "&ACTION=query"
                           + "&START=0"
                           + "&TYPE=Publication"
                           + "&C=AND";

        int index = 0;
        String args = "";
        final Bundle bookData = newBundleInstance();

        try {
            if (author != null && !author.isEmpty()) {
                index++;
                args += "&USE_" + index + "=author_canonical"
                        + "&O_" + index + "=contains"
                        + "&TERM_" + index + "=" + URLEncoder.encode(author, CHARSET_ENCODE_URL);
            }

            if (title != null && !title.isEmpty()) {
                index++;
                args += "&USE_" + index + "=pub_title"
                        + "&O_" + index + "=contains"
                        + "&TERM_" + index + "=" + URLEncoder.encode(title, CHARSET_ENCODE_URL);
            }

            // as per user settings.
            if (ServiceLocator.getGlobalPreferences().getBoolean(PK_USE_PUBLISHER, false)) {
                if (publisher != null && !publisher.isEmpty()) {
                    index++;
                    args += "&USE_" + index + "=pub_publisher"
                            + "&O_" + index + "=contains"
                            + "&TERM_" + index + "=" + URLEncoder
                                    .encode(publisher, CHARSET_ENCODE_URL);
                }
            }

            // there is support for up to 6 search terms.
            // &USE_4=pub_title&O_4=exact&TERM_4=
            // &USE_5=pub_title&O_5=exact&TERM_5=
            // &USE_6=pub_title&O_6=exact&TERM_6=

            // sanity check: any data to search for?
            if (!args.isEmpty()) {
                final List<Edition> editions = fetchEditions(context, url + args);
                if (!editions.isEmpty()) {
                    fetchByEdition(context, editions.get(0), fetchCovers, bookData);
                }
            }
        } catch (@NonNull final IOException e) {
            throw new SearchException(getName(context), e);
        }
        return bookData;
    }

    @Nullable
    @Override
    public String searchCoverByIsbn(@NonNull final Context context,
                                    @NonNull final String validIsbn,
                                    @IntRange(from = 0, to = 1) final int cIdx,
                                    @Nullable final ImageFileInfo.Size size)
            throws DiskFullException, CoverStorageException, SearchException, CredentialsException {

        final List<Edition> editions = fetchEditionsByIsbn(context, validIsbn);
        if (!editions.isEmpty()) {
            final Edition edition = editions.get(0);
            final Document document = loadDocumentByEdition(context, edition);
            if (!isCancelled()) {
                final ArrayList<String> imageList = parseCovers(document, edition.getIsbn(), 0);
                if (!imageList.isEmpty()) {
                    // let the system resolve any path variations
                    return new File(imageList.get(0)).getAbsolutePath();
                }
            }
        }
        return null;
    }

    /**
     * Search for edition data.
     *
     * <strong>Note:</strong> we assume the isbn numbers retrieved from the site are valid.
     * No extra checks are made at this point.
     *
     * <br>{@inheritDoc}
     */
    @NonNull
    @Override
    public List<String> searchAlternativeEditions(@NonNull final Context context,
                                                  @NonNull final String validIsbn)
            throws SearchException, CredentialsException {

        // transform the Edition list to a simple isbn list
        return fetchEditionsByIsbn(context, validIsbn)
                .stream()
                .map(Edition::getIsbn)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /**
     * Parses the downloaded {@link Document}.
     * <p>
     * First "ContentBox" contains all basic details.
     * IMPORTANT: 2019-07: the format has slightly changed! See below.
     * <pre>
     * {@code
     * <div class="ContentBox">
     *    <table>
     *      <tr class="scan">
     *
     *        <td>
     *          <a href="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg">
     *            <img src="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg"
     *                 alt="picture" class="scan"></a>
     *        </td>
     *
     *        <td class="pubheader">
     *          <ul>
     *            <li><b>Publication:</b> The Days of Perky Pat <span class="recordID">
     *                <b>Publication Record # </b>230949
     *                [<a href="http://www.isfdb.org/cgi-bin/edit/editpub.cgi?230949">Edit</a>]
     *                </span>
     *            <li><b>Author:</b>
     *              <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23">Philip K. Dick</a>
     *            <li><b>Date:</b> 1991-05-00
     *            <li><b>ISBN:</b> 0-586-20768-6 [<small>978-0-586-20768-0</small>]
     *            <li><b>Publisher:</b>
     *              <a href="http://www.isfdb.org/cgi-bin/publisher.cgi?62">Grafton</a>
     *            <li><b>Price:</b> £5.99
     *            <li><b>Pages:</b> 494
     *            <li><b>Format:</b> <div class="tooltip">tp<sup class="mouseover">?</sup>
     *            <span class="tooltiptext tooltipnarrow">Trade paperback...</span></div>
     *            <li><b>Type:</b> COLLECTION
     *            <li><b>Cover:</b>
     *              <a href="http://www.isfdb.org/cgi-bin/title.cgi?737949">
     *                  The Days of Perky Pat</a>
     *                  by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?21338">Chris Moore</a>
     *            <li>
     *              <div class="notes"><b>Notes:</b>
     *                "Published by Grafton Books 1991" on copyright page
     *                Artist credited on back cover
     *                "Top Stand-By Job" listed in contents and title page of story as "Stand-By"
     *                • Month from Locus1
     *                • Notes from page 487 to 494
     *                • OCLC <A href="http://www.worldcat.org/oclc/60047795">60047795</a>
     *              </div>
     *            </ul>
     *          </td>
     *    </table>
     *  Cover art supplied by
     *      <a href="http://www.isfdb.org/wiki/index.php/Image:THDSFPRKPT1991.jpg"
     *                           target="_blank">ISFDB</a>
     * </div>
     * }
     * </pre>
     * <p>
     * 20-19-07: new format:
     * <pre>
     * {@code
     * <div class="ContentBox">
     *   <table>
     *     <tr class="scan">
     *
     *       <td>
     *         <a href="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg">
     *         <img src="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg" class="scan"></a>
     *       </td>
     *
     *       <td class="pubheader">
     *         <ul>
     *           <li><b>Publication:</b> The Days of Perky Pat<span class="recordID">
     *               <b>Publication Record # </b>230949
     *               [<a href="http://www.isfdb.org/cgi-bin/edit/editpub.cgi?230949">Edit</a>]
     *               </span>
     *           <li><b>Author:</b>
     *              <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23">Philip K. Dick</a>
     *           <li> <b>Date:</b> 1991-05-00
     *           <li><b>ISBN:</b> 0-586-20768-6 [<small>978-0-586-20768-0</small>]
     *           <li><b>Publisher:</b>
     *              <a href="http://www.isfdb.org/cgi-bin/publisher.cgi?62">Grafton</a>
     *           <li><b>Price:</b> £5.99
     *           <li><b>Pages:</b> 494
     *           <li><b>Format:</b> <div class="tooltip">tp<sup class="mouseover">?</sup>
     *              <span class="tooltiptext tooltipnarrow">Trade paperback...</span></div>
     *           <li><b>Type:</b> COLLECTION
     *           <li><b>Cover:</b>
     *              <a href="http://www.isfdb.org/cgi-bin/title.cgi?737949">
     *                  The Days of Perky Pat</a>
     *                  by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?21338">Chris Moore</a>
     *           <li>
     *             <div class="notes"><b>Notes:</b>
     *               "Published by Grafton Books 1991" on copyright page
     *               Artist credited on back cover
     *               "Top Stand-By Job" listed in contents and title page of story as "Stand-By"
     *               • Month from Locus1
     *               • Notes from page 487 to 494
     *             </div>
     *           <li>
     *             <b>External IDs:</b>
     *             <ul class="noindent">
     *               <li> <abbr class="template" title="Online Computer Library Center">
     *                   OCLC/WorldCat</abbr>:
     *                   <a href="http://www.worldcat.org/oclc/60047795">60047795</a>
     *             </ul>
     *           <li><a href="http://www.isfdb.org/wiki/index.php/Special:Upload?
     *                  wpDestFile=THDSFPRKPT1991.jpg
     *                  &amp;wpUploadDescription=%7B%7BCID1%0A
     *                  %7CTitle%3DThe%20Days%20of%20Perky%20Pat%0A
     *                  %7CEdition%3DGrafton%201991%20tp%0A
     *                  %7CPub%3DTHDSFPRKPT1991%0A
     *                  %7CPublisher%3DGrafton%0A
     *                  %7CArtist%3DChris%20Moore%0A
     *                  %7CArtistId%3D21338%0A
     *                  %7CSource%3DScanned%20by%20%5B%5BUser%3AHardbackNut%5D%5D%7D%7D"
     *                  >Upload new cover scan</a>
     *         </ul>
     *       </td>
     *   </table>
     * Cover art supplied by <a href="http://www.isfdb.org" target="_blank">ISFDB</a> on
     * <a href="http://www.isfdb.org/wiki/index.php/Image:THDSFPRKPT1991.jpg"
     * target="_blank">this Web page</a>
     * </div>
     * }
     * </pre>
     * <p>
     * => external ID's have their own section
     * Example from elsewhere:
     * <pre>
     * {@code
     * <li>
     *  <b>External IDs:</b>
     *  <ul class="noindent">
     *    <li> <abbr class="template" title="Library of Congress Control Number">LCCN</abbr>:
     *    <a href="http://lccn.loc.gov/85070137" target="_blank">85070137</a>
     *    <li> <abbr class="template" title="Online Computer Library Center">OCLC/WorldCat</abbr>:
     *    <a href="http://www.worldcat.org/oclc/13063516" target="_blank">13063516</a>
     *  </ul>
     * <li>
     * }
     * </pre>
     * => not in above example, but seen elsewhere: the notes are HTML structured now:
     * Example:
     * <pre>
     * {@code
     * <div class="notes">
     *   <b>Notes:</b>
     *   <ul>
     *     <li>Month from Amazon.co.uk and publisher's web site.<li>First print by
     *     full number line 1 3 5 7 9 10 8 6 4 2
     *     <li>Cover image: © Shutterstock<li>Design by www.blacksheep-uk.com
     *     <li>Author photo: Alastair Reynolds © Barbara Bella
     *     <li>Afterword on page [327]
     *   </ul>
     * </div>
     * }
     * </pre>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Bundle bookData)
            throws DiskFullException, CoverStorageException, SearchException {
        super.parse(context, document, fetchCovers, bookData);

        final DateParser dateParser = new FullDateParser(context);

        final Elements allContentBoxes = document.select(CSS_Q_DIV_CONTENTBOX);
        // sanity check
        if (allContentBoxes.isEmpty()) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "parseDoc|no contentbox found|mDoc.location()="
                           + document.location());
            }
            return;
        }

        final Element contentBox = allContentBoxes.first();
        // sanity check
        if (contentBox == null) {
            return;
        }
        final Element ul = contentBox.selectFirst("ul");
        // sanity check
        if (ul == null) {
            return;
        }
        final Elements lis = ul.children();

        String tmpString;
        Node nextSibling;

        for (final Element li : lis) {
            if (isCancelled()) {
                return;
            }

            try {
                String fieldName = null;

                // We want the first 'bold' child Element of the li; e.g. "<b>Publisher:</b>"
                final Element fieldLabelElement = li.selectFirst("b");
                if (fieldLabelElement != null) {
                    fieldName = fieldLabelElement.text();
                }

                if (fieldName == null) {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB) {
                        Log.d(TAG, "fetch|" + li);
                    }
                    continue;
                }

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB) {
                    Log.d(TAG, "fetch|fieldName=" + fieldName);
                }

                if ("Publication:".equalsIgnoreCase(fieldName)) {
                    nextSibling = fieldLabelElement.nextSibling();
                    if (nextSibling != null) {
                        mTitle = nextSibling.toString().trim();
                        bookData.putString(DBKey.KEY_TITLE, mTitle);
                    }
                } else if ("Author:".equalsIgnoreCase(fieldName)
                           || "Authors:".equalsIgnoreCase(fieldName)) {
                    for (final Element a : li.select("a")) {
                        final Author author = Author.from(a.text());
                        // author.setIsfDbId(stripNumber(a.attr("href"), '?'));
                        mAuthors.add(author);
                    }

                } else if ("Date:".equalsIgnoreCase(fieldName)) {
                    nextSibling = fieldLabelElement.nextSibling();
                    if (nextSibling != null) {
                        // dates are in fact displayed as YYYY-MM-DD which is very nice.
                        tmpString = nextSibling.toString().trim();
                        // except that ISFDB uses 00 for the day/month when unknown ...
                        // e.g. "1975-04-00" or "1974-00-00" Cut that part off.
                        tmpString = UNKNOWN_M_D_LITERAL.matcher(tmpString).replaceAll("");
                        // and we're paranoid...
                        final LocalDateTime date = dateParser.parse(tmpString, getLocale(context));
                        if (date != null) {
                            // Note that partial dates, e.g. "1987", "1978-03"
                            // will get 'completed' to "1987-01-01", "1978-03-01"
                            // This should be acceptable IMHO.
                            bookData.putString(DBKey.DATE_BOOK_PUBLICATION,
                                               date.format(DateTimeFormatter.ISO_LOCAL_DATE));
                        }
                    }
                } else if ("ISBN:".equalsIgnoreCase(fieldName)) {
                    nextSibling = fieldLabelElement.nextSibling();
                    if (nextSibling != null) {
                        // we use them in the order found here.
                        //   <li><b>ISBN:</b> 0-00-712774-X [<small>978-0-00-712774-0</small>]
                        tmpString = nextSibling.toString().trim();
                        tmpString = ISBN.cleanText(tmpString);
                        if (!tmpString.isEmpty()) {
                            bookData.putString(DBKey.KEY_ISBN, tmpString);
                        }

                        final Element nextElementSibling = fieldLabelElement.nextElementSibling();
                        if (nextElementSibling != null) {
                            tmpString = nextElementSibling.text();
                            tmpString = ISBN.cleanText(tmpString);
                            if (!tmpString.isEmpty()) {
                                bookData.putString(SiteField.ISBN_2, tmpString);
                            }
                        }
                    }
                } else if ("Publisher:".equalsIgnoreCase(fieldName)) {
                    for (final Element a : li.select("a")) {
                        final Publisher publisher = Publisher.from(a.text());
                        // publisher.setIsfDbId(stripNumber(a.attr("href"), '?'));
                        mPublishers.add(publisher);
                    }
                } else if ("Pub. Series:".equalsIgnoreCase(fieldName)) {
                    for (final Element a : li.select("a")) {
                        final Series series = Series.from(a.text());
                        // series.setIsfDbId(stripNumber(a.attr("href"), '?'));
                        mSeries.add(series);
                    }
                } else if ("Pub. Series #:".equalsIgnoreCase(fieldName)) {
                    nextSibling = fieldLabelElement.nextSibling();
                    if (nextSibling != null) {
                        tmpString = nextSibling.toString().trim();
                        // assume that if we get here, then we added a "Pub. Series:" as last one.
                        mSeries.get(mSeries.size() - 1).setNumber(tmpString);
                    }
                } else if ("Price:".equalsIgnoreCase(fieldName)) {
                    nextSibling = fieldLabelElement.nextSibling();
                    if (nextSibling != null) {
                        tmpString = nextSibling.toString().trim();
                        final Money money = new Money(getLocale(context), tmpString);
                        if (money.getCurrencyCode() != null) {
                            bookData.putDouble(DBKey.PRICE_LISTED, money.doubleValue());
                            bookData.putString(DBKey.PRICE_LISTED_CURRENCY,
                                               money.getCurrencyCode());
                        } else {
                            bookData.putString(DBKey.PRICE_LISTED, tmpString);
                        }
                    }
                } else if ("Pages:".equalsIgnoreCase(fieldName)) {
                    nextSibling = fieldLabelElement.nextSibling();
                    if (nextSibling != null) {
                        tmpString = nextSibling.toString().trim();
                        bookData.putString(DBKey.KEY_PAGES, tmpString);
                    }
                } else if ("Format:".equalsIgnoreCase(fieldName)) {
                    // <li><b>Format:</b> <div class="tooltip">tp<sup class="mouseover">?</sup>
                    // <span class="tooltiptext tooltipnarrow">Trade paperback. bla bla...
                    // need to lift "tp".
                    final Element nextElementSibling = fieldLabelElement.nextElementSibling();
                    if (nextElementSibling != null) {
                        tmpString = nextElementSibling.ownText();
                        bookData.putString(DBKey.KEY_FORMAT, tmpString);
                    }

                } else if ("Type:".equalsIgnoreCase(fieldName)) {
                    nextSibling = fieldLabelElement.nextSibling();
                    if (nextSibling != null) {
                        // <li><b>Type:</b> COLLECTION
                        tmpString = nextSibling.toString().trim();
                        bookData.putString(SiteField.BOOK_TYPE, tmpString);
                        final Book.ContentType type = TYPE_MAP.get(tmpString);
                        if (type != null) {
                            bookData.putLong(DBKey.BITMASK_TOC, type.value);
                        }
                    }
                } else if ("Cover:".equalsIgnoreCase(fieldName)) {
                    final Elements as = li.select("a");
                    //TODO: if there are multiple art/artists... will this barf ?
                    // bookData.putString(SiteField.BOOK_COVER_ART_TXT, as.text());
                    if (as.size() > 1) {
                        // Cover artist
                        final Element a = as.get(1);
                        final Author author = Author.from(a.text());
                        author.setType(Author.TYPE_COVER_ARTIST);
                        // author.setIsfDbId(stripNumber(a.attr("href"),'?'));
                        mAuthors.add(author);
                    }

                } else if ("External IDs:".equalsIgnoreCase(fieldName)) {
                    // send the <ul> children
                    processExternalIdElements(li.select("ul li"), bookData);

                } else if ("Editors:".equalsIgnoreCase(fieldName)) {
                    for (final Element a : li.select("a")) {
                        final Author author = Author.from(a.text());
                        author.setType(Author.TYPE_EDITOR);
                        // author.setIsfDbId(stripNumber(a.attr("href"), '?'));
                        mAuthors.add(author);
                    }
                }
            } catch (@NonNull final IndexOutOfBoundsException e) {
                // does not happen now, but could happen if we come about non-standard entries,
                // or if ISFDB website changes
                Logger.error(TAG, e, "path: " + document.location() + "\n\nLI: " + li);
            }
        }

        // publication record.
        final Element recordIDDiv = contentBox.select("span.recordID").first();
        if (recordIDDiv != null) {
            tmpString = recordIDDiv.ownText();
            tmpString = ParseUtils.digits(tmpString);
            if (!tmpString.isEmpty()) {
                try {
                    final long record = Long.parseLong(tmpString);
                    bookData.putLong(DBKey.SID_ISFDB, record);
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
        }

        //ENHANCE: it would make much more sense to get the notes from the URL_TITLE_CGI page.
        // and if there are none, then fall back to the notes on this page.
        final Elements notesDiv = contentBox.select("div.notes");
        if (!notesDiv.isEmpty()) {
            tmpString = notesDiv.html();
            // it should always have this at the start, but paranoia...
            if (tmpString.startsWith("<b>Notes:</b>")) {
                tmpString = tmpString.substring(13).trim();
            }
            bookData.putString(DBKey.KEY_DESCRIPTION, tmpString);
        }

        // ISFDB does not offer the books language on the main page
        // (although they store it in their database).
        //ENHANCE: the site is adding language to the data; revisit. For now, default to English
        bookData.putString(DBKey.KEY_LANGUAGE, "eng");


        // post-process all found data.

        final ArrayList<TocEntry> toc = parseToc(context, document);
        if (!toc.isEmpty()) {
            // We always store the toc even if there is only a single entry.
            // ISFDB provides the *original* publication year in the toc which we want to preserve.
            bookData.putParcelableArrayList(Book.BKEY_TOC_LIST, toc);
            if (toc.size() > 1) {
                if (TocEntry.hasMultipleAuthors(toc)) {
                    bookData.putLong(DBKey.BITMASK_TOC, Book.ContentType.Anthology.value);
                } else {
                    bookData.putLong(DBKey.BITMASK_TOC, Book.ContentType.Collection.value);
                }
            }
        }

        // store accumulated ArrayList's *after* we parsed the TOC
        if (!mAuthors.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, mAuthors);
        }
        if (!mSeries.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_SERIES_LIST, mSeries);
        }
        if (!mPublishers.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, mPublishers);
        }

        checkForSeriesNameInTitle(bookData);

        // try to deduce the first publication date from the TOC
        if (toc.size() == 1) {
            // if the content table has only one entry,
            // then this will have the first publication year for sure
            tmpString = ParseUtils.digits(toc.get(0).getFirstPublicationDate().getIsoString());
            if (!tmpString.isEmpty()) {
                bookData.putString(DBKey.DATE_FIRST_PUBLICATION, tmpString);
            }
        } else if (toc.size() > 1) {
            // we gamble and take what we found while parsing the TOC (first entry with a year)
            if (mFirstPublicationYear != null) {
                bookData.putString(DBKey.DATE_FIRST_PUBLICATION, mFirstPublicationYear);
            }
        }

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            final String isbn = bookData.getString(DBKey.KEY_ISBN);
            final ArrayList<String> list = parseCovers(document, isbn, 0);
            if (!list.isEmpty()) {
                bookData.putStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0], list);
            }
        }
    }

    /**
     * Second ContentBox contains the TOC.
     * <pre>
     *     {@code
     *
     * <div class="ContentBox">
     *  <span class="containertitle">Collection Title:</span>
     *  <a href="http://www.isfdb.org/cgi-bin/title.cgi?37576">
     *      The Days of Perky Pat
     *  </a> &#8226;
     *  [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?22461">
     *      The Collected Stories of Philip K. Dick</a> &#8226; 4] &#8226; (1987) &#8226;
     *      collection by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23"
     *     >Philip K. Dick</a>
     *  <h2>Contents <a href="http://www.isfdb.org/cgi-bin/pl.cgi?230949+c">
     *      <span class="listingtext">(view Concise Listing)</span></a></h2>
     *  <ul>
     *
     * <li> == entry
     *
     * }
     * </pre>
     *
     * @param context  Current context
     * @param document to parse
     *
     * @return the toc list
     */
    @WorkerThread
    private ArrayList<TocEntry> parseToc(@NonNull final Context context,
                                         @NonNull final Document document) {

        final boolean addSeriesFromToc = ServiceLocator.getGlobalPreferences()
                                                       .getBoolean(PK_SERIES_FROM_TOC, false);
        final ArrayList<TocEntry> toc = new ArrayList<>();

        // <div class="ContentBox"> but there are two, so get last one
        final Element contentBox = document.select(CSS_Q_DIV_CONTENTBOX).last();
        if (contentBox != null) {
            for (final Element li : contentBox.select("li")) {

            /* LI entries, possibilities:
            7
            &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?118799">
                Introduction (The Days of Perky Pat)</a>
            &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?31226">
                Introductions to the Collected Stories of Philip K. Dick</a> &#8226; 4]
            &#8226; (1987)
            &#8226; essay by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?57">James Tiptree, Jr.</a>

            11
            &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?53646">Autofac</a>
            &#8226; (1955)
            &#8226; novelette by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23">
                Philip K. Dick</a>

            <a href="http://www.isfdb.org/cgi-bin/title.cgi?41613">Beyond Lies the Wub</a>
            &#8226; (1952)
            &#8226; short story by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23">
                Philip K. Dick</a>

            <a href="http://www.isfdb.org/cgi-bin/title.cgi?118803">
            Introduction (Beyond Lies the Wub)</a>
            &#8226; [ <a href="http://www.isfdb.org/cgi-bin/pe.cgi?31226">
            Introductions to the Collected Stories of Philip K. Dick</a> &#8226; 1]
            &#8226; (1987)
            &#8226; essay by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?69">Roger Zelazny</a>

            61
            &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?417331">
                That Thou Art Mindful of Him</a>
            &#8226; (1974)
            &#8226; novelette by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?5">Isaac Asimov</a>
            (variant of <i><a href="http://www.isfdb.org/cgi-bin/title.cgi?50798">
                —That Thou Art Mindful of Him!</a></i>)

            A book belonging to a Series will have one content entry with the same title
            as the book, and potentially have the Series/nr in it:

            <a href="http://www.isfdb.org/cgi-bin/title.cgi?2210372">
                The Delirium Brief</a>
            &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?23081">
                Laundry Files</a> &#8226; 8]
            &#8226; (2017)
            &#8226; novel by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?2200">Charles Stross</a>

            ENHANCE: type of entry: "short story", "novelette", "essay", "novel"
            ENHANCE: if type "novel" -> *that* is the one to use for the first publication year

            2019-07: translation information seems to be added,
            and a further sub-classification (here: 'juvenile')

            <a href="http://www.isfdb.org/cgi-bin/title.cgi?1347238">
                Zwerftocht Tussen de Sterren</a>
            &#8226; juvenile
            &#8226; (1973)
            &#8226; novel by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?29">Robert A. Heinlein</a>
            (trans. of <a href="http://www.isfdb.org/cgi-bin/title.cgi?2233">
                <i>Citizen of the Galaxy</i></a> 1957)

            2019-09-26: this has been there for a longer time, but just noticed these:
            ISBN: 90-290-1541-1
            7 • Één Nacht per Jaar • interior artwork by John Stewart
            9 • Één Nacht per Jaar • [Cyrion] • novelette by Tanith Lee
                (trans. of One Night of the Year 1980)
            39 • Aaches Geheim • interior artwork by Jim Pitts
            41 • Aaches Geheim • [Dilvish] • short story by Roger Zelazny
                (trans. of The Places of Aache 1980)

            iow: each story appears twice due to the extra interior artwork.
            For now, we will get two entries in the TOC, same title but different author.
            TODO: avoid duplicate TOC entries when there are two lines.
             */
                final String liAsString = li.toString();
                String title = null;
                Author author = null;
                // find the first occurrence of each
                for (final Element a : li.select("a")) {
                    final String href = a.attr("href");

                    if (title == null && href.contains(CGI_TITLE)) {
                        title = ParseUtils.cleanName(a.text());
                        //ENHANCE: tackle 'variant' titles later

                    } else if (author == null && href.contains(CGI_EA)) {
                        author = Author.from(ParseUtils.cleanName(a.text()));

                    } else if (addSeriesFromToc && href.contains(CGI_PE)) {
                        final Series series = Series.from(a.text());

                        //  • 4] • (1987) • novel by
                        final Node nextSibling = a.nextSibling();
                        if (nextSibling != null) {
                            String nr = nextSibling.toString();
                            final int dotIdx = nr.indexOf('•');
                            if (dotIdx != -1) {
                                final int closeBrIdx = nr.indexOf(']');
                                if (closeBrIdx > dotIdx) {
                                    nr = nr.substring(dotIdx + 1, closeBrIdx).trim();
                                    if (!nr.isEmpty()) {
                                        series.setNumber(nr);
                                    }
                                }
                            }
                        }
                        mSeries.add(series);
                    }
                }

                // no author found, set to 'unknown, unknown'
                // example when this happens: ISBN=044100590X
                // <li> 475 • <a href="http://www.isfdb.org/cgi-bin/title.cgi?1659151">
                //      Appendixes (Dune)</a> • essay by uncredited
                // </li>
                if (author == null) {
                    author = Author.createUnknownAuthor(context);
                }
                // very unlikely
                if (title == null) {
                    title = "";
                }

                // scan for first occurrence of "• (1234)"
                final Matcher matcher = YEAR_PATTERN.matcher(liAsString);
                final String year = matcher.find() ? matcher.group(2) : "";
                // see if we can use it as the first publication year for the book.
                // i.e. if this entry has the same title as the book title
                if ((mFirstPublicationYear == null || mFirstPublicationYear.isEmpty())
                    && title.equalsIgnoreCase(mTitle)) {
                    mFirstPublicationYear = ParseUtils.digits(year);
                }

                final TocEntry tocEntry = new TocEntry(author, title, year);
                toc.add(tocEntry);
            }
        }
        return toc;
    }

    /**
     * Parses the downloaded {@link Document} for the cover and fetches it when present.
     *
     * @param document to parse
     * @param isbn     (optional) ISBN of the book, will be used for the cover filename
     * @param cIdx     0..n image index
     */
    @WorkerThread
    @VisibleForTesting
    @NonNull
    private ArrayList<String> parseCovers(@NonNull final Document document,
                                          @Nullable final String isbn,
                                          @SuppressWarnings("SameParameterValue")
                                          @IntRange(from = 0, to = 1) final int cIdx)
            throws DiskFullException, CoverStorageException {
        /* First "ContentBox" contains all basic details.
         * <pre>
         *   {@code
         *     <div class="ContentBox">
         *       <table>
         *       <tr class="scan">
         *         <td>
         *           <a href="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg">
         *              <img src="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg"
         *                  alt="picture" class="scan">
         *           </a>
         *         </td>
         *         ...
         *     }
         * </pre>
         *
         * These can be external urls as well. Example:
         * <pre>
         *   {@code
         *      <a href="http://ecx.images-amazon.com/images/I/51j867aTc0L.jpg">
         *          <img src="http://ecx.images-amazon.com/images/I/51j867aTc0L.jpg"
         *              alt="picture" class="scan">
         *      </a>
         *     }
         * </pre>
         */

        final ArrayList<String> imageList = new ArrayList<>();

        final Element contentBox = document.selectFirst(CSS_Q_DIV_CONTENTBOX);
        if (contentBox != null) {
            final Element img = contentBox.selectFirst("img");
            if (img != null) {
                final String url = img.attr("src");
                final String fileSpec = saveImage(url, isbn, cIdx, null);
                if (fileSpec != null) {
                    imageList.add(fileSpec);
                }
            }
        }
        return imageList;
    }


    /**
     * Get the list with {@link Edition} information for the given url.
     *
     * @param context Current context
     * @param url     A fully qualified ISFDB search url
     *
     * @return list of editions found, can be empty, but never {@code null}
     */
    @WorkerThread
    @NonNull
    private List<Edition> fetchEditions(@NonNull final Context context,
                                        @NonNull final String url)
            throws SearchException, CredentialsException {

        final Document document = loadDocument(context, url);
        if (!isCancelled()) {
            return parseEditions(document);
        }
        return new ArrayList<>();
    }

    /**
     * Get the list with {@link Edition} information for the given isbn.
     *
     * @param context   Current context
     * @param validIsbn to get editions for. MUST be valid.
     *
     * @return list of editions found, can be empty, but never {@code null}
     */
    @WorkerThread
    @NonNull
    List<Edition> fetchEditionsByIsbn(@NonNull final Context context,
                                      @NonNull final String validIsbn)
            throws SearchException, CredentialsException {
        mIsbn = validIsbn;

        final String url = getSiteUrl() + String.format(CGI_EDITIONS, validIsbn);
        return fetchEditions(context, url);
    }

    /**
     * Parses the downloaded {@link Document} for the edition list.
     *
     * @param document to parse
     *
     * @return list of editions found, can be empty, but never {@code null}
     */
    @NonNull
    @VisibleForTesting
    List<Edition> parseEditions(@NonNull final Document document) {

        final List<Edition> editions = new ArrayList<>();

        final String pageUrl = document.location();

        if (pageUrl.contains(CGI_PL)) {
            // We got redirected to a book. Populate with the doc (web page) we got back.
            editions.add(new Edition(stripNumber(pageUrl, '?'), mIsbn, document));

        } else if (pageUrl.contains(CGI_TITLE)
                   || pageUrl.contains(CGI_SE)
                   || pageUrl.contains(CGI_ADV_SEARCH_RESULTS)) {
            // example: http://www.isfdb.org/cgi-bin/title.cgi?11169
            // we have multiple editions. We get here from one of:
            // - direct link to the "title" of the publication; i.e. 'show the editions'
            // - search or advanced-search for the title.

            final Element publications = document.selectFirst("table.publications");
            if (publications != null) {
                // first edition line is a "tr.table1", 2nd "tr.table0", 3rd "tr.table1" etc...
                final Elements oddEntries = publications.select("tr.table1");
                final Elements evenEntries = publications.select("tr.table0");

                // combine them in a sorted list
                final Collection<Element> entries = new Elements();
                int i = 0;
                while (i < oddEntries.size() && i < evenEntries.size()) {
                    entries.add(oddEntries.get(i));
                    entries.add(evenEntries.get(i));
                    i++;
                }

                // either odd or even list might have another element.
                if (i < oddEntries.size()) {
                    entries.add(oddEntries.get(i));
                } else if (i < evenEntries.size()) {
                    entries.add(evenEntries.get(i));
                }

                for (final Element tr : entries) {
                    // 1st column: Title == the book link
                    final Element edLink = tr.child(0).select("a").first();
                    if (edLink != null) {
                        final String url = edLink.attr("href");
                        if (!url.isEmpty()) {
                            String isbnStr = null;
                            // 4th column: the ISBN/Catalog ID.
                            final String catNr = tr.child(4).text();
                            if (catNr.length() > 9) {
                                final ISBN isbn = ISBN.createISBN(catNr);
                                if (isbn.isValid(true)) {
                                    isbnStr = isbn.asText();
                                }
                            }

                            editions.add(new Edition(stripNumber(url, '?'), isbnStr));
                        }
                    }
                }
            }

        } else {
            // dunno, let's log it
            Logger.warn(TAG, "parseDoc|pageUrl=" + pageUrl);
        }

        return editions;
    }


    /**
     * Fetch a book.
     *
     * @param context     Current context
     * @param edition     to get
     * @param fetchCovers Set to {@code true} if we want to get covers
     * @param bookData    Bundle to update <em>(passed in to allow mocking)</em>
     */
    @WorkerThread
    void fetchByEdition(@NonNull final Context context,
                        @NonNull final Edition edition,
                        @NonNull final boolean[] fetchCovers,
                        @NonNull final Bundle bookData)
            throws DiskFullException, CoverStorageException, SearchException, CredentialsException {

        final Document document = loadDocumentByEdition(context, edition);
        if (!isCancelled()) {
            parse(context, document, fetchCovers, bookData);
        }
    }

    @NonNull
    private Document loadDocumentByEdition(@NonNull final Context context,
                                           @NonNull final Edition edition)
            throws SearchException, CredentialsException {

        // check if we already got the page
        final Document document = edition.getDocument();
        if (document != null) {
            return document;
        }

        // go get it.
        final String url = getSiteUrl() + String.format(CGI_BY_EXTERNAL_ID, edition.getIsfdbId());
        return loadDocument(context, url);
    }


    /**
     * All lines are normally.
     * {@code
     * <li> <abbr class="template" title="Online Computer Library Center">OCLC/WorldCat</abbr>:
     * <a href="http://www.worldcat.org/oclc/963112443">963112443</a>}
     * Except for Amazon:
     * {@code
     * <li><abbr class="template" ... >ASIN</abbr>:  B003ODIWEG
     * (<a href="https://www.amazon.com.au/dp/B003ODIWEG">AU</a>
     * <a href="https://www.amazon.com.br/dp/B003ODIWEG">BR</a>
     * <a href="https://www.amazon.ca/dp/B003ODIWEG">CA</a>
     * <a href="https://www.amazon.cn/dp/B003ODIWEG">CN</a>
     * <a href="https://www.amazon.de/dp/B003ODIWEG">DE</a>
     * <a href="https://www.amazon.es/dp/B003ODIWEG">ES</a>
     * <a href="https://www.amazon.fr/dp/B003ODIWEG">FR</a>
     * <a href="https://www.amazon.in/dp/B003ODIWEG">IN</a>
     * <a href="https://www.amazon.it/dp/B003ODIWEG">IT</a>
     * <a href="https://www.amazon.co.jp/dp/B003ODIWEG">JP</a>
     * <a href="https://www.amazon.com.mx/dp/B003ODIWEG">MX</a>
     * <a href="https://www.amazon.nl/dp/B003ODIWEG">NL</a>
     * <a href="https://www.amazon.co.uk/dp/B003ODIWEG">UK</a>
     * <a href="https://www.amazon.com/dp/B003ODIWEG?">US</a>)}
     * <p>
     * So for Amazon we only get a single link which is ok as the ASIN is the same in all.
     *
     * @param elements LI elements
     * @param bookData Bundle to update
     */
    private void processExternalIdElements(@NonNull final Collection<Element> elements,
                                           @NonNull final Bundle bookData) {
        elements.stream()
                .map(element -> element.select("a").first())
                .filter(Objects::nonNull)
                .map(element -> element.attr("href"))
                .filter(Objects::nonNull)
                .forEach(url -> {
                    if (url.contains("www.worldcat.org")) {
                        // http://www.worldcat.org/oclc/60560136
                        bookData.putString(DBKey.SID_OCLC, stripString(url, '/'));

                    } else if (url.contains("amazon")) {
                        final int start = url.lastIndexOf('/');
                        if (start != -1) {
                            int end = url.indexOf('?', start);
                            if (end == -1) {
                                end = url.length();
                            }
                            final String asin = url.substring(start + 1, end);
                            bookData.putString(DBKey.SID_ASIN, asin);
                        }
                    } else if (url.contains("lccn.loc.gov")) {
                        // Library of Congress (USA)
                        // http://lccn.loc.gov/2008299472
                        // http://lccn.loc.gov/95-22691
                        bookData.putString(DBKey.SID_LCCN, stripString(url, '/'));

                        //            } else if (url.contains("explore.bl.uk")) {
                        // http://explore.bl.uk/primo_library/libweb/action/dlDisplay.do?
                        // vid=BLVU1&docId=BLL01014057142
                        // British Library

                        //            } else if (url.contains("d-nb.info")) {
                        // http://d-nb.info/986851329
                        // DEUTSCHEN NATIONALBIBLIOTHEK

                        //            } else if (url.contains("picarta.pica.nl")) {
                        // http://picarta.pica.nl/xslt/DB=3.9/XMLPRS=Y/PPN?PPN=802041833
                        // Nederlandse Bibliografie


                        //           } else if (url.contains("tercerafundacion.net")) {
                        // Spanish
                        // https://tercerafundacion.net/biblioteca/ver/libro/2329
                    }
                });
    }

    @NonNull
    private String stripString(@NonNull final String url,
                               @SuppressWarnings("SameParameterValue") final char last) {
        final int index = url.lastIndexOf(last) + 1;
        if (index == 0) {
            return "";
        }

        return url.substring(index);
    }

    /**
     * A url ends with 'last'123.  Strip and return the '123' part.
     *
     * @param url  to handle
     * @param last character to look for as last-index
     *
     * @return the number
     */
    private long stripNumber(@NonNull final String url,
                             @SuppressWarnings("SameParameterValue") final char last) {
        final int index = url.lastIndexOf(last) + 1;
        if (index == 0) {
            return 0;
        }

        return Long.parseLong(url.substring(index));
    }

    // Experimenting with the REST API... to limited for full use for now.
    @VisibleForTesting
    @NonNull
    public Bundle xmlSearchByExternalId(@NonNull final Context context,
                                        @NonNull final String externalId,
                                        @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException {

        // getpub_by_internal_ID.cgi
        //   calls this server code:
        // def SQLGetPubById(id):
        //	query = "select * from pubs where pub_id = '%d'" % (int(id))
        //	db.query(query)
        //	result = db.store_result()
        //	if result.num_rows() > 0:
        //		pub = result.fetch_row()
        //		return pub[0]
        //	else:
        //		return 0
        final String url = getSiteUrl() + String.format(REST_BY_EXTERNAL_ID, externalId);
        final List<Bundle> bookData = fetchPublications(context, url, fetchCovers, 1);
        if (bookData.isEmpty()) {
            return newBundleInstance();
        } else {
            return bookData.get(0);
        }
    }

    /**
     * Fetch a (list of) publications by REST-url which returns an xml doc.
     *
     * @param context     Current context
     * @param url         to fetch
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param maxRecords  the maximum number of "Publication" records to fetch
     */
    @NonNull
    private List<Bundle> fetchPublications(@NonNull final Context context,
                                           @NonNull final String url,
                                           @NonNull final boolean[] fetchCovers,
                                           @SuppressWarnings("SameParameterValue")
                                           final int maxRecords)
            throws StorageException, SearchException {

        final IsfdbPublicationListHandler listHandler =
                new IsfdbPublicationListHandler(this,
                                                fetchCovers, maxRecords,
                                                getLocale(context));

        final SAXParserFactory factory = SAXParserFactory.newInstance();

        try {
            final SAXParser parser = factory.newSAXParser();
            try (TerminatorConnection con = createConnection(url)) {
                parser.parse(con.getInputStream(), listHandler);
            }

            if (isCancelled()) {
                return new ArrayList<>();
            }

        } catch (@NonNull final SAXException e) {
            // unwrap SAXException if possible
            final Exception embedded = e.getException();
            if (embedded instanceof EOFException) {
                // not an error; we're done.
                return listHandler.getResult();

            } else if (embedded instanceof StorageException) {
                throw (StorageException) embedded;
            } else {
                throw new SearchException(getName(context), e);
            }

        } catch (@NonNull final ParserConfigurationException | IOException e) {
            throw new SearchException(getName(context), e);
        }

        return listHandler.getResult();
    }


    /**
     * ISFDB specific field names we add to the bundle based on parsed XML data.
     */
    static final class SiteField {
        // private static final String AUTHOR_ID = "__ISFDB_AUTHORS_ID";
        // private static final String SERIES_ID = "__ISFDB_SERIES_ID";
        // private static final String PUBLISHER_ID = "__ISFDB_PUBLISHER_ID";
        // private static final String EDITORS_ID = "__ISFDB_EDITORS_ID";
        // private static final String BOOK_COVER_ARTIST_ID = "__ISFDB_BOOK_COVER_ARTIST_ID";
        // private static final String BOOK_COVER_ART_TXT = "__BOOK_COVER_ART_TXT";

        static final String BOOK_TYPE = "__ISFDB_BOOK_TYPE";
        static final String ISBN_2 = "__ISFDB_ISBN2";
        static final String BOOK_TAG = "__TAG";

        private SiteField() {
        }
    }
}
