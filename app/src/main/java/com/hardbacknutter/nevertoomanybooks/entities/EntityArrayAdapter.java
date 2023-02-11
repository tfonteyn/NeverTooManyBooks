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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.ExtArrayAdapter;

/**
 * An ArrayAdapter for using an {@link Entity}
 * with an {@link android.widget.AutoCompleteTextView}.
 * i.e. a Material Exposed dropdown menu.
 *
 * @param <T> type of list item
 */
public class EntityArrayAdapter<T extends Entity>
        extends ExtArrayAdapter<T> {

    /**
     * Constructor.
     *
     * @param context Current context
     * @param list    of entities to choose from
     */
    public EntityArrayAdapter(@NonNull final Context context,
                              @NonNull final List<T> list) {
        this(context, FilterType.Passthrough, list);
    }

    /**
     * Constructor.
     *
     * @param context Current context
     * @param list    of entities to choose from
     */
    public EntityArrayAdapter(@NonNull final Context context,
                              @NonNull final FilterType filterType,
                              @NonNull final List<T> list) {
        super(context, R.layout.popup_dropdown_menu_item, filterType, list);
    }

    /**
     * Return the id of the {@link Entity}.
     *
     * @param position of the item
     *
     * @return id
     */
    @Override
    public long getItemId(final int position) {
        //noinspection ConstantConditions
        return getItem(position).getId();
    }

    /**
     * Return the label of the {@link Entity}.
     *
     * @param item to use
     *
     * @return label
     */
    @NonNull
    @Override
    protected CharSequence getItemText(@Nullable final T item) {
        return item == null ? "" : item.getLabel(getContext());
    }
}
