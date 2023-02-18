/*
 * @Copyright 2018-2022 HardBackNutter
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
import androidx.annotation.StringRes;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * Validator to require a non-blank field.
 */
public class NonBlankValidator
        implements DataValidator {

    @Override
    public void validate(@NonNull final Context context,
                         @NonNull final DataManager dataManager,
                         @NonNull final String key,
                         @StringRes final int errorLabelResId)
            throws ValidatorException {

        final Object o = dataManager.get(context, key);
        if (o == null) {
            throw new ValidatorException(context.getString(R.string.vldt_non_blank_required_for_x,
                                                           context.getString(errorLabelResId)));

        } else if (o instanceof String && dataManager.getString(key).isEmpty()) {
            throw new ValidatorException(context.getString(R.string.vldt_non_blank_required_for_x,
                                                           context.getString(errorLabelResId)));
        } else if (o instanceof ArrayList && dataManager.getParcelableArrayList(key).isEmpty()) {
            throw new ValidatorException(context.getString(R.string.vldt_non_blank_required_for_x,
                                                           context.getString(errorLabelResId)));
        }
    }
}
