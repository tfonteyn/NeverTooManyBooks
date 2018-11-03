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
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.properties.Property.BooleanValue;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.Objects;

/**
 * Extends ListProperty to create a trinary value (or nullable boolean?) with associated editing support.
 *
 * Resulting editing display is a list of values in a dialog.
 *
 * @author Philip Warner
 */
public class BooleanListProperty extends ListProperty<Boolean> implements BooleanValue {

    public BooleanListProperty(final @NonNull ItemEntries<Boolean> list,
                               final @NonNull String uniqueId,
                               final @NonNull PropertyGroup group,
                               final @StringRes int nameResourceId) {
        super(list, uniqueId, group, nameResourceId, false, false);
    }

    @Override
    protected Boolean getGlobalDefault() {
        Boolean value = getDefaultValue();
        Objects.requireNonNull(value);
        return BookCatalogueApp.Prefs.getBoolean(getPreferenceKey(), value);
    }

    @NonNull
    protected BooleanListProperty setGlobalDefault(final Boolean value) {
        BookCatalogueApp.Prefs.putBoolean(getPreferenceKey(), value);
        return this;
    }

    @NonNull
    @Override
    @CallSuper
    public BooleanListProperty setGlobal(final boolean isGlobal) {
        super.setGlobal(isGlobal);
        return this;
    }

    @NonNull
    @Override
    @CallSuper
    public BooleanListProperty setHint(final int hint) {
        super.setHint(hint);
        return this;
    }

    @NonNull
    @Override
    @CallSuper
    public BooleanListProperty setDefaultValue(final Boolean value) {
        super.setDefaultValue(value);
        return this;
    }

    @NonNull
    @Override
    @CallSuper
    public BooleanListProperty setWeight(final int weight) {
        super.setWeight(weight);
        return this;
    }

    @NonNull
    @Override
    @CallSuper
    public BooleanListProperty setPreferenceKey(final @NonNull String key) {
        super.setPreferenceKey(key);
        return this;
    }

    @NonNull
    @Override
    public BooleanListProperty set(final @NonNull Property p) {
        if (!(p instanceof BooleanValue))
            throw new RTE.IllegalTypeException(p.getClass().getCanonicalName());
        BooleanValue v = (BooleanValue) p;
        set(v.get());
        return this;
    }

    @CallSuper
    public boolean isTrue() {
        Boolean b = super.getResolvedValue();
        return (b != null ? b : false);
    }
}

