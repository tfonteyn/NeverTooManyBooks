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

import android.os.Parcel;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.utils.Prefs;

import java.util.Objects;

import androidx.annotation.ArrayRes;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.StringRes;

/**
 * Implements ListOfValuesProperty with an Integer(nullable) value with associated editing support.
 *
 * to Parcel the value, use {@link #writeToParcel(Parcel)} and {@link #readFromParcel(Parcel)}
 *
 * @author Philip Warner
 */
public class ListOfIntegerValuesProperty extends ListOfValuesProperty<Integer> {

    /**
     * @param list list with options. Minimum 3 elements.
     */
    public ListOfIntegerValuesProperty(@StringRes final int nameResourceId,
                                       @NonNull final PropertyGroup group,
                                       @NonNull final Integer defaultValue,
                                       @NonNull final @Size(min = 3) ItemList<Integer> list) {
        super(nameResourceId, group, defaultValue);
        setList(list);
    }

    /**
     * @param labels list with options.
     */
    public ListOfIntegerValuesProperty(@StringRes final int nameResourceId,
                                       @NonNull final PropertyGroup group,
                                       @NonNull final Integer defaultValue,
                                       final @ArrayRes int labels,
                                       final @ArrayRes int values) {
        super(nameResourceId, group, defaultValue);

        ItemList<Integer> list = new ItemList<>();
        String[] labelArray = BookCatalogueApp.getResStringArray(labels);
        String[] valueArray = BookCatalogueApp.getResStringArray(values);

        list.add(new ListEntry<Integer>(null, R.string.use_default_setting));
        for (int i = 0; i < labelArray.length; i++) {
            list.add(new ListEntry<>(Integer.parseInt(valueArray[i]), labelArray[i]));
        }
        setList(list);
    }

    @Override
    @NonNull
    protected Integer getGlobalValue() {
        return Prefs.getPrefs().getInt(getPreferenceKey(), getDefaultValue());
    }

    @Override
    @NonNull
    protected ListOfIntegerValuesProperty setGlobalValue(@Nullable final Integer value) {
        Objects.requireNonNull(value);
        Prefs.getPrefs().edit().putInt(getPreferenceKey(), value).apply();
        return this;
    }

    /**
     * For chaining with correct return type
     */
    @NonNull
    @Override
    @CallSuper
    public ListOfIntegerValuesProperty setIsGlobal(final boolean isGlobal) {
        super.setIsGlobal(isGlobal);
        return this;
    }

    /**
     * For chaining with correct return type
     */
    @NonNull
    @Override
    @CallSuper
    public ListOfIntegerValuesProperty setWeight(final int weight) {
        super.setWeight(weight);
        return this;
    }

    /**
     * For chaining with correct return type
     */
    @NonNull
    @Override
    @CallSuper
    public ListOfIntegerValuesProperty setDefaultValue(@NonNull final Integer value) {
        super.setDefaultValue(value);
        return this;
    }

    /**
     * For chaining with correct return type
     */
    @NonNull
    @Override
    @CallSuper
    public ListOfIntegerValuesProperty setPreferenceKey(@NonNull final String key) {
        super.setPreferenceKey(key);
        return this;
    }

    /**
     * For chaining with correct return type
     */
    @NonNull
    @Override
    @CallSuper
    public ListOfIntegerValuesProperty setPreferenceKey(@StringRes final int key) {
        super.setPreferenceKey(key);
        return this;
    }

    public void writeToParcel(@NonNull final Parcel dest) {
        dest.writeValue(getValue());
    }

    public void readFromParcel(@NonNull final Parcel in) {
        setValue((Integer)in.readValue(getClass().getClassLoader()));
    }
}

