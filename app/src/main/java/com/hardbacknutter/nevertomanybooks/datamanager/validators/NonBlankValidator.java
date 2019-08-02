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
package com.hardbacknutter.nevertomanybooks.datamanager.validators;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertomanybooks.datamanager.Datum;

/**
 * Validator to require a non-blank field.
 *
 * @author Philip Warner
 */
public class NonBlankValidator
        implements DataValidator {

    @Override
    public void validate(@NonNull final DataManager dataManager,
                         @NonNull final Datum datum,
                         final boolean crossValidating)
            throws ValidatorException {

        if (crossValidating) {
            return;
        }

        Object o = dataManager.get(datum);
        if (o == null) {
            throw new ValidatorException(R.string.vldt_non_blank_required_for_x, datum.getKey());

        } else if ((o instanceof String) && (dataManager.getString(datum).trim().isEmpty())) {
                throw new ValidatorException(R.string.vldt_non_blank_required_for_x, datum.getKey());

        } else if ((o instanceof ArrayList) && (dataManager.getParcelableArrayList(datum).isEmpty())) {
                throw new ValidatorException(R.string.vldt_non_blank_required_for_x, datum.getKey());
        }
    }
}
