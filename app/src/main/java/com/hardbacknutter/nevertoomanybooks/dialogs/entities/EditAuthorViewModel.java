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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

public class EditAuthorViewModel
        extends ViewModel {

    private static final String TAG = "EditAuthorViewModel";
    public static final String BKEY_REQUEST_KEY = TAG + ":rk";

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

            requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
            author = Objects.requireNonNull(args.getParcelable(DBKey.FK_AUTHOR), DBKey.FK_AUTHOR);

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
     * @return {@code false} if the author does not exist and 'create' was {@code false}
     */
    public boolean validateAndSetRealAuthor(@NonNull final Context context,
                                            @NonNull final Locale bookLocale,
                                            final boolean create) {
        // If we have a pseudonym set, it must be a valid/existing author.
        if (currentRealAuthorName != null && !currentRealAuthorName.isBlank()) {
            final Author tmpRealAuthor = Author.from(currentRealAuthorName);
            final AuthorDao dao = ServiceLocator.getInstance().getAuthorDao();

            dao.fixId(context, tmpRealAuthor, false, bookLocale);
            if (tmpRealAuthor.getId() == 0) {
                if (create) {
                    try {
                        dao.insert(context, tmpRealAuthor, bookLocale);
                    } catch (@NonNull final DaoWriteException e) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            currentEdit.setRealAuthor(tmpRealAuthor);
        } else {
            currentEdit.setRealAuthor(null);
        }
        return true;
    }
}
