/*
 * @Copyright 2018-2023 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

public class EditAuthorViewModel
        extends ViewModel {

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    /** The Author we're editing. */
    private Author author;

    /** Current edit. */
    private Author currentEdit;
    /** Current edit. */
    @Nullable
    private String currentRealAuthorName;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (requestKey == null) {

            requestKey = Objects.requireNonNull(
                    args.getString(EditLauncher.BKEY_REQUEST_KEY), EditLauncher.BKEY_REQUEST_KEY);
            author = Objects.requireNonNull(
                    args.getParcelable(EditLauncher.BKEY_ITEM), EditLauncher.BKEY_ITEM);

            currentEdit = new Author(author);
            final Author tmp = currentEdit.getRealAuthor();
            currentRealAuthorName = tmp != null ? tmp.getFormattedName(false) : null;
        }
    }

    /**
     * Get the request-key for Launcher/Listener communications.
     *
     * @return key
     */
    @NonNull
    public String getRequestKey() {
        return requestKey;
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
     * @param context    Current context
     * @param bookLocale Locale to use if the item has none set
     * @param create     {@code true} if a non-existent Author should be created
     *
     * @return {@code true} if the 'real' Author was validated and set.
     *         {@code false} if the real author did not exist and we were not allowed to create them
     *         (or if creating threw an error)
     */
    public boolean validateAndSetRealAuthor(@NonNull final Context context,
                                            @NonNull final Locale bookLocale,
                                            final boolean create) {
        // no pseudonym?
        if (currentRealAuthorName == null || currentRealAuthorName.isBlank()) {
            currentEdit.setRealAuthor(null);
            return true;
        }

        // If we have a pseudonym set, it must be a valid/existing author.
        final Author tmpRealAuthor = Author.from(currentRealAuthorName);

        final AuthorDao dao = ServiceLocator.getInstance().getAuthorDao();
        final Optional<Author> exiting = dao.findByName(context, tmpRealAuthor, () -> bookLocale);

        if (exiting.isPresent()) {
            currentEdit.setRealAuthor(exiting.get());
            return true;
        }

        if (!create) {
            // force the caller to ask the user to try again the 2nd time allowing creating.
            return false;
        }

        try {
            dao.insert(context, tmpRealAuthor, bookLocale);
            currentEdit.setRealAuthor(tmpRealAuthor);
            return true;
        } catch (@NonNull final DaoWriteException e) {
            return false;
        }
    }
}
