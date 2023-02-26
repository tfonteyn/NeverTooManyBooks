/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.datamanager.validators;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * Interface for all data-level validators.
 */
@FunctionalInterface
public interface DataValidator {

    /**
     * Validation method. Must throw a ValidatorException if validation fails.
     * <p>
     * Dev. note: passing in the #errorLabelResId here instead of in a constructor
     * allows us to reuse a single validator for multiple fields.
     *
     * @param context         Current context
     * @param dataManager     The DataManager object
     * @param key             key for the data to validate
     * @param errorLabelResId string resource id for a user visible message
     *
     * @throws ValidatorException For any validation failure.
     */
    void validate(@NonNull Context context,
                  @NonNull DataManager dataManager,
                  @NonNull String key,
                  @StringRes int errorLabelResId)
            throws ValidatorException;
}
