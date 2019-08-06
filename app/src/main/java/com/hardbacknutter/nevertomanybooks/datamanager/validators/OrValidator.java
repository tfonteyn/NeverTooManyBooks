/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.datamanager.validators;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertomanybooks.datamanager.Datum;

/**
 * 'Meta' Validator to evaluate a list of validators; any one being {@code true} is OK.
 */
public class OrValidator
        extends ArrayList<DataValidator>
        implements DataValidator {

    private static final long serialVersionUID = 550199747728692370L;

    /**
     * Constructor.
     *
     * @param validators list of validators
     */
    public OrValidator(@NonNull final DataValidator... validators) {
        addAll(Arrays.asList(validators));
    }

    @Override
    public void validate(@NonNull final DataManager dataManager,
                         @NonNull final Datum datum,
                         final boolean crossValidating)
            throws ValidatorException {
        ValidatorException lastException = null;
        for (DataValidator validator : this) {
            try {
                validator.validate(dataManager, datum, crossValidating);
                // first one 'ok' and we're done.
                return;
            } catch (@NonNull final ValidatorException e) {
                // Do nothing...try next validator
                lastException = e;
            }
        }

        if (lastException != null) {
            throw lastException;
        } else {
            throw new ValidatorException(R.string.vldt_failed_for_x, datum.getKey());
        }
    }
}
