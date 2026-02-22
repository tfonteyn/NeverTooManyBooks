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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.series;

import android.os.Bundle;

import androidx.preference.PreferenceManager;

import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EditSeriesViewModelTest
        extends BaseDBTest {

    // wrongly stored in the db
    private static final String WRONG = "Series, The";
    // what it should be/become
    private static final String CORRECT = "The Series";
    private SeriesDao dao;
    private Locale locale;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putBoolean(ReorderHelper.PK_SORT_TITLE_REORDERED, true)
                         .apply();

        dao = serviceLocator.getSeriesDao();
        locale = context.getResources().getConfiguration().getLocales().get(0);

        // make sure neither are present
        Series p = new Series(CORRECT);
        dao.fixId(context, p, locale);
        if (p.getId() > 0) {
            dao.delete(context, p);
        }
        p = new Series(WRONG);
        dao.fixId(context, p, locale);
        if (p.getId() > 0) {
            dao.delete(context, p);
        }
    }

    @Test
    void rename()
            throws DaoWriteException {

        dao.insert(context, new Series(WRONG), locale);

        final Series original = new Series(WRONG);
        dao.fixId(context, original, locale);
        assertTrue(original.getId() != 0);

        final Bundle args = new Bundle();
        args.putParcelable(EditParcelableLauncher.BKEY_ITEM,
                           original);
        final EditSeriesViewModel vm = new EditSeriesViewModel();
        vm.init(args);

        final Series currentEdit = vm.getCurrentEdit();
        currentEdit.setTitle(CORRECT);

        final Optional<Series> existing = vm.saveIfUnique(context);
        assertTrue(existing.isEmpty());
    }
}
