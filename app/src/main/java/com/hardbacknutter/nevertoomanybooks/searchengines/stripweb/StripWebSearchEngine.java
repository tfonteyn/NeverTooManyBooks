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

package com.hardbacknutter.nevertoomanybooks.searchengines.stripweb;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BedethequeAuthorResolver;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class StripWebSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByBarcode {

    static final String PK_RESOLVE_AUTHORS_ON_BEDETHEQUE = "stripweb.resolve.authors.bedetheque";


    /**
     * Param 1: ISBN.
     */
    private static final String BY_ISBN = "/nl-nl/zoeken?type=&text=%1$s";

    /**
     * Some titles have suffixes which we need to strip.
     * A big mess.... there is no structure on the website for these.. seems
     * to depend on the mood of the person entering the title...
     * However, these books are generally NOT listed under their ISBN,
     * so unless the user enters the private site code they won't show up anyhow.
     * <p>
     * Entries MUST all be lowercase.
     */
    private static final Set<String> TITLE_SUFFIXES = Set.of(" - met ex libris - sc",
                                                             " - met ex libris hc",
                                                             " - met ex libris",
                                                             " met ex libris sc",
                                                             " + ex libris",
                                                             " -vip club met ex libris",
                                                             " + ex libris gesigneerd hc",
                                                             // typo is from website!
                                                             " + ex lbiris sc",
                                                             " sc");

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public StripWebSearchEngine(@NonNull final Context appContext,
                                @NonNull final SearchEngineConfig config) {
        super(appContext, config);
    }

    @Nullable
    private AuthorResolver getAuthorResolver(@NonNull final Context context) {
        if (ServiceLocator.getInstance().isFieldEnabled(DBKey.AUTHOR_REAL_AUTHOR)
            && PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PK_RESOLVE_AUTHORS_ON_BEDETHEQUE, false)) {
            return new BedethequeAuthorResolver(context, this);
        } else {
            return null;
        }
    }

    @NonNull
    @Override
    public Book searchByBarcode(@NonNull final Context context,
                                @NonNull final String barcode,
                                @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        // The site also sells comic related merchandise, which has a site-specific code
        // and can be searched as a generic code.
        // The site treats this as a plain (but invalid) ISBN code.
        return searchByIsbn(context, barcode, fetchCovers);
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        final String url = getHostUrl(context) + String.format(BY_ISBN, validIsbn);
        final Document document = loadDocument(context, url, null);
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            parseMultiResult(context, document, fetchCovers, book);
        }
        return book;
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws StorageException     on storage related failures
     */
    @WorkerThread
    private void parseMultiResult(@NonNull final Context context,
                                  @NonNull final Document document,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        // Grab the first search result, and redirect to that page
        final Element section = document.selectFirst("div.overview-item");
        // it will be null if there were no results.
        if (section != null) {
            final Element urlElement = section.selectFirst("a");
            if (urlElement != null) {
                String url = urlElement.attr("href");
                // sanity check
                if (url.startsWith("/")) {
                    url = getHostUrl(context) + url;
                }
                final Document redirected = loadDocument(context, url, null);
                if (!isCancelled()) {
                    parse(context, redirected, fetchCovers, book, getAuthorResolver(context));
                }
            }
        }
    }

    /**
     * Parses the downloaded {@link org.jsoup.nodes.Document}.
     * We only parse the <strong>first book</strong> found.
     *
     * @param context        Current context
     * @param document       to parse
     * @param fetchCovers    Set to {@code true} if we want to get covers
     *                       The array is guaranteed to have at least one element.
     * @param book           Bundle to update
     * @param authorResolver (optional) {@link AuthorResolver} to use
     *
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     *                              This should only occur if the engine calls/relies on
     *                              secondary sites.
     */
    @VisibleForTesting
    @WorkerThread
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book,
                      @Nullable final AuthorResolver authorResolver)
            throws StorageException, SearchException, CredentialsException {

        final Element main = document.selectFirst("main.content");
        if (main == null) {
            return;
        }
        final Element row = main.selectFirst("div.row");
        if (row == null) {
            return;
        }

        // col-lg-6 col-sm-8 pr-xl-9
        final Element details = main.selectFirst("div.col-lg-6");
        if (details == null) {
            return;
        }

        final Element titleElement = details.selectFirst("h1");
        if (titleElement == null) {
            return;
        }
        parseTitle(titleElement, book);

        final Element techInfoSection = details.selectFirst("div.techinfo");
        if (techInfoSection == null) {
            return;
        }

        //noinspection NonConstantStringShouldBeStringBuffer
        String tmpSeriesNr = null;

        for (final Element divRows : techInfoSection.select("div")) {
            final Element th = divRows.selectFirst("strong");
            final Element td = divRows.selectFirst("span");
            if (th != null && td != null) {
                switch (th.text()) {
                    case "ISBN nummer": {
                        final String text = ISBN.cleanText(td.text());
                        if (!text.isEmpty()) {
                            book.putString(DBKey.BOOK_ISBN, text);
                        }
                        break;
                    }
                    case "Pagina's":
                        processText(td, DBKey.PAGE_COUNT, book);
                        break;
                    case "Reeks":
                        processSeries(td, book);
                        break;
                    case "Nummer":
                        tmpSeriesNr = td.text();
                        break;
                    case "Taal":
                        parseLanguage(book, td);
                        break;
                    case "Cover":
                        processText(td, DBKey.FORMAT, book);
                        break;
                    case "Genre":
                        processText(td, DBKey.GENRE, book);
                        break;
                    case "Verschijningsdatum": {
                        final String text = SearchEngineUtils.cleanText(td.text());
                        if (!text.isEmpty()) {
                            processPublicationDate(context, getLocale(context), text, book);
                        }
                        break;
                    }
                    case "Tekenaars":
                        processAuthor(td, Author.TYPE_ARTIST, book);
                        break;
                    case "Scenarist":
                        processAuthor(td, Author.TYPE_WRITER, book);
                        break;
                    case "Inkleuring":
                        processAuthor(td, Author.TYPE_COLORIST, book);
                        break;
                    case "Cover artiest":
                        processAuthor(td, Author.TYPE_COVER_ARTIST, book);
                        break;
                    case "Uitgeverij":
                        processPublisher(td, book);
                        break;

                    case "Afmetingen":
                        processText(td, SiteField.SIZE, book);
                        break;

                    case "Trefwoorden": {
                        // comma separated words but with extra whitespace we must remove
                        final String[] split = SearchEngineUtils.cleanText(td.text())
                                                                .split(",");
                        final String text = Arrays.stream(split)
                                                  .map(String::strip)
                                                  .collect(Collectors.joining(","));
                        book.putString(SiteField.KEY_WORDS, text);
                        break;
                    }
                    default:
                        // Other labels which are ignored:
                        // "Collectie" seen for a "Schilderdoeken"
                        // "Gewicht" weight in grams
                        //  size
                        // "Formaat" size
                        // "Levertermijn"
                        // "FocVendorDate": Final Order Cutoff Date
                        // "Brand Code"
                        // "Diamond Code"
                        // "Upc"
                        break;
                }
            }
        }

        parsePrice(context, document, book);
        parseDescription(document, book);
        parseRating(details, book);

        // post-process all found data.

        if (tmpSeriesNr != null && !tmpSeriesNr.isEmpty()) {
            final List<Series> seriesList = book.getSeries();
            if (seriesList.size() == 1) {
                final Series series = seriesList.get(0);
                series.setNumber(tmpSeriesNr);
            }
            //else if (seriesList.isEmpty()) {
            // A book can have a series number without a series name.
            // This was noted on certain Manga/Comics publications.
            // The title always (?) contains the number,
            // so we're ignoring this.
            //}
        }

        if (authorResolver != null) {
            for (final Author author : book.getAuthors()) {
                authorResolver.resolve(author);
            }
        }

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0] || fetchCovers[1]) {
            final String isbn = book.getString(DBKey.BOOK_ISBN);
            // start from 'main' !
            parseCovers(context, main, isbn, fetchCovers, book);
        }
    }

    private void parseTitle(@NonNull final Element titleElement,
                            @NonNull final Book book) {
        final String text = SearchEngineUtils.cleanText(titleElement.text());
        if (!text.isEmpty()) {
            final String lcText = text.toLowerCase(Locale.ROOT);
            final String title = TITLE_SUFFIXES
                    .stream()
                    .filter(lcText::endsWith)
                    .map(suffix -> text.substring(0, text.length() - suffix.length()))
                    .findAny()
                    .orElse(text);
            book.putString(DBKey.TITLE, title);
        }
    }

    private void parseRating(@NonNull final Element details,
                             @NonNull final Book book) {
        final Element stars = details.selectFirst("div.stars");
        if (stars != null) {
            final Elements nr = stars.select("i.text-yellow");
            // Rating is simply the amount of stars, i.e. 0..5
            // Only add if at least 1 star (and max 5 as sanity check)
            final int rating = nr.size();
            if (rating > 0 && rating < 6) {
                book.putFloat(DBKey.RATING, rating);
            }
        }
    }

    private void parseLanguage(@NonNull final Book book,
                               @NonNull final Element td) {
        final String langCode = SearchEngineUtils.cleanText(td.text());
        if (!langCode.isEmpty()) {
            // Seen: NL,FR,EN,Fr
            switch (langCode.toLowerCase(Locale.ROOT)) {
                case "nl":
                    book.putString(DBKey.LANGUAGE, "nld");
                    break;
                case "fr":
                    book.putString(DBKey.LANGUAGE, "fra");
                    break;
                case "en":
                    book.putString(DBKey.LANGUAGE, "eng");
                    break;
                default:
                    book.putString(DBKey.LANGUAGE, langCode);
                    break;
            }
        }
    }

    private void parseDescription(@NonNull final Document document,
                                  @NonNull final Book book) {
        final Element desc = document.selectFirst("div.txt-product");
        if (desc != null) {
            String html = desc.html();
            // Potentially contains an iframe (e.g. to youtube content); remove it.
            final int iStart = html.indexOf("<iframe");
            if (iStart > 0) {
                final int iEnd = html.indexOf("</iframe>");
                // Sanity check
                if (iEnd > iStart) {
                    html = html.substring(0, iStart) + " " + html.substring(iEnd + 9);
                }
            }

            book.putString(DBKey.DESCRIPTION, html);
        }
    }

    private void parsePrice(@NonNull final Context context,
                            @NonNull final Document document,
                            @NonNull final Book book) {
        final Element cartForm = document.selectFirst("form[id='frmAddToCart']");
        if (cartForm != null) {
            // In EURO; contains a comma as decimal separate.
            final Element price = cartForm.selectFirst("span[itemprop='price']");
            if (price != null) {
                final Locale siteLocale = getLocale(context, document.location().split("/")[2]);

                final String priceStr = price.text().strip();
                final boolean full = processPriceListed(context, siteLocale, priceStr, book);
                // Processing the price is unlikely to fail, but if it does, just add EURO
                if (!full) {
                    book.putString(DBKey.PRICE_LISTED_CURRENCY, MoneyParser.EUR);
                }
            }
        }
    }

    /**
     * Process a td which is pure text.
     *
     * @param td   data td
     * @param key  for this field
     * @param book Bundle to update
     */
    private void processText(@Nullable final Element td,
                             @NonNull final String key,
                             @NonNull final Book book) {
        if (td != null) {
            final String text = SearchEngineUtils.cleanText(td.text());
            if (!text.isEmpty()) {
                book.putString(key, text);
            }
        }
    }

    /**
     * Found an Author.
     *
     * @param td   data td
     * @param type of this entry
     * @param book Bundle to update
     */
    private void processAuthor(@NonNull final Element td,
                               @Author.Type final int type,
                               @NonNull final Book book) {

        // Most books list the authors as "a" elements
        final Elements aas = td.select("a");
        if (aas.isEmpty()) {
            // but some are plain text separated by commas
            final String[] names = SearchEngineUtils.cleanText(td.text()).split(",");
            for (final String name : names) {
                processAuthor(book, type, name);
            }
        } else {
            for (final Element a : aas) {
                final String name = SearchEngineUtils.cleanText(a.text());
                processAuthor(book, type, name);
            }
        }
    }

    private void processAuthor(@NonNull final Book book,
                               @Author.Type final int type,
                               @NonNull final String name) {
        // The site actually uses "lastname firstname" or just "lastname".
        // This create additional issues with names like "Van Hamme" which is a "lastname"
        // with a space in... nice mess...
        // So far this is the only site doing so consistently (other sites 'sometimes' do it).
        // We decode as usual,
        final Author author = Author.from(name);
        // and then swap the names... sigh... this is easier than adapting the parser.
        final String family = author.getGivenNames();
        // but only swap when there ARE two names....
        if (!family.isEmpty()) {
            // and apply a HACK.... which is NOT exhaustive but better than nothing...
            if ("van".equalsIgnoreCase(family) || "de".equalsIgnoreCase(family)) {
                author.setName(family + " " + author.getFamilyName(), "");
            } else {
                author.setName(family, author.getFamilyName());
            }
        }
        // Add/merge or skip if already present
        processAuthor(author, type, book);
    }

    /**
     * Found a Series.
     *
     * @param td   data td
     * @param book Bundle to update
     */
    private void processSeries(@NonNull final Element td,
                               @NonNull final Book book) {
        // Most books list the series as "a" elements
        final Elements aas = td.select("a");
        if (aas.isEmpty()) {
            // but some are plain text separated by commas
            final String[] names = SearchEngineUtils.cleanText(td.text()).split(",");
            for (final String name : names) {
                processSeries(book, name);
            }
        } else {
            for (final Element a : aas) {
                final String name = SearchEngineUtils.cleanText(a.text());
                processSeries(book, name);
            }
        }
    }

    private void processSeries(@NonNull final Book book,
                               @NonNull final String name) {
        final Series currentSeries = Series.from(name);
        // add if not already present
        if (book.getSeries().stream().noneMatch(series -> series.equals(currentSeries))) {
            book.add(currentSeries);
        }
    }

    /**
     * Found a Publisher.
     *
     * @param td   data td
     * @param book Bundle to update
     */
    private void processPublisher(@NonNull final Element td,
                                  @NonNull final Book book) {
        // Most books list the publishers as "a" elements
        final Elements aas = td.select("a");
        if (aas.isEmpty()) {
            // but some are plain text separated by commas
            final String[] names = SearchEngineUtils.cleanText(td.text()).split(",");
            for (final String name : names) {
                processPublisher(book, name);
            }
        } else {
            for (final Element a : aas) {
                final String name = SearchEngineUtils.cleanText(a.text());
                processPublisher(book, name);
            }
        }
    }

    private void processPublisher(@NonNull final Book book,
                                  @NonNull final String name) {
        final Publisher currentPublisher = Publisher.from(name);
        // add if not already present
        if (book.getPublishers().stream().noneMatch(pub -> pub.equals(currentPublisher))) {
            book.add(currentPublisher);
        }
    }

    private void parseCovers(@NonNull final Context context,
                             @NonNull final Element document,
                             @Nullable final String isbn,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book)
            throws StorageException {

        if (fetchCovers[0]) {
            final Element cover = document.selectFirst("a.d-block");
            if (cover != null) {
                String url = cover.attr("href");
                // Sanity check; the url is supposed to be relative
                if (url.startsWith("/")) {
                    url = getHostUrl(context) + url;
                }
                saveImage(context, url, isbn, 0, null).ifPresent(
                        fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
            }
        }
    }

    public static final class SiteField {

        static final String KEY_WORDS = "__key_words";
        static final String SIZE = "__size";

        private SiteField() {
        }
    }
}
