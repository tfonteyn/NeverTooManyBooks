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
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

@SuppressWarnings("WeakerAccess")
public class EditSeriesViewModel
        extends ViewModel {

    /** The Series we're editing. */
    private Series series;

    /** Current edit. */
    private Series currentEdit;
    private SeriesDao dao;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#requireArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (dao == null) {
            dao = ServiceLocator.getInstance().getSeriesDao();

            series = Objects.requireNonNull(args.getParcelable(EditParcelableLauncher.BKEY_ITEM),
                                            EditParcelableLauncher.BKEY_ITEM);

            currentEdit = new Series(series, true);
        }
    }

    @NonNull
    public Series getCurrentEdit() {
        return currentEdit;
    }

    @NonNull
    public Series getSeries() {
        return series;
    }

    boolean isModified() {
        // Case-sensitive! We must allow the user to correct case.
        return !(series.isSameName(currentEdit)
                 && series.isComplete() == currentEdit.isComplete());
    }

    /**
     * Check if the current user entered Series name already exists.
     * <p>
     * If it does not, insert or update the current edit,
     * and return an empty optional indicating a successful insert/update.
     * <p>
     * If it does, return the existing Series indicating failure to save.
     *
     * @param context Current context
     *
     * @return an empty Optional for SUCCESS, or else the existing Series.
     *
     * @throws DaoWriteException on failure
     */
    @NonNull
    Optional<Series> saveIfUnique(@NonNull final Context context)
            throws DaoWriteException {
        series.copyFrom(currentEdit, false);

        final Locale locale = series.getLocale(context).orElseGet(
                () -> context.getResources().getConfiguration().getLocales().get(0));

        // It's an existing one and the name was not changed;
        // just update the other attributes
        if (series.getId() != 0 && series.isSameName(currentEdit)) {
            dao.update(context, series, locale);
            return Optional.empty();
        }

        // Check if there is an another one with the same new name.
        final Optional<Series> existingEntity = dao.findByName(context, series, locale);
        if (existingEntity.isPresent()) {
            return existingEntity;
        }

        // Just insert or update as needed
        if (series.getId() == 0) {
            dao.insert(context, series, locale);
        } else {
            dao.update(context, series, locale);
        }
        return Optional.empty();
    }

    void move(@NonNull final Context context,
              @NonNull final Series destination)
            throws DaoWriteException {
        // Note that we ONLY move the books. No other attributes from
        // the source item are copied to the target item!
        dao.moveBooks(context, series, destination);
    }
}
