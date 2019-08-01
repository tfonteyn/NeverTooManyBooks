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

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;

import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;

/**
 * 'Meta' Validator to evaluate a list of validators; all validators must be true.
 *
 * @author Philip Warner
 */
public class AndValidator
        extends ArrayList<DataValidator>
        implements DataValidator {


    private static final long serialVersionUID = 4162885236932269555L;

    /**
     * Constructor.
     *
     * @param validators list of validators
     */
    public AndValidator(@NonNull final DataValidator... validators) {
        addAll(Arrays.asList(validators));
    }

    @Override
    public void validate(@NonNull final DataManager dataManager,
                         @NonNull final Datum datum,
                         final boolean crossValidating)
            throws ValidatorException {

        for (DataValidator validator : this) {
            // Only set the Bundle for the last in the list
            validator.validate(dataManager, datum, crossValidating);
        }
    }
}
