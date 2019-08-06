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

import com.hardbacknutter.nevertomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertomanybooks.datamanager.Datum;

/**
 * Interface for all field-level validators. Each field validator is called twice; once
 * with the crossValidating flag set to false, then, if all validations were successful,
 * they are all called a second time with the flag set to true. This is an alternate
 * method of applying cross-validation.
 */
public interface DataValidator {

    /**
     * Validation method. Must throw a ValidatorException if validation fails.
     *
     * @param dataManager     The DataManager object containing the Datum being validated
     * @param datum           The Datum to validate
     * @param crossValidating Options indicating if this is the cross-validation pass.
     *
     * @throws ValidatorException For any validation failure.
     */
    void validate(@NonNull DataManager dataManager,
                  @NonNull Datum datum,
                  boolean crossValidating)
            throws ValidatorException;
}
