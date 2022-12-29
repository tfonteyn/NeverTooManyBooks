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
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.network.Throttler;
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

    public static final Throttler THROTTLER = new Throttler(1_000);
    private static final Pattern PUB_DATE = Pattern.compile("\\d\\d/\\d\\d\\d\\d");
    private static final Pattern SERIES_WITH_BRACKETS = Pattern.compile("(.*)\\((.*)\\)");

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param config the search engine configuration
     */
    @Keep
    public BedethequeSearchEngine(@NonNull final SearchEngineConfig config) {
        super(config);

        // Setup BEFORE doing first request!
        ServiceLocator.getInstance().getCookieManager();
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context context,
                               @NonNull final String validIsbn,
                               @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Bundle bookData = ServiceLocator.newBundle();

        final String url = getHostUrl() + "/search/albums?RechISBN=" + validIsbn;
        final Document document = loadDocument(context, url);
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            parseMultiResult(context, document, fetchCovers, bookData);
        }
        return bookData;
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param bookData    Bundle to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     */
    @WorkerThread
    private void parseMultiResult(@NonNull final Context context,
                                  @NonNull final Document document,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Bundle bookData)
            throws StorageException, SearchException, CredentialsException {

        // Grab the first search result, and redirect to that page
        final Element section = document.selectFirst("ul.search-list");
        if (section != null) {
            final Element urlElement = section.selectFirst("a");
            if (urlElement != null) {
                final String url = urlElement.attr("href");
                if (!url.isBlank()) {
                    final Document redirected = loadDocument(context, url);
                    if (!isCancelled()) {
                        parse(context, redirected, fetchCovers, bookData);
                    }
                }
            }
        }
    }

    @Override
    @VisibleForTesting
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Bundle bookData)
            throws StorageException, SearchException, CredentialsException {
        super.parse(context, document, fetchCovers, bookData);

        final Element section = document.selectFirst(
                "div.tab_content_liste_albums > ul.infos-albums");
        if (section != null) {
            int lastAuthorType = -1;
            Series currentSeries = null;

            final Elements labels = section.select("li > label");
            for (final Element label : labels) {
                final String text = label.text();
                // check for multiple author entries of the same type
                if (text.isBlank() && lastAuthorType != -1) {
                    final Element span = label.nextElementSibling();
                    if (span != null) {
                        processAuthor(span, lastAuthorType);
                    }
                    continue;
                }
                lastAuthorType = -1;

                //noinspection SwitchStatementWithoutDefaultBranch
                switch (text) {
                    case "Titre :": {
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            bookData.putString(DBKey.TITLE, textNode.toString().trim());
                        }
                        break;
                    }
                    case "EAN/ISBN :": {
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            bookData.putString(DBKey.BOOK_ISBN, span.text());
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
                            bookData.putString(DBKey.BOOK_PUBLICATION__DATE, date);
                        }
                        break;
                    }
                    case "Planches :": {
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            bookData.putString(DBKey.PAGE_COUNT, span.text());
                        }
                        break;
                    }
                    case "Format : ": {
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            bookData.putString(DBKey.FORMAT, span.text());
                        }
                        break;
                    }
                    case "Série :": {
                        final Node textNode = label.nextSibling();
                        if (textNode != null) {
                            String name = textNode.toString().trim();
                            // Series are formatted like "Fond du monde (Le)"
                            // If we find this pattern, transform it to "Le Fond du monde"
                            final Matcher matcher = SERIES_WITH_BRACKETS.matcher(name);
                            if (matcher.find()) {
                                final String n = matcher.group(1);
                                final String prefix = matcher.group(2);
                                if (prefix != null) {
                                    name = prefix + ' ' + n;
                                }
                            }
                            currentSeries = Series.from(name);
                        }
                        break;
                    }
                    case "Tome :": {
                        final Node textNode = label.nextSibling();
                        if (textNode != null && currentSeries != null) {
                            currentSeries.setNumber(textNode.toString().trim());
                        }
                        break;
                    }
                    case "Editeur :": {
                        final Element span = label.nextElementSibling();
                        if (span != null) {
                            publisherList.add(new Publisher(span.text()));
                        }
                        break;
                    }
                    case "Scénario :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_WRITER;
                            processAuthor(a, Author.TYPE_WRITER);
                        }
                        break;
                    }
                    case "Dessin :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_ARTIST;
                            processAuthor(a, Author.TYPE_ARTIST);
                        }
                        break;
                    }
                    case "Couleurs :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_COLORIST;
                            processAuthor(a, Author.TYPE_COLORIST);
                        }
                        break;
                    }
                    case "Couverture :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_COVER_ARTIST;
                            processAuthor(a, Author.TYPE_COVER_ARTIST);
                        }
                        break;
                    }
                    case "Préface :": {
                        final Element a = label.nextElementSibling();
                        if (a != null) {
                            lastAuthorType = Author.TYPE_FOREWORD;
                            processAuthor(a, Author.TYPE_FOREWORD);
                        }
                        break;
                    }

                    // There is no field for the language :(
                }
            }

            if (currentSeries != null) {
                seriesList.add(currentSeries);
            }

            if (!authorList.isEmpty()) {
                final AuthorResolver resolver = new AuthorResolver(context, this);
                for (final Author author : authorList) {
                    resolver.resolve(context, author);
                }
                bookData.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, authorList);
            }

            if (!seriesList.isEmpty()) {
                bookData.putParcelableArrayList(Book.BKEY_SERIES_LIST, seriesList);
            }

            if (!publisherList.isEmpty()) {
                bookData.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, publisherList);
            }

            // Unless present, add the default language
            if (!bookData.containsKey(DBKey.LANGUAGE)) {
                bookData.putString(DBKey.LANGUAGE, "fra");
            }

            if (isCancelled()) {
                return;
            }

            if (fetchCovers[0]) {
                final String isbn = bookData.getString(DBKey.BOOK_ISBN);
                parseCovers(document, isbn, bookData);
            }
        }
    }

    private void parseCovers(@NonNull final Document document,
                             @Nullable final String isbn,
                             @NonNull final Bundle bookData)
            throws StorageException {

        final Element a = document.selectFirst(
                "div.bandeau-principal > div.bandeau-image > a");
        if (a != null) {
            final String url = a.attr("href");
            final String fileSpec = saveImage(url, isbn, 0, null);
            if (fileSpec != null) {
                final ArrayList<String> list = new ArrayList<>();
                list.add(fileSpec);
                bookData.putStringArrayList(
                        SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0], list);
            }

        }
    }

    private void processAuthor(@NonNull final Element a,
                               @Author.Type final int currentAuthorType) {

        final String names = a.text();
        final Author currentAuthor = Author.from(names);
        boolean add = true;
        // check if already present
        for (final Author author : authorList) {
            if (author.equals(currentAuthor)) {
                // merge types.
                author.addType(currentAuthorType);
                add = false;
                // keep looping
            }
        }

        if (add) {
            currentAuthor.setType(currentAuthorType);
            authorList.add(currentAuthor);
        }
    }
}
