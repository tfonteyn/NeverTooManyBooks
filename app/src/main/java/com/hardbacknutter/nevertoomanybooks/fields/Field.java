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
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.FieldViewAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.TextAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.validators.FieldValidator;

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
 * A Formatter can be set on any class extending {@link TextAccessor},
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
 *          {@link EditFieldFormatter#extract} ->
 *          {@link FieldViewAccessor#getValue()} ->
 *          {@link FieldViewAccessor#getValue(DataManager)}</li>
 * </ul>
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

    private final FragmentId mFragmentId;
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
     * i.e. the key to be used for {@link DBKey#isUsed(SharedPreferences, String)}.
     */
    @NonNull
    private final String mIsUsedKey;

    /** Fields that need to follow visibility. */
    private final Collection<Integer> mRelatedFields = new HashSet<>();

    @Nullable
    private FieldValidator<T, V> mValidator;
    @Nullable
    private WeakReference<AfterFieldChangeListener> mAfterFieldChangeListener;
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
     * @param id       for this field.
     * @param accessor to use
     * @param key      Key used to access a {@link DataManager}
     *                 Set to {@code ""} to suppress all access.
     *                 (also used as the preference key to check if this Field is used or not)
     */
    public Field(@NonNull final FragmentId fragmentId,
                 @IdRes final int id,
                 @NonNull final FieldViewAccessor<T, V> accessor,
                 @NonNull final String key) {
        this(fragmentId, id, accessor, key, key);
    }

    /**
     * Constructor.
     *
     * @param id        for this field.
     * @param accessor  to use
     * @param key       Key used to access a {@link DataManager}
     *                  Set to {@code ""} to suppress all access.
     * @param entityKey The preference key to check if this Field is used or not
     */
    public Field(@NonNull final FragmentId fragmentId,
                 @IdRes final int id,
                 @NonNull final FieldViewAccessor<T, V> accessor,
                 @NonNull final String key,
                 @NonNull final String entityKey) {

        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValue(key, "key");
        }

        mFragmentId = fragmentId;
        mId = id;
        mKey = key;
        mIsUsedKey = entityKey;

        mFieldViewAccessor = accessor;
        mFieldViewAccessor.setField(this);
    }

    /**
     * Load all fields from the passed {@link DataManager}.
     * The values will be passed from the DataManager to the individual fields;
     * then to the view accessor; the optional formatter; and finally into the View.
     *
     * @param dataManager {@link DataManager} to load Field objects from.
     */
    public static void load(@NonNull final DataManager dataManager,
                            @NonNull final List<Field<?, ? extends View>> fields) {

        // do NOT call onChanged, as this is the initial load
        fields.stream()
              .filter(Field::isAutoPopulated)
              .forEach(field -> field.setInitialValue(dataManager));
    }

    /**
     * Save all fields to the passed {@link DataManager}.
     *
     * @param dataManager {@link DataManager} to put Field objects in.
     */
    public static void save(@NonNull final List<Field<?, ? extends View>> fields,
                            @NonNull final DataManager dataManager) {
        fields.stream()
              .filter(Field::isAutoPopulated)
              .forEach(field -> field.getValue(dataManager));
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

    /**
     * Get the {@link FragmentId} in which this Field is handled.
     *
     * @return id
     */
    @NonNull
    public FragmentId getFragmentId() {
        return mFragmentId;
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
    public void setParentView(@NonNull final SharedPreferences global,
                              @NonNull final View parent) {
        mFieldViewAccessor.setView(parent.findViewById(mId));
        if (isUsed(global)) {
            if (mErrorViewId != 0) {
                mFieldViewAccessor.setErrorView(parent.findViewById(mErrorViewId));
            }
            if (mTextInputLayoutId != 0) {
                final TextInputLayout til = parent.findViewById(mTextInputLayoutId);
                til.setEndIconOnClickListener(v -> mFieldViewAccessor.setValue(null));
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

    @NonNull
    public V requireView() {
        return Objects.requireNonNull(mFieldViewAccessor.getView());
    }

    public void setAfterFieldChangeListener(@Nullable final AfterFieldChangeListener listener) {
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

    /**
     * Set the value directly. (e.g. upon another field changing... etc...)
     *
     * @param value to set
     */
    public void setValue(@Nullable final T value) {
        mFieldViewAccessor.setValue(value);
        if (mValidator != null) {
            mValidator.validate(this);
        }
    }

    /**
     * Load the field from the passed {@link DataManager}.
     * <p>
     * This is used for the <strong>INITIAL LOAD</strong>, i.e. the value as stored
     * in the database.
     *
     * @param source DataManager to load the Field objects from
     */
    public void setInitialValue(@NonNull final DataManager source) {
        mFieldViewAccessor.setInitialValue(source);
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
    private boolean isAutoPopulated() {
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
     * Propagate the fact that this field was changed to the {@link AfterFieldChangeListener}.
     */
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

    public interface AfterFieldChangeListener {

        void onAfterFieldChange(@NonNull Field<?, ? extends View> field);
    }
}
