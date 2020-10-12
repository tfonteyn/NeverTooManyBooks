/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * 'Meta' Validator to evaluate a list of validators; all validators must be true.
 */
public class AndValidator
        implements DataValidator {

    private final Collection<DataValidator> mList = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param validators list of validators
     */
    public AndValidator(@NonNull final DataValidator... validators) {
        mList.addAll(Arrays.asList(validators));
    }

    @Override
    public void validate(@NonNull final Context context,
                         @NonNull final DataManager dataManager,
                         @NonNull final String key,
                         final int errorLabelId)
            throws ValidatorException {

        for (final DataValidator validator : mList) {
            validator.validate(context, dataManager, key, errorLabelId);
        }
    }
}
