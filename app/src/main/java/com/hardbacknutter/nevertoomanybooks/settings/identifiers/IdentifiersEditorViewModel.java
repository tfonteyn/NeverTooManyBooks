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

package com.hardbacknutter.nevertoomanybooks.settings.identifiers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierValueDao;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

@SuppressWarnings("WeakerAccess")
public class IdentifiersEditorViewModel
        extends ViewModel {

    /** Flag set when anything is changed. */
    private boolean modified;

    private IdentifierDao identifierDao;
    private List<Identifier> identifiers;
    private IdentifierValueDao bookIdentifierDao;
    private IdentifierValueDao authorIdentifierDao;

    /**
     * Pseudo constructor.
     */
    void init() {
        if (identifierDao == null) {
            final ServiceLocator serviceLocator = ServiceLocator.getInstance();
            identifierDao = serviceLocator.getIdentifierDao();
            bookIdentifierDao = serviceLocator.getBookIdentifierDao();
            authorIdentifierDao = serviceLocator.getAuthorIdentifierDao();
        }

        identifiers = identifierDao.getAll();
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
        identifiers.addAll(identifierDao.getAll());
    }

    @NonNull
    List<Identifier> getIdentifiers() {
        return identifiers;
    }

    void restoreBuiltin(@NonNull final Context context)
            throws DaoWriteException {
        identifierDao.restore(context);
        identifiers.clear();
        identifiers.addAll(identifierDao.getAll());
        setModified();
    }

    void delete(@NonNull final Identifier identifier) {
        identifierDao.delete(identifier);
        identifiers.remove(identifier);
        // brute force... the user modified something
        setModified();
    }

    public int countBooks(final Identifier identifier) {
        return bookIdentifierDao.countLinks(identifier);
    }

    public int countAuthors(final Identifier identifier) {
        return authorIdentifierDao.countLinks(identifier);
    }
}
