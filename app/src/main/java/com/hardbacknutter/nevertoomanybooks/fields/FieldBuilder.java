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

import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.FieldViewAccessor;

public class FieldBuilder<T, V extends View> {

    @IdRes
    protected final int mId;
    @NonNull
    protected final String mKey;
    @NonNull
    protected FieldViewAccessor<T, V> mAccessor;

    @Nullable
    protected String mEntityKey;
    @Nullable
    protected Integer[] mRelatedFields;

    public FieldBuilder(@IdRes final int id,
                        @NonNull final String key,
                        @NonNull final FieldViewAccessor<T, V> accessor) {
        mId = id;
        mKey = key;
        mAccessor = accessor;
    }

    /**
     * set the field ID's which should follow visibility with this Field.
     * <p>
     * <strong>Dev. note:</strong> this could be done using
     * {@link androidx.constraintlayout.widget.Group}
     * but that means creating a group for EACH field. That would be overkill.
     *
     * @param relatedFields labels etc
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public FieldBuilder<T, V> setRelatedFields(@NonNull @IdRes final Integer... relatedFields) {
        mRelatedFields = relatedFields;
        return this;
    }

    @NonNull
    public FieldBuilder<T, V> setEntityKey(@NonNull final String entityKey) {
        mEntityKey = entityKey;
        return this;
    }

    @NonNull
    public Field<T, V> build() {
        Objects.requireNonNull(mAccessor, "Missing FieldViewAccessor");
        SanityCheck.requireValue(mKey, "mKey");

        final String entityKey = mEntityKey != null ? mEntityKey : mKey;

        final FieldImpl<T, V> field = new FieldImpl<>(FragmentId.Main, mId, mKey,
                                                      entityKey, mAccessor);
        if (mRelatedFields != null) {
            field.addRelatedFields(mRelatedFields);
        }
        return field;
    }
}
