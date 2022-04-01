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

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

public interface Field<T, V extends View> {

    /**
     * Get the {@link FragmentId} in which this Field is handled.
     *
     * @return id
     */
    @NonNull
    FragmentId getFragmentId();

    @IdRes
    int getId();

    /**
     * Set the View for the field.
     * <p>
     * Unused fields (as configured in the user preferences) will be hidden after this step.
     *
     * @param global Global preferences
     * @param parent of the field View
     */
    void setParentView(@NonNull SharedPreferences global,
                       @NonNull View parent);

    @Nullable
    V getView();

    @NonNull
    V requireView();

    /**
     * <strong>Conditionally</strong> set the visibility for the field and its related fields.
     *
     * @param parent                 parent view; used to find the <strong>related fields</strong>
     * @param hideEmptyFields        hide empty field:
     *                               Use {@code true} when displaying;
     *                               and {@code false} when editing.
     * @param keepHiddenFieldsHidden keep a field hidden if it's already hidden
     *                               (even when it has content)
     */
    void setVisibility(@NonNull SharedPreferences global,
                       @NonNull View parent,
                       boolean hideEmptyFields,
                       boolean keepHiddenFieldsHidden);

    /**
     * Set the value directly. (e.g. upon another field changing... etc...)
     *
     * @param value to set
     */
    void setValue(@Nullable T value);


    /**
     * Load the field from the passed {@link DataManager}.
     * <p>
     * This is used for the <strong>INITIAL LOAD</strong>, i.e. the value as stored
     * in the database.
     *
     * @param source DataManager to load the Field objects from
     */
    void setInitialValue(@NonNull DataManager source);

    @NonNull
    String getKey();

    /**
     * Is the field in use; i.e. is it enabled in the user-preferences.
     *
     * @param global Global preferences
     *
     * @return {@code true} if the field *can* be visible
     */
    boolean isUsed(@NonNull SharedPreferences global);

    /**
     * Check if this field can be automatically populated.
     *
     * @return {@code true} if it can
     */
    boolean isAutoPopulated();
}
