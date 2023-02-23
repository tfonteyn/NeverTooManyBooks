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

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleListUtils;

/**
 * Validator to apply a default String value to empty fields.
 */
public class DefaultFieldValidator
        implements DataValidator {

    /** Default to apply if the field is empty. */
    @NonNull
    private final String defaultValue;

    /**
     * Constructor with default value.
     *
     * @param defaultValue Default to apply if the field is empty
     */
    DefaultFieldValidator(@NonNull final String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Gets the current value, and if {@code null} or empty,
     * replaces it with the  {@link #defaultValue}.
     *
     * @param context         Current context
     * @param dataManager     The DataManager object
     * @param key             key for the data to validate
     * @param errorLabelResId not used.
     */
    @Override
    @CallSuper
    public void validate(@NonNull final Context context,
                         @NonNull final DataManager dataManager,
                         @NonNull final String key,
                         @StringRes final int errorLabelResId) {

        final Object value = dataManager.get(key, LocaleListUtils.asList(context));
        if (value != null && value.toString().trim().isEmpty()) {
            dataManager.putString(key, defaultValue);
        }
    }
}
