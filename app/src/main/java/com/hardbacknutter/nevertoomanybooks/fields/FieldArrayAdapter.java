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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

public class FieldArrayAdapter
        extends ExtArrayAdapter<String> {

    /** The formatter to apply on each line item. */
    @NonNull
    private final FieldFormatter<String> formatter;

    /**
     * Constructor.
     *
     * @param context    Current context.
     * @param filterType use {@link FilterType#Diacritic} for AutoComplete fields.
     *                   i.e. the user can type the value in.
     *                   use {@link FilterType#Passthrough} for drop-down menu's.
     *                   i.e. the user can select from a fixed list of values
     * @param items      The values to represent in the list view
     * @param formatter  (optional) formatter to use
     */
    public FieldArrayAdapter(@NonNull final Context context,
                             @NonNull final FilterType filterType,
                             @NonNull final List<String> items,
                             @Nullable final FieldFormatter<String> formatter) {
        super(context, R.layout.popup_dropdown_menu_item, filterType, items);
        this.formatter = Objects.requireNonNullElseGet(
                formatter, () -> (c, value) -> value != null ? value : "");
    }

    @NonNull
    public FieldFormatter<String> getFormatter() {
        return formatter;
    }

    @NonNull
    @Override
    protected CharSequence getItemText(@Nullable final String text) {
        return formatter.format(getContext(), text);
    }
}
