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
package com.eleybourn.bookcatalogue.datamanager.datavalidators;

import android.support.annotation.CallSuper;
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

    public IntegerValidator(final @NonNull String defaultValue) {
        super(defaultValue);
    }

    @Override
    @CallSuper
    public void validate(final @NonNull DataManager data, final @NonNull Datum datum, final boolean crossValidating)
            throws ValidatorException {
        if (datum.isHidden()) {
            // No validation required for invisible fields
            return;
        }
        if (crossValidating)
            return;

        super.validate(data, datum, false);
        try {
            Integer value;
            Object o = data.get(datum);
            if (o instanceof Integer) {
                value = (Integer) o;
            } else {
                //noinspection ConstantConditions
                value = Integer.parseInt(o.toString());
            }
            data.putInt(datum, value);
        } catch (Exception e) {
            throw new ValidatorException(R.string.vldt_integer_expected, new Object[]{datum.getKey()});
        }
    }
}