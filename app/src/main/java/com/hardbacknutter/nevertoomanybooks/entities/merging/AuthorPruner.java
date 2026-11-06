/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.entities.merging;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Collection;
import java.util.Locale;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

public class AuthorPruner
        implements Pruner<Author> {

    private final AuthorDao dao;
    private final EntityMerger<Author> mergeHelper;
    @Nullable
    private IdFixer<Author> idFixer;

    /**
     * Constructor.
     */
    public AuthorPruner() {
        dao = ServiceLocator.getInstance().getAuthorDao();
        this.idFixer = dao;
        mergeHelper = new EntityMerger<>();
    }

    /**
     * Test constructor. The {@link #idFixer} is set to {@code null}.
     *
     * @return new instance
     */
    @VisibleForTesting
    @NonNull
    public static AuthorPruner forTest() {
        final AuthorPruner pruner = new AuthorPruner();
        pruner.idFixer = null;
        return pruner;
    }

    /**
     * Remove duplicates.
     * Consolidates author/- and author/role.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public boolean prune(@NonNull final Context context,
                         @NonNull final Collection<Author> list,
                         @NonNull final Function<Author, Locale> localeSupplier) {
        // Reminder: only abort if empty. We rely on 'fixId' being called for ALL list values.
        if (list.isEmpty()) {
            return false;
        }

        boolean modified = false;

        // Remove 'unknown' authors if there are other 'known' authors.
        if (list.size() > 1) {
            final String unknown = context.getString(R.string.unknown_author);
            // remove ALL unknown author entries.
            // Note we check on exact equality as the incoming name
            // would have come from the same resource string
            modified = list.removeIf(author -> author.getFamilyName().equals(unknown));
            if (list.isEmpty()) {
                // All authors were unknown and hence removed,
                // re-add a single unknown author.
                final Author unknownAuthor = Author.createUnknownAuthor(context);
                // shortcut: no need to drop through and engage the AuthorMergeHelper,
                // just fix the id manually here
                dao.fixId(context, unknownAuthor, localeSupplier.apply(unknownAuthor));
                list.add(unknownAuthor);
                return true;
            }
        }

        return mergeHelper.merge(context, list, localeSupplier, idFixer)
               || modified;
    }
}
