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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * Connects to the Bedetheque website to resolve author pseudonyms.
 * <p>
 * Aside of Bedetheque itself, this class is also used by StripInfo and LastDodo.
 */
public class AuthorResolver {

    @NonNull
    private final Context context;
    @NonNull
    private final BedethequeSearchEngine searchEngine;
    @NonNull
    private final Locale locale;

    /**
     * Constructor used by the {@link BedethequeSearchEngine} itself.
     *
     * @param context      Current context
     * @param searchEngine the engine
     */
    AuthorResolver(@NonNull final Context context,
                   @NonNull final BedethequeSearchEngine searchEngine) {
        this.context = context;
        this.searchEngine = searchEngine;
        locale = searchEngine.getLocale(context);
    }

    /**
     * Constructor to use by 'other' search engines.
     *
     * @param context Current context
     * @param caller  a {@link Cancellable} which can forward requests
     *                to the (internal) {@link BedethequeSearchEngine}
     */
    public AuthorResolver(@NonNull final Context context,
                          @Nullable final Cancellable caller) {
        this.context = context;
        searchEngine = (BedethequeSearchEngine) EngineId.Bedetheque.createSearchEngine();
        searchEngine.setCaller(caller);
        locale = searchEngine.getLocale(context);
    }

    /**
     * Update the given {@link Author} with any missing diacritics and resolve pen-names.
     *
     * @param author to lookup
     *
     * @return {@code true} if the {@link Author} was modified; {@code false} otherwise
     */
    public boolean resolve(@NonNull final Author author)
            throws SearchException, CredentialsException {

        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
        // We SHOULD pass in the book-locale here...
        authorDao.refresh(context, author, false, locale);

        // If we already have a real-author set, we're done.
        if (author.getRealAuthor() != null) {
            return false;
        }

        // Check if we have the author in the cache
        final BdtAuthor bdtAuthor = new BdtAuthor(context, searchEngine, author);
        boolean found = bdtAuthor.findInCache();
        if (!found) {
            // If not resolved / not found,
            final AuthorListLoader pageLoader = new AuthorListLoader(context, searchEngine);
            final char c1 = pageLoader.firstChar(author.getFamilyName());
            // and the list-page was never fetched before,
            // URGENT: we need to add a "purge cache" option on the preference fragment
            if (!pageLoader.isAuthorPageCached(c1)) {
                // go fetch the the list-page on which the author should/could be
                if (pageLoader.fetch(c1)) {
                    // If the author was on the list page, we should find it in the cache now.
                    found = bdtAuthor.findInCache();
                }
            }
        }

        // If the author is not resolved, try fetch the author details page
        if (!found && !bdtAuthor.lookup()) {
            // Not found at all.
            return false;
        }

        final String resolvedName = bdtAuthor.getResolvedName();
        // If the author does not use a pen-name, we're done.
        if (resolvedName == null) {
            return false;
        }

        // The name was a pen-name and we have resolved it to their real name
        // Add it accordingly to the original Author object
        final Author realAuthor = Author.from(resolvedName);
        authorDao.refresh(context, realAuthor, false, locale);
        author.setRealAuthor(realAuthor);

        // While resolving, the name of the bdtAuthor CAN be overwritten.
        // Check that it still MATCHES the original author name
        final Author penAuthor = Author.from(bdtAuthor.getName());
        if (penAuthor.hashCodeOfNameOnly() == author.hashCodeOfNameOnly()) {
            // It does, we now overwrite the original name; this will correct any diacritics
            author.setName(penAuthor.getFamilyName(), penAuthor.getGivenNames());
        }

        return true;
    }
}
