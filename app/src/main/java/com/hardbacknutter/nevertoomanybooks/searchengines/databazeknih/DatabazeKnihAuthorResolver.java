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

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class DatabazeKnihAuthorResolver
        implements AuthorResolver {
    private static final String TAG = "DatabazeKnihAuthorRes";
    @NonNull
    private final Context context;
    // FIXME: we only use the reference to call #loadDocument... should decouple.
    @NonNull
    private final DatabazeKnihSearchEngine searchEngine;
    @NonNull
    private final Identifier identifier;


    public DatabazeKnihAuthorResolver(@NonNull final Context context,
                                      @NonNull final DatabazeKnihSearchEngine searchEngine) {
        this.context = context;
        this.searchEngine = searchEngine;
        //noinspection OptionalGetWithoutIsPresent
        identifier = searchEngine.getEngineId().getIdentifier().get();
    }

    @Override
    public boolean resolve(@NonNull final Author author)
            throws SearchException, CredentialsException {
        // If we already have a real-author set, we're done.
        if (author.getRealAuthor() != null) {
            return false;
        }

        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH);
        // no id, give up
        if (oIv.isEmpty()) {
            return false;
        }

        final Optional<String> oAuthorUri = identifier.getAuthorUri(context);
        // the user can delete it...
        if (oAuthorUri.isEmpty()) {
            return false;
        }

        final String url = String.format(oAuthorUri.get(), oIv.get());

        final Document document = searchEngine.loadDocument(context, url, null);
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

        return false;
    }
}
