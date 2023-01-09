/*
 * @Copyright 2018-2022 HardBackNutter
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.utils.dates.FullDateParser;

/**
 * Validator to apply a default value and validate as a Date.
 */
public class DateValidator
        implements DataValidator {

    /** Default to apply if the field is empty. */
    @NonNull
    private final String defaultValue;

    /**
     * Constructor with default value.
     *
     * @param defaultValue Default to apply if the field is empty
     */
    public DateValidator(@NonNull final String defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    @CallSuper
    public void validate(@NonNull final Context context,
                         @NonNull final DataManager dataManager,
                         @NonNull final String key,
                         @StringRes final int errorLabelResId)
            throws ValidatorException {

        String value = dataManager.getString(key, null);
        if (value == null || value.isEmpty()) {
            value = defaultValue;
        } else {
            final LocalDateTime date = new FullDateParser(context).parse(value);
            if (date != null) {
                value = date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } else {
                throw new ValidatorException(context.getString(R.string.vldt_date_expected_for_x,
                                                               context.getString(errorLabelResId)));
            }
        }
        dataManager.putString(key, value);
    }
}
