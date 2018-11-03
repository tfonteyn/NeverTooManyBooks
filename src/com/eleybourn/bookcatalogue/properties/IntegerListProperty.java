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
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.properties.Property.IntegerValue;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.Objects;

/**
 * Extends ListProperty to create a nullable integer property with associated editing support.
 *
 * @author Philip Warner
 */
public class IntegerListProperty extends ListProperty<Integer> implements IntegerValue {

    public IntegerListProperty(final @NonNull ItemEntries<Integer> list,
                               final @NonNull String uniqueId,
                               final @NonNull PropertyGroup group,
                               final @StringRes int nameResourceId) {
        super(list, uniqueId, group, nameResourceId);
    }

    @Override
    @Nullable
    protected Integer getGlobalDefault() {
        return BookCatalogueApp.Prefs.getInt(getPreferenceKey(), Objects.requireNonNull(getDefaultValue()));
    }

    @Override
    @NonNull
    protected IntegerListProperty setGlobalDefault(final @Nullable Integer value) {
        Objects.requireNonNull(value);
        BookCatalogueApp.Prefs.putInt(getPreferenceKey(), value);
        return this;
    }

    @NonNull
    @Override
    @CallSuper
    public IntegerListProperty setGlobal(final boolean isGlobal) {
        super.setGlobal(isGlobal);
        return this;
    }

    @NonNull
    @Override
    @CallSuper
    public IntegerListProperty setDefaultValue(final Integer value) {
        super.setDefaultValue(value);
        return this;
    }

    @Nullable
    @Override
    @CallSuper
    public Integer getDefaultValue() {
        return super.getDefaultValue();
    }

    @NonNull
    @Override
    @CallSuper
    public IntegerListProperty setPreferenceKey(final @NonNull String key) {
        super.setPreferenceKey(key);
        return this;
    }

    @NonNull
    @Override
    public IntegerListProperty set(final @NonNull Property p) {
        if (!(p instanceof IntegerValue)) {
            throw new RTE.IllegalTypeException(p.getClass().getCanonicalName());
        }
        IntegerValue v = (IntegerValue) p;
        set(v.get());
        return this;
    }

    /**
     *
     * @return value itself, or 0 when null
     */
    @CallSuper
    public int getInt() {
        Integer value = super.getResolvedValue();
        return (value != null ? value : 0);
    }
}

