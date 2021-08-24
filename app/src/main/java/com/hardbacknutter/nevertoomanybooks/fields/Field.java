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
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputLayout;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.FieldViewAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.validators.FieldValidator;

/**
 * Field definition contains all information and methods necessary
 * to manage display and extraction of data in a view.
 *
 * @param <T> type of Field value.
 * @param <V> type of View for this field
 */
@SuppressWarnings("FieldNotUsedInToString")
public class Field<T, V extends View> {

    /** Log tag. */
    private static final String TAG = "Field";

    /** Accessor to use. Encapsulates the formatter. */
    @NonNull
    private final FieldViewAccessor<T, V> mFieldViewAccessor;

    /** Field ID. */
    @IdRes
    private final int mId;

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
    private final String mKey;

    /**
     * The preference key (field-name) to check if this Field is used or not.
     * i.e. the key to be used for {@code App.isUsed(mIsUsedKey)}.
     */
    @NonNull
    private final String mIsUsedKey;

    /** Fields that need to follow visibility. */
    private final Collection<Integer> mRelatedFields = new HashSet<>();

    @Nullable
    private FieldValidator<T, V> mValidator;
    @Nullable
    private WeakReference<Fields.AfterChangeListener> mAfterFieldChangeListener;
    @IdRes
    private int mErrorViewId;
    @IdRes
    private int mTextInputLayoutId;

    @IdRes
    private int mResetBtnId;
    @Nullable
    private T mResetValue;
    @Nullable
    private WeakReference<View> mResetBtnViewReference;

    /**
     * Constructor.
     *
     * @param id        for this field.
     * @param accessor  to use
     * @param key       Key used to access a {@link DataManager}
     *                  Set to {@code ""} to suppress all access.
     * @param entityKey The preference key to check if this Field is used or not
     */
    Field(@IdRes final int id,
          @NonNull final FieldViewAccessor<T, V> accessor,
          @NonNull final String key,
          @NonNull final String entityKey) {

        mId = id;
        mKey = key;
        mIsUsedKey = entityKey;

        mFieldViewAccessor = accessor;
        mFieldViewAccessor.setField(this);
    }


