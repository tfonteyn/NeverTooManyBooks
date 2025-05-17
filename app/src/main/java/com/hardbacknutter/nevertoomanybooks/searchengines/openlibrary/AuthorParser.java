/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISNI;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONObject;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Shared parser code between the SearchEngine and the AuthorResolver.
 */
public class AuthorParser {

    private static final String TAG = "AuthorParser";

    /**
     * <a href="https://github.com/internetarchive/openlibrary/blob/master/openlibrary/plugins/openlibrary/config/author/identifiers.yml">
     * openlibrary author/identifiers.yml</a>.
     * <p>
     * Example:
     * <pre>{@code
     *     "remote_ids": {
     *       "viaf": "97113511",
     *       "goodreads": "3389",
     *       "storygraph": "c4c684e1-3f2b-48e4-b9cc-e819f61e0177",
     *       "isni": "0000000121446296",
     *       "librarything": "kingstephen-1",
     *       "amazon": "B000AQ0842",
     *       "wikidata": "Q39829",
     *       "inventaire": "wd:Q39829",
     *       "gnd": "118813250",
     *       "lc_naf": "n79063767",
     *       "bookbrainz": "128d9490-ee19-4270-a070-32e0a36847f5",
     *       "imdb": "nm0000175",
     *       "musicbrainz": "a4ac255f-9775-4c16-8642-7f51502e45dd"
     *     },
     * }</pre>
     * As far as I can tell most common/reliable ones are "viaf" and "wikidata"
     * Arbitrary decision: we're limiting us to the ISNI and these:
     */
    private static final String AUTHOR_SIDS =
            "viaf|wikidata|goodreads|librarything|amazon|storygraph";

    private final DateParser<PartialDate> dateParser = new PartialDateParser();
    @NonNull
    private final Locale locale;
    @NonNull
    private final OpenLibrarySearchEngine searchEngine;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param searchEngine the engine
     */
    public AuthorParser(@NonNull final Context context,
                        @NonNull final OpenLibrarySearchEngine searchEngine) {
        this.searchEngine = searchEngine;
        this.locale = searchEngine.getLocale(context);
    }

    /**
     * Parse an author json-blob.
     * This method <strong>should</strong> be able to deal with a json blob coming from.
     * the <a href="https://openlibrary.org/dev/docs/api/authors">author API</a>
     * as a direct OLID link or as a query.
     * <p>
     * A direct OLID link:
     * <a href="https://openlibrary.org/authors/OL34221A.json">OL34221A.json</a>
     * <pre>
     * {@code
     * {
     *   "name": "Isaac Asimov",
     *   "key": "/authors/OL34221A",
     *   "entity_type": "Pseudonym",
     *   "birth_date": "2 January 1920",
     *   "personal_name": "Isaac Asimov",
     *   "remote_ids": {
     *     "viaf": "24597135",
     *     "wikidata": "Q34981",
     *     "isni": "0000000122590564"
     *   },
     *   "source_records": [
     *     ...SNIP...
     *   ],
     *   "photos": [
     *     7425151,
     *     7893107,
     *     7241360,
     *     5544324,
     *     -1
     *   ],
     *   "death_date": "6 April 1992",
     *   "type": {
     *     "key": "/type/author"
     *   },
     *   "title": "Ph.D.",
     *   "alternate_names": [
     *     "Aximofu",
     *     "Azimov Ayzek",
     *     "Dr. A",
     *     "Isaac Asimoc",
     *     "Isaak Asimov",
     *     "Isaak Judovič",
     *     "Issac Azimov",
     *     "The Good Doctor"
     *   ],
     *   "role": "ed",
     *   "links": [
     *     {
     *       "title": "French Wikipédia Page",
     *       "url": "http://fr.wikipedia.org/wiki/Isaac_Asimov",
     *       "type": {
     *         "key": "/type/link"
     *       }
     *     }
     *   ],
     *   "bio": "Asimov was born sometime between O.....",
     *   "latest_revision": 91,
     *   "revision": 91,
     *   "created": {
     *     "type": "/type/datetime",
     *     "value": "2008-04-01T03:28:50.625462"
     *   },
     *   "last_modified": {
     *     "type": "/type/datetime",
     *     "value": "2025-01-13T16:14:01.971754"
     *   }
     * }
     * }
     * </pre>
     * <p>
     * A query:
     * <a href="https://openlibrary.org/search/authors.json?q=isaac+asimov">q=isaac asimov</a>
     * <pre>
     * {@code
     * {
     *   "numFound": 12,
     *   "start": 0,
     *   "numFoundExact": true,
     *   "docs": [
     *     {
     *       "alternate_names": [
     *         "Aximofu",
     *         "Azimov Ayzek",
     *         "Dr. A",
     *         "Isaac Asimoc",
     *         "Isaak Asimov",
     *         "Isaak Judovič",
     *         "Issac Azimov",
     *         "The Good Doctor"
     *       ],
     *       "birth_date": "2 January 1920",
     *       "death_date": "6 April 1992",
     *       "key": "OL34221A",
     *       "name": "Isaac Asimov",
     *       "top_subjects": [
     *         "Science fiction",
     *         "American Science fiction",
     *         "Juvenile literature",
     *         "Fiction",
     *         "Science",
     *         "Fiction, science fiction, general",
     *         "Short stories",
     *         "History",
     *         "Children's fiction",
     *         "English Science fiction"
     *       ],
     *       "top_work": "Foundation",
     *       "type": "author",
     *       "work_count": 1358,
     *       "ratings_average": 4.118677,
     *       "ratings_sortable": 4.076448,
     *       "ratings_count": 1542,
     *       "ratings_count_1": 21,
     *       "ratings_count_2": 57,
     *       "ratings_count_3": 270,
     *       "ratings_count_4": 564,
     *       "ratings_count_5": 630,
     *       "want_to_read_count": 10311,
     *       "already_read_count": 2988,
     *       "currently_reading_count": 504,
     *       "readinglog_count": 13803,
     *       "_version_": 1828575192250580992
     *     },
     *     ...
     *   ]
     * }
     * }
     * </pre>
     *
     * @param context  Current context
     * @param document to parse
     *
     * @return the author, or {@code null} on failure
     */
    @Nullable
    Author parse(@NonNull final Context context,
                 @NonNull final JSONObject document) {
        // As so often with OpenLibrary, the confusion starts at the very start...

        // This is seemingly the name as it would appear on a book
        // 1. "James Tiptree, Jr."
        // 2. "Kurt Vonnegut"
        // 3. "Stephen King"
        // 4. "Philip K. Dick"
        //
        // #search : present
        // #resolve: present
        final String name = document.optString("name");
        // A variant of "name" ?
        // 1. "James Tiptree"
        // 2. "Vonnegut, Kurt."
        // 3. "King, Stephen"
        // 4. "Dick, Philip K."
        //
        // #search : absent
        // #resolve: present
        final String personalName = document.optString("personal_name");
        // If the above was a pseudonym, this seems to be the real-name
        // 1. "Alice Bradley Sheldon"
        // 2. [absent]
        // 3. [absent]
        // 4. [absent]
        //
        // #search : absent
        // #resolve: present
        final String fullerName = document.optString("fuller_name");
        // real-name(s), but not necessarily what appears on a book
        // 1. "Alice B. Sheldon"
        // 2. "Kurt Vonnegut, Jr."
        // 3. "Stephen king"
        // 4. "Philip Kindred Dick"
        //
        // #search : present
        // #resolve: present
        final JSONArray alternateNames = document.optJSONArray("alternate_names");

        // We've seen, BUT not used consistently (or at all)...
        // - "Pseudonym"  for "Isaac Asimov" ?? but his russian name is not even listed
        // - "org" : e.g. ""James S. A. Corey""
        //
        // #search : absent
        // #resolve: present
        final String entityType = document.optString("entity_type");

        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger().d(TAG,
                                        "name=" + name,
                                        "personalName=" + personalName,
                                        "fullerName=" + fullerName,
                                        "alternateNames=" + alternateNames,
                                        "entityType=" + entityType);
        }

