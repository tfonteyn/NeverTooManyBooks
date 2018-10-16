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

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.utils.RTE;

/**
 * Extends ListProperty to create a nullable integer property with associated editing support.
 *
 * @author Philip Warner
 */
public class StringListProperty extends ListProperty<String> implements Property.StringValue {

    public StringListProperty(@NonNull final ItemEntries<String> list,
                              @NonNull final String uniqueId,
                              @NonNull final PropertyGroup group,
                              @StringRes final int nameResourceId) {
        super(list, uniqueId, group, nameResourceId);
    }

    @Override
    protected String getGlobalDefault() {
        return BookCatalogueApp.Prefs.getString(getPreferenceKey(), getDefaultValue());
    }

    @NonNull
    @Override
    protected StringListProperty setGlobalDefault(@Nullable final String value) {
        BookCatalogueApp.Prefs.putString(getPreferenceKey(), value);
        return this;
    }

    @NonNull
    @Override
    public StringListProperty set(@NonNull final Property p) {
        if (!(p instanceof StringValue)) {
            throw new RTE.IllegalTypeException(p.getClass().getCanonicalName());
        }
        StringValue v = (StringValue) p;
        set(v.get());
        return this;
    }
}

