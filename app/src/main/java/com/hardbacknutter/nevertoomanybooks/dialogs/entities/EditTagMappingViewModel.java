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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.TagMappingDao;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;

@SuppressWarnings("WeakerAccess")
public class EditTagMappingViewModel
        extends ViewModel {

    /** The TagMapping we're editing. */
    private TagMapping original;

    /** Current edit. */
    private TagMapping currentEdit;
    private TagMappingDao dao;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#requireArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (dao == null) {
            dao = ServiceLocator.getInstance().getTagMappingDao();

            original = Objects.requireNonNull(args.getParcelable(EditParcelableLauncher.BKEY_ITEM),
                                              EditParcelableLauncher.BKEY_ITEM);

            currentEdit = new TagMapping(original);
        }
    }

    @NonNull
    public TagMapping getOriginal() {
        return original;
    }

    @NonNull
    public TagMapping getCurrentEdit() {
        return currentEdit;
    }

    /**
     * Were any of the fields changed?
     *
     * @return {@code true} if modified
     */
    boolean isModified() {
        return !original.getName().equals(currentEdit.getName())
               || !original.getMappings().equals(currentEdit.getMappings());
    }

    /**
     * Check if the current user entered TagMapping name already exists.
     * <p>
     * If it does not, insert or update the current edit,
     * and return an empty optional indicating a successful insert/update.
     * <p>
     * If it does, return the existing TagMapping indicating failure to save.
     *
     * @param context Current context
     *
     * @return an empty Optional for SUCCESS, or else the existing TagMapping.
     *
     * @throws DaoWriteException on failure
     */
    @NonNull
    Optional<TagMapping> saveIfUnique(@NonNull final Context context)
            throws DaoWriteException {

        // FIRST check if the name was changed
        final boolean sameName = original.getName().equals(currentEdit.getName());

        // now copy changes, including the name and any other attributes
        original.copyFrom(currentEdit);

        // It's an existing one and the name was not changed;
        // just update the other attributes
        if (original.getId() != 0 && sameName) {
            dao.update(original);
            return Optional.empty();
        }

        // Check if there is an another one with the same new name.
        final Optional<TagMapping> existingEntity = dao.findByName(original);
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
