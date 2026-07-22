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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

@SuppressWarnings("WeakerAccess")
public class EditSeriesViewModel
        extends ViewModel {

    /** The Series we're editing. */
    private Series original;

    /** Current edit. */
    private Series currentEdit;
    private String bookIssn;

    private SeriesDao dao;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#requireArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (dao == null) {
            dao = ServiceLocator.getInstance().getSeriesDao();

            bookIssn = args.getString(EditParcelableLauncher.BKEY_BOOK_ISSN, null);

            //noinspection deprecation
            original = Objects.requireNonNull(args.getParcelable(EditParcelableLauncher.BKEY_ITEM),
                                              EditParcelableLauncher.BKEY_ITEM);

            currentEdit = new Series(original, true);
        }
    }

    @NonNull
    List<String> getAllTitles() {
        return dao.getNames();
    }

    /**
     * The ISSN from the book this Series belongs to.
     *
     * @return issn, or {@code null} if the book had none, or if it was not an ISSN.
     */
    @Nullable
    public String getBookIssn() {
        return bookIssn;
    }

    @NonNull
    public Series getOriginal() {
        return original;
    }

    @NonNull
    public Series getCurrentEdit() {
        return currentEdit;
    }

    /**
     * Check if any of the fields were changed.
     *
     * @return {@code true} if modified
     */
    boolean isModified() {
        return !original.isSameName(currentEdit)
               || original.isComplete() != currentEdit.isComplete()
               || !Objects.equals(original.getIdentifiers(), currentEdit.getIdentifiers());
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

        // FIRST check if the name was changed
        final boolean sameName = original.isSameName(currentEdit);

        // now copy changes, including the name and any other attributes
        original.copyFrom(currentEdit, false);

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        final Locale locale = original.getLocale(userLocale).orElse(userLocale);

        // It's an existing one and the name was not changed;
        // just update the other attributes
        if (original.getId() != 0 && sameName) {
            dao.update(context, original, locale);
            return Optional.empty();
        }

        // Check if there is another one with the same new name.
        final Optional<Series> existingEntity = dao.findByName(context, original, locale);
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
              @NonNull final Series destination)
            throws DaoWriteException {
        // Note that we ONLY move the books. No other attributes from
        // the source item are copied to the target item!
        dao.moveBooks(context, original, destination);
    }
}
