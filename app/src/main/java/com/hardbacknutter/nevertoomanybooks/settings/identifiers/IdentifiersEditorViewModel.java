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

package com.hardbacknutter.nevertoomanybooks.settings.identifiers;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierValueDao;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

@SuppressWarnings("WeakerAccess")
public class IdentifiersEditorViewModel
        extends ViewModel {

    private final MutableLiveData<Void> onReload = new MutableLiveData<>();

    /** Flag set when anything is changed. */
    private boolean modified;

    private IdentifierDao identifierDao;
    private List<Identifier> identifiers;
    private IdentifierValueDao bookIdentifierDao;
    private IdentifierValueDao authorIdentifierDao;
    private Identifier.EntityType entityType;

    @NonNull
    LiveData<Void> onUpdate() {
        return onReload;
    }

    /**
     * Pseudo constructor.
     *
     * @param args Bundle with arguments
     */
    void init(@NonNull final Bundle args) {
        if (entityType == null) {
            entityType = Objects.requireNonNull(args.getParcelable(
                    IdentifiersEditorFragment.BKEY_ENTITY_TYPE));

            final ServiceLocator serviceLocator = ServiceLocator.getInstance();
            identifierDao = serviceLocator.getIdentifierDao();
            bookIdentifierDao = serviceLocator.getBookIdentifierDao();
            authorIdentifierDao = serviceLocator.getAuthorIdentifierDao();
        }

        identifiers = identifierDao.getAll(entityType);
    }

    @NonNull
    Identifier.EntityType getEntityType() {
        return entityType;
    }

    /**
     * Check if <strong>anything at all</strong> was changed.
     *
     * @return {@code true} if changes made
     */
    boolean isModified() {
        return modified;
    }

    void setModified() {
        modified = true;
    }

    void refreshList() {
        identifiers.clear();
        identifiers.addAll(identifierDao.getAll(entityType));
        onReload.setValue(null);
    }

    @NonNull
    List<Identifier> getIdentifiers() {
        return identifiers;
    }

    void restoreBuiltin(@NonNull final Context context)
            throws DaoWriteException {
        identifierDao.restore(context);
        identifiers.clear();
        identifiers.addAll(identifierDao.getAll(entityType));
        setModified();
        onReload.setValue(null);
    }

    void delete(@NonNull final Identifier identifier) {
        identifierDao.delete(identifier);
        identifiers.remove(identifier);
        // brute force... the user modified something
        setModified();
    }

    int count(@NonNull final Identifier identifier) {
        switch (identifier.getEntityType()) {
            case Book:
                return bookIdentifierDao.countLinks(identifier);
            case Author:
                return authorIdentifierDao.countLinks(identifier);
            default:
                throw new IllegalArgumentException("TODO");
        }
    }
}
