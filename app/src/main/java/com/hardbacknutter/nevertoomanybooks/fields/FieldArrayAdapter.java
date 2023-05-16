/*
 * @Copyright 2018-2023 HardBackNutter
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
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

public final class FieldArrayAdapter
        extends ExtArrayAdapter<String> {

    /** The formatter to apply on each line item. */
    @Nullable
    private final FieldFormatter<String> formatter;

    @Nullable
    private final long[] idList;

    /**
     * Constructor.
     *
     * @param context    Current context.
     * @param filterType use {@link FilterType#Diacritic} for AutoComplete fields.
     *                   i.e. the user can type the value in.
     *                   use {@link FilterType#Passthrough} for drop-down menu's.
     *                   i.e. the user can select from a fixed list of values
     * @param ids        The id's for the values; pass in {@code null} to use the position instead.
     * @param labels     The labels to show in the list view
     * @param formatter  (optional) formatter to use
     */
    private FieldArrayAdapter(@NonNull final Context context,
                              @NonNull final FilterType filterType,
                              @Nullable final long[] ids,
                              @NonNull final List<String> labels,
                              @Nullable final FieldFormatter<String> formatter) {
        super(context, R.layout.popup_dropdown_menu_item, filterType, labels);
        idList = ids;

        this.formatter = formatter;
    }

    /**
     * Create an adapter suited for an AutoComplete field.
     * A formatter is <strong>required</strong>.
     *
     * @param context   Current context
     * @param labels    The labels to show in the list view
     * @param formatter formatter to use
     *
     * @return adapter
     */
    @NonNull
    public static FieldArrayAdapter createAutoComplete(@NonNull final Context context,
                                                       @NonNull final List<String> labels,
                                                       @NonNull
                                                       final FieldFormatter<String> formatter) {
        return new FieldArrayAdapter(context, ExtArrayAdapter.FilterType.Diacritic,
                                     null, labels, formatter);
    }

    /**
     * Create an adapter suited for a fixed DropDownMenu using plain String values.
     *
     * @param context   Current context
     * @param labels    The labels to show in the list view
     * @param formatter (optional) formatter to use
     *
     * @return adapter
     */
    @NonNull
    public static FieldArrayAdapter createStringDropDown(@NonNull final Context context,
                                                         @NonNull final List<String> labels,
                                                         @Nullable
                                                         final FieldFormatter<String> formatter) {
        return new FieldArrayAdapter(context, ExtArrayAdapter.FilterType.Passthrough,
                                     null, labels, formatter);
    }

    /**
     * Create an adapter suited for a fixed DropDownMenu using {@link Entity} values.
     *
     * @param context  Current context
     * @param entities The entities (labels) to show in the list view
     *
     * @return adapter
     */
    @NonNull
    public static FieldArrayAdapter createEntityDropDown(@NonNull final Context context,
                                                         @NonNull
                                                         final List<? extends Entity> entities) {

        final long[] ids = entities.stream().mapToLong(Entity::getId).toArray();
        final List<String> labels = entities.stream()
                                            .map(entity -> entity.getLabel(context))
                                            .collect(Collectors.toList());

        return new FieldArrayAdapter(context, ExtArrayAdapter.FilterType.Passthrough,
                                     ids, labels, null);
    }

    @Override
    public long getItemId(final int position) {
        if (idList != null) {
            return idList[position];
        } else {
            return position;
        }
    }

    @NonNull
    @Override
    protected CharSequence getItemText(@Nullable final String text) {
        if (formatter != null) {
            return formatter.format(getContext(), text);
        } else {
            return super.getItemText(text);
        }
    }
}
