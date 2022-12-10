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
import androidx.lifecycle.ViewModel;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

public class EditAuthorViewModel
        extends ViewModel {

    private static final String TAG = "EditAuthorViewModel";
    public static final String BKEY_REQUEST_KEY = TAG + ":rk";

    private AuthorDao authorDao;

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    /** The Author we're editing. */
    private Author author;

    /** Current edit. */
    private Author currentEdit;
    /** Current edit. */
    @Nullable
    private String currentRealAuthorName;

    public void init(@NonNull final Bundle args) {
        if (authorDao == null) {
            authorDao = ServiceLocator.getInstance().getAuthorDao();

            requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
            author = Objects.requireNonNull(args.getParcelable(DBKey.FK_AUTHOR), DBKey.FK_AUTHOR);

            currentEdit = new Author(author);
            final Author tmp = currentEdit.getRealAuthor();
            currentRealAuthorName = tmp != null ? tmp.getFormattedName(false) : null;
        }
    }

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

    public boolean validateAndSetRealAuthor(@NonNull final Context context,
                                            @NonNull final Locale bookLocale) {
        final AuthorDao dao = ServiceLocator.getInstance().getAuthorDao();
        // If we have a pseudonym set, it must be a valid/existing author.
        if (currentRealAuthorName != null && !currentRealAuthorName.isBlank()) {
            final Author tmpRealAuthor = Author.from(currentRealAuthorName);
            dao.fixId(context, tmpRealAuthor, false, bookLocale);
            //URGENT: allow inserting a new one here?
            if (tmpRealAuthor.getId() == 0) {
                return false;
            }
            currentEdit.setRealAuthor(tmpRealAuthor);
        } else {
            currentEdit.setRealAuthor(null);
        }
        return true;
    }
}
