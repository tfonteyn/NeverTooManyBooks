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
    public Properties add(Property p) {
        mList.add(p);
        mHash.put(p.getUniqueName(), p);
        return this;
    }

    /**
     * @return the named property from this collection.
     */
    public Property get(@NonNull final String name) {
        return mHash.get(name);
    }

    /**
     * Passed a parent ViewGroup, build the property editors for all properties
     * inside the parent.
     */
    public void buildView(@NonNull final LayoutInflater inflater, @NonNull final ViewGroup parent) {
        // Sort them correctly
        sort();
        // Record last group used, so we know when to output a header.
        PropertyGroup lastGroup = null;
        for (Property p : mList) {
            // new header ?
            PropertyGroup currGroup = p.getGroup();
            if (currGroup != lastGroup) {
                // Add a new header
                TextView v = (TextView) inflater.inflate(R.layout.property_group, null);
                v.setText(currGroup.getNameId());
                parent.addView(v);
            }

            // add the property editor
            View pv = p.getView(inflater);
            parent.addView(pv);
            lastGroup = currGroup;
        }
    }

    @NonNull
    @Override
    public Iterator<Property> iterator() {
        return mList.iterator();
    }

    /** Call the validate() method on all properties. Errors will be thrown. */
    public void validate() throws ValidationException {
        for (Property p : mList) {
            p.validate();
        }
    }
}
