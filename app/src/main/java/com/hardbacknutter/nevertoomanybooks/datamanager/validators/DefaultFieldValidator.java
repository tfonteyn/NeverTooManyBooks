/*
 * @Copyright 2020 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * Validator to apply a default String value to empty fields.
 */
public class DefaultFieldValidator
        implements DataValidator {

    /** Default to apply if the field is empty. */
    @NonNull
    private final String mDefaultValue;

    /**
     * Constructor with default value.
     *
     * @param defValue Default to apply if the field is empty
     */
    DefaultFieldValidator(@NonNull final String defValue) {
        mDefaultValue = defValue;
    }

    /**
     * Gets the current value, and if {@code null} or empty,
     * replaces it with the mDefaultValue value.
     *
     * @param context      Current context
     * @param dataManager  The DataManager object
     * @param key          key for the data to validate
     * @param errorLabelId not used.
     */
    @Override
    @CallSuper
    public void validate(@NonNull final Context context,
                         @NonNull final DataManager dataManager,
                         @NonNull final String key,
                         final int errorLabelId) {

        final Object value = dataManager.get(key);
        if (value != null && value.toString().trim().isEmpty()) {
            dataManager.putString(key, mDefaultValue);
        }
    }
}
