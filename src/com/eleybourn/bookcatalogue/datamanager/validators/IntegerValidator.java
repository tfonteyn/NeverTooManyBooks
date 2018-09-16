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
 * Validator to apply a default value and validate as integer.
 *
 * @author Philip Warner
 */
public class IntegerValidator extends DefaultFieldValidator {
    public IntegerValidator() {
        super();
    }

    public IntegerValidator(@NonNull final String defaultValue) {
        super(defaultValue);
    }

    @Override
    public void validate(@NonNull final DataManager data, @NonNull final Datum datum, final boolean crossValidating)
            throws ValidatorException {
        if (!datum.isVisible()) {
            // No validation required for invisible fields
            return;
        }
        if (crossValidating)
            return;

        super.validate(data, datum, false);

        Object o;
        try {
            o = data.get(datum);
            Integer v;
            if (o instanceof Integer) {
                v = (Integer) o;
            } else {
                v = Integer.parseInt(o.toString());
            }
            data.putInt(datum, v);
            return;
        } catch (Exception e) {
            throw new ValidatorException(R.string.vldt_integer_expected, new Object[]{datum.getKey()});
        }
    }
}