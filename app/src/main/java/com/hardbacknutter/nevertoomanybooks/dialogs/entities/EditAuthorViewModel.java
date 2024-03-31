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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.ParcelableDialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

/**
 * Visibility of the {@link DBKey#AUTHOR_REAL_AUTHOR} and {@link DBKey#AUTHOR_TYPE__BITMASK}
 * is based on <strong>global USAGE</strong>.
 */
public class EditAuthorViewModel
        extends ViewModel {

    private static final String TAG = "EditAuthorViewModel";

    /** The Author we're editing. */
    private Author author;

    /**
     * Current edit. We don't use the real-author directly to avoid unneeded validation
     * at each key-stroke from the user.
     *
     * @see #currentRealAuthorName
     */
    private Author currentEdit;
    /** Current edit. */
    @Nullable
    private String currentRealAuthorName;

    private boolean useRealAuthorName;
    private boolean useAuthorType;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (author == null) {
            author = Objects.requireNonNull(
                    args.getParcelable(ParcelableDialogLauncher.BKEY_ITEM),
                    ParcelableDialogLauncher.BKEY_ITEM);

            final ServiceLocator serviceLocator = ServiceLocator.getInstance();
            useRealAuthorName = serviceLocator.isFieldEnabled(DBKey.AUTHOR_REAL_AUTHOR);
            useAuthorType = serviceLocator.isFieldEnabled(DBKey.AUTHOR_TYPE__BITMASK);

            currentEdit = new Author(author);
            final Author tmp = currentEdit.getRealAuthor();
            currentRealAuthorName = tmp != null ? tmp.getFormattedName(false) : null;
        }
    }

    public boolean useRealAuthorName() {
        return useRealAuthorName;
    }

    public boolean useAuthorType() {
        return useAuthorType;
    }

    @NonNull
    public Author getAuthor() {
        return author;
    }

    @NonNull
    public Author getCurrentEdit() {
        return currentEdit;
    }

    @Nullable
    public String getCurrentRealAuthorName() {
        return currentRealAuthorName;
    }

    public void setCurrentRealAuthorName(@Nullable final String name) {
        this.currentRealAuthorName = name;
    }

    /**
     * Check if there is a real-author set, and whether it is an existing one.
     *
     * @param context Current context
     * @param locale  Locale to use if the item has none set
     * @param create  {@code true} if a non-existent Author should be created
     *
     * @return {@code true} if the 'real' Author was validated and set.
     *         {@code false} if the real author did not exist and we were not allowed to create them
     *         (or if creating threw an error)
     */
    public boolean validateAndSetRealAuthor(@NonNull final Context context,
                                            @NonNull final Locale locale,
                                            final boolean create) {
        // no pseudonym?
        if (currentRealAuthorName == null || currentRealAuthorName.isBlank()) {
            currentEdit.setRealAuthor(null);
            return true;
        }

        // If we have a pseudonym set, it must be a valid/existing author.
        final Author tmpRealAuthor = Author.from(currentRealAuthorName);

        final AuthorDao dao = ServiceLocator.getInstance().getAuthorDao();
        final Optional<Author> existing = dao.findByName(context, tmpRealAuthor, locale);

        if (existing.isPresent()) {
            currentEdit.setRealAuthor(existing.get());
            return true;
        }

        // If for whatever reason we have a non-existing real-author set
        // while the user has globally switched off support for real-author,
        // we simply remove that real-author and return success.
        if (!useRealAuthorName) {
            currentEdit.setRealAuthor(null);
            return true;
        }

        if (!create) {
            // force the caller to ask the user to try again the 2nd time allowing creating.
            return false;
        }

        try {
            dao.insert(context, tmpRealAuthor, locale);
            currentEdit.setRealAuthor(tmpRealAuthor);
            return true;

        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e, tmpRealAuthor);
            return false;
        }
    }
}
