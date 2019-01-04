/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.properties;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.utils.Prefs;

import java.util.Objects;

import androidx.annotation.ArrayRes;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Implements ListOfValuesProperty with a String(nullable) value with associated editing support.
 *
 * @author Philip Warner
 */
public class ListOfStringValuesProperty extends ListOfValuesProperty<String> {

    /**
     * @param labels list with options.
     */
    public ListOfStringValuesProperty(@StringRes final int nameResourceId,
                                       @NonNull final PropertyGroup group,
                                       @NonNull final String defaultValue,
                                       final @ArrayRes int labels,
                                       final @ArrayRes int values) {
        super(nameResourceId, group, defaultValue);

        ItemList<String> list = new ItemList<>();
        String[] labelArray = BookCatalogueApp.getResourceStringArray(labels);
        String[] valueArray = BookCatalogueApp.getResourceStringArray(values);

        list.add(new ListEntry<String>(null, R.string.use_default_setting));
        for (int i = 0; i < labelArray.length; i++) {
            list.add(new ListEntry<>(valueArray[i], labelArray[i]));
        }
        setList(list);
    }

    @Override
    @NonNull
    protected String getGlobalValue() {
        //noinspection ConstantConditions
        return Prefs.getString(getPreferenceKey(), getDefaultValue());
    }

    @NonNull
    @Override
    protected ListOfStringValuesProperty setGlobalValue(@Nullable final String value) {
        Objects.requireNonNull(value);
        Prefs.getPrefs().edit().putString(getPreferenceKey(), value).apply();
        return this;
    }

    /**
     * For chaining with correct return type
     */
    @NonNull
    @Override
    @CallSuper
    public ListOfStringValuesProperty setDefaultValue(@NonNull final String value) {
        super.setDefaultValue(value);
        return this;
    }

    /**
     * For chaining with correct return type
     */
    @NonNull
    @Override
    @CallSuper
    public ListOfStringValuesProperty setPreferenceKey(@NonNull final String key) {
        super.setPreferenceKey(key);
        return this;
    }

    /**
     * For chaining with correct return type
     */
    @NonNull
    @Override
    @CallSuper
    public ListOfStringValuesProperty setPreferenceKey(@StringRes final int key) {
        super.setPreferenceKey(key);
        return this;
    }
}

