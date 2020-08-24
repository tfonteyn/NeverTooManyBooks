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
package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;

/**
 * FieldFormatter for a list field. Formats the Entity's as a CSV String.
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong></li>
 * </ul>
 */
public class CsvFormatter
        implements FieldFormatter<List<Entity>> {

    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final List<Entity> rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        } else {
            return Csv.join(", ", rawValue, true, null,
                            element -> element.getLabel(context));
        }
    }
}
