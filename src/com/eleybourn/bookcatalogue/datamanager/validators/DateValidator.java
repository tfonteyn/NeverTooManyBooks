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
import com.eleybourn.bookcatalogue.utils.DateUtils;

/**
 * Validator to apply a default value and validate as a Date
 *
 * @author Philip Warner
 */
public class DateValidator extends DefaultFieldValidator {

    public DateValidator() {
        super();
    }

    public DateValidator(@NonNull final String defaultValue) {
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

        try {
            java.util.Date d = DateUtils.parseDate(data.getString(datum));
            if (d == null) {
                throw new ValidatorException(R.string.vldt_date_expected, new Object[]{datum.getKey()});
            }
            data.putString(datum, DateUtils.toSqlDateTime(d));
        } catch (Exception e) {
            throw new ValidatorException(R.string.vldt_date_expected, new Object[]{datum.getKey()});
        }
    }
}