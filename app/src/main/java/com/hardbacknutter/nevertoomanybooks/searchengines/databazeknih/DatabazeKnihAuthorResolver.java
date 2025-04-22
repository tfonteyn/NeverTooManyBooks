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

package com.hardbacknutter.nevertoomanybooks.searchengines.databazeknih;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Connects to the DatabazeKnih website to resolve author pseudonyms.
 * <p>
 * {@link AuthorResolver#resolve(Context, Author)} relies on the {@link Identifier#SID_DATABAZE_KNIH}
 * being correct to do  the lookup. It will:
 * <ul>
 *     <li>add the {@link Author#setRealAuthor(Author)} if applicable</li>
 * </ul>
 */
public final class DatabazeKnihAuthorResolver
        implements AuthorResolver {
    private static final String TAG = "DatabazeKnihAuthorRes";

    @NonNull
    private final DatabazeKnihSearchEngine searchEngine;
    @Nullable
    private final String authorUri;

    /**
     * Private Constructor.
     *
     * @param context      Current context
     * @param searchEngine the engine
     */
    private DatabazeKnihAuthorResolver(@NonNull final Context context,
                                       @NonNull final DatabazeKnihSearchEngine searchEngine) {
        this.searchEngine = searchEngine;
        // The engine is hardcoded/defined with the identifier,
        // but the author-uri can be absent
        authorUri = this.searchEngine
                .getEngineId()
                .getIdentifier()
                .flatMap(identifier -> identifier.getAuthorUri(context))
                .orElse(null);
    }

    /**
     * Private Constructor.
     *
     * @param context Current context
     * @param caller  a {@link Cancellable} which can forward requests
     *                to the (internal) {@link DatabazeKnihSearchEngine}
     */
    @VisibleForTesting
    private DatabazeKnihAuthorResolver(@NonNull final Context context,
                                       @Nullable final Cancellable caller) {
        this(context, (DatabazeKnihSearchEngine) EngineId.DatabazeKnih.createSearchEngine(context));
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
        if (searchEngine instanceof DatabazeKnihSearchEngine) {
            return new DatabazeKnihAuthorResolver(context,
                                                  (DatabazeKnihSearchEngine) searchEngine);
        } else {
            return new DatabazeKnihAuthorResolver(context, searchEngine);
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

        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH);
        // no id, give up
        if (oIv.isEmpty()) {
            return false;
        }

        final String url = String.format(authorUri, oIv.get());

        final Document document = searchEngine.loadDocument(context, url, null);
        if (!searchEngine.isCancelled()) {
            Element element = document.selectFirst("div#left_less");
            if (element != null) {
                element = element.selectFirst("h1");
                if (element != null) {
                    final String text = element.text();
                    if (!text.isEmpty()) {
                        author.setRealAuthor(Author.from(text));
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
