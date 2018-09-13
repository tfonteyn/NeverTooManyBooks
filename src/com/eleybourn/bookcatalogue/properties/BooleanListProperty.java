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
        super(list, uniqueId, group, nameResourceId, false, null, false);
    }

    @Override
    protected Boolean getGlobalDefault() {
        return BCPreferences.getBoolean(getPreferenceKey(), getDefaultValue());
    }

    @Override
    protected BooleanListProperty setGlobalDefault(Boolean value) {
        BCPreferences.setBoolean(getPreferenceKey(), value);
        return this;
    }

    @Override
    public BooleanListProperty setGlobal(final boolean isGlobal) {
        super.setGlobal(isGlobal);
        return this;
    }

    @Override
    public BooleanListProperty setHint(final int hint) {
        super.setHint(hint);
        return this;
    }

    @Override
    public BooleanListProperty setDefaultValue(final Boolean value) {
        super.setDefaultValue(value);
        return this;
    }

    @Override
    public BooleanListProperty setWeight(final int weight) {
        super.setWeight(weight);
        return this;
    }

    @Override
    public BooleanListProperty setPreferenceKey(final String key) {
        super.setPreferenceKey(key);
        return this;
    }

    @Override
    public BooleanListProperty set(Property p) {
        if (!(p instanceof BooleanValue))
            throw new RuntimeException("Can not find a compatible interface for boolean parameter");
        BooleanValue v = (BooleanValue) p;
        set(v.get());
        return this;
    }
}

