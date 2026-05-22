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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.identifier;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

@SuppressWarnings("WeakerAccess")
public class EditIdentifierViewModel
        extends ViewModel {

    /** The Identifier we're editing. */
    private Identifier original;

    /** Current edit. */
    private Identifier currentEdit;
    private IdentifierDao dao;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#requireArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (dao == null) {
            dao = ServiceLocator.getInstance().getIdentifierDao();

            original = Objects.requireNonNull(args.getParcelable(EditParcelableLauncher.BKEY_ITEM),
                                              EditParcelableLauncher.BKEY_ITEM);

            currentEdit = new Identifier(original);
        }
    }

    @NonNull
    public Identifier getOriginal() {
        return original;
    }

    @NonNull
    public Identifier getCurrentEdit() {
        return currentEdit;
    }

    /**
     * Check if any of the fields changed.
     *
     * @return {@code true} if modified
     */
    boolean isModified() {
        return !original.equals(currentEdit);
    }

    /**
     * Check if the current user entered Identifier name already exists.
     * <p>
     * If it does not, insert or update the current edit,
     * and return an empty optional indicating a successful insert/update.
     * <p>
     * If it does, return the existing Identifier indicating failure to save.
     *
     * @return an empty Optional for SUCCESS, or else the existing Identifier.
     *
     * @throws DaoWriteException on failure
     */
    @NonNull
    Optional<Identifier> saveIfUnique()
            throws DaoWriteException {

        // FIRST check if the name was changed
        final boolean sameName = original.isSameName(currentEdit);

        // now copy changes, including the name and any other attributes
        original.copyFrom(currentEdit);

        // It's an existing one and the name was not changed;
        // just update the other attributes
        if (original.getId() != 0 && sameName) {
            dao.update(original);
            return Optional.empty();
        }

        // Check if there is another one with the same new key and entity type.
        final Optional<Identifier> existingEntity = dao.findByKey(original.getKey(),
                                                                  original.getEntityType());
        if (existingEntity.isPresent()) {
            return existingEntity;
        }

        // Just insert or update as needed
        if (original.getId() == 0) {
            dao.insert(original);
        } else {
            dao.update(original);
        }
        // return SUCCESS
        return Optional.empty();
    }
}
