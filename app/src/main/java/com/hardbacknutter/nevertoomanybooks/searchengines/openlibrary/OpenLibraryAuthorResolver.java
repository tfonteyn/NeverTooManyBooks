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
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISNI;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * It's a little unreliable... see comments inside {@link #parse}.
 */
public final class OpenLibraryAuthorResolver
        implements AuthorResolver {

    // There can be MANY different keys... sigh.
    //  "remote_ids": {
    //    "viaf": "97113511",
    //    "goodreads": "3389",
    //    "storygraph": "c4c684e1-3f2b-48e4-b9cc-e819f61e0177",
    //    "isni": "0000000121446296",
    //    "librarything": "kingstephen-1",
    //    "amazon": "B000AQ0842",
    //    "wikidata": "Q39829",
    //    "inventaire": "wd:Q39829",
    //    "gnd": "118813250",
    //    "lc_naf": "n79063767",
    //    "bookbrainz": "128d9490-ee19-4270-a070-32e0a36847f5",
    //    "imdb": "nm0000175",
    //    "musicbrainz": "a4ac255f-9775-4c16-8642-7f51502e45dd"
    //  },
    // as far as I can tell most common/reliable ones are "viaf" and "wikidata"
    // Arbitrary decision: we're limiting us to the ISNI and these:
    private static final String AUTHOR_SIDS =
            "viaf|wikidata|goodreads|librarything|amazon|storygraph";

    @NonNull
    private final OpenLibrarySearchEngine searchEngine;
    @Nullable
    private final String authorUri;

    /**
     * Private Constructor.
     *
     * @param context      Current context
     * @param searchEngine the engine
     */
    private OpenLibraryAuthorResolver(@NonNull final Context context,
                                      @NonNull final OpenLibrarySearchEngine searchEngine) {
        this.searchEngine = searchEngine;
        authorUri = searchEngine
                .getEngineId()
                .getIdentifier()
                .flatMap(identifier -> identifier.getAuthorUri(context))
                .orElse(OpenLibrarySearchEngine.AUTHOR_URL);
    }

    /**
     * Private Constructor.
     *
     * @param context Current context
     * @param caller  a {@link Cancellable} which can forward requests
     *                to the (internal) {@link OpenLibrarySearchEngine}
     */
    @VisibleForTesting
    private OpenLibraryAuthorResolver(@NonNull final Context context,
                                      @Nullable final Cancellable caller) {
        this(context, (OpenLibrarySearchEngine) EngineId.OpenLibrary.createSearchEngine(context));
        searchEngine.setCaller(caller);
    }

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param searchEngine the engine which is requesting this resolver
     *
     * @return new instance
     */
    @NonNull
    public static AuthorResolver create(@NonNull final Context context,
                                        @NonNull final SearchEngine searchEngine) {
        if (searchEngine instanceof OpenLibrarySearchEngine) {
            return new OpenLibraryAuthorResolver(context,
                                                 (OpenLibrarySearchEngine) searchEngine);
        } else {
            return new OpenLibraryAuthorResolver(context, searchEngine);
        }
    }

    @Override
    public boolean resolve(@NonNull final Context context,
                           @NonNull final Author author)
            throws SearchException, CredentialsException {
        // the user can delete it...
        if (authorUri == null) {
            return false;
        }

        // If we already have a real-author set, we're done.
        if (author.getRealAuthor() != null) {
            return false;
        }

        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_OPEN_LIBRARY);
        // no id, give up
        if (oIv.isEmpty()) {
            return false;
        }

        final String url = String.format(authorUri, oIv.get()) + ".json";

        final FutureHttpGet<String> futureHttpGet = searchEngine.createGetDocumentRequest(context);
        try {
            final String response = futureHttpGet.getAsString(url, (con, s) -> s);
            final JSONObject jsonObject = new JSONObject(response);
            if (!searchEngine.isCancelled()) {
                final Author found = parse(jsonObject);
                if (found != null) {
                    author.setRealAuthor(found);
                    return true;
                }
            }
        } catch (@NonNull final StorageException | IOException | JSONException e) {
            throw new SearchException(searchEngine.getEngineId(), e);
        }
        return false;
    }

    @VisibleForTesting
    @Nullable
    Author parse(@NonNull final JSONObject document) {
        // As so often with OpenLibrary, the confusion starts at the very start...

        // This is seemingly the name as it would appear on a book
        // 1. "James Tiptree, Jr."
        // 2. "Kurt Vonnegut"
        // 3. "Stephen King"
        // 4. "Philip K. Dick"
        final String name = document.optString("name");
        // A variant of "name" ?
        // 1. "James Tiptree"
        // 2. "Vonnegut, Kurt."
        // 3. "King, Stephen"
        // 4. "Dick, Philip K."
        final String personalName = document.optString("personal_name");
        // If the above was a pseudonym, this seems to be the real-name
        // 1. "Alice Bradley Sheldon"
        // 2. [absent]
        // 3. [absent]
        // 4. [absent]
        final String fullerName = document.optString("fuller_name");
        // real-name(s), but not necessarily what appears on a book
        // 1. "Alice B. Sheldon"
        // 2. "Kurt Vonnegut, Jr."
        // 3. "Stephen king"
        // 4. "Philip Kindred Dick"
        final JSONArray alternateNames = document.optJSONArray("alternate_names");

        // We've seen, BUT not used consistently (or at all)...
        // "Pseudonym"  for "Isaac Asimov" ?? but his russian name is not even listed
        // "org" : e.g. ""James S. A. Corey""
        final String entityType = document.optString("entity_type");

        // ok... best guess/attempt here
        final Author author;
        if (!name.isEmpty()) {
            author = Author.from(name);
        } else if (!personalName.isEmpty()) {
            author = Author.from(personalName);
        } else {
            return null;
        }

        // entityType and fullerName/alternateNames ...
        // from a couple of examples, it's likely that fullerName is used
        // when "name" is a Pseudonym.
        // "alternateNames" is rather useless/unreliable in this context..
        if (!fullerName.isEmpty()) {
            final Author ps = Author.from(fullerName);
            // there is no OL number, so we can't easily lookup identifiers...
            author.setRealAuthor(ps);
        }

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

        return author;
    }
}
