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
import com.eleybourn.bookcatalogue.properties.Property.BooleanValue;

/**
 * Extends ListProperty to create a trinary value (or nullable boolean?) with associated editing support.
 *
 * Resulting editing display is a list of values in a dialog.
 *
 * @author Philip Warner
 */
public class BooleanListProperty extends ListProperty<Boolean> implements BooleanValue {

    public BooleanListProperty(@NonNull final ItemEntries<Boolean> list,
                                @NonNull final String uniqueId,
                               final PropertyGroup group,
                               final int nameResourceId) {
        super(list, uniqueId, group, nameResourceId, false, false);
    }

    @Override
    protected Boolean getGlobalDefault() {
        Boolean value = getDefaultValue();
        if (value == null) {
            throw new IllegalStateException();
        }
        return BCPreferences.getBoolean(getPreferenceKey(), value);
    }

    protected BooleanListProperty setGlobalDefault(final Boolean value) {
        BCPreferences.setBoolean(getPreferenceKey(), value);
        return this;
    }

    @NonNull
    @Override
    public BooleanListProperty setGlobal(final boolean isGlobal) {
        super.setGlobal(isGlobal);
        return this;
    }

    @NonNull
    @Override
    public BooleanListProperty setHint(final int hint) {
        super.setHint(hint);
        return this;
    }

    @NonNull
    @Override
    public BooleanListProperty setDefaultValue(final Boolean value) {
        super.setDefaultValue(value);
        return this;
    }

    @NonNull
    @Override
    public BooleanListProperty setWeight(final int weight) {
        super.setWeight(weight);
        return this;
    }

    @NonNull
    @Override
    public BooleanListProperty setPreferenceKey(@NonNull final String key) {
        super.setPreferenceKey(key);
        return this;
    }

    @NonNull
    @Override
    public BooleanListProperty set(@NonNull final Property p) {
        if (!(p instanceof BooleanValue))
            throw new IllegalStateException();
        BooleanValue v = (BooleanValue) p;
        set(v.get());
        return this;
    }

    public boolean isTrue() {
        Boolean b =  super.getResolvedValue();
        return (b != null ? b: false);
    }
}

