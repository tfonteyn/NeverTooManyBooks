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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.bookshelf;

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
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

@SuppressWarnings("WeakerAccess")
public class EditBookshelfViewModel
        extends ViewModel {

    /** The Bookshelf we're editing. */
    private Bookshelf original;

    /** Current edit. */
    private Bookshelf currentEdit;
    private BookshelfDao dao;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#requireArguments()}
     */
    void init(@NonNull final Bundle args) {
        if (dao == null) {
            dao = ServiceLocator.getInstance().getBookshelfDao();

            original = Objects.requireNonNull(args.getParcelable(EditParcelableLauncher.BKEY_ITEM),
                                              EditParcelableLauncher.BKEY_ITEM);

            currentEdit = new Bookshelf(original);
        }
    }

    @NonNull
    Bookshelf getOriginal() {
        return original;
    }

    @NonNull
    Bookshelf getCurrentEdit() {
        return currentEdit;
    }

    /**
     * Were any of the fields changed?
     *
     * @return {@code true} if modified
     */
    boolean isModified() {
        return !original.isSameName(currentEdit);
    }

    /**
     * Check if the current user entered Bookshelf name already exists.
     * <p>
     * If it does not, insert or update the current edit,
     * and return an empty optional indicating a successful insert/update.
     * <p>
     * If it does, return the existing Bookshelf indicating failure to save.
     *
     * @param context Current context
     *
     * @return an empty Optional for SUCCESS, or else the existing Bookshelf.
     *
     * @throws DaoWriteException on failure
     */
    @NonNull
    Optional<Bookshelf> saveIfUnique(@NonNull final Context context)
            throws DaoWriteException {
        // The logic flow here is different from the default one as used for e.g. an Author.
        // See the code which is calling this method

        original.setName(currentEdit.getName());

        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

        // Check if there is an another one with the same new name.
        final Optional<Bookshelf> existingEntity = dao.findByName(context, original, locale);
        if (existingEntity.isPresent()) {
            return existingEntity;
        }

        // Just insert or update as needed
        if (original.getId() == 0) {
            dao.insert(context, original, locale);
        } else {
            dao.update(context, original, locale);
        }
        return Optional.empty();
    }

    void move(@NonNull final Context context,
              @NonNull final Bookshelf destination)
            throws DaoWriteException {
        // Note that we ONLY move the books. No other attributes from
        // the source item are copied to the target item!
        dao.moveBooks(context, original, destination);
    }
}
