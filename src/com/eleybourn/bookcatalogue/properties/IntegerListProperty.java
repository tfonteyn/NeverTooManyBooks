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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.BCPreferences;
import com.eleybourn.bookcatalogue.properties.Property.IntegerValue;

/**
 * Extends ListProperty to create a nullable integer property with associated editing support.
 *
 * @author Philip Warner
 */
public class IntegerListProperty extends ListProperty<Integer> implements IntegerValue {

    public IntegerListProperty(@NonNull final ItemEntries<Integer> list,
                               @NonNull final String uniqueId,
                               @NonNull final PropertyGroup group,
                               @StringRes final int nameResourceId) {
        super(list, uniqueId, group, nameResourceId);
    }

    @Override
    @Nullable
    protected Integer getGlobalDefault() {
        Integer value = getDefaultValue();
        if (value == null) {
            throw new IllegalStateException();
        }
        return BCPreferences.getInt(getPreferenceKey(), value);
    }

    @Override
    @NonNull
    protected IntegerListProperty setGlobalDefault(@Nullable final Integer value) {
        if (value == null) {
            throw new IllegalStateException();
        }
        BCPreferences.setInt(getPreferenceKey(), value);
        return this;
    }

    @NonNull
    @Override
    public IntegerListProperty setGlobal(final boolean isGlobal) {
        super.setGlobal(isGlobal);
        return this;
    }

    @NonNull
    @Override
    public IntegerListProperty setDefaultValue(final Integer value) {
        super.setDefaultValue(value);
        return this;
    }

    @Nullable
    @Override
    public Integer getDefaultValue() {
        return super.getDefaultValue();
    }

    @NonNull
    @Override
    public IntegerListProperty setPreferenceKey(@NonNull final String key) {
        super.setPreferenceKey(key);
        return this;
    }

    @NonNull
    @Override
    public IntegerListProperty set(@NonNull final Property p) {
        if (!(p instanceof IntegerValue)) {
            throw new IllegalStateException("Can not find a compatible interface for integer parameter");
        }
        IntegerValue v = (IntegerValue) p;
        set(v.get());
        return this;
    }

    /**
     *
     * @return value itself, or 0 when null
     */
    public int getInt() {
        Integer value = super.getResolvedValue();
        return (value != null ? value : 0);
    }
}

