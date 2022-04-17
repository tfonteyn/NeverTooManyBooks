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

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.FieldViewAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.validators.FieldValidator;
import com.hardbacknutter.nevertoomanybooks.widgets.endicon.ExtEndIconDelegate;

/**
 * Field definition contains all information and methods necessary
 * to manage display and extraction of data in a view.
 * <ul>Features provides are:
 *      <li>Handling of visibility via preferences / 'mIsUsedKey' property of a field.</li>
 *      <li>Understanding of kinds of views (setting a Checkbox (Checkable) value to 'true'
 *          will work as expected as will setting the value of an ExposedDropDownMenu).
 *          As new view types are added, it will be necessary to add new {@link FieldViewAccessor}
 *          implementations.
 *          In some specific circumstances, an accessor can be defined manually.</li>
 *      <li> Custom data accessors and formatters to provide application-specific data rules.</li>
 *      <li> simplified extraction of data.</li>
 * </ul>
 * <p>
 * Accessors
 * <p>
 * A {@link FieldViewAccessor} handles interactions between the value and the View
 * (with an optional {@link FieldFormatter}).
 * <p>
 * Formatters
 * <p>
 * A Formatter can be set on {@link android.widget.TextView}
 * and any class extending {@link EditTextAccessor}
 * i.e. for TextView and EditText elements.
 * Formatters should implement {@link FieldFormatter#format(Context, Object)} where the Object
 * is transformed to a String - DO NOT CHANGE class variables while doing this.
 * In contrast {@link FieldFormatter#apply} CAN change class variables
 * but should leave the real formatter to the format method.
 * <p>
 * This way, other code can access {@link FieldFormatter#format(Context, Object)}
 * without side-effects.
 * <p>
 * <ul>Data flows to and from a view as follows:
 *      <li>IN  (no formatter ):<br>
 *          {@link FieldViewAccessor#setInitialValue(DataManager)} ->
 *          {@link FieldViewAccessor#setValue(Object)} ->
 *          populates the View.</li>
 *      <li>IN  (with formatter):<br>
 *          {@link FieldViewAccessor#setInitialValue(DataManager)} ->
 *          {@link FieldViewAccessor#setValue(Object)} ->
 *          {@link FieldFormatter#apply} ->
 *          populates the View.</li>
 *       <li>OUT (no formatter ):
 *          View ->
 *          {@link FieldViewAccessor#getValue()} ->
 *          {@link FieldViewAccessor#getValue(DataManager)}</li>
 *      <li>OUT (with formatter):
 *          View ->
 *          {@link EditFieldFormatter#extract(Context, String)} ->
 *          {@link FieldViewAccessor#getValue()} ->
 *          {@link FieldViewAccessor#getValue(DataManager)}</li>
 * </ul>
 *
 * @param <T> type of Field value.
 * @param <V> type of View for this field
 */
