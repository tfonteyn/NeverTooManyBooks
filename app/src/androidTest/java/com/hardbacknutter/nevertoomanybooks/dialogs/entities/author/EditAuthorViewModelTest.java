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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.author;

import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAction;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableInput;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EditAuthorViewModelTest
        extends BaseDBTest {

    // wrongly stored in the db
    private static final String WRONG_FAMILY = "Myself Family";
    // what it should be/become
    private static final String WRONG_GIVEN = "Me";

    private static final String CORRECT_FAMILY = "Family";
    private static final String CORRECT_GIVEN = "Me Myself";
    private AuthorDao dao;
    private Locale locale;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putBoolean(ReorderHelper.PK_SORT_TITLE_REORDERED, true)
                      .apply();

        dao = serviceLocator.getAuthorDao();
        locale = context.getResources().getConfiguration().getLocales().get(0);

        // make sure neither are present
        Author p = new Author(CORRECT_FAMILY, CORRECT_GIVEN);
        dao.fixId(context, p, locale);
        if (p.getId() > 0) {
            dao.delete(context, p);
        }

        p = new Author(WRONG_FAMILY, WRONG_GIVEN);
        dao.fixId(context, p, locale);
        if (p.getId() > 0) {
            dao.delete(context, p);
        }
    }

    @Test
    void rename()
            throws DaoWriteException {

        dao.insert(context, new Author(WRONG_FAMILY, WRONG_GIVEN), locale);

        final Author original = new Author(WRONG_FAMILY, WRONG_GIVEN);
        dao.fixId(context, original, locale);
        assertTrue(original.getId() != 0);

        final EditParcelableInput<Author> args = new EditParcelableInput<>(
                "unused", /* unused */ EditAction.Edit,
                original, null);

        final EditAuthorViewModel vm = new EditAuthorViewModel();
        vm.init(args);

        final Author currentEdit = vm.getCurrentEdit();
        currentEdit.setName(CORRECT_FAMILY, CORRECT_GIVEN);

        final Optional<Author> existing = vm.saveIfUnique(context);
        assertTrue(existing.isEmpty());
    }

    @Test
    void rename2()
            throws DaoWriteException {

        dao.insert(context, new Author(CORRECT_FAMILY, CORRECT_GIVEN), locale);

        final Author original = new Author(CORRECT_FAMILY, CORRECT_GIVEN);
        dao.fixId(context, original, locale);
        assertTrue(original.getId() != 0);

        final EditParcelableInput<Author> args = new EditParcelableInput<>(
                "unused", /* unused */ EditAction.Edit,
                original, null);

        final EditAuthorViewModel vm = new EditAuthorViewModel();
        vm.init(args);

        final Author currentEdit = vm.getCurrentEdit();
        currentEdit.setName(WRONG_FAMILY, WRONG_GIVEN);

        final Optional<Author> existing = vm.saveIfUnique(context);
        assertTrue(existing.isEmpty());
    }
}