    /**
     * set the field ID's which should follow visibility with this Field.
     * <p>
     * Consider calling {@link #setErrorViewId} instead if it's a single related label-field.
     *
     * <p>
     * <strong>Dev. note:</strong> this could be done using
     * {@link androidx.constraintlayout.widget.Group}
     * but that means creating a group for EACH field. That would be overkill.
     *
     * @param relatedFields labels etc
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    public Field<T, V> setRelatedFields(@NonNull @IdRes final Integer... relatedFields) {
        mRelatedFields.addAll(Arrays.asList(relatedFields));
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
     * @param viewId view id
     *
     * @return {@code this} (for chaining)
     */
    public Field<T, V> setTextInputLayout(@IdRes final int viewId) {
        mTextInputLayoutId = viewId;
        mErrorViewId = viewId;
        mRelatedFields.add(viewId);
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
    public Field<T, V> setFieldValidator(@NonNull final FieldValidator<T, V> validator) {
        mValidator = validator;
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
     * @param viewId view id
     *
     * @return {@code this} (for chaining)
     */
    public Field<T, V> setErrorViewId(@IdRes final int viewId) {
        mErrorViewId = viewId;
        mRelatedFields.add(viewId);
        return this;
    }

    /**
     * Enable a clear/reset button for a picker enabled field.
     *
     * @param id         of the button (on which the onClickListener wil be set)
     * @param resetValue value to set when clicked
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public Field<T, V> setResetButton(@IdRes final int id,
                                      @Nullable final T resetValue) {
        mResetBtnId = id;
        mResetValue = resetValue;
        return this;
    }


    @IdRes
    public int getId() {
        return mId;
    }

    /**
     * Set the View for the field.
     * <p>
     * Unused fields (as configured in the user preferences) will be hidden after this step.
     *
     * @param global Global preferences
     * @param parent of the field View
     */
    @CallSuper
    void setParentView(@NonNull final SharedPreferences global,
                       @NonNull final View parent) {
        mFieldViewAccessor.setView(parent.findViewById(mId));
        if (isUsed(global)) {
            if (mErrorViewId != 0) {
                mFieldViewAccessor.setErrorView(parent.findViewById(mErrorViewId));
            }
            if (mTextInputLayoutId != 0) {
                final TextInputLayout til = parent.findViewById(mTextInputLayoutId);
                til.setEndIconOnClickListener(v -> mFieldViewAccessor.setValue((T) null));
            }
            if (mResetBtnId != 0) {
                mResetBtnViewReference = new WeakReference<>(parent.findViewById(mResetBtnId));
                mResetBtnViewReference.get().setOnClickListener(v -> {
                    mFieldViewAccessor.setValue(mResetValue);
                    v.setVisibility(View.INVISIBLE);
                });
            }
        } else {
            setVisibility(parent, View.GONE);
        }
    }

    @Nullable
    public V getView() {
        return mFieldViewAccessor.getView();
    }

    void setAfterFieldChangeListener(@Nullable final Fields.AfterChangeListener listener) {
        mAfterFieldChangeListener = listener != null ? new WeakReference<>(listener) : null;
    }


    /**
     * <strong>Conditionally</strong> set the visibility for the field and its related fields.
     *
     * @param parent                 parent view for all fields in this collection.
     * @param hideEmptyFields        hide empty field:
     *                               Use {@code true} when displaying;
     *                               and {@code false} when editing.
     * @param keepHiddenFieldsHidden keep a field hidden if it's already hidden
     *                               (even when it has content)
     */
    @SuppressWarnings("StatementWithEmptyBody")
    public void setVisibility(@NonNull final View parent,
                              final boolean hideEmptyFields,
                              final boolean keepHiddenFieldsHidden) {

        final View view = Objects.requireNonNull(mFieldViewAccessor.getView());

        if ((view instanceof ImageView)
            || (view.getVisibility() == View.GONE && keepHiddenFieldsHidden)) {
            // An ImageView always keeps its current visibility
            // When 'keepHiddenFieldsHidden' is set, hidden fields stay hidden.

        } else if (mFieldViewAccessor.isEmpty() && hideEmptyFields) {
            // When 'hideEmptyFields' is set, empty fields are hidden.
            view.setVisibility(View.GONE);

        } else {
            final SharedPreferences global = PreferenceManager
                    .getDefaultSharedPreferences(parent.getContext());
            if (isUsed(global)) {
                // Anything else (in use) should be visible if it's not yet.
                view.setVisibility(View.VISIBLE);
            }
        }

        setRelatedFieldsVisibility(parent, view.getVisibility());
    }

    /**
     * <strong>Unconditionally</strong> set the visibility for the field and its related fields.
     *
     * @param parent     parent view; used to find the <strong>related fields only</strong>
     * @param visibility to use
     */
    public void setVisibility(@NonNull final View parent,
                              final int visibility) {
        final View view = Objects.requireNonNull(mFieldViewAccessor.getView());
        view.setVisibility(visibility);
        setRelatedFieldsVisibility(parent, visibility);
    }

    /**
     * Set the visibility for the related fields.
     *
     * @param parent     parent view for all related fields.
     * @param visibility to use
     */
    private void setRelatedFieldsVisibility(@NonNull final View parent,
                                            final int visibility) {
        for (final int fieldId : mRelatedFields) {
            final View view = parent.findViewById(fieldId);
            if (view != null) {
                view.setVisibility(visibility);
            }
        }

        if (mResetBtnViewReference != null) {
            final View clearBtnView = Objects.requireNonNull(mResetBtnViewReference.get());
            if (visibility == View.VISIBLE) {
                clearBtnView.setVisibility(mFieldViewAccessor.isEmpty()
                                           ? View.INVISIBLE : View.VISIBLE);
            } else {
                clearBtnView.setVisibility(visibility);
            }
        }
    }

    public void setError(@Nullable final String errorText) {
        mFieldViewAccessor.setError(errorText);
    }

    public void setErrorIfEmpty(@NonNull final String errorText) {
        mFieldViewAccessor.setErrorIfEmpty(errorText);
    }

    public boolean isEmpty() {
        return mFieldViewAccessor.isEmpty();
    }

    public void getValue(@NonNull final DataManager target) {
        mFieldViewAccessor.getValue(target);
        //TODO: is there a point calling 'validate' here?
        if (mValidator != null) {
            mValidator.validate(this);
        }
    }

    @Nullable
    public T getValue() {
        return mFieldViewAccessor.getValue();
    }

    public void setValue(@NonNull final DataManager source) {
        mFieldViewAccessor.setValue(source);
    }

    public void setValue(@Nullable final T value) {
        mFieldViewAccessor.setValue(value);
        if (mValidator != null) {
            mValidator.validate(this);
        }
    }

    @NonNull
    public String getKey() {
        return mKey;
    }

    /**
     * Check if this field can be automatically populated.
     *
     * @return {@code true} if it can
     */
    boolean isAutoPopulated() {
        return !mKey.isEmpty();
    }

    /**
     * Is the field in use; i.e. is it enabled in the user-preferences.
     *
     * @param global Global preferences
     *
     * @return {@code true} if the field *can* be visible
     */
    public boolean isUsed(@NonNull final SharedPreferences global) {
        return DBKey.isUsed(global, mIsUsedKey);
    }

    /**
     * Propagate the fact that this field was changed to the {@link Fields.AfterChangeListener}.
     */
    @CallSuper
    public void onChanged() {
        if (mAfterFieldChangeListener != null && mAfterFieldChangeListener.get() != null) {
            mAfterFieldChangeListener.get().afterFieldChange(mId);

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

        if (mResetBtnViewReference != null) {
            final View clearBtnView = Objects.requireNonNull(mResetBtnViewReference.get());
            clearBtnView.setVisibility(mFieldViewAccessor.isEmpty()
                                       ? View.INVISIBLE : View.VISIBLE);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "Field{"
               + "mId=" + mId
               + ", mIsUsedKey=`" + mIsUsedKey + '`'
               + ", mKey=`" + mKey + '`'
               + ", mFieldDataAccessor=" + mFieldViewAccessor
               + ", mValidator=" + mValidator
               + '}';
    }
}
