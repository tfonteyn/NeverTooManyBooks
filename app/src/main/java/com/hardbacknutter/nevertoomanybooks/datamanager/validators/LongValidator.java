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
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleListUtils;

/**
 * Validator to apply a default value and validate as Long.
 */
public class LongValidator
        implements DataValidator {

    /** Default to apply if the field is {@code null} or empty. */
    private final long defaultValue;

    /**
     * Constructor; default value is 0.
     */
    public LongValidator() {
        defaultValue = 0;
    }

    /**
     * Constructor with default value.
     *
     * @param defaultValue Default to apply if the field is empty
     */
    public LongValidator(final long defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    @CallSuper
    public void validate(@NonNull final Context context,
                         @NonNull final DataManager dataManager,
                         @NonNull final String key,
                         @StringRes final int errorLabelResId)
            throws ValidatorException {

        final long value;
        final Object obj = dataManager.get(key, LocaleListUtils.asList(context));
        if (obj == null) {
            value = defaultValue;
        } else if (obj instanceof Long) {
            value = (long) obj;
        } else if (obj instanceof Integer) {
            value = ((Integer) obj).longValue();
        } else {
            final String stringValue = obj.toString().trim();
            if (stringValue.isEmpty()) {
                value = defaultValue;
            } else {
                try {
                    value = Long.parseLong(stringValue);
                } catch (@NonNull final NumberFormatException e) {
                    throw new ValidatorException(
                            context.getString(R.string.vldt_integer_expected_for_x,
                                              context.getString(errorLabelResId)));
                }
            }
        }
        dataManager.putLong(key, value);
    }
}
