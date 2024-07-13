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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpCookie;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpHead;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

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
    /** A text indicating it's a softcover. Can occur in more than one field. */
    private static final String FORMAT_COUVERTURE_SOUPLE = "Couverture souple";
    private static final String SEARCH_URL = "/search";

    private final Map<String, String> extraRequestProperties;
    @Nullable
    private HttpCookie csrfCookie;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public BedethequeSearchEngine(@NonNull final Context appContext,
                                  @NonNull final SearchEngineConfig config) {
        super(appContext, config);
        extraRequestProperties = Map.of(HttpConstants.REFERER, getHostUrl(appContext) + SEARCH_URL);
    }

    @NonNull
    private List<AuthorResolver> getAuthorResolvers(@NonNull final Context context) {
        return AuthorResolverFactory.getResolvers(context, this);
    }

    @NonNull
    private String requireCookieNameValueString(@NonNull final Context context)
            throws SearchException {
        if (csrfCookie == null || csrfCookie.hasExpired()) {
            try {
                final FutureHttpHead<HttpCookie> head = createFutureHeadRequest(context);
                // Reminder: the "request" will be connected and the response code will be OK,
                // so just extract the cookie we need for the next request
                csrfCookie = head.send(getHostUrl(context) + SEARCH_URL, response -> cookieManager
                        .getCookieStore()
                        .getCookies()
                        .stream()
                        .filter(c -> COOKIE_DOMAIN.equals(c.getDomain())
                                     && COOKIE.equals(c.getName()))
                        .findFirst()
                        .orElse(new HttpCookie(COOKIE, "")));
            } catch (@NonNull final IOException | UncheckedIOException | StorageException e) {
                throw new SearchException(getEngineId(), e);
            }
        }
        if (csrfCookie == null || csrfCookie.getValue().isEmpty()) {
            throw new SearchException(getEngineId(), null,
                                      context.getString(R.string.httpError));
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
        final String url = getHostUrl(context) + "/search/albums?RechIdSerie=&RechIdAuteur="
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
     * @throws SearchException      on generic exceptions (wrapped) during search
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
                        parse(context, redirected, fetchCovers, book, getAuthorResolvers(context));
                    }
                }
            }
        }
    }

    /**
     * Parses the downloaded {@link org.jsoup.nodes.Document}.
     * We only parse the <strong>first book</strong> found.
     *
     * @param context         Current context
     * @param document        to parse
     * @param fetchCovers     Set to {@code true} if we want to get covers
     *                        The array is guaranteed to have at least one element.
     * @param book            Bundle to update
     * @param authorResolvers {@link AuthorResolver}s to use
     *                        (passed in for easy testing)
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
                      @NonNull final List<AuthorResolver> authorResolvers)
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
                        parseAuthor(context, span.text(), lastAuthorType, book);
                    }
                    continue;
                }
                lastAuthorType = -1;

                //noinspection SwitchStatementWithoutDefaultBranch
                switch (text) {
                    case "Série :": {
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            book.add(processSeries(textNode.toString().trim(), book));
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
                    case "Identifiant :": {
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            book.putString(DBKey.SID_BEDETHEQUE, textNode.toString().trim());
                        }
                        break;
                    }

                    case "Scénario :":
                    case "Adapté de :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_WRITER;
                            parseAuthor(context, a.text(), Author.TYPE_WRITER, book);
                        }
                        break;
                    }
                    case "Dessin :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_ARTIST;
                            parseAuthor(context, a.text(), Author.TYPE_ARTIST, book);
                        }
                        break;
                    }
                    case "Encrage :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_INKING;
                            parseAuthor(context, a.text(), Author.TYPE_INKING, book);
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
                                parseAuthor(context, colorOrColorist, Author.TYPE_COLORIST,
                                            book);
                            }
                        }
                        break;
                    }
                    case "Couverture :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_COVER_ARTIST;
                            parseAuthor(context, a.text(), Author.TYPE_COVER_ARTIST, book);
                        }
                        break;
                    }
                    case "Préface :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_FOREWORD;
                            parseAuthor(context, a.text(), Author.TYPE_FOREWORD, book);
                        }
                        break;
                    }
                    case "Traduction :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_TRANSLATOR;
                            parseAuthor(context, a.text(), Author.TYPE_TRANSLATOR, book);
                        }
                        break;
                    }
                    case "Autres :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_CONTRIBUTOR;
                            parseAuthor(context, a.text(), Author.TYPE_CONTRIBUTOR, book);
                        }
                        break;
                    }

                    case "Dépot légal :": {
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            String date = textNode.toString().strip();
                            if (PUB_DATE.matcher(date).matches()) {
                                // Flip to "YYYY-MM" (or use as-is)
                                date = date.substring(3) + "-" + date.substring(0, 2);
                            }
                            addPublicationDate(context, getLocale(context), date, book);
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
                    case "Format :": {
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            currentFormat = textNode.toString().trim();
                            mapFormat(context, currentFormat, false, book);
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
                    case "Planches :": {
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            book.putString(DBKey.PAGE_COUNT, span.text());
                        }
                        break;
                    }
                    case "Autres info :": {
                        if (label.nextElementSiblings()
                                 .stream()
                                 .map(sib -> sib.attr("title"))
                                 .anyMatch(FORMAT_COUVERTURE_SOUPLE::equals)) {
                            // Sanity check, it should never be null at this point.
                            if (currentFormat == null) {
                                currentFormat = FORMAT_COUVERTURE_SOUPLE;
                            }
                            mapFormat(context, currentFormat, true, book);
                        }
                    }
                }
            }

            final Element description = document.selectFirst("span[itemprop='description']");
            if (description != null) {
                book.putString(DBKey.DESCRIPTION, description.text());
            }

            for (final AuthorResolver resolver : authorResolvers) {
                for (final Author author : book.getAuthors()) {
                    resolver.resolve(author);
                }
            }

            // Unless present, add the default language
            if (!book.contains(DBKey.LANGUAGE)) {
                book.putString(DBKey.LANGUAGE, "fra");
            }

            if (isCancelled()) {
                return;
            }

            final String isbn = book.getString(DBKey.BOOK_ISBN);

            if (fetchCovers[0]) {
                parseCover(context, document, isbn, 0).ifPresent(
                        fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
            }

            if (isCancelled()) {
                return;
            }

            if (fetchCovers.length > 1 && fetchCovers[1]) {
                parseCover(context, document, isbn, 1).ifPresent(
                        fileSpec -> CoverFileSpecArray.setFileSpec(book, 1, fileSpec));
            }
        }
    }

    /**
     * Map Bedetheque specific formats to our generalized ones if allowed.
     *
     * @param context       Current context
     * @param currentFormat original french format string
     * @param softcover     {@code true} if the books is a softcover, {@code false} for hardcover
     * @param book          Bundle to update
     */
    private void mapFormat(@NonNull final Context context,
                           @NonNull final String currentFormat,
                           final boolean softcover,
                           @NonNull final Book book) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(PK_BEDETHEQUE_PRESERVE_FORMAT_NAMES, false)) {
            book.putString(DBKey.FORMAT, currentFormat
                                         + (softcover ? "; Couverture souple" : ""));
            return;
        }

        final String format;
        switch (currentFormat) {
            case FORMAT_COUVERTURE_SOUPLE:
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
     *
     * @param text to parse
     * @param book Bundle to update
     *
     * @return the Series found
     */
    @VisibleForTesting
    @NonNull
    Series processSeries(@NonNull final String text,
                         @NonNull final Book book) {
        // Series names can be formatted in a LOT of ways.
        // We're not going to try and capture each and every special format
        // but stick to the most common ones.
        String seriesName = text;

        Matcher matcher;

        // Try extracting a language
        matcher = SERIES_WITH_LANGUAGE.matcher(text);
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
        matcher = SERIES_WITH_SIMPLE_PREFIX.matcher(text);
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

    /**
     * Parses the given {@link Document} for the cover and fetches it when present.
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
    @NonNull
    private Optional<String> parseCover(@NonNull final Context context,
                                        @NonNull final Document document,
                                        @Nullable final String bookId,
                                        @IntRange(from = 0, to = 1) final int cIdx)
            throws StorageException {

        Element a = null;
        if (cIdx == 0) {
            a = document.selectFirst("div.bandeau-principal > div.bandeau-image > a");
        } else if (cIdx == 1) {
            // bandeau-vignette contains a list, each "li" contains an "a"
            a = document.select("div.bandeau-vignette a").last();
        }
        if (a == null) {
            return Optional.empty();
        }
        final String url = a.attr("href");
        return saveImage(context, url, bookId, cIdx, null);
    }

    /**
     * Parse an Author. Handles Bedetheque specific hardcoded pseudo-names.
     *
     * @param context Current context
     * @param text    to parse
     * @param type    the Author type
     * @param book    Bundle to update
     */
    private void parseAuthor(@NonNull final Context context,
                             @NonNull final String text,
                             @Author.Type final int type,
                             @NonNull final Book book) {

        // REMOVE potential "<>" as we really don't want fake html tags
        String names = text;
        if (names.startsWith("<")) {
            names = names.substring(1);
        }
        if (names.endsWith(">")) {
            names = names.substring(0, names.length() - 1);
        }

        // colors - handled by "Couleurs"
        //"<N&B>", "<Monochromie>", "<Bichromie>", "<Trichromie>", "<Quadrichromie>"
        // scenario author for an art-book; ignore
        // "<Art Book>"
        // Used for books; The authors for "dessin" and "couleurs"; ignore
        // "<Texte non illustré>"
        switch (names) {
            case "Indéterminé":
            case "Anonyme":
                addAuthor(Author.createUnknownAuthor(context), type, book);
                break;

            case "Art Book":
            case "Texte non illustré":
                // ignore these
                return;

            case "Collectif":
            default:
                addAuthor(Author.from(names), type, book);
                break;
        }
    }
}
