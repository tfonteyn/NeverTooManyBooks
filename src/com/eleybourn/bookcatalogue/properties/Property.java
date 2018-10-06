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
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;

import com.eleybourn.bookcatalogue.BookCatalogueApp;

/**
 * Base class for generic properties.
 *
 * @author Philip Warner
 */
public abstract class Property {
    /**
     * Counter used to generate unique View IDs. Needed to prevent some fields being overwritten when
     * screen is rotated (if they all have the same ID).
     *
     * ENHANCE: allow topological sort of parameters to allow arbitrary grouping and sorting.
     *
     * NOTE ABOUT SERIALIZATION
     *
     * It is very tempting to make these serializable, but fraught with danger. Specifically, these
     * objects contain resource IDs and, as far as I can tell, resource IDs can change across versions.
     * This means that any serialized version would only be useful for in-process data passing. But this
     * can be accomplished by custom serialization in the referencing object much more easily.
     */
    private static Integer mViewIdCounter = 0;

    /** Unique 'name' of this property. */
    @NonNull
    private final String mUniqueId;
    /** PropertyGroup in which this property should reside. Display-purposes only */
    @NonNull
    private transient PropertyGroup mGroup;
    /** Resource ID for name of this property */
    @StringRes
    private final transient int mNameResourceId;

    /** Property weight (for sorting). Most will remain set at 0. */
    private int mWeight = 0;
    /** Hint associated with this property. Subclasses need to use, where appropriate */
    private int mHint = 0;

    /**
     * Constructor
     *
     * @param uniqueId       Unique name for this property (ideally, unique for entire app)
     * @param group          PropertyGroup in which this property belongs
     * @param nameResourceId Resource ID for name of this property
     */
    public Property(@NonNull final String uniqueId, @NonNull final PropertyGroup group, @StringRes final int nameResourceId) {
        mUniqueId = uniqueId;
        mGroup = group;
        mNameResourceId = nameResourceId;
    }

    /** Increment and return the view counter */
    static int nextViewId() {
        return ++mViewIdCounter;
    }

    /**
     * @return the string name of this property
     */
    @NonNull
    public String getName() {
        return BookCatalogueApp.getResourceString(mNameResourceId);
    }

    public int getWeight() {
        return mWeight;
    }

    @NonNull
    public Property setWeight(final int weight) {
        mWeight = weight;
        return this;
    }

    @NonNull
    public String getUniqueName() {
        return mUniqueId;
    }

    @NonNull
    public PropertyGroup getGroup() {
        return mGroup;
    }

    @NonNull
    public Property setGroup(@NonNull final PropertyGroup group) {
        mGroup = group;
        return this;
    }

    @StringRes
    int getNameResourceId() {
        return mNameResourceId;
    }

    boolean hasHint() {
        return mHint != 0;
    }

    public int getHint() {
        return mHint;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Property setHint(final int hint) {
        mHint = hint;
        return this;
    }

    /** Default validation method. Override to provide validation. */
    public void validate() {
    }

    /** Children must implement set(Property) */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public abstract Property set(@NonNull final Property p);

    /** Children must implement getView to return an editor for this object */
    @NonNull
    public abstract View getView(@NonNull final LayoutInflater inflater);

    /**
     * Interface used to help setting one property based on another property value.
     * eg. there are multiple 'Boolean' properties, and *maybe* one day there will be
     * a use for type conversions.
     *
     * @author Philip Warner
     */
    public interface BooleanValue {
        Boolean get();
    }

    /**
     * Interface used to help setting one property based on another property value.
     * eg. there are multiple 'Boolean' properties, and *maybe* one day there will be
     * a use for type conversions.
     *
     * @author Philip Warner
     */
    protected interface StringValue {
        String get();
    }

    /**
     * Interface used to help setting one property based on another property value.
     * eg. there are multiple 'Boolean' properties, and *maybe* one day there will be
     * a use for type conversions.
     *
     * @author Philip Warner
     */
    public interface IntegerValue {
        Integer get();
    }

    /**  Exception used by validation code. */
    public static class ValidationException extends IllegalStateException {
        private static final long serialVersionUID = -1086124703257379812L;

        ValidationException(@NonNull final String message) {
            super(message);
        }
    }
}
