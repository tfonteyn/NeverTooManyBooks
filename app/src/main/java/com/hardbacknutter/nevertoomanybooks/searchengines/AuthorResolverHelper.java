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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.util.logger.LoggerFactory;

public final class AuthorResolverHelper {

    private static final String TAG = "AuthorResolverHelper";

    private AuthorResolverHelper() {
    }

    /**
     * Run the resolvers for the given engine, for all authors of the given book.
     * Uses the Book, or when not available, the site {@link Locale}.
     * <p>
     * The authors are modified as needed, but <strong>NOT written</strong> to the database.
     * <p>
     * Any {@link SearchException} will cause an abort but is deemed NOT critical.
     * This may result in some authors having been processed, while others have not.
     *
     * @param context      Current context
     * @param searchEngine requesting the resolve action
     * @param book         with authors
     *
     * @throws CredentialsException on authentication/login failures
     */
    @WorkerThread
    public static void resolve(@NonNull final Context context,
                               @NonNull final SearchEngine searchEngine,
                               @NonNull final Book book)
            throws CredentialsException {

        final List<AuthorResolver> resolvers = AuthorResolverFactory
                .getResolvers(context, searchEngine);

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        final Locale locale = book.getLocale(userLocale)
                                  .orElseGet(() -> searchEngine.getLocale(context));

        try {
            resolve(context, locale, book.getAuthors(), resolvers, false, false);
        } catch (@NonNull final DaoWriteException na) {
            // not applicable as we pass in "doStore=false"
        } catch (@NonNull final SearchException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }
    }

    /**
     * Run the resolvers.
     * <p>
     * Note that the resolvers will access the network, hence this method must
     * only be called from a WorkerThread.
     * <p>
     * Any {@link SearchException} or {@code DaoWriteException} will cause an abort.
     * When {@code doStore} is {@code true} all database writes happen in a transaction
     * which will be aborted, but the authors in the list authors may have been modified!
     * <strong>ALL results should be discarded in this case</strong>
     *
     * @param context   Current context
     * @param locale    for author updates
     * @param authors   list to process
     * @param resolvers to use
     * @param doMerge   flag;
     *                  {@code true} to force a lookup/merge with the database BEFORE
     *                  resolving an author. {@code false} to skip.
     * @param doStore   flag;
     *                  {@code true} to write all modifications directly to the database,
     *                  {@code false} not to.
     *
     * @return {@code true} if the any {@link Author}s were modified; {@code false} otherwise
     *
     * @throws CredentialsException on authentication/login failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws DaoWriteException    on failure
     */
    @WorkerThread
    static boolean resolve(@NonNull final Context context,
                           @NonNull final Locale locale,
                           @NonNull final List<Author> authors,
                           @NonNull final List<AuthorResolver> resolvers,
                           final boolean doMerge,
                           final boolean doStore)
            throws CredentialsException, SearchException, DaoWriteException {

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final AuthorDao authorDao = serviceLocator.getAuthorDao();
        final SynchronizedDb db = serviceLocator.getDb();

        boolean result = false;
        Synchronizer.SyncLock txLock = null;
        try {
            if (doStore) {
                txLock = db.beginTransaction(true);
            }
            for (final Author author : authors) {
                if (doMerge) {
                    // Check if we already know this author, and merge its data if possible
                    authorDao.fixId(context, author, locale);
                    if (author.getId() > 0) {
                        authorDao.findById(author.getId())
                                 .ifPresent(value -> author.merge(value, false));
                    }
                }

                for (final AuthorResolver resolver : resolvers) {
                    final boolean modified = resolver.resolve(context, author);
                    if (modified && doStore) {
                        authorDao.update(context, author, locale);
                    }
                    result = modified || result;
                }
            }
            if (doStore) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (doStore) {
                db.endTransaction(txLock);
            }
        }
        return result;
    }
}
