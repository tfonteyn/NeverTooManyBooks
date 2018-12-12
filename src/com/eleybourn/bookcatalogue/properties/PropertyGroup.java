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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;

/**
 * Defines a set of groups of properties for use in the UI
 */
public class PropertyGroup implements Comparable<PropertyGroup> {

    public static final PropertyGroup GRP_GENERAL = new PropertyGroup(R.string.general, 1);
    public static final PropertyGroup GRP_ADVANCED_OPTIONS = new PropertyGroup(R.string.lbl_advanced_options, 80);

    public static final PropertyGroup GRP_USER_INTERFACE = new PropertyGroup(R.string.user_interface, 35);
    public static final PropertyGroup GRP_SCANNER = new PropertyGroup(R.string.scanning, 70);

    public static final PropertyGroup GRP_THUMBNAILS = new PropertyGroup(R.string.thumbnails, 40);

    public static final PropertyGroup GRP_AUTHOR = new PropertyGroup(R.string.lbl_author, 50);
    public static final PropertyGroup GRP_SERIES = new PropertyGroup(R.string.lbl_series, 50);

    public static final PropertyGroup GRP_FILTERS = new PropertyGroup(R.string.booklist_filters, 70);
    public static final PropertyGroup GRP_EXTRA_BOOK_DETAILS = new PropertyGroup(R.string.blp_extra_book_details, 100);

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
    public PropertyGroup(final @StringRes int nameResourceId, final int weight) {
        this.mNameResourceId = nameResourceId;
        this.mWeight = weight;
    }

    @StringRes
    int getNameId() {
        return mNameResourceId;
    }

    /** Realize and return the group name */
    @NonNull
    private String getName() {
        if (mName == null) {
            mName = BookCatalogueApp.getResourceString(mNameResourceId);
        }
        return mName;
    }

    /** Compare two groups for sorting purposes */
    @Override
    public int compareTo(@NonNull final PropertyGroup rhs) {
        // Compare weights
        final int wCmp = mWeight.compareTo(rhs.mWeight);
        if (wCmp != 0) {
            return wCmp;
        }

        // Weights match, compare names
        if (mNameResourceId != rhs.mNameResourceId) {
            return getName().compareTo(rhs.getName());
        } else {
            return 0;
        }
    }
}
