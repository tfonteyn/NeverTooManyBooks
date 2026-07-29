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

import android.os.Parcelable;

import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAction;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableInput;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EditPublisherViewModelTest
        extends BaseDBTest {

    // wrongly stored in the db
    private static final String WRONG = "Pub, The";
    // what it should be/become
    private static final String CORRECT = "The Pub";
    private PublisherDao dao;
    private Locale locale;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putBoolean(ReorderHelper.PK_SORT_TITLE_REORDERED, true)
                      .apply();

        dao = serviceLocator.getPublisherDao();
        locale = context.getResources().getConfiguration().getLocales().get(0);

        // make sure neither are present
        Publisher p = new Publisher(CORRECT);
        dao.fixId(context, p, locale);
        if (p.getId() > 0) {
            dao.delete(context, p);
        }
        p = new Publisher(WRONG);
        dao.fixId(context, p, locale);
        if (p.getId() > 0) {
            dao.delete(context, p);
        }
    }

    @Test
    void rename()
            throws DaoWriteException {

        dao.insert(context, new Publisher(WRONG), locale);

        final Publisher original = new Publisher(WRONG);
        dao.fixId(context, original, locale);
        assertTrue(original.getId() != 0);

        final EditParcelableInput<Parcelable> args = new EditParcelableInput<>(
                "unused", /* unused */ EditAction.Edit,
                original, null);

        final EditPublisherViewModel vm = new EditPublisherViewModel();
        vm.init(args);

        final Publisher currentEdit = vm.getCurrentEdit();
        currentEdit.setName(CORRECT);

        final Optional<Publisher> existing = vm.saveIfUnique(context);
        assertTrue(existing.isEmpty());
    }
}
