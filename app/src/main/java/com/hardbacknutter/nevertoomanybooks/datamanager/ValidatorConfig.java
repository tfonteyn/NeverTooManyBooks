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

package com.hardbacknutter.nevertoomanybooks.datamanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.DataCrossValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.DataValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.ValidatorException;

public class ValidatorConfig {

    /** DataValidators. */
    private final Map<String, DataValidator> validatorsMap = new HashMap<>();

    /** DataValidators. Same key as {@link #validatorsMap}; value: @StringRes. */
    @SuppressWarnings("FieldNotUsedInToString")
    private final Map<String, Integer> validatorErrorIdMap = new HashMap<>();

    /** A list of cross-validators to apply if all fields pass simple validation. */
    private final Collection<DataCrossValidator> crossValidators = new ArrayList<>();

    /** The validator exceptions caught by this object. */
    private final Collection<ValidatorException> validationExceptions = new ArrayList<>();


    /**
     * Add a validator for the specified key.
     * <p>
     * Accepts only one validator for a key. Setting a second one will override the first.
     *
     * @param key             Key for the data
     * @param validator       Validator
     * @param errorLabelResId string resource id for a user visible message
     */
    public void addValidator(@NonNull final String key,
                             @NonNull final DataValidator validator,
                             @StringRes final int errorLabelResId) {
        validatorsMap.put(key, validator);
        validatorErrorIdMap.put(key, errorLabelResId);
    }

    /**
     * Add a cross validator.
     *
     * @param validator Validator
     */
    public void addCrossValidator(@NonNull final DataCrossValidator validator) {
        crossValidators.add(validator);
    }

    /**
     * Loop through and apply validators.
     * <p>
     * {@link ValidatorException} are added to {@link #validationExceptions}
     * Use {@link #getValidationExceptionMessage} for the results.
     *
     * @param context Current context
     *
     * @return {@code true} if all validation passed.
     */
    public boolean validate(@NonNull final Context context,
                            @NonNull final DataManager dataManager) {

        validationExceptions.clear();

        for (final Map.Entry<String, DataValidator> entry : validatorsMap.entrySet()) {
            final String key = entry.getKey();
            try {
                entry.getValue()
                     .validate(context, dataManager, key,
                               Objects.requireNonNull(validatorErrorIdMap.get(key), key));
            } catch (@NonNull final ValidatorException e) {
                validationExceptions.add(e);
            }
        }

        for (final DataCrossValidator crossValidator : crossValidators) {
            try {
                crossValidator.validate(context, dataManager);
            } catch (@NonNull final ValidatorException e) {
                validationExceptions.add(e);
            }
        }
        return validationExceptions.isEmpty();
    }

    /**
     * Retrieve the text message associated with the validation exceptions (if any).
     *
     * @param context Current context
     *
     * @return a user displayable list of error messages, or {@code null} if none present
     */
    @Nullable
    public String getValidationExceptionMessage(@NonNull final Context context) {
        if (validationExceptions.isEmpty()) {
            return null;

        } else {
            final StringBuilder msg = new StringBuilder();
            int i = 0;
            for (final ValidatorException e : validationExceptions) {
                msg.append(context.getString(R.string.vldt_list_message,
                                             ++i, e.getLocalizedMessage()))
                   .append('\n');
            }
            return msg.toString();
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "ValidatorConfig{"
               + "validatorsMap=" + validatorsMap
               + ", crossValidators=" + crossValidators
               + ", validationExceptions=" + validationExceptions
               + '}';
    }
}
