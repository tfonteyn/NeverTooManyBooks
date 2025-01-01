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

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

public class IdentifiersEditorViewModel
        extends ViewModel {

    /** Flag set when anything is changed. */
    private boolean dirty;

    private IdentifierDao identifierDao;
    private List<Identifier> identifiers;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@Nullable final Bundle args) {
        if (identifierDao == null) {
            identifierDao = ServiceLocator.getInstance().getIdentifierDao();
        }
        identifiers = identifierDao.getAll();
    }

    /**
     * Check if <strong>anything at all</strong> was changed.
     *
     * @return {@code true} if changes made
     */
    boolean isDirty() {
        return dirty;
    }

    void setDirty(@SuppressWarnings("SameParameterValue") final boolean isDirty) {
        dirty = isDirty;
    }

    void refreshStyleList() {
        identifiers.clear();
        identifiers.addAll(identifierDao.getAll());
    }

    @NonNull
    List<Identifier> getIdentifiers() {
        return identifiers;
    }
}
