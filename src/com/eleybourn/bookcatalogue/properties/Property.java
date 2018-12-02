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

import java.util.Objects;
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

    /** Unique 'name' of this property. In-memory use only (as a key into a Map), not persisted. */
    @NonNull
    private final String mUniqueId;
    /** Resource ID for displayed name of this property */
    @StringRes
    private final transient int mNameResourceId;
    /** Underlying value */
    @Nullable
    protected T mValue = null;
    /** PropertyGroup in which this property should reside. Display-purposes only */
    @NonNull
    private transient PropertyGroup mGroup;
    /** Property weight (for sorting). Most will remain set at 0. */
    private int mWeight = 0;
    /** Hint associated with this property. Subclasses need to use, where appropriate */
    @StringRes
    private int mHint = 0;
    /** Default value, for case when not in preferences, or no preferences given */
    @Nullable
    private T mDefaultValue = null;

    /** Key in preferences for optional persistence */
    @Nullable
    private String mPreferenceKey = null;

    /**
     * Constructor, both Value and DefaultValue will be 'null'
     *
     * @param uniqueId       Unique name for this property (ideally, unique for entire app)
     * @param group          PropertyGroup in which this property belongs
     * @param nameResourceId Resource ID for name of this property
     */
    public Property(final @NonNull String uniqueId,
                    final @NonNull PropertyGroup group,
                    final @StringRes int nameResourceId) {
        mUniqueId = uniqueId;
        mGroup = group;
        mNameResourceId = nameResourceId;
    }

    /**
     * Constructor, both Value and DefaultValue will be set to the parameter 'defaultValue'
     *
     * @param uniqueId       Unique name for this property (ideally, unique for entire app)
     * @param group          PropertyGroup in which this property belongs
     * @param nameResourceId Resource ID for name of this property
     * @param defaultValue   value to set as both the default, and as the current actual value.
     */
    public Property(final @NonNull String uniqueId,
                    final @NonNull PropertyGroup group,
                    final @StringRes int nameResourceId,
                    final @Nullable T defaultValue) {
        mUniqueId = uniqueId;
        mGroup = group;
        mNameResourceId = nameResourceId;
        mDefaultValue = defaultValue;
        mValue = defaultValue;
    }

    /** Increment and return the view counter */
    static int nextViewId() {
        return mViewIdCounter.incrementAndGet();
    }

    @Nullable
    protected T getDefaultValue() {
        return mDefaultValue;
    }

    /**
     * Set the DefaultValue *and* the Value
     *
     * The assumption is that the default will be set at creation time, before we have a valid value
     * for this property.
     */
    @NonNull
    public Property<T> setDefaultValue(final @Nullable T value) {
        mDefaultValue = value;
        mValue = value;
        return this;
    }

    /** Utility to check if the passed value == the default value */
    boolean isDefault(final @Nullable T value) {
        return (value == null && mDefaultValue == null)
                || (value != null && value.equals(mDefaultValue));
    }

    /** Utility to check if the current value == the default value */
    boolean isDefault() {
        return (mValue == null && mDefaultValue == null)
                || (mValue != null && mValue.equals(mDefaultValue));
    }

    @NonNull
    protected String getPreferenceKey() {
        return Objects.requireNonNull(mPreferenceKey);
    }

    @NonNull
    public Property<T> setPreferenceKey(final @NonNull String key) {
        mPreferenceKey = key;
        return this;
    }

    public int getWeight() {
        return mWeight;
    }

    @NonNull
    public Property<T> setWeight(final int weight) {
        mWeight = weight;
        return this;
    }

    /** check if there is a preference key for persisting the value */
    public boolean hasPreferenceKey() {
        return (mPreferenceKey != null && !mPreferenceKey.isEmpty());
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

    /** Accessor for underlying (or global) value */
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
