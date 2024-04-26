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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.ParcelableDialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

@SuppressWarnings("WeakerAccess")
public class EditBookshelfViewModel
        extends ViewModel {

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    /** The Bookshelf we're editing. */
    private Bookshelf bookshelf;

    /** Current edit. */
    private Bookshelf currentEdit;
    private BookshelfDao dao;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#requireArguments()}
     */
    void init(@NonNull final Bundle args) {
        if (requestKey == null) {
            dao = ServiceLocator.getInstance().getBookshelfDao();

            requestKey = Objects.requireNonNull(
                    args.getString(ParcelableDialogLauncher.BKEY_REQUEST_KEY),
                    ParcelableDialogLauncher.BKEY_REQUEST_KEY);
            bookshelf = Objects.requireNonNull(
                    args.getParcelable(ParcelableDialogLauncher.BKEY_ITEM),
                    ParcelableDialogLauncher.BKEY_ITEM);

            currentEdit = bookshelf;
        }
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    @NonNull
    Bookshelf getBookshelf() {
        return bookshelf;
    }

    @NonNull
    Bookshelf getCurrentEdit() {
        return currentEdit;
    }


    boolean isChanged() {
        // Case-sensitive! We must allow the user to correct case.
        return !bookshelf.isSameName(currentEdit);
    }

    @NonNull
    Optional<Bookshelf> saveIfUnique(@NonNull final Context context)
            throws DaoWriteException {
        bookshelf.setName(currentEdit.getName());

        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

        // The logic flow here is different from the default one as used for e.g. an Author.
        // IF the user meant to create a NEW shelf
        // REJECT an already existing Bookshelf with the same name.
        final Optional<Bookshelf> existingEntity = dao.findByName(context, bookshelf, locale);
        if (bookshelf.getId() == 0) {
            return existingEntity;
        }

        if (existingEntity.isEmpty()) {
            // Just insert or update as needed
            if (bookshelf.getId() == 0) {
                dao.insert(context, bookshelf, locale);
            } else {
                dao.update(context, bookshelf, locale);
            }
            return Optional.empty();
        }

        return existingEntity;
    }

    void move(@NonNull final Context context,
              @NonNull final Bookshelf destination)
            throws DaoWriteException {
        // Note that we ONLY move the books. No other attributes from
        // the source item are copied to the target item!
        dao.moveBooks(context, bookshelf, destination);
    }
}
