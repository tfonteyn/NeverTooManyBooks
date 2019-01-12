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

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Defines a set of groups of properties for use in the UI
 */
public class PropertyGroup implements Comparable<PropertyGroup> {

    public static final PropertyGroup GRP_GENERAL = new PropertyGroup(R.string.pg_general, 1);

    /** String resource ID for group name */
    @StringRes
    private final int mNameResourceId;
    /** Weight of this group, for sorting */
    @NonNull
    private final Integer mWeight;
    /** Name of this group, for sorting (from resource ID) */
    @Nullable
    private String mName = null;

    /**
     * Constructor
     */
    public PropertyGroup(@StringRes final int nameResourceId, final int weight) {
        this.mNameResourceId = nameResourceId;
        this.mWeight = weight;
    }

    @StringRes
    public int getNameResourceId() {
        return mNameResourceId;
    }

    /** Realize and return the group name. */
    @NonNull
    private String getName() {
        if (mName == null) {
            mName = BookCatalogueApp.getResString(mNameResourceId);
        }
        return mName;
    }

    /** Compare two groups for sorting purposes. */
    @Override
    public int compareTo(@NonNull final PropertyGroup o) {
        // Compare weights
        final int wCmp = mWeight.compareTo(o.mWeight);
        if (wCmp != 0) {
            return wCmp;
        }

        // Weights match, compare names
        if (mNameResourceId != o.mNameResourceId) {
            return getName().compareTo(o.getName());
        } else {
            return 0;
        }
    }
}
