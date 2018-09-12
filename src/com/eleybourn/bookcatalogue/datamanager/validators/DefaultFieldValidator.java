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

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;

/**
 * Validator to apply a default String value to empty fields.
 *
 * @author Philip Warner
 */
public class DefaultFieldValidator implements DataValidator {
    private final String mDefault;

    /**
     * Allow for no default value.
     */
    public DefaultFieldValidator() {
        this("");
    }

    /**
     * Constructor with default value
     *
     * @param defaultValue Default to apply
     */
    public DefaultFieldValidator(String defaultValue) {
        mDefault = defaultValue;
    }

    @Override
    public void validate(@NonNull final DataManager data, @NonNull final Datum datum, boolean crossValidating)
            throws ValidatorException {
        if (!datum.isVisible()) {
            // No validation required for invisible fields
            return;
        }
        Object value = data.get(datum);
        // Default validator does not cross-validate
        if (crossValidating)
            return;

        try {
            if (value.toString().trim().isEmpty()) {
                data.putString(datum, mDefault);
            }
            return;
        } catch (Exception e) {
            throw new ValidatorException(R.string.vldt_unable_to_get_value, new Object[]{datum.getKey()});
        }
    }
}