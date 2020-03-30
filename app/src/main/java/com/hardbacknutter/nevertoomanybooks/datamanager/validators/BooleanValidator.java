/*
 * @Copyright 2020 HardBackNutter
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

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * Validator to apply a default value and validate as Boolean.
 */
public class BooleanValidator
        implements DataValidator {

    /** Default to apply if the field is empty. */
    private final boolean mDefaultValue;

    /**
     * Constructor with default value.
     *
     * @param defValue Default to apply
     */
    public BooleanValidator(final boolean defValue) {
        mDefaultValue = defValue;
    }

    @Override
    @CallSuper
    public void validate(@NonNull final Context context,
                         @NonNull final DataManager dataManager,
                         @NonNull final String key,
                         final int errorLabelId)
            throws ValidatorException {

        Object o = dataManager.get(key);
        if (o == null || o.toString().trim().isEmpty()) {
            dataManager.putBoolean(key, mDefaultValue);
            return;
        }
        try {
            ParseUtils.toBoolean(o);
        } catch (@NonNull final NumberFormatException e) {
            throw new ValidatorException(R.string.vldt_boolean_expected_for_x, errorLabelId);
        }
    }
}
