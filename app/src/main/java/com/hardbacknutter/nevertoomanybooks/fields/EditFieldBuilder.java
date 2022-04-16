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
import com.hardbacknutter.nevertoomanybooks.fields.validators.FieldValidator;

public class EditFieldBuilder<T, V extends View>
        extends FieldBuilder<T, V> {

    @Nullable
    private FragmentId mFragmentId;
    @IdRes
    private int mTextInputLayoutId;
    @IdRes
    private int mErrorViewId;
    @Nullable
    private FieldValidator<T, V> mValidator;

    private boolean mHasCustomClearTextEndIcon;

    public EditFieldBuilder(@IdRes final int id,
                            @NonNull final String key,
                            @NonNull final FieldViewAccessor<T, V> accessor) {
        super(id, key, accessor);
    }

    @NonNull
    public EditFieldBuilder<T, V> setRelatedFields(@NonNull @IdRes final Integer... relatedFields) {
        super.setRelatedFields(relatedFields);
        return this;
    }

    @NonNull
    public EditFieldBuilder<T, V> setEntityKey(@NonNull final String entityKey) {
        super.setEntityKey(entityKey);
        return this;
    }

    @NonNull
    public EditFieldBuilder<T, V> setFragmentId(@NonNull final FragmentId fragmentId) {
        this.mFragmentId = fragmentId;
        return this;
    }

    /**
     * Set the id for the surrounding TextInputLayout (if this field has one).
     * <ul>
     *     <li>This <strong>must</strong> be called to make the end-icon clear_text work.</li>
     *     <li>The id will override any id set by {@link #setErrorViewId}.</li>
     *     <li>The id is added to {@link #setRelatedFields} so it is used for visibility.</li>
     * </ul>
     *
     * @param textInputLayoutId view id
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public EditFieldBuilder<T, V> setTextInputLayout(@IdRes final int textInputLayoutId) {
        mTextInputLayoutId = textInputLayoutId;
        return this;
    }

    /**
     * Set the id for the error view. This can be set independently from calling
     * {@link #setFieldValidator} for cross-validation / error reporting.
     * <ul>
     *     <li>This call will override the value set by {@link #setTextInputLayout}.</li>
     *     <li>The id is added to {@link #setRelatedFields} so it is used for visibility.</li>
     * </ul>
     *
     * @param errorViewId view id
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public EditFieldBuilder<T, V> setErrorViewId(@IdRes final int errorViewId) {
        mErrorViewId = errorViewId;
        return this;
    }

    /**
     * Set the validator for this field. This can be set independently from calling
     * {@link #setErrorViewId} for cross-validation / error reporting.
     *
     * @param validator to use
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public EditFieldBuilder<T, V> setFieldValidator(@NonNull final FieldValidator<T, V> validator) {
        mValidator = validator;
        return this;
    }

    @NonNull
    public EditFieldBuilder<T, V> setUseCustomClearTextEndIcon(final boolean use) {
        mHasCustomClearTextEndIcon = use;
        return this;
    }

    @NonNull
    public EditField<T, V> build() {
        Objects.requireNonNull(mAccessor, "Missing FieldViewAccessor");
        SanityCheck.requireValue(mKey, "mKey");

        final FragmentId fragmentId = mFragmentId != null ? mFragmentId : FragmentId.Main;
        final String entityKey = mEntityKey != null ? mEntityKey : mKey;

        final EditFieldImpl<T, V> field = new EditFieldImpl<>(fragmentId, mId, mKey,
                                                              entityKey, mAccessor);
        if (mRelatedFields != null) {
            field.addRelatedFields(mRelatedFields);
        }
        if (mTextInputLayoutId != 0) {
            field.setTextInputLayout(mTextInputLayoutId);
        }
        if (mErrorViewId != 0) {
            field.setErrorViewId(mErrorViewId);
        }
        if (mValidator != null) {
            field.setFieldValidator(mValidator);
        }
        field.setHasCustomClearTextEndIcon(mHasCustomClearTextEndIcon);
        return field;
    }
}
