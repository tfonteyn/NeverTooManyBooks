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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.publisher;

import android.content.Context;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableInput;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

@SuppressWarnings("WeakerAccess")
public class EditPublisherViewModel
        extends ViewModel {

    /** The Publisher we're editing. */
    private Publisher original;

    /** Current edit. */
    private Publisher currentEdit;
    private PublisherDao dao;

    /**
     * Pseudo constructor.
     *
     * @param args all arguments
     */
    public void init(@NonNull final EditParcelableInput<Parcelable> args) {
        if (dao == null) {
            dao = ServiceLocator.getInstance().getPublisherDao();

            original = (Publisher) args.getItem();
            currentEdit = new Publisher(original);
        }
    }

    @NonNull
    List<String> getAllNames() {
        return dao.getNames();
    }

    @NonNull
    public Publisher getOriginal() {
        return original;
    }

    @NonNull
    public Publisher getCurrentEdit() {
        return currentEdit;
    }

    /**
     * Check if any of the fields were changed.
     *
     * @return {@code true} if modified
     */
    boolean isModified() {
        return !original.isSameName(currentEdit);
    }

    /**
     * Check if the current user entered Publisher name already exists.
     * <p>
     * If it does not, insert or update the current edit,
     * and return an empty optional indicating a successful insert/update.
     * <p>
     * If it does, return the existing Publisher indicating failure to save.
     *
     * @param context Current context
     *
     * @return an empty Optional for SUCCESS, or else the existing Publisher.
     *
     * @throws DaoWriteException on failure
     */
    @NonNull
    Optional<Publisher> saveIfUnique(@NonNull final Context context)
            throws DaoWriteException {

        // FIRST check if the name was changed
        final boolean sameName = original.isSameName(currentEdit);

        // now copy changes, including the name and any other attributes
        original.copyFrom(currentEdit);

        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

        // It's an existing one and the name was not changed;
        // just update the other attributes
        if (original.getId() != 0 && sameName) {
            dao.update(context, original, locale);
            return Optional.empty();
        }

        // Check if there is another one with the same new name.
        final Optional<Publisher> existingEntity = dao.findByName(context, original, locale);
        if (existingEntity.isPresent()) {
            // original can have an id==0, or id!=0. Both are acceptable.
            if (original.getId() != existingEntity.get().getId()) {
                // it's really another entry with the same name -> we could merge
                return existingEntity;
            }
            // else: we found our own entry and the name is different due to re-ordering.
        }

        // insert or update as needed
        if (original.getId() == 0) {
            dao.insert(context, original, locale);
        } else {
            dao.update(context, original, locale);
        }
        // return SUCCESS
        return Optional.empty();
    }

    void move(@NonNull final Context context,
              @NonNull final Publisher destination)
            throws DaoWriteException {
        // Note that we ONLY move the books. No other attributes from
        // the source item are copied to the target item!
        dao.moveBooks(context, original, destination);
    }
}
