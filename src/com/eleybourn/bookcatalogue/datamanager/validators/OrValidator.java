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

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;

/**
 * 'Meta' Validator to evaluate a list of validators; any one being <tt>true</tt> is OK.
 *
 * @author Philip Warner
 */
public class OrValidator
        extends ArrayList<DataValidator>
        implements DataValidator {

    private static final long serialVersionUID = 550199747728692370L;

    /**
     * Constructor.
     *
     * @param validators list of validators
     */
    public OrValidator(@NonNull final DataValidator... validators) {
        addAll(Arrays.asList(validators));
    }

    @Override
    public void validate(@NonNull final DataManager dataManager,
                         @NonNull final Datum datum,
                         final boolean crossValidating)
            throws ValidatorException {
        ValidatorException lastException = null;
        for (DataValidator validator : this) {
            try {
                validator.validate(dataManager, datum, crossValidating);
                // first one 'ok' and we're done.
                return;
            } catch (ValidatorException e) {
                // Do nothing...try next validator
                lastException = e;
            }
        }

        if (lastException != null) {
            throw lastException;
        } else {
            throw new ValidatorException(R.string.vldt_failed, new Object[]{datum.getKey()});
        }
    }
}
