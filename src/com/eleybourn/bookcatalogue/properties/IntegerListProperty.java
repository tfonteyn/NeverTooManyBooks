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

import com.eleybourn.bookcatalogue.BCPreferences;
import com.eleybourn.bookcatalogue.properties.Property.IntegerValue;

/**
 * Extends ListProperty to create a nullable integer property with associated editing support.
 *
 * @author Philip Warner
 */
public class IntegerListProperty extends ListProperty<Integer> implements IntegerValue {

    public IntegerListProperty(@NonNull final ItemEntries<Integer> list,
                               @NonNull final String uniqueId, @NonNull final PropertyGroup group,
                               final int nameResourceId) {
        super(list, uniqueId, group, nameResourceId, null, null, null);
    }

    @Override
    protected Integer getGlobalDefault() {
        return BCPreferences.getInt(getPreferenceKey(), getDefaultValue());
    }

    @Override
    protected IntegerListProperty setGlobalDefault(final Integer value) {
        BCPreferences.setInt(getPreferenceKey(), value);
        return this;
    }



    @Override
    public IntegerListProperty setGlobal(final boolean isGlobal) {
        super.setGlobal(isGlobal);
        return this;
    }

    @Override
    public IntegerListProperty setDefaultValue(final Integer value) {
        super.setDefaultValue(value);
        return this;
    }

    @Override
    public IntegerListProperty setPreferenceKey(final String key) {
        super.setPreferenceKey(key);
        return this;
    }

    @Override
    public IntegerListProperty set(final Property p) {
        if (!(p instanceof IntegerValue)) {
            throw new RuntimeException("Can not find a compatible interface for integer parameter");
        }
        IntegerValue v = (IntegerValue) p;
        set(v.get());
        return this;
    }
}

