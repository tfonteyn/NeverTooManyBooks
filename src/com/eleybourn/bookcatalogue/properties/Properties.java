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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.properties.Property.ValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Class to manage a set of properties.
 *
 * @author Philip Warner
 */
public class Properties implements Iterable<Property> {
    private final List<Property> mList = new ArrayList<>();
    private final Map<String, Property> mHash = new HashMap<>();

    /** Sort the properties based on their group weight, group name, weight and name. */
    private void sort() {
        Collections.sort(mList, new PropertyComparator());
    }

    /** Add a property to this collection */
    @NonNull
    public Properties add(@NonNull Property p) {
        mList.add(p);
        mHash.put(p.getUniqueName(), p);
        return this;
    }

    /**
     * @return the named property from this collection.
     */
    public Property get(final @NonNull String name) {
        return mHash.get(name);
    }

    /**
     * Passed a parent ViewGroup, build the property editors for all properties
     * inside the parent.
     */
    public void buildView(final @NonNull LayoutInflater inflater, final @NonNull ViewGroup parent) {
        // Sort them correctly
        sort();
        // Record last group used, so we know when to output a header.
        PropertyGroup lastGroup = null;
        for (Property property : mList) {
            // new header ?
            PropertyGroup currGroup = property.getGroup();
            if (currGroup != lastGroup) {
                // Add a new header
                TextView header = (TextView) inflater.inflate(R.layout.row_property_group_heading, null);
                header.setText(currGroup.getNameId());
                parent.addView(header);
            }

            // add the property editor
            View pv = property.getView(inflater);
            parent.addView(pv);
            lastGroup = currGroup;
        }
    }

    @NonNull
    @Override
    public Iterator<Property> iterator() {
        return mList.iterator();
    }

    /**
     * Validate all properties.
     *
     * @throws ValidationException on error
     */
    public void validate() throws ValidationException {
        for (Property p : mList) {
            p.validate();
        }
    }
}
