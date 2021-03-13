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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;

import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;

public abstract class JsoupSearchEngineBase
        extends SearchEngineBase {

    /** The description contains div tags which we remove to make the text shorter. */
    private static final Pattern DIV_PATTERN = Pattern.compile("(\n*\\s*<div>\\s*|\\s*</div>)");
    /** Convert "&amp;" to '&'. */
    private static final Pattern AMPERSAND_LITERAL = Pattern.compile("&amp;", Pattern.LITERAL);
    /** a CR is replaced with a space. */
    private static final Pattern CR_LITERAL = Pattern.compile("\n", Pattern.LITERAL);


    /** accumulate all Authors for this book. */
    protected final ArrayList<Author> mAuthors = new ArrayList<>();
    /** accumulate all Series for this book. */
    protected final ArrayList<Series> mSeries = new ArrayList<>();
    /** accumulate all Publishers for this book. */
    protected final ArrayList<Publisher> mPublishers = new ArrayList<>();

    /** Responsible for loading and parsing the web page. */
    @NonNull
    private final JsoupLoader mJsoupLoader;

    /**
     * Constructor.
     *
     * @param config the search engine configuration
     */
    protected JsoupSearchEngineBase(@NonNull final SearchEngineConfig config) {
        super(config);
        mJsoupLoader = new JsoupLoader();
    }

    /**
     * Constructor.
     *
     * @param config      the search engine configuration
     * @param charSetName to use
     */
    protected JsoupSearchEngineBase(@NonNull final SearchEngineConfig config,
                                    @NonNull final String charSetName) {
        this(config);
        mJsoupLoader.setCharSetName(charSetName);
    }

    /**
     * Load the url into a parsed {@link org.jsoup.nodes.Document}.
     *
     * @param url to load
     *
     * @return the document, or {@code null} if it failed to load while NOT causing a real error.
     * e.g. the website said 404
     *
     * @throws IOException on any failure except a FileNotFoundException.
     */
    @WorkerThread
    @Nullable
    public Document loadDocument(@NonNull final String url)
            throws IOException {
        try {
            final Function<String, Optional<TerminatorConnection>> conCreator = (String u) -> {
                try {
                    return Optional.of(createConnection(u));
                } catch (@NonNull final IOException ignore) {
                    return Optional.empty();
                }
            };

            return mJsoupLoader.loadDocument(getContext(), url, conCreator);

        } catch (@NonNull final FileNotFoundException e) {
            // we couldn't load the page
            return null;
        }
    }

    /**
     * Parses the downloaded {@link org.jsoup.nodes.Document}.
     * We only parse the <strong>first book</strong> found.
     * <p>
     * Implementations <strong>must</strong> call this super first
     * to ensure cached data is purged.
     *
     * @param document       to parse
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     * @param bookData       Bundle to update
     *
     * @throws IOException on failure
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @WorkerThread
    @CallSuper
    public void parse(@NonNull final Document document,
                      @NonNull final boolean[] fetchThumbnail,
                      @NonNull final Bundle bookData)
            throws IOException {
        // yes, instead of forcing child classes to call this super,
        // we could make them call a 'clear()' method instead.
        // But this way is more future oriented... maybe we'll need/can share more logic/data
        // between children... or change or mind later on.

        mAuthors.clear();
        mSeries.clear();
        mPublishers.clear();
    }

    /**
     * Filter a string of all non-digits. Used to clean isbn strings, years... etc.
     *
     * @param s      string to parse
     * @param isIsbn When set will also allow 'X' and 'x'
     *
     * @return stripped string
     */
    @NonNull
    protected String digits(@Nullable final String s,
                            final boolean isIsbn) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            // allows an X anywhere instead of just at the end; doesn't really matter.
            if (Character.isDigit(c) || (isIsbn && (c == 'X' || c == 'x'))) {
                sb.append(c);
            }
        }
        // ... but let empty Strings here just return.
        return sb.toString();
    }

    @NonNull
    protected String cleanText(@NonNull final String s) {
        String text = s.trim();
        // add more rules when needed.
        if (text.contains("&")) {
            text = AMPERSAND_LITERAL.matcher(text).replaceAll(Matcher.quoteReplacement("&"));
        }
        if (text.contains("<div>")) {
            // the div elements only create empty lines, we remove them to save screen space
            text = DIV_PATTERN.matcher(text).replaceAll("");
        }
        if (text.contains("\n")) {
            text = CR_LITERAL.matcher(text).replaceAll(" ").trim();
        }
        return text;
    }
}
