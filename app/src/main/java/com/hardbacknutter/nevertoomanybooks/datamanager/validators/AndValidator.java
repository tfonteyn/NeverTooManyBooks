/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.datamanager.validators;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * 'Meta' Validator to evaluate a list of validators; all validators must be true.
 */
public class AndValidator
        extends ArrayList<DataValidator>
        implements DataValidator {

    private static final long serialVersionUID = -8002105679237033069L;

    /**
     * Constructor.
     *
     * @param validators list of validators
     */
    public AndValidator(@NonNull final DataValidator... validators) {
        addAll(Arrays.asList(validators));
    }

    @Override
    public void validate(@NonNull final DataManager dataManager,
                         @NonNull final String key,
                         final int errorLabelId)
            throws ValidatorException {

        for (DataValidator validator : this) {
            // Only set the Bundle for the last in the list
            validator.validate(dataManager, key, errorLabelId);
        }
    }
}
