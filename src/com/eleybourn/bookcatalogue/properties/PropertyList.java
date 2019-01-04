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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.properties.Property.ValidationException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;

/**
 * Class to manage a set of properties.
 *
 * @author Philip Warner
 */
public class PropertyList {
    private final Map<Integer, Property> mMap = new LinkedHashMap<>();

    /** Add a property to this collection */
    public Property add(@NonNull final Property p) {
        return mMap.put(p.getUniqueId(), p);
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
    public void buildView(@NonNull final LayoutInflater inflater, @NonNull final ViewGroup parent) {
        // Sort the properties based on their group weight, group name, weight and name.
        List<Property> tmpList = new ArrayList<>(mMap.values());
        Collections.sort(tmpList, new PropertyComparator());

        // Record last group used, so we know when to output a header.
        PropertyGroup lastGroup = null;
        for (Property property : tmpList) {
            // new header ?
            PropertyGroup currGroup = property.getGroup();
            if (currGroup != lastGroup) {
                // Add a new header
                TextView header = (TextView) inflater.inflate(R.layout.row_property_group_heading, null);
                header.setText(currGroup.getNameResourceId());
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
        for (Property p : mMap.values()) {
            p.validate();
        }
    }

    /**
     * Class to compare two properties for the purpose of sorting.
     *
     * @author Philip Warner
     */
    private static class PropertyComparator implements Comparator<Property>, Serializable {

        private static final long serialVersionUID = 2686026228883800535L;

        @Override
        public int compare(@NonNull final Property o1, @NonNull final Property o2) {
            // First compare their groups
            int gCmp = o1.getGroup().compareTo(o2.getGroup());
            if (gCmp != 0) {
                return gCmp;
            }

            // Same group, compare weights
            if (o1.getWeight() < o2.getWeight()) {
                return -1;
            } else if (o1.getWeight() > o2.getWeight()) {
                return 1;
            }

            Context context = BookCatalogueApp.getAppContext();

            // Same weights, compare names
            if (o1.getNameResourceId() != o2.getNameResourceId()) {
                return context.getString(o1.getNameResourceId())
                        .compareTo(context.getString(o2.getNameResourceId()));
            } else {
                return 0;
            }
        }
    }
}
