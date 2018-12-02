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

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;

/**
 * Implements ListOfValuesProperty with a String(nullable) value with associated editing support.
 *
 * @author Philip Warner
 */
public class ListOfStringValuesProperty extends ListOfValuesProperty<String> {

    /**
     * @param list list with options. Minimum 1 element.
     */
    public ListOfStringValuesProperty(final @NonNull @Size(min = 1) ItemList<String> list,
                                      final @NonNull String uniqueId,
                                      final @NonNull PropertyGroup group,
                                      final @StringRes int nameResourceId,
                                      final @Nullable String defaultValue) {
        super(list, uniqueId, group, nameResourceId, defaultValue);
    }

    @Override
    protected String getGlobalValue() {
        return BookCatalogueApp.getStringPreference(getPreferenceKey(), getDefaultValue());
    }

    @NonNull
    @Override
    protected ListOfStringValuesProperty setGlobalValue(final @Nullable String value) {
        BookCatalogueApp.getSharedPreferences().edit().putString(getPreferenceKey(), value).apply();
        return this;
    }

    /**
     * For chaining with correct return type
     */
    @NonNull
    @Override
    @CallSuper
    public ListOfStringValuesProperty setDefaultValue(final String value) {
        super.setDefaultValue(value);
        return this;
    }

    /**
     * For chaining with correct return type
     */
    @NonNull
    @Override
    @CallSuper
    public ListOfStringValuesProperty setPreferenceKey(final @NonNull String key) {
        super.setPreferenceKey(key);
        return this;
    }
}

