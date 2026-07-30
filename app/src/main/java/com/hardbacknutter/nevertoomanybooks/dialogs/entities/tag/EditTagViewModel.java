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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.tag;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.TagDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableInput;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;

@SuppressWarnings("WeakerAccess")
public class EditTagViewModel
        extends ViewModel {

    /** The Tag we're editing. */
    private Tag original;

    /** Current edit. */
    private Tag currentEdit;
    private TagDao dao;

    /**
     * Pseudo constructor.
     *
     * @param args all arguments
     */
    public void init(@NonNull final EditParcelableInput<Tag> args) {
        if (dao == null) {
            dao = ServiceLocator.getInstance().getTagDao();
            original = args.getItem();
            currentEdit = new Tag(original);
        }
    }

    @NonNull
    List<String> getAllNames() {
        return dao.getAll().stream().map(Tag::getName).collect(Collectors.toList());
    }

    @NonNull
    public Tag getOriginal() {
        return original;
    }

    @NonNull
    public Tag getCurrentEdit() {
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
     * Check if the current user entered Tag name already exists.
     * <p>
     * If it does not, insert or update the current edit,
     * and return an empty optional indicating a successful insert/update.
     * <p>
     * If it does, return the existing Tag indicating failure to save.
     *
     * @param context Current context
     *
     * @return an empty Optional for SUCCESS, or else the existing Tag.
     *
     * @throws DaoWriteException on failure
     */
    @NonNull
    Optional<Tag> saveIfUnique(@NonNull final Context context)
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

        // Check if there is another one with the same new name.
        final Optional<Tag> existingEntity = dao.findByName(original);
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

    void move(@NonNull final Context context,
              @NonNull final Tag destination)
            throws DaoWriteException {
        // Note that we ONLY move the books. No other attributes from
        // the source item are copied to the target item!
        dao.moveBooks(context, original, destination);
    }

}
