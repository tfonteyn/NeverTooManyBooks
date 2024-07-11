/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEditionIsbn;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.xml.sax.SAXException;

/**
 * See notes in the package-info.java file.
 */
public class IsfdbSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByText,
                   SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId,
                   SearchEngine.ViewBookByExternalId,
                   SearchEngine.CoverByEdition,
                   SearchEngine.AlternativeEditions<AltEditionIsfdb> {

    /** Preferences - Type: {@code boolean}. */
    public static final String PK_USE_PUBLISHER = EngineId.Isfdb.getPreferenceKey()
                                                  + ".search.uses.publisher";

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
    /** Preferences - Type: {@code boolean}. */
    static final String PK_SERIES_FROM_TOC = EngineId.Isfdb.getPreferenceKey()
                                             + ".search.toc.series";
    /** Log tag. */
    private static final String TAG = "IsfdbSearchEngine";

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
    private static final String CGI_ADV_SEARCH_PREFIX = CGI_BIN + CGI_ADV_SEARCH_RESULTS + "?"
                                                        + "ORDERBY=pub_title"
                                                        + "&ACTION=query"
                                                        + "&START=0"
                                                        + "&TYPE=Publication"
                                                        + "&C=AND";

    /** Param 1: external book ID. */
    private static final String CGI_BY_EXTERNAL_ID = CGI_BIN + CGI_PL + "?%1$s";
    /** Search URL template. */
    private static final String CGI_EDITIONS = CGI_BIN + CGI_SE + "?arg=%s&type=ISBN";
    /** View in browser. */
    private static final String CGI_BROWSER = CGI_BIN + CGI_PL + "?";

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

    /**
     * We TRY to get the books language, but this is not always possible.
     * For those occasions, default the English.
     */
    private static final String LANGUAGE_DEFAULT = "eng";

    /** Format string for searches. */
    private static final String USE = "&USE_%1$s=%2$s&O_%1$s=contains&TERM_%1$s=%3$s";

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
    private String bookTitle;
    /** with some luck we'll get these as well. */
    @Nullable
    private PartialDate firstPublicationYear;
    /** The ISBN we searched for. Not guaranteed to be identical to the book we find. */
    private String searchForIsbn;
    @Nullable
    private FutureHttpGet<Boolean> futureHttpGet;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public IsfdbSearchEngine(@NonNull final Context appContext,
                             @NonNull final SearchEngineConfig config) {
        super(appContext, config, CHARSET_DECODE_PAGE);
    }

    @NonNull
    @Override
    public String createBrowserUrl(@NonNull final Context context,
                                   @NonNull final String externalId) {
        return getHostUrl(context) + CGI_BROWSER + externalId;
    }

    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final String externalId,
                                   @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        final String url = getHostUrl(context) + String.format(CGI_BY_EXTERNAL_ID, externalId);

        // added due to https://github.com/square/okhttp/issues/1517
        // it's a server issue, this is a workaround.
        final Document document = loadDocument(context, url,
                                               Map.of(HttpConstants.CONNECTION,
                                                      HttpConstants.CONNECTION_CLOSE));
        if (!isCancelled()) {
            parse(context, document, fetchCovers, book);
            // ISFDB only shows the books language on the publications page.
            // We use that page in all other searches.
            // However when searching by their native id, we're not visiting that page.
            // Default to English...
            if (!book.contains(DBKey.LANGUAGE)) {
                book.putString(DBKey.LANGUAGE, LANGUAGE_DEFAULT);
            }
        }
        return book;
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        final List<AltEditionIsfdb> editions = fetchEditionsByIsbn(context, validIsbn);
        if (!editions.isEmpty()) {
            fetchByEdition(context, editions.get(0), fetchCovers, book);
        }
        return book;
    }

    @NonNull
    @Override
    @WorkerThread
    public Book search(@NonNull final Context context,
                       @Nullable final /* not supported */ String code,
                       @Nullable final String author,
                       @Nullable final String title,
                       @Nullable final String publisher,
                       @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final String url = getHostUrl(context) + CGI_ADV_SEARCH_PREFIX;

        int index = 0;
        String args = "";
        final Book book = new Book();

        //noinspection OverlyBroadCatchBlock
        try {
            if (author != null && !author.isEmpty()) {
                index++;
                args += String.format(USE, index, "author_canonical",
                                      URLEncoder.encode(author, CHARSET_ENCODE_URL));
                // "&USE_" + index + "=author_canonical"
                // + "&O_" + index + "=contains"
                // + "&TERM_" + index + "=" + URLEncoder.encode(author, CHARSET_ENCODE_URL);
            }

            if (title != null && !title.isEmpty()) {
                index++;
                args += String.format(USE, index, "pub_title",
                                      URLEncoder.encode(title, CHARSET_ENCODE_URL));
                // "&USE_" + index + "=pub_title"
                // + "&O_" + index + "=contains"
                // + "&TERM_" + index + "=" + URLEncoder.encode(title, CHARSET_ENCODE_URL);
            }

            // as per user settings.
            if (PreferenceManager.getDefaultSharedPreferences(context)
                                 .getBoolean(PK_USE_PUBLISHER, false)) {
                if (publisher != null && !publisher.isEmpty()) {
                    index++;
                    args += String.format(USE, index, "pub_publisher",
                                          URLEncoder.encode(publisher, CHARSET_ENCODE_URL));
                    // "&USE_" + index + "=pub_publisher"
                    // + "&O_" + index + "=contains"
                    // + "&TERM_" + index + "=" + URLEncoder.encode(publisher, CHARSET_ENCODE_URL);
                }
            }

            // there is support for up to 6 search terms.
            // &USE_4=pub_title&O_4=exact&TERM_4=
            // &USE_5=pub_title&O_5=exact&TERM_5=
            // &USE_6=pub_title&O_6=exact&TERM_6=

            // sanity check: any data to search for?
            if (!args.isEmpty()) {
                final List<AltEditionIsfdb> editions = fetchEditions(context, url + args);
                if (!editions.isEmpty()) {
                    fetchByEdition(context, editions.get(0), fetchCovers, book);
                }
            }
        } catch (@NonNull final IOException e) {
            throw new SearchException(getEngineId(), e);
        }
        return book;
    }

    /**
     * Search for edition data.
     * <p>
     * <strong>Note:</strong> we assume the isbn numbers retrieved from the site are valid.
     * No extra checks are made at this point.
     *
     * <br>{@inheritDoc}
     */
    @NonNull
    @Override
    public List<AltEditionIsfdb> searchAlternativeEditions(@NonNull final Context context,
                                                           @NonNull final String validIsbn)
            throws SearchException, CredentialsException {

        // We strip the potential document (which can be large)
        // as the caller does not use it for now
        final List<AltEditionIsfdb> list = fetchEditionsByIsbn(context, validIsbn);
        list.forEach(AltEditionIsfdb::clearDocument);

        return list;
    }

    @NonNull
    public Optional<String> searchCoverByEdition(@NonNull final Context context,
                                                 @NonNull final AltEdition altEdition,
                                                 @IntRange(from = 0, to = 1) final int cIdx,
                                                 @Nullable final Size size)
            throws SearchException, CredentialsException, StorageException {

        if (altEdition instanceof AltEditionIsfdb) {
            final AltEditionIsfdb edition = (AltEditionIsfdb) altEdition;
            final long isfdbId = edition.getIsfdbId();

            // The id should always be valid, but paranoia...
            if (isfdbId > 0) {
                final Document document = loadDocumentByEdition(context, edition);
                if (!isCancelled()) {
                    return parseCovers(context, document, String.valueOf(isfdbId), cIdx)
                            // let the system resolve any path variations
                            .map(fileSpec -> new File(fileSpec).getAbsolutePath());
                }
            }
        } else if (altEdition instanceof AltEditionIsbn) {
            final AltEditionIsbn edition = (AltEditionIsbn) altEdition;
            final String isbn = edition.getIsbn();

            final List<AltEditionIsfdb> editions = fetchEditionsByIsbn(context, isbn);
            if (!editions.isEmpty() && !isCancelled()) {
                // Grab the first edition found and search again by isfdb id
                return searchCoverByEdition(context, editions.get(0), cIdx, size);
            }
        }

        return Optional.empty();
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
     * @param context           Current context
     * @param document          to parse
     * @param book              Bundle to update
     * @param partialDateParser we can use
     *
     * @return the toc list
     */
    @WorkerThread
    @NonNull
    private List<TocEntry> parseToc(@NonNull final Context context,
                                    @NonNull final Document document,
                                    @NonNull final Book book,
                                    @NonNull final PartialDateParser partialDateParser) {

        final boolean addSeriesFromToc = PreferenceManager.getDefaultSharedPreferences(context)
                                                          .getBoolean(PK_SERIES_FROM_TOC, false);
        final List<TocEntry> toc = new ArrayList<>();

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
                &#8226; essay by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?57">
                    James Tiptree, Jr.</a>

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
                &#8226; novelette by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?5">
                    Isaac Asimov</a>
                (variant of <i><a href="http://www.isfdb.org/cgi-bin/title.cgi?50798">
                    —That Thou Art Mindful of Him!</a></i>)

                A book belonging to a Series will have one content entry with the same title
                as the book, and potentially have the Series/nr in it:

                <a href="http://www.isfdb.org/cgi-bin/title.cgi?2210372">
                    The Delirium Brief</a>
                &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?23081">
                    Laundry Files</a> &#8226; 8]
                &#8226; (2017)
                &#8226; novel by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?2200">
                    Charles Stross</a>

                ENHANCE: type of entry: "short story", "novelette", "essay", "novel"
                ENHANCE: if type "novel" -> *that* is the one to use for the first publication year

                2019-07: translation information seems to be added,
                and a further sub-classification (here: 'juvenile')

                <a href="http://www.isfdb.org/cgi-bin/title.cgi?1347238">
                    Zwerftocht Tussen de Sterren</a>
                &#8226; juvenile
                &#8226; (1973)
                &#8226; novel by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?29">
                    Robert A. Heinlein</a>
                (trans. of <a href="http://www.isfdb.org/cgi-bin/title.cgi?2233">
                    <i>Citizen of the Galaxy</i></a> 1957)

                2019-09-26: this has been there for a longer time, but just noticed these:
                ISBN: 90-290-1541-1
                7 • Één Nacht per Jaar • interior artwork by John Stewart
                9 • Één Nacht per Jaar • [Cyrion] • novelette by Tanith Lee
                    (trans. of One Night of the Year 1980)
                39 • Aaches Geheim • interior artwork by Jim Pitts
                41 • Aaches Geheim • [Dilvish] • short story by Roger Zelazny
                    (trans. of The Places of Aache 1980)

                iow: each story appears twice due to the extra interior artwork.
                For now, we will get two entries in the TOC, same title but different author.
                ENHANCE: avoid duplicate TOC entries when there are two lines.
                         This will require that a TOCEntry can have multiple authors.
                 */
                final String liAsString = li.toString();
                String title = null;
                Author author = null;
                // find the first occurrence of each
                for (final Element a : li.select("a")) {
                    final String href = a.attr("href");

                    if (title == null && href.contains(CGI_TITLE)) {
                        title = SearchEngineUtils.cleanName(a.text());
                        //ENHANCE: tackle 'variant' titles later

                    } else if (author == null && href.contains(CGI_EA)) {
                        author = Author.from(SearchEngineUtils.cleanName(a.text()));

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
                        book.add(series);
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

                //TODO: using similar logic as for the year (here below),
                // we could scan for a translation/language:
                // "trans. of abc def 1976)" and use it as the DBKey.TITLE_ORIGINAL_LANG

                // scan for first occurrence of "• (1234)"
                final Matcher matcher = YEAR_PATTERN.matcher(liAsString);
                @NonNull
                PartialDate tocDate = PartialDate.NOT_SET;
                if (matcher.find()) {
                    final String yearStr = SearchEngineUtils.digits(matcher.group(2));
                    final Optional<PartialDate> oTocDate = partialDateParser.parse(yearStr, false);
                    if (oTocDate.isPresent()) {
                        tocDate = oTocDate.get();
                        // If this entry has the same title as the book title
                        // and we did not find the date in a previous toc entry,
                        // use it as the first publication year for the book.
                        if (title.equalsIgnoreCase(bookTitle) && firstPublicationYear == null) {
                            firstPublicationYear = tocDate;
                        }
                    }
                }

                toc.add(new TocEntry(author, title, tocDate));
            }
        }
        return toc;
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
     * Parses the downloaded {@link org.jsoup.nodes.Document}.
     * We only parse the <strong>first book</strong> found.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
     *
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     *                              This should only occur if the engine calls/relies on
     *                              secondary sites.
     */
    @WorkerThread
    @VisibleForTesting
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        final Elements allContentBoxes = document.select(CSS_Q_DIV_CONTENTBOX);
        // sanity check
        if (allContentBoxes.isEmpty()) {
            LoggerFactory.getLogger().w(TAG, "parse|no contentbox found",
                                        "document.location()=" + document.location());
            return;
        }

        final Locale siteLocale = getLocale(context);
        final PartialDateParser partialDateParser = new PartialDateParser();

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

            //noinspection ProhibitedExceptionCaught
            try {
                String fieldName = null;

                // We want the first 'bold' child Element of the li; e.g. "<b>Publisher:</b>"
                final Element labelElement = li.selectFirst("b");
                if (labelElement != null) {
                    fieldName = labelElement.text();
                }

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB) {
                    final Logger logger = LoggerFactory.getLogger();
                    if (fieldName == null) {
                        logger.d(TAG, "fetch", "li=" + li);
                    } else {
                        logger.d(TAG, "fetch", "fieldName=" + fieldName);
                    }
                }

                if (fieldName != null) {
                    switch (fieldName) {
                        case "Publication:": {
                            nextSibling = labelElement.nextSibling();
                            if (nextSibling != null) {
                                bookTitle = nextSibling.toString().trim();
                                book.putString(DBKey.TITLE, bookTitle);
                            }
                            break;
                        }
                        case "Author:":
                        case "Authors:": {
                            for (final Element a : li.select("a")) {
                                addAuthor(Author.from(a.text()), Author.TYPE_UNKNOWN, book);
                            }
                            break;
                        }
                        case "Date:": {
                            nextSibling = labelElement.nextSibling();
                            if (nextSibling != null) {
                                // dates are in fact displayed as YYYY-MM-DD which is very nice.
                                tmpString = nextSibling.toString().trim();
                                // except that ISFDB uses 00 for the day/month when unknown ...
                                // e.g. "1975-04-00" or "1974-00-00"
                                // Cut those parts off.
                                tmpString = UNKNOWN_M_D_LITERAL.matcher(tmpString).replaceAll("");
                                partialDateParser.parse(tmpString, false)
                                                 .ifPresent(book::setPublicationDate);
                            }
                            break;
                        }
                        case "ISBN:": {
                            nextSibling = labelElement.nextSibling();
                            if (nextSibling != null) {
                                // we use them in the order found here.
                                // <b>ISBN:</b> 0-00-712774-X [<small>978-0-00-712774-0</small>]
                                tmpString = nextSibling.toString().trim();
                                tmpString = ISBN.cleanText(tmpString);
                                if (!tmpString.isEmpty()) {
                                    book.putString(DBKey.BOOK_ISBN, tmpString);
                                }

                                final Element nextElementSibling =
                                        labelElement.nextElementSibling();
                                if (nextElementSibling != null) {
                                    tmpString = nextElementSibling.text();
                                    tmpString = ISBN.cleanText(tmpString);
                                    if (!tmpString.isEmpty()) {
                                        book.putString(SiteField.ISBN_2, tmpString);
                                    }
                                }
                            }
                            break;
                        }
                        case "Publisher:": {
                            for (final Element a : li.select("a")) {
                                final Publisher publisher = Publisher.from(a.text());
                                book.add(publisher);
                            }
                            break;
                        }
                        case "Pub. Series:": {
                            for (final Element a : li.select("a")) {
                                final Series series = Series.from(a.text());
                                book.add(series);
                            }
                            break;
                        }
                        case "Pub. Series #:": {
                            nextSibling = labelElement.nextSibling();
                            if (nextSibling != null) {
                                tmpString = nextSibling.toString().trim();
                                // assume that if we get here,
                                // then we added a "Pub. Series:" as last one.
                                final List<Series> seriesList = book.getSeries();
                                seriesList.get(seriesList.size() - 1).setNumber(tmpString);
                            }
                            break;
                        }
                        case "Price:": {
                            final Element nextElementSibling = labelElement.nextElementSibling();
                            if (nextElementSibling != null) {
                                tmpString = nextElementSibling.ownText();
                                if (!tmpString.isEmpty()) {
                                    addPriceListed(context, siteLocale, tmpString, null, book);
                                }
                            }
                            break;
                        }
                        case "Pages:": {
                            nextSibling = labelElement.nextSibling();
                            if (nextSibling != null) {
                                tmpString = nextSibling.toString().trim();
                                book.putString(DBKey.PAGE_COUNT, tmpString);
                            }
                            break;
                        }
                        case "Format:": {
                            // <li><b>Format:</b> <div ...>tp<sup ...>?</sup>
                            // <span class="tooltiptext tooltipnarrow">Trade paperback. bla bla...
                            // need to extract the "tp".
                            final Element nextElementSibling = labelElement.nextElementSibling();
                            if (nextElementSibling != null) {
                                tmpString = nextElementSibling.ownText();
                                book.putString(DBKey.FORMAT, tmpString);
                            }

                            break;
                        }
                        case "Type:": {
                            nextSibling = labelElement.nextSibling();
                            if (nextSibling != null) {
                                // <li><b>Type:</b> COLLECTION
                                tmpString = nextSibling.toString().trim();
                                book.putString(SiteField.BOOK_TYPE, tmpString);
                                final Book.ContentType type = TYPE_MAP.get(tmpString);
                                if (type != null) {
                                    book.putLong(DBKey.BOOK_CONTENT_TYPE, type.getId());
                                }
                            }
                            break;
                        }
                        case "Cover:": {
                            final Elements as = li.select("a");
                            if (as.size() > 1) {
                                final Element a = as.get(1);
                                addAuthor(Author.from(a.text()), Author.TYPE_COVER_ARTIST, book);
                            }
                            break;
                        }
                        case "External IDs:": {
                            // send the <ul> children
                            processExternalIdElements(li.select("ul li"), book);
                            break;
                        }
                        case "Editors:": {
                            for (final Element a : li.select("a")) {
                                addAuthor(Author.from(a.text()), Author.TYPE_EDITOR, book);
                            }
                            break;
                        }

                        default:
                            break;
                    }
                }
            } catch (@NonNull final IndexOutOfBoundsException e) {
                // does not happen now, but could happen if we come about non-standard entries,
                // or if ISFDB website changes
                LoggerFactory.getLogger()
                             .e(TAG, e, "path: " + document.location() + "\n\nLI: " + li);
            }
        }

        // publication record.
        final Element recordIDDiv = contentBox.select("span.recordID").first();
        if (recordIDDiv != null) {
            tmpString = recordIDDiv.ownText();
            tmpString = SearchEngineUtils.digits(tmpString);
            if (!tmpString.isEmpty()) {
                try {
                    final long record = Long.parseLong(tmpString);
                    book.putLong(DBKey.SID_ISFDB, record);
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
        }

        //ENHANCE: it would make much more sense to get the notes from the TITLE_CGI page.
        // and if there are none, then fall back to the notes on this page.
        final Elements notesDiv = contentBox.select("div.notes");
        if (!notesDiv.isEmpty()) {
            tmpString = notesDiv.html();
            // it should always have this at the start, but paranoia...
            if (tmpString.startsWith("<b>Notes:</b>")) {
                tmpString = tmpString.substring(13).trim();
            }
            book.putString(DBKey.DESCRIPTION, tmpString);
        }

        // post-process all found data.

        final List<TocEntry> toc = parseToc(context, document, book, partialDateParser);
        if (!toc.isEmpty()) {
            // We always store the toc even if there is only a single entry.
            // ISFDB provides the *original* publication year in the toc which we want to preserve.
            book.setToc(toc);
            if (toc.size() > 1) {
                if (TocEntry.hasMultipleAuthors(toc)) {
                    book.putLong(DBKey.BOOK_CONTENT_TYPE,
                                 Book.ContentType.Anthology.getId());
                } else {
                    book.putLong(DBKey.BOOK_CONTENT_TYPE,
                                 Book.ContentType.Collection.getId());
                }
            }
        }

        Series.checkForSeriesNameInTitle(book);

        // try to deduce the first publication date from the TOC
        if (toc.size() == 1) {
            // if the content table has only one entry,
            // then this will have the first publication year for sure
            book.setFirstPublicationDate(toc.get(0).getFirstPublicationDate());

        } else if (toc.size() > 1) {
            // we gamble and take what we found while parsing the TOC (first entry with a year)
            if (firstPublicationYear != null && firstPublicationYear.isPresent()) {
                book.setFirstPublicationDate(firstPublicationYear);
            }
        }

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            final String isbn = book.getString(DBKey.BOOK_ISBN);
            parseCovers(context, document, isbn, 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }

    /**
     * Parses the downloaded {@link Document} for the cover and fetches it when present.
     *
     * @param context  Current context
     * @param document to parse
     * @param bookId   (optional) isbn or native id of the book,
     *                 will only be used for the temporary cover filename
     * @param cIdx     0..n image index
     *
     * @return fileSpec
     *
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @VisibleForTesting
    @NonNull
    private Optional<String> parseCovers(@NonNull final Context context,
                                         @NonNull final Document document,
                                         @Nullable final String bookId,
                                         @SuppressWarnings("SameParameterValue")
                                         @IntRange(from = 0, to = 1) final int cIdx)
            throws StorageException {
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

        final Element contentBox = document.selectFirst(CSS_Q_DIV_CONTENTBOX);
        if (contentBox != null) {
            final Element img = contentBox.selectFirst("img");
            if (img != null) {
                final String url = img.attr("src");
                return saveImage(context, url, bookId, cIdx, null);
            }
        }
        return Optional.empty();
    }

    /**
     * Get the list with {@link AltEditionIsfdb}s for the given isbn.
     *
     * @param context   Current context
     * @param validIsbn to get editions for. MUST be valid.
     *
     * @return list of editions found, can be empty, but never {@code null}
     *
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     */
    @WorkerThread
    @NonNull
    List<AltEditionIsfdb> fetchEditionsByIsbn(@NonNull final Context context,
                                              @NonNull final String validIsbn)
            throws SearchException, CredentialsException {
        searchForIsbn = validIsbn;

        final String url = getHostUrl(context) + String.format(CGI_EDITIONS, validIsbn);
        return fetchEditions(context, url);
    }

    /**
     * Parses the downloaded {@link Document} for the edition list.
     *
     * @param context  Current context
     * @param document to parse
     *
     * @return list of editions found, can be empty, but never {@code null}
     */
    @NonNull
    @VisibleForTesting
    List<AltEditionIsfdb> parseEditions(@NonNull final Context context,
                                        @NonNull final Document document) {

        final List<AltEditionIsfdb> editions = new ArrayList<>();

        final String pageUrl = document.location();

        if (pageUrl.contains(CGI_PL)) {
            // We got redirected to a book. Populate with the doc (web page) we got back.
            final long isfdbId = stripNumber(pageUrl, '?');
            // Sanity check
            if (isfdbId != 0) {
                editions.add(new AltEditionIsfdb(isfdbId, searchForIsbn, document));
            }

        } else if (pageUrl.contains(CGI_TITLE)
                   || pageUrl.contains(CGI_SE)
                   || pageUrl.contains(CGI_ADV_SEARCH_RESULTS)) {
            // example: http://www.isfdb.org/cgi-bin/title.cgi?11169
            // we have multiple editions. We get here from one of:
            // - direct link to the "title" of the publication; i.e. 'show the editions'
            // - search or advanced-search for the title.

            // Editions are shown by language; hence we can extract the language from the header:
            // Title: The Five Gold Bands Title Record # 11169 [Edit] [Edit History]
            // Author: Jack Vance
            // Date: 1953-00-00
            // Type: NOVEL
            // Webpages: Wikipedia-EN
            // Language: English
            // ...
            String lang = null;
            final Element header = document.selectFirst("div.ContentBox");
            if (header != null) {
                final Element langHeader = header.selectFirst("b:contains(Language:)");
                if (langHeader != null) {
                    final Node node = langHeader.nextSibling();
                    if (node != null) {
                        final Languages languages = ServiceLocator.getInstance().getLanguages();
                        lang = languages.getISO3FromDisplayLanguage(
                                context, getLocale(context),
                                node.toString().trim());
                    }
                }
            }

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
                    final Element edLink = tr.child(0).selectFirst("a");
                    if (edLink != null) {
                        final String url = edLink.attr("href");
                        if (!url.isEmpty()) {
                            String publisher = null;
                            String isbnStr = null;

                            // 3rd column: the publisher
                            final Element pa = tr.child(3).selectFirst("a");
                            if (pa != null) {
                                publisher = pa.text();
                            }
                            // 4th column: the ISBN/Catalog ID.
                            final String catNr = tr.child(4).text();
                            if (catNr.length() > 9) {
                                final ISBN isbn = new ISBN(catNr, true);
                                if (isbn.isValid(true)) {
                                    isbnStr = isbn.asText();
                                }
                            }
                            final long isfdbId = stripNumber(url, '?');
                            // Sanity check
                            if (isfdbId != 0) {
                                editions.add(new AltEditionIsfdb(isfdbId, isbnStr,
                                                                 publisher, lang));
                            }
                        }
                    }
                }
            }

        } else {
            // dunno, let's log it
            LoggerFactory.getLogger().w(TAG, "parseDoc|pageUrl=" + pageUrl);
        }

        return editions;
    }

    /**
     * Get the list with {@link AltEditionIsfdb}s for the given url.
     *
     * @param context Current context
     * @param url     A fully qualified ISFDB search url
     *
     * @return list of editions found, can be empty, but never {@code null}
     *
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     */
    @WorkerThread
    @NonNull
    private List<AltEditionIsfdb> fetchEditions(@NonNull final Context context,
                                                @NonNull final String url)
            throws SearchException, CredentialsException {

        final Document document = loadDocument(context, url, null);

        if (!isCancelled()) {
            return parseEditions(context, document);
        }
        return new ArrayList<>();
    }

    @NonNull
    private Document loadDocumentByEdition(@NonNull final Context context,
                                           @NonNull final AltEditionIsfdb edition)
            throws SearchException, CredentialsException {

        // check if we already got the page
        final Document document = edition.getDocument();
        if (document != null) {
            return document;
        }

        // go get it.
        final String url = getHostUrl(context) + String.format(CGI_BY_EXTERNAL_ID,
                                                               edition.getIsfdbId());
        // added due to https://github.com/square/okhttp/issues/1517
        // it's a server issue, this is a workaround.
        return loadDocument(context, url, Map.of(HttpConstants.CONNECTION,
                                                 HttpConstants.CONNECTION_CLOSE));
    }


    /**
     * All lines are normally.
     * <pre>
     * {@code
     * <li> <abbr class="template" title="Online Computer Library Center">OCLC/WorldCat</abbr>:
     * <a href="http://www.worldcat.org/oclc/963112443">963112443</a>}
     * </pre>
     * Except for Amazon:
     * <pre>
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
     * <a href="https://www.amazon.com/dp/B003ODIWEG?">US</a>)
     * }
     * </pre>
     * So for Amazon we only get a single link which is ok as the ASIN is the same in all.
     * <p>
     * Much more information can be found on the
     * <a href="https://www.isfdb.org/wiki/index.php/Sources_of_Bibliographic_Information">
     * ISFDB Wiki</a>
     *
     * @param elements LI elements
     * @param book     to update
     */
    private void processExternalIdElements(@NonNull final Collection<Element> elements,
                                           @NonNull final Book book) {
        elements.stream()
                .map(element -> element.select("a").first())
                .filter(Objects::nonNull)
                .map(element -> element.attr("href"))
                .filter(Objects::nonNull)
                .forEach(url -> {
                    if (url.contains("openlibrary.org")) {
                        // https://openlibrary.org/books/OL7524037M
                        book.putString(DBKey.SID_OPEN_LIBRARY, stripString(url, '/'));

                    } else if (url.contains("goodreads.com")) {
                        // https://www.goodreads.com/book/show/211357
                        final long id = stripNumber(url, '/');
                        // Sanity check
                        if (id != 0) {
                            book.putLong(DBKey.SID_GOODREADS_BOOK, id);
                        }

                    } else if (url.contains("www.worldcat.org")) {
                        // http://www.worldcat.org/oclc/60560136
                        book.putString(DBKey.SID_OCLC, stripString(url, '/'));

                    } else if (url.contains("amazon")) {
                        final int start = url.lastIndexOf('/');
                        if (start != -1) {
                            int end = url.indexOf('?', start);
                            if (end == -1) {
                                end = url.length();
                            }
                            final String asin = url.substring(start + 1, end);
                            book.putString(DBKey.SID_ASIN, asin);
                        }
//                    } else if (url.contains("audible.com")) {
                        // https://www.audible.com/pd/B00HJZAQPI

                    } else if (url.contains("lccn.loc.gov")) {
                        // Library of Congress (USA)
                        // http://lccn.loc.gov/2008299472
                        // http://lccn.loc.gov/95-22691
                        book.putString(DBKey.SID_LCCN, stripString(url, '/'));

//                    } else if (url.contains("explore.bl.uk")) {
                        // http://explore.bl.uk/primo_library/libweb/action/dlDisplay.do?
                        // vid=BLVU1&docId=BLL01014057142
                        // British Library

//                    } else if (url.contains("d-nb.info")) {
                        // http://d-nb.info/986851329
                        // DEUTSCHEN NATIONALBIBLIOTHEK

//                    } else if (url.contains("picarta.pica.nl")) {
                        // http://picarta.pica.nl/xslt/DB=3.9/XMLPRS=Y/PPN?PPN=802041833
                        // Nederlandse Bibliografie

//                    } else if (url.contains("tercerafundacion.net")) {
                        // Spanish
                        // https://tercerafundacion.net/biblioteca/ver/libro/2329

//                    } else if (url.contains("sfbg.us")) {
                        // Bulgarian
                        // http://www.sfbg.us/book/KAME-BFN-023X
//                    } else if (url.contains("fantlab.ru")) {
                        // Russian
                        // https://fantlab.ru/edition169821
//                    } else if (url.contains("chitanka.info")) {
                        // Russian
                        // https://biblioman.chitanka.info/books/12668
//                    } else if (url.contains("libris.kb.se")) {
                        // Swedish; a single book can have multiple libris.kb.se links!
                        // https://libris.kb.se/bib/7626661
                        // https://libris.kb.se/resource/bib/7626661
                        // https://libris.kb.se/katalogisering/p60w3h3112v1wsm
                        // https://libris.kb.se/p60w3h3112v1wsm
//                    } else if (url.contains("noosfere.org")) {
                        // French
                        // https://www.noosfere.org/livres/niourf.asp?numlivre=-323033
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
     * @return the number or {@code 0} if parsing failed.
     */
    private long stripNumber(@NonNull final String url,
                             @SuppressWarnings("SameParameterValue") final char last) {
        final int index = url.lastIndexOf(last) + 1;
        if (index == 0) {
            return 0;
        }

        try {
            return Long.parseLong(url.substring(index));
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }
        return 0;
    }

    // Experimenting with the REST API... to limited for full use for now.
    @VisibleForTesting
    @NonNull
    public Book xmlSearchByExternalId(@NonNull final Context context,
                                      @NonNull final String externalId,
                                      @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException {

        // getpub_by_internal_ID.cgi
        //   calls this server code:
        // def SQLGetPubById(id):
        //  query = "select * from pubs where pub_id = '%d'" % (int(id))
        //  db.query(query)
        //  result = db.store_result()
        //  if result.num_rows() > 0:
        //    pub = result.fetch_row()
        //    return pub[0]
        //  else:
        //    return 0
        final String url = getHostUrl(context) + String.format(REST_BY_EXTERNAL_ID, externalId);

        final List<Book> publicationsList = fetchPublications(context, url, fetchCovers, 1);
        if (publicationsList.isEmpty()) {
            return new Book();
        } else {
            return publicationsList.get(0);
        }
    }

    /**
     * Fetch a book.
     *
     * @param context     Current context
     * @param edition     to get
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws StorageException     on storage related failures
     */
    @WorkerThread
    void fetchByEdition(@NonNull final Context context,
                        @NonNull final AltEditionIsfdb edition,
                        @NonNull final boolean[] fetchCovers,
                        @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        final Document document = loadDocumentByEdition(context, edition);
        if (!isCancelled()) {
            parse(context, document, fetchCovers, book);

            // if there was only a single book found, then we won't have passed via the
            // publications page, and we won't have a language!
            // We *could* during parsing force load the publication page,
            // but that's quite an overhead just to get the language.
            // Instead...
            if (!book.contains(DBKey.LANGUAGE)) {
                final String lang = edition.getLangIso3();
                if (lang != null && !lang.isEmpty()) {
                    book.putString(DBKey.LANGUAGE, lang);
                } else {
                    // ... just set English as the language and let the user manually correct it.
                    book.putString(DBKey.LANGUAGE, LANGUAGE_DEFAULT);
                }
            }
        }
    }

    @Override
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (futureHttpGet != null) {
                futureHttpGet.cancel();
            }
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
     *
     * @return list of books found
     *
     * @throws StorageException      on storage related failures
     * @throws SearchException       on generic exceptions (wrapped) during search
     * @throws IllegalStateException if the SAX parser could not be created
     */
    @NonNull
    private List<Book> fetchPublications(@NonNull final Context context,
                                         @NonNull final String url,
                                         @NonNull final boolean[] fetchCovers,
                                         @SuppressWarnings("SameParameterValue") final int maxRecords)
            throws StorageException, SearchException {

        futureHttpGet = createFutureGetRequest(context);

        final IsfdbPublicationListHandler listHandler =
                new IsfdbPublicationListHandler(context, this, fetchCovers, maxRecords);

        final SAXParser parser;
        try {
            parser = SAXParserFactory.newInstance().newSAXParser();
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            throw new IllegalStateException(e);
        }

        try {
            futureHttpGet.get(url, (con, is) -> {
                try {
                    parser.parse(is, listHandler);
                    return true;
                } catch (@NonNull final SAXException e) {
                    // unwrap SAXException using getException() !
                    final Exception cause = e.getException();
                    if (cause instanceof EOFException) {
                        // not an error; we're done.
                        return true;
                    }
                    throw e;
                }
            });

            return listHandler.getResult();

        } catch (@NonNull final IOException e) {
            throw new SearchException(getEngineId(), e);
        }
    }


    /**
     * ISFDB specific field names we add to the bundle based on parsed XML data.
     */
    static final class SiteField {
        static final String BOOK_TYPE = "__ISFDB_BOOK_TYPE";
        static final String ISBN_2 = "__ISFDB_ISBN2";
        static final String BOOK_TAG = "__TAG";

        private SiteField() {
        }
    }
}
