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
 * Validator that requires a blank field
 *
 * @author Philip Warner
 */
public class BlankValidator implements DataValidator {
    @Override
    public void validate(@NonNull DataManager data, @NonNull Datum datum, boolean crossValidating)
            throws ValidatorException {
        if (!datum.isVisible()) {
            // No validation required for invisible fields
            return;
        }

        if (crossValidating)
            return;
        try {
            String s = data.getString(datum);
            if (!s.isEmpty()) {
                s = s.trim();
                if (!s.isEmpty()) {
                    throw new ValidatorException(R.string.vldt_blank_required, new Object[]{datum.getKey()});
                }
                data.putString(datum, s);
            }
            return;
        } catch (Exception e) {
            throw new ValidatorException(R.string.vldt_blank_required, new Object[]{datum.getKey()});
        }
    }
}