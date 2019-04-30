/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.datamanager.validators;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;

/**
 * Validator to apply a default value and validate as Integer.
 * Long is casted to Integer.
 *
 * @author Philip Warner
 */
public class IntegerValidator
        implements DataValidator {

    private final int mDefaultValue;

    /**
     * Constructor with default value.
     *
     * @param defaultValue Default to apply if the field is empty
     */
    public IntegerValidator(final int defaultValue) {
        mDefaultValue = defaultValue;
    }

    @Override
    @CallSuper
    public void validate(@NonNull final DataManager dataManager,
                         @NonNull final Datum datum,
                         final boolean crossValidating)
            throws ValidatorException {

        if (!datum.isVisible() || crossValidating) {
            return;
        }

        Integer value;
        Object o = dataManager.get(datum);
        if (o == null) {
            value = mDefaultValue;
        } else if (o instanceof Integer) {
            value = (Integer) o;
        } else if (o instanceof Long) {
            value = ((Long) o).intValue();
        } else if (o.toString().trim().isEmpty()) {
            value = mDefaultValue;
        } else {
            try {
                value = Integer.parseInt(o.toString());
            } catch (NumberFormatException e) {
                throw new ValidatorException(R.string.vldt_integer_expected,
                                             datum.getKey());
            }
        }
        dataManager.putInt(datum, value);
    }
}
