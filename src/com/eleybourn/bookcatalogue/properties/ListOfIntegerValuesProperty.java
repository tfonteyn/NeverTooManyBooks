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
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;

import java.util.Objects;

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
    public ListOfIntegerValuesProperty(final @StringRes int nameResourceId,
                                       final @NonNull PropertyGroup group,
                                       final @NonNull Integer defaultValue,
                                       final @NonNull @Size(min = 3) ItemList<Integer> list) {
        super(nameResourceId, group, defaultValue, list);
    }

    @Override
    @NonNull
    protected Integer getGlobalValue() {
        return BookCatalogueApp.getIntPreference(getPreferenceKey(), getDefaultValue());
    }

    @Override
    @NonNull
    protected ListOfIntegerValuesProperty setGlobalValue(final @Nullable Integer value) {
        Objects.requireNonNull(value);
        BookCatalogueApp.getSharedPreferences().edit().putInt(getPreferenceKey(), value).apply();
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
    public ListOfIntegerValuesProperty setPreferenceKey(final @NonNull String key) {
        super.setPreferenceKey(key);
        return this;
    }

    /**
     * Our value is Nullable. To Parcel it, we use Integer.MIN_VALUE for null.
     * Note: Boolean uses -1, but we can't use that here, as an 'int' is far wider.
     * Let's simply hope we never need Integer.MIN_VALUE as a real value.
     *
     * Note that {@link Parcel#writeValue(Object)} actually writes an Integer as 'int'
     * So 'null' is NOT preserved.
     *
     * API_UPGRADE 23 use {@link Parcel#writeTypedObject}
     */
    public void writeToParcel(final @NonNull Parcel dest) {
        Integer value = this.getValue();
        if (value == null) {
            dest.writeInt(Integer.MIN_VALUE);
        } else {
            dest.writeInt(value);
        }
    }

    /**
     *
     * API_UPGRADE 23  use {@link Parcel#readTypedObject}
     */
    public void readFromParcel(final @NonNull Parcel in) {
        int parceledInt = in.readInt();
        setValue(parceledInt == Integer.MIN_VALUE ? null : parceledInt);
    }
}

