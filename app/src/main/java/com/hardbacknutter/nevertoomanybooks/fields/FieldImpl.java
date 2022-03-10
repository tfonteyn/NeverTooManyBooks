/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.BooleanIndicatorAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.FieldViewAccessor;

public class FieldImpl<T, V extends View>
        implements Field<T, V> {

    @NonNull
    protected final FragmentId mFragmentId;
    /** Field ID. */
    @IdRes
    protected final int mId;
    /**
     * Key used to access a {@link DataManager} or {@code Bundle}.
     * <ul>
     *      <li>key is set<br>
     *          Data is fetched from the {@link DataManager} (or Bundle),
     *          and populated on the screen.
     *          Extraction depends on the formatter in use.</li>
     *      <li>key is not set (i.e. "")<br>
     *          field is defined, but data handling must be done manually.</li>
     * </ul>
     * <p>
     * See {@link #isAutoPopulated()}.
     */
    @NonNull
    protected final String mKey;
    /**
     * The preference key (field-name) to check if this Field is used or not.
     * i.e. the key to be used for {@link DBKey#isUsed(SharedPreferences, String)}.
     */
    @NonNull
    protected final String mIsUsedKey;

    /** Fields that need to follow visibility. */
    protected final Collection<Integer> mRelatedFields = new HashSet<>();

    /**
     * Accessor to use. Encapsulates the formatter.
     */
    @NonNull
    final FieldViewAccessor<T, V> mFieldViewAccessor;

    /**
     * Constructor.
     *
     * @param id        for this field.
     * @param key       Key used to access a {@link DataManager}
     *                  Set to {@code ""} to suppress all access.
     * @param entityKey The preference key to check if this Field is used or not
     * @param accessor  to use
     */
    FieldImpl(@NonNull final FragmentId fragmentId,
              @IdRes final int id,
              @NonNull final String key,
              @NonNull final String entityKey,
              @NonNull final FieldViewAccessor<T, V> accessor) {
        mFragmentId = fragmentId;
        mId = id;
        mKey = key;
        mIsUsedKey = entityKey;

        mFieldViewAccessor = accessor;
        mFieldViewAccessor.setField(this);
    }

    /**
     * set the field ID's which should follow visibility with this Field.
     *
     * <p>
     * <strong>Dev. note:</strong> this could be done using
     * {@link androidx.constraintlayout.widget.Group}
     * but that means creating a group for EACH field. That would be overkill.
     *
     * @param relatedFields labels etc
     */
    @CallSuper
    void addRelatedFields(@NonNull @IdRes final Integer... relatedFields) {
        mRelatedFields.addAll(Arrays.asList(relatedFields));
    }

    @Override
    @Nullable
    public V getView() {
        return mFieldViewAccessor.getView();
    }

    @Override
    @NonNull
    public V requireView() {
        return mFieldViewAccessor.requireView();
    }

    @CallSuper
    @Override
    public void setParentView(@NonNull final SharedPreferences global,
                              @NonNull final View parent) {
        mFieldViewAccessor.setView(parent.findViewById(mId));
        if (!isUsed(global)) {
            setVisibility(parent, View.GONE);
        }
    }

    @CallSuper
    @Override
    public void setValue(@Nullable final T value) {
        mFieldViewAccessor.setValue(value);
    }

    @CallSuper
    @Override
    public void setInitialValue(@NonNull final DataManager source) {
        mFieldViewAccessor.setInitialValue(source);
    }

    @CallSuper
    @Override
    @SuppressWarnings("StatementWithEmptyBody")
    public void setVisibility(@NonNull final SharedPreferences global,
                              @NonNull final View parent,
                              final boolean hideEmptyFields,
                              final boolean keepHiddenFieldsHidden) {

        final View view = mFieldViewAccessor.requireView();

        if ((view instanceof ImageView)
            || mFieldViewAccessor instanceof BooleanIndicatorAccessor
            || (view.getVisibility() == View.GONE && keepHiddenFieldsHidden)) {
            // An ImageView always keeps its current visibility.
            // A BooleanIndicatorAccessor handles it automatically when the value is set.
            // When 'keepHiddenFieldsHidden' is set, hidden fields stay hidden.

        } else if (hideEmptyFields && mFieldViewAccessor.isEmpty()) {
            // When 'hideEmptyFields' is set, empty fields are hidden.
            view.setVisibility(View.GONE);

        } else if (isUsed(global)) {
            // Anything else (in use) should be visible if it's not yet.
            view.setVisibility(View.VISIBLE);
        }

        setRelatedFieldsVisibility(parent, view.getVisibility());
    }

    @CallSuper
    @Override
    public void setVisibility(@NonNull final View parent,
                              final int visibility) {
        final View view = mFieldViewAccessor.requireView();
        view.setVisibility(visibility);
        setRelatedFieldsVisibility(parent, visibility);
    }

    /**
     * Set the visibility for the related fields.
     *
     * @param parent     parent view for all related fields.
     * @param visibility to use
     */
    protected void setRelatedFieldsVisibility(@NonNull final View parent,
                                              final int visibility) {
        for (final int fieldId : mRelatedFields) {
            final View view = parent.findViewById(fieldId);
            if (view != null) {
                view.setVisibility(visibility);
            }
        }
    }

    @Override
    @NonNull
    public FragmentId getFragmentId() {
        return mFragmentId;
    }

    @Override
    @IdRes
    public int getId() {
        return mId;
    }

    @Override
    @NonNull
    public String getKey() {
        return mKey;
    }

    @Override
    public boolean isAutoPopulated() {
        return !mKey.isEmpty();
    }

    @Override
    public boolean isUsed(@NonNull final SharedPreferences global) {
        return DBKey.isUsed(global, mIsUsedKey);
    }

    @Override
    @NonNull
    public String toString() {
        return "FieldImpl{"
               + "mFragmentId=" + mFragmentId
               + ", mId=" + mId
               + ", mKey='" + mKey + '\''
               + ", mIsUsedKey='" + mIsUsedKey + '\''
               + ", mRelatedFields=" + mRelatedFields
               + ", mFieldViewAccessor=" + mFieldViewAccessor
               + '}';
    }
}
