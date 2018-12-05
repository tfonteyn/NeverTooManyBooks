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

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.properties.Property.ValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Class to manage a set of properties.
 *
 * @author Philip Warner
 */
public class PropertyList extends ArrayList<Property> {
    @SuppressLint("UseSparseArrays")
    private final Map<Integer, Property> mMap = new HashMap<>();

    /** Add a property to this collection */
    public boolean add(final @NonNull Property p) {
        mMap.put(p.getUniqueId(), p);
        return super.add(p);
    }

    /**
     * @return the required property from this collection.
     */
    public Property get(final int id) {
        return mMap.get(id);
    }

    /**
     * Passed a parent ViewGroup, build the property editors for all properties
     * inside the parent.
     */
    public void buildView(final @NonNull LayoutInflater inflater, final @NonNull ViewGroup parent) {
        // Sort the properties based on their group weight, group name, weight and name.
        Collections.sort(this, new PropertyComparator(BookCatalogueApp.getAppContext()));
        // Record last group used, so we know when to output a header.
        PropertyGroup lastGroup = null;
        for (Property property : this) {
            // new header ?
            PropertyGroup currGroup = property.getGroup();
            if (currGroup != lastGroup) {
                // Add a new header
                TextView header = (TextView) inflater.inflate(R.layout.row_property_group_heading, null);
                header.setText(currGroup.getNameId());
                parent.addView(header);
            }

            // put the property editor
            View pv = property.getView(inflater);
            parent.addView(pv);
            lastGroup = currGroup;
        }
    }

    /**
     * Validate all properties.
     *
     * @throws ValidationException on error
     */
    public void validate() throws ValidationException {
        for (Property p : this) {
            p.validate();
        }
    }

    /**
     * Class to compare two properties for the purpose of sorting.
     *
     * @author Philip Warner
     */
    private static class PropertyComparator implements Comparator<Property> {

        @NonNull
        private Context mContext;

        PropertyComparator(final @NonNull Context context) {
            mContext = context;
        }

        @Override
        public int compare(final @NonNull Property lhs, final @NonNull Property rhs) {
            // First compare their groups
            int gCmp = lhs.getGroup().compareTo(rhs.getGroup());
            if (gCmp != 0) {
                return gCmp;
            }

            // Same group, compare weights
            if (lhs.getWeight() < rhs.getWeight()) {
                return -1;
            } else if (lhs.getWeight() > rhs.getWeight()) {
                return 1;
            }

            // Same weights, compare names
            if (lhs.getNameResourceId() != rhs.getNameResourceId()) {
                return mContext.getString(lhs.getNameResourceId())
                        .compareTo(mContext.getString(rhs.getNameResourceId()));
            } else {
                return 0;
            }
        }
    }
}
