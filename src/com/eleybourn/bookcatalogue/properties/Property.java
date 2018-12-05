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
import android.view.LayoutInflater;
import android.view.View;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for generic in-memory properties which can have an optional default set at construction time.
 *
 * NOTE ABOUT SERIALIZATION
 *
 * It is very tempting to make these serializable, but fraught with danger. Specifically, these
 * objects contain resource IDs and resource IDs can change across compiling.
 * This means that any serialized version would only be useful for in-process data passing. But this
 * can be accomplished by custom serialization in the referencing object much more easily.
 *
 * @param <T> type of underlying Value
 *
 * @author Philip Warner
 */
public abstract class Property<T> {
    /**
     * Counter used to generate unique View IDs. Needed to prevent some fields being overwritten
     * when the screen is rotated (if they all have the same ID).
     */
    @NonNull
    private static AtomicInteger mViewIdCounter = new AtomicInteger();

    /**
     * Counter used to generate values for the {@link #mUniqueId} field.
     */
    @NonNull
    private static AtomicInteger mUniqueIdCounter = new AtomicInteger();
    private final int mUniqueId;

    /** Resource ID for displayed name of this property */
    @StringRes
    private final int mNameResourceId;
    /** Underlying value */
    @Nullable
    T mValue = null;
    /** PropertyGroup in which this property should reside. Display-purposes only */
    @NonNull
    private PropertyGroup mGroup;
    /** Property weight (for sorting). Most will remain set at 0. */
    private int mWeight = 0;
    /** Hint associated with this property. Subclasses need to use, where appropriate */
    @StringRes
    private int mHint = 0;
    /** Default value, for case when not in preferences, or no preferences given */
    @NonNull
    private T mDefaultValue;

    /**
     * @param nameResourceId Resource ID for name of this property
     * @param group          PropertyGroup in which this property belongs
     * @param defaultValue   value to set as the default, used when the actual value is null.
     */
    public Property(final @StringRes int nameResourceId,
                    final @NonNull PropertyGroup group,
                    final @NonNull T defaultValue) {
        mUniqueId = mUniqueIdCounter.incrementAndGet();
        mGroup = group;
        mNameResourceId = nameResourceId;
        mDefaultValue = defaultValue;
    }

    /** Increment and return the view counter */
    static int nextViewId() {
        return mViewIdCounter.incrementAndGet();
    }

    @NonNull
    protected T getDefaultValue() {
        return mDefaultValue;
    }

    /**
     * Set the DefaultValue
     */
    @NonNull
    public Property<T> setDefaultValue(final @NonNull T value) {
        mDefaultValue = value;
        return this;
    }

    /** Utility to check if the passed value == the default value */
    boolean isDefault(final @Nullable T value) {
        return value != null && value.equals(mDefaultValue);
    }

    /** Utility to check if the current value == the default value */
    boolean isDefault() {
        return mValue != null && mValue.equals(mDefaultValue);
    }

    int getWeight() {
        return mWeight;
    }

    @NonNull
    public Property<T> setWeight(final int weight) {
        mWeight = weight;
        return this;
    }

    public int getUniqueId() {
        return mUniqueId;
    }

    @NonNull
    public PropertyGroup getGroup() {
        return mGroup;
    }

    @NonNull
    public Property<T> setGroup(final @NonNull PropertyGroup group) {
        mGroup = group;
        return this;
    }

    @StringRes
    protected int getNameResourceId() {
        return mNameResourceId;
    }

    boolean hasHint() {
        return mHint != 0;
    }

    @StringRes
    public int getHint() {
        return mHint;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Property<T> setHint(final @StringRes int hint) {
        mHint = hint;
        return this;
    }

    /** Default validation method. Override to provide validation. */
    @CallSuper
    public void validate() throws ValidationException {
        //do nothing
    }

    @Nullable
    public String toString() {
        return (mValue == null ? null : mValue.toString());
    }

    @Nullable
    public T getValue() {
        return mValue;
    }

    /** Accessor for underlying value */
    @NonNull
    public Property<T> setValue(final @Nullable T value) {
        this.mValue = value;
        return this;
    }

    /** Children must implement getView to return an editor for this object */
    @NonNull
    public abstract View getView(final @NonNull LayoutInflater inflater);

    /** Exception used by validation code. */
    public static class ValidationException extends IllegalStateException {
        private static final long serialVersionUID = -1086124703257379812L;

        ValidationException(final @NonNull String message) {
            super(message);
        }
    }
}
