/*
 * @Copyright 2018-2022 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.network.HttpUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

public class BedethequeSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn {

    private static final Pattern PUB_DATE = Pattern.compile("\\d\\d/\\d\\d\\d\\d");

    private static final String PK_BEDETHEQUE_PRESERVE_FORMAT_NAMES = "bedetheque.resolve.formats";

    /** These are generic author names which are really the color. */
    private static final List<String> AUTHOR_NAME_COLOR =
            List.of("<N&B>", "<Monochromie>", "<Bichromie>", "<Trichromie>", "<Quadrichromie>");

    /** The "en" must be as-is. */
    private static final Pattern SERIES_WITH_LANGUAGE = Pattern
            .compile("(.*)\\s+\\(en (.*)\\)");
    private static final Pattern SERIES_WITH_SIMPLE_PREFIX = Pattern
            .compile("(.*)\\s+\\((le|la|les|l'|the)\\)",
                     Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final String COOKIE = "csrf_cookie_bel";
    private static final String COOKIE_DOMAIN = ".bedetheque.com";
    private final CookieManager cookieManager;
    private final Map<String, String> extraRequestProperties;
    @Nullable
    private HttpCookie csrfCookie;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param config the search engine configuration
     */
    @Keep
    public BedethequeSearchEngine(@NonNull final SearchEngineConfig config) {
        super(config);

        cookieManager = ServiceLocator.getInstance().getCookieManager();

        extraRequestProperties = Map.of(HttpUtils.REFERER, getHostUrl() + "/search");
    }

    @NonNull
    private String requireCookieNameValueString(@NonNull final Context context)
            throws SearchException {
        if (csrfCookie == null || csrfCookie.hasExpired()) {
            try {
                final FutureHttpGet<HttpCookie> head = createFutureHeadRequest();
                // Reminder: the "request" will be connected and the response code will be OK,
                // so just extract the cookie we need for the next request
                csrfCookie = head.get(getHostUrl() + "/search", request -> cookieManager
                        .getCookieStore()
                        .getCookies()
                        .stream()
                        .filter(c -> COOKIE_DOMAIN.equals(c.getDomain())
                                     && COOKIE.equals(c.getName()))
                        .findFirst()
                        .orElse(new HttpCookie(COOKIE, "")));
            } catch (@NonNull final IOException | UncheckedIOException | StorageException e) {
                throw new SearchException(getName(context), e);
            }
        }
        if (csrfCookie == null || csrfCookie.getValue().isEmpty()) {
            throw new SearchException(getName(context), context.getString(R.string.httpError));
        }

        return csrfCookie.getName() + '=' + Objects.requireNonNull(csrfCookie.getValue());
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        //The site is very "defensive". We must specify the full url and set the "Referer".
        final String url = getHostUrl() + "/search/albums?RechIdSerie=&RechIdAuteur="
                           + '&' + requireCookieNameValueString(context)
                           + "&RechSerie=&RechTitre=&RechEditeur=&RechCollection="
                           + "&RechStyle=&RechAuteur="
                           + "&RechISBN=" + validIsbn
                           + "&RechParution=&RechOrigine=&RechLangue="
                           + "&RechMotCle=&RechDLDeb=&RechDLFin="
                           + "&RechCoteMin=&RechCoteMax="
                           + "&RechEO=0";

        final Document document = loadDocument(context, url, extraRequestProperties);

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
     * @throws StorageException     on storage related failures
     */
    @WorkerThread
    private void parseMultiResult(@NonNull final Context context,
                                  @NonNull final Document document,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        // Grab the first search result, and redirect to that page
        final Element section = document.selectFirst("ul.search-list");
        if (section != null) {
            final Element urlElement = section.selectFirst("a");
            if (urlElement != null) {
                final String url = urlElement.attr("href");
                if (!url.isBlank()) {
                    final Document redirected = loadDocument(context, url, extraRequestProperties);
                    if (!isCancelled()) {
                        parse(context, redirected, fetchCovers, book);
                    }
                }
            }
        }
    }

    /**
     * Parses the downloaded {@link org.jsoup.nodes.Document}.
     * We only parse the <strong>first book</strong> found.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     *
     * @throws StorageException     on storage related failures
     * @throws CredentialsException on authentication/login failures
     *                              This should only occur if the engine calls/relies on
     *                              secondary sites.
     */
    @VisibleForTesting
    @WorkerThread
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        final Element section = document.selectFirst(
                "div.tab_content_liste_albums > ul.infos-albums");
        if (section != null) {
            int lastAuthorType = -1;

            String currentFormat = null;

            final Elements labels = section.select("li > label");
            for (final Element label : labels) {
                final String text = label.text();
                // check for multiple author entries of the same type
                if (text.isBlank() && lastAuthorType != -1) {
                    final Element span = label.nextElementSibling();
                    if (span != null) {
                        processAuthor(context, span.text(), lastAuthorType, book);
                    }
                    continue;
                }
                lastAuthorType = -1;

                //noinspection SwitchStatementWithoutDefaultBranch
                switch (text) {
                    case "Identifiant :": {
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            book.putString(DBKey.SID_BEDETHEQUE, textNode.toString().trim());
                        }
                        break;
                    }
                    case "Titre :": {
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            book.putString(DBKey.TITLE, textNode.toString().trim());
                        }
                        break;
                    }
                    case "EAN/ISBN :": {
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            book.putString(DBKey.BOOK_ISBN, span.text());
                        }
                        break;
                    }
                    case "Dépot légal :": {
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            String date = textNode.toString().trim();
                            if (PUB_DATE.matcher(date).matches()) {
                                // Flip to "YYYY-MM" (or use as-is)
                                date = date.substring(3) + "-" + date.substring(0, 2);
                            }
                            book.putString(DBKey.BOOK_PUBLICATION__DATE, date);
                        }
                        break;
                    }
                    case "Planches :": {
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            book.putString(DBKey.PAGE_COUNT, span.text());
                        }
                        break;
                    }
                    case "Format :": {
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            currentFormat = textNode.toString().trim();
                            mapFormat(context, book, currentFormat, false);
                        }
                        break;
                    }
                    case "Série :": {
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            book.add(processSeries(book, textNode.toString().trim()));
                        }
                        break;
                    }
                    case "Tome :": {
                        //FIXME: some books (non-french?) have two numbers which the site
                        // concatenates... uh???
                        // e.g. the series "Lucky Luke (en anglais):
                        // https://www.bedetheque.com/BD-Lucky-Luke-en-anglais-Tome-148-Dick-Digger-s-Gold-Mine-227463.html
                        // seems to have BOTH "1" and "48" ... and we end up with "148"
                        // This is clearly a bug on the site... not sure what we can do about that
                        final Node textNode = label.nextSibling();
                        final List<Series> seriesList = book.getSeries();
                        if (textNode != null && !seriesList.isEmpty()) {
                            seriesList.get(seriesList.size() - 1)
                                      .setNumber(textNode.toString().trim());
                        }
                        break;
                    }
                    case "Editeur :": {
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            book.add(new Publisher(span.text()));
                        }
                        break;
                    }
                    case "Scénario :":
                    case "Adapté de :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_WRITER;
                            processAuthor(context, a.text(), Author.TYPE_WRITER, book);
                        }
                        break;
                    }
                    case "Dessin :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_ARTIST;
                            processAuthor(context, a.text(), Author.TYPE_ARTIST, book);
                        }
                        break;
                    }
                    case "Encrage :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_INKING;
                            processAuthor(context, a.text(), Author.TYPE_INKING, book);
                        }
                        break;
                    }

                    case "Couleurs :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_COLORIST;

                            final String colorOrColorist = a.text();
                            if (AUTHOR_NAME_COLOR.contains(colorOrColorist)) {
                                // REMOVE the "<>" as we really don't want fake html tags
                                book.putString(DBKey.COLOR, colorOrColorist
                                        .substring(1, colorOrColorist.length() - 1));
                            } else {
                                // it's a real name
                                processAuthor(context, colorOrColorist, Author.TYPE_COLORIST,
                                              book);
                            }
                        }
                        break;
                    }
                    case "Couverture :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_COVER_ARTIST;
                            processAuthor(context, a.text(), Author.TYPE_COVER_ARTIST, book);
                        }
                        break;
                    }
                    case "Préface :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_FOREWORD;
                            processAuthor(context, a.text(), Author.TYPE_FOREWORD, book);
                        }
                        break;
                    }
                    case "Traduction :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_TRANSLATOR;
                            processAuthor(context, a.text(), Author.TYPE_TRANSLATOR, book);
                        }
                        break;
                    }
                    case "Autres :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_CONTRIBUTOR;
                            processAuthor(context, a.text(), Author.TYPE_CONTRIBUTOR, book);
                        }
                        break;
                    }
                    case "Autres info :": {
                        if (label.nextElementSiblings()
                                 .stream()
                                 .map(sib -> sib.attr("title"))
                                 .anyMatch("Couverture souple"::equals)) {
                            // Sanity check, it should never be null at this point.
                            if (currentFormat == null) {
                                currentFormat = "Couverture souple";
                            }
                            mapFormat(context, book, currentFormat, true);
                        }
                    }
                }
            }

            if (!book.getAuthors().isEmpty()) {
                final AuthorResolver resolver = new AuthorResolver(context, this);
                for (final Author author : book.getAuthors()) {
                    resolver.resolve(context, author);
                }
            }

            // Unless present, add the default language
            if (!book.contains(DBKey.LANGUAGE)) {
                book.putString(DBKey.LANGUAGE, "fra");
            }

            if (isCancelled()) {
                return;
            }

            if (fetchCovers[0] || fetchCovers[1]) {
                parseCovers(document, fetchCovers, book);
            }
        }
    }

    /**
     * Map Bedetheque specific formats to our generalized ones if allowed.
     *
     * @param context       Current context
     * @param book      Bundle to update
     * @param currentFormat original french format string
     * @param softcover     {@code true} if the books is a softcover, {@code false} for hardcover
     */
    private void mapFormat(@NonNull final Context context,
                           @NonNull final Book book,
                           @NonNull final String currentFormat,
                           final boolean softcover) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(PK_BEDETHEQUE_PRESERVE_FORMAT_NAMES, false)) {
            book.putString(DBKey.FORMAT, currentFormat
                                         + (softcover ? "; Couverture souple" : ""));
            return;
        }

        final String format;
        switch (currentFormat) {
            case "Couverture souple":
                format = context.getString(R.string.book_format_softcover);
                break;

            case "Format normal":
            case "Grand format":
                format = context.getString(softcover ? R.string.book_format_softcover
                                                     : R.string.book_format_hardcover);
                break;

            case "A l'italienne":
                format = context.getString(softcover ? R.string.book_format_softcover_oblong
                                                     : R.string.book_format_hardcover_oblong);
                break;

            case "Format comics":
                format = context.getString(softcover ? R.string.book_format_comic
                                                     : R.string.book_format_hardcover);
                break;

            case "Format manga":
            case "Format poche":
                format = context.getString(softcover ? R.string.book_format_paperback
                                                     : R.string.book_format_hardcover);
                break;

            case "Autre format":
                format = context.getString(R.string.book_format_other);
                break;

            default:
                // fallback
                format = currentFormat;
                break;
        }

        book.putString(DBKey.FORMAT, format);
    }

    /**
     * Parsing the series title is done <strong>locally</strong> to this search-engine.
     */
    @VisibleForTesting
    @NonNull
    Series processSeries(@NonNull final Book book,
                         @NonNull final String name) {
        // Series names can be formatted in a LOT of ways.
        // We're not going to try and capture each and every special format
        // but stick to the most common ones.
        String seriesName = name;

        Matcher matcher;

        // Try extracting a language
        matcher = SERIES_WITH_LANGUAGE.matcher(name);
        if (matcher.find()) {
            String maybeLanguage = matcher.group(2);
            if (maybeLanguage != null) {
                final int space = maybeLanguage.indexOf(' ');
                if (space > 1) {
                    // Lucky Luke Classics (en espagnol - Ediciones Kraken)
                    maybeLanguage = maybeLanguage.substring(0, space);
                } else {
                    // The brackets part was a 'pure' language; strip it
                    final String n = matcher.group(1);
                    if (n != null) {
                        seriesName = n;
                    }
                }
                book.putString(DBKey.LANGUAGE, maybeLanguage);
            }
        }

        // Find/move a simple "Le|La/Les|L'" prefix
        matcher = SERIES_WITH_SIMPLE_PREFIX.matcher(name);
        if (matcher.find()) {
            final String n = matcher.group(1);
            final String prefix = matcher.group(2);
            if (n != null && prefix != null) {
                if (prefix.endsWith("'")) {
                    seriesName = prefix + n;
                } else {
                    seriesName = prefix + ' ' + n;
                }
            }
        }

        // plain constructor, no extra parsing
        return new Series(seriesName);
    }

    private void parseCovers(@NonNull final Document document,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book)
            throws StorageException {

        if (fetchCovers[0]) {
            final Element a = document.selectFirst(
                    "div.bandeau-principal > div.bandeau-image > a");
            processCover(a, 0, book);
        }

        if (fetchCovers[1]) {
            // bandeau-vignette contains a list, each "li" contains an "a"
            final Elements as = document.select("div.bandeau-vignette a");
            processCover(as.last(), 1, book);
        }
    }

    private void processCover(@Nullable final Element a,
                              final int cIdx,
                              @NonNull final Book book)
            throws StorageException {
        if (a != null) {
            final String url = a.attr("href");
            final String isbn = book.getString(DBKey.BOOK_ISBN);
            final String fileSpec = saveImage(url, isbn, cIdx, null);
            if (fileSpec != null) {
                final ArrayList<String> list = new ArrayList<>();
                list.add(fileSpec);
                book.putStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[cIdx], list);
            }
        }
    }

    private void processAuthor(@NonNull final Context context,
                               @NonNull final String text,
                               @Author.Type final int currentAuthorType,
                               @NonNull final Book book) {

        // REMOVE potential "<>" as we really don't want fake html tags
        String names = text;
        if (names.startsWith("<")) {
            names = names.substring(1);
        }
        if (names.endsWith(">")) {
            names = names.substring(0, names.length() - 1);
        }

        final Author currentAuthor;

        // colors - handled by "Couleurs"
        //"<N&B>", "<Monochromie>", "<Bichromie>", "<Trichromie>", "<Quadrichromie>"
        // scenario author for an art-book; ignore
        // "<Art Book>"
        // Used for books; The authors for "dessin" and "couleurs"; ignore
        // "<Texte non illustré>"
        switch (names) {
            case "Indéterminé":
            case "Anonyme":
                currentAuthor = Author.createUnknownAuthor(context);
                break;

            case "Art Book":
            case "Texte non illustré":
                // ignore these
                return;

            case "Collectif":
            default:
                currentAuthor = Author.from(names);
                break;
        }

        boolean add = true;
        // check if already present
        for (final Author author : book.getAuthors()) {
            if (author.equals(currentAuthor)) {
                // merge types.
                author.addType(currentAuthorType);
                add = false;
                // keep looping
            }
        }

        if (add) {
            currentAuthor.setType(currentAuthorType);
            book.add(currentAuthor);
        }
    }
}