class EditFieldImpl<T, V extends View>
        extends FieldImpl<T, V>
        implements EditField<T, V> {

    /** Log tag. */
    private static final String TAG = "EditFieldImpl";

    @Nullable
    private FieldValidator<T, V> mValidator;

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private WeakReference<EditField.AfterFieldChangeListener> mAfterFieldChangeListener;

    @SuppressWarnings("FieldNotUsedInToString")
    @IdRes
    private int mErrorViewId;

    @SuppressWarnings("FieldNotUsedInToString")
    @IdRes
    private int mTextInputLayoutId;

    private boolean mHasCustomClearTextEndIcon;
    @SuppressWarnings({"FieldCanBeLocal", "FieldNotUsedInToString"})
    private ExtEndIconDelegate mExtEndIconDelegate;

    /**
     * Constructor.
     *
     * @param id        for this field.
     * @param key       Key used to access a {@link DataManager}
     *                  Set to {@code ""} to suppress all access.
     * @param entityKey The preference key to check if this Field is used or not
     * @param accessor  to use
     */
    EditFieldImpl(@NonNull final FragmentId fragmentId,
                  @IdRes final int id,
                  @NonNull final String key,
                  @NonNull final String entityKey,
                  @NonNull final FieldViewAccessor<T, V> accessor) {
        super(fragmentId, id, key, entityKey, accessor);
    }

    /**
     * Set the id for the surrounding TextInputLayout (if this field has one).
     * <ul>
     *     <li>This <strong>must</strong> be called to make the end-icon clear_text work.</li>
     *     <li>The id will override any id set by {@link #setErrorViewId}.</li>
     *     <li>The id is added to {@link #addRelatedFields} so it is used for visibility.</li>
     * </ul>
     *
     * @param viewId view id
     */
    void setTextInputLayout(@IdRes final int viewId) {
        mTextInputLayoutId = viewId;
        mErrorViewId = viewId;
        mRelatedFields.add(viewId);
    }

    void setHasCustomClearTextEndIcon(final boolean hasCustomClearTextEndIcon) {
        mHasCustomClearTextEndIcon = hasCustomClearTextEndIcon;
    }

    /**
     * Set the validator for this field. This can be set independently from calling
     * {@link #setErrorViewId} for cross-validation / error reporting.
     *
     * @param validator to use
     */
    void setFieldValidator(@NonNull final FieldValidator<T, V> validator) {
        mValidator = validator;
    }

    /**
     * Set the id for the error view. This can be set independently from calling
     * {@link #setFieldValidator} for cross-validation / error reporting.
     * <ul>
     *     <li>This call will override the value set by {@link #setTextInputLayout}.</li>
     *     <li>The id is added to {@link #addRelatedFields} so it is used for visibility.</li>
     * </ul>
     *
     * @param viewId view id
     */
    void setErrorViewId(@IdRes final int viewId) {
        mErrorViewId = viewId;
        mRelatedFields.add(viewId);
    }

    @Override
    @CallSuper
    public void setParentView(@NonNull final SharedPreferences global,
                              @NonNull final View parent) {
        super.setParentView(global, parent);
        if (isUsed(global)) {
            if (mErrorViewId != 0) {
                mFieldViewAccessor.setErrorView(parent.findViewById(mErrorViewId));
            }
            if (mTextInputLayoutId != 0) {
                final TextInputLayout til = parent.findViewById(mTextInputLayoutId);
                // sanity/typo check
                Objects.requireNonNull(til, "missing TIL for field key=" + mKey);

                if (til.getEndIconMode() == TextInputLayout.END_ICON_CLEAR_TEXT) {
                    til.setEndIconOnClickListener(v -> {
                        mFieldViewAccessor.setValue(null);
                        onChanged();
                    });

                } else if (til.getEndIconMode() == TextInputLayout.END_ICON_CUSTOM
                           && mHasCustomClearTextEndIcon) {

                    mExtEndIconDelegate = new ExtEndIconDelegate(til);
                    mExtEndIconDelegate.setEndIconOnClickConsumer(v -> {
                        mFieldViewAccessor.setValue(null);
                        onChanged();
                    });
                    mExtEndIconDelegate.initialize();
                }
            }
        }
    }

    @Override
    public void setAfterFieldChangeListener(
            @Nullable final EditField.AfterFieldChangeListener listener) {
        mAfterFieldChangeListener = listener != null ? new WeakReference<>(listener) : null;
    }

    @Override
    public void setError(@Nullable final String errorText) {
        mFieldViewAccessor.setError(errorText);
    }

    @Override
    public void setErrorIfEmpty(@NonNull final String errorText) {
        mFieldViewAccessor.setErrorIfEmpty(errorText);
    }

    @Override
    public boolean isEmpty() {
        return mFieldViewAccessor.isEmpty();
    }

    @Override
    public void putValue(@NonNull final DataManager target) {
        mFieldViewAccessor.getValue(target);
        //TODO: is there a point calling 'validate' here?
        if (mValidator != null) {
            mValidator.validate(this);
        }
    }

    @Override
    @Nullable
    public T getValue() {
        return mFieldViewAccessor.getValue();
    }

    @Override
    public void setValue(@Nullable final T value) {
        super.setValue(value);
        if (mValidator != null) {
            mValidator.validate(this);
        }
    }

    @Override
    @CallSuper
    public void onChanged() {
        if (mAfterFieldChangeListener != null && mAfterFieldChangeListener.get() != null) {
            mAfterFieldChangeListener.get().onAfterFieldChange(this);

        } else {
            if (BuildConfig.DEBUG /* always */) {
                // mAfterFieldChangeListener == null is perfectly fine.
                // i.e. it will be null during population of fields.
                if (mAfterFieldChangeListener != null) {
                    // The REFERENT being dead is however not fine, so log this in debug.
                    // flw: this message should never be seen!
                    Log.w(TAG, "onChanged|mAfterFieldChangeListener was dead");
                }
            }
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "EditFieldImpl{"
               + super.toString()
               + ", mHasCustomClearTextEndIcon=" + mHasCustomClearTextEndIcon
               + ", mFieldViewAccessor=" + mFieldViewAccessor
               + ", mValidator=" + mValidator
               + '}';
    }
}
