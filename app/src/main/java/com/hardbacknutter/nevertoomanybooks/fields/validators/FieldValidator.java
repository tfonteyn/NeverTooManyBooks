/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.fields.validators;

import android.view.View;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.fields.Field;

/**
 * Interface for all field-level validators.
 * Could be replaced with a {@code Consumer<Field<T, V>>}.
 */
public interface FieldValidator<T, V extends View> {

    /**
     * Validation method.
     *
     * @param field to validate
     */
    void validate(@NonNull Field<T, V> field);
}
