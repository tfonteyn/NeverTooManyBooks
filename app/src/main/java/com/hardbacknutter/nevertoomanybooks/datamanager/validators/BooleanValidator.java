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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.parsers.BooleanParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * Validator to apply a default value and validate as Boolean.
 */
public class BooleanValidator
        implements DataValidator {

    @NonNull
    private final RealNumberParser realNumberParser;
    /** Default to apply if the field is empty. */
    private final boolean defaultValue;

    /**
     * Constructor with default value.
     *
     * @param realNumberParser to use for number parsing
     * @param defaultValue     Default to apply
     */
    public BooleanValidator(@NonNull final RealNumberParser realNumberParser,
                            final boolean defaultValue) {
        this.realNumberParser = realNumberParser;
        this.defaultValue = defaultValue;
    }

    @Override
    @CallSuper
    public void validate(@NonNull final Context context,
                         @NonNull final DataManager dataManager,
                         @NonNull final String key,
                         @StringRes final int errorLabelResId)
            throws ValidatorException {

        final Object o = dataManager.get(key, realNumberParser);
        if (o == null || o.toString().trim().isEmpty()) {
            dataManager.putBoolean(key, defaultValue);
            return;
        }
        try {
            BooleanParser.toBoolean(o);
        } catch (@NonNull final NumberFormatException e) {
            throw new ValidatorException(context.getString(R.string.vldt_boolean_expected_for_x,
                                                           context.getString(errorLabelResId)));
        }
    }
}
