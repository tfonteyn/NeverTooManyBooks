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

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * Validator to apply a default value and validate as Double.
 * Float, Integer, Long are casted to Double.
 * <p>
 * {@code null} or empty string become 0d.
 * <p>
 * All locales are taken into account for parsing.
 */
public class DoubleValidator
        implements DataValidator {

    /** Default to apply if the field is {@code null} or empty. */
    private final double mDefaultValue;

    /**
     * Constructor; default value is 0d.
     */
    public DoubleValidator() {
        mDefaultValue = 0d;
    }

    /**
     * Constructor with default value.
     *
     * @param defValue Default to apply if the field is empty
     */
    public DoubleValidator(final double defValue) {
        mDefaultValue = defValue;
    }

    @Override
    @CallSuper
    public void validate(@NonNull final Context context,
                         @NonNull final DataManager dataManager,
                         @NonNull final String key,
                         final int errorLabelId)
            throws ValidatorException {

        Double value;
        Object obj = dataManager.get(key);
        if (obj == null) {
            value = mDefaultValue;
        } else if (obj instanceof Double) {
            value = (Double) obj;
        } else if (obj instanceof Float) {
            value = ((Float) obj).doubleValue();
        } else if (obj instanceof Long) {
            value = ((Long) obj).doubleValue();
        } else if (obj instanceof Integer) {
            value = ((Integer) obj).doubleValue();
        } else {
            String stringValue = obj.toString().trim();
            if (stringValue.isEmpty()) {
                value = mDefaultValue;
            } else {
                try {
                    // Locale taken into account
                    value = ParseUtils.parseDouble(stringValue, Locale.getDefault());

                } catch (@NonNull final NumberFormatException e) {
                    throw new ValidatorException(R.string.vldt_real_expected_for_x,
                                                 context.getString(errorLabelId));
                }
            }
        }
        dataManager.putDouble(key, value);
    }
}