        // ok... best guess/attempt here
        final Author author;
        if (!name.isEmpty()) {
            author = Author.from(name);
        } else if (!personalName.isEmpty()) {
            author = Author.from(personalName);
        } else {
            return null;
        }

        // fullerName/alternateNames ...
        // from a couple of examples, it's likely that fullerName is used
        // when "name" is a Pseudonym.
        // "alternateNames" is rather useless/unreliable in this context..
        if (!fullerName.isEmpty()) {
            final Author ps = Author.from(fullerName);
            // there is no OL number, so we can't easily lookup identifiers...
            author.setRealAuthor(ps);
        }

        // #search : present
        // #resolve: present
        dateParser.parse(document.optString("birth_date"), locale).ifPresent(
                date -> author.setBirthDate(date.getIsoString()));
        // #search : present
        // #resolve: present
        dateParser.parse(document.optString("death_date"), locale).ifPresent(
                date -> author.setDeathDate(date.getIsoString()));

        // The sid can be present in two different formats (this is OpenLibrary... sigh...)
        // #search   "key": "OL34221A"
        // #resolve "key": "/authors/OL34221A"
        String sid = document.optString("key");
        if (!sid.isEmpty()) {
            if (sid.startsWith("/authors/")) {
                sid = sid.substring(9);
            }
            author.setIdentifierValue(Identifier.SID_OPEN_LIBRARY, sid);
        }

        // #search : absent
        // #resolve: present
        final JSONObject remoteIds = document.optJSONObject("remote_ids");
        if (remoteIds != null) {
            for (final String key : remoteIds.keySet()) {
                // The ISNI key is verified/normalized as needed
                if (Identifier.SID_ISNI.equals(key)) {
                    final String s = remoteIds.optString(Identifier.SID_ISNI);
                    if (!s.isEmpty()) {
                        final ISNI isni = new ISNI(s);
                        if (isni.isValid()) {
                            author.setIdentifierValue(Identifier.SID_ISNI, isni.getIsni());
                        }
                    }
                } else if (AUTHOR_SIDS.contains(key)) {
                    final String s = remoteIds.optString(key);
                    if (!s.isEmpty()) {
                        author.setIdentifierValue(key, s);
                    }
                }
            }
        }

        // #search: absent
        // #resolve: present (if there are any)
        // We're NOT checking the "photos" key explicitly,
        // just try getting one using the OLID
        try {
            searchEngine.fetchImageByKey(context, 'a', "OLID", sid, 0, null)
                        .ifPresent(author::setTmpPictureFileSpec);
        } catch (@NonNull final StorageException ignore) {
            // ignore; keep in mind that OpenLibrary often fails to return images even
            // when they exist
        }
        return author;
    }
}
