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
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

@SuppressWarnings("WeakerAccess")
public class EditPublisherViewModel
        extends ViewModel {

    /** The Publisher we're editing. */
    private Publisher publisher;

    /** Current edit. */
    private Publisher currentEdit;
    private PublisherDao dao;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#requireArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (dao == null) {
            dao = ServiceLocator.getInstance().getPublisherDao();

            publisher = Objects.requireNonNull(args.getParcelable(EditParcelableLauncher.BKEY_ITEM),
                                               EditParcelableLauncher.BKEY_ITEM);

            currentEdit = new Publisher(publisher);
        }
    }

    @NonNull
    public Publisher getPublisher() {
        return publisher;
    }

    @NonNull
    public Publisher getCurrentEdit() {
        return currentEdit;
    }

    boolean isModified() {
        // Case-sensitive! We must allow the user to correct case.
        return !publisher.isSameName(currentEdit);
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

        publisher.copyFrom(currentEdit);

        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

        // It's an existing one and the name was not changed;
        // just update the other attributes
        if (publisher.getId() != 0 && publisher.isSameName(currentEdit)) {
            dao.update(context, publisher, locale);
            return Optional.empty();
        }

        // Check if there is an another one with the same new name.
        final Optional<Publisher> existingEntity = dao.findByName(context, publisher, locale);
        if (existingEntity.isPresent()) {
            return existingEntity;
        }

        // Just insert or update as needed
        if (publisher.getId() == 0) {
            dao.insert(context, publisher, locale);
        } else {
            dao.update(context, publisher, locale);
        }
        // return SUCCESS
        return Optional.empty();
    }

    void move(@NonNull final Context context,
              @NonNull final Publisher destination)
            throws DaoWriteException {
        // Note that we ONLY move the books. No other attributes from
        // the source item are copied to the target item!
        dao.moveBooks(context, publisher, destination);
    }
}
