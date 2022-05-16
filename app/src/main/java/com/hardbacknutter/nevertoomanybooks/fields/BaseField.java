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

import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * Base implementation.
 *
 * @param <T> type of Field value.
 * @param <V> type of Field View.
 */
public abstract class BaseField<T, V extends View>
        implements Field<T, V> {

    private static final String TAG = "BaseField";

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
    final String mFieldKey;

    /**
     * The preference key (field-name) to check if this Field is used or not.
     * i.e. the key to be used for {@link #isUsed()}.
     */
    @NonNull
    private final String mIsUsedKey;

    @NonNull
    private final FragmentId mFragmentId;

    /** Fields that need to follow visibility. */
    private final Collection<Integer> mRelatedViews = new HashSet<>();

    @SuppressWarnings("FieldNotUsedInToString")
    @IdRes
    private final int mFieldViewId;

    /** The value as originally loaded from the database. */
    @Nullable
    T mInitialValue;

    /**
     * The value which is currently held in memory.
     * If there is no current View, then this value *is* the correct current value.
     * If there is a View, then the View will contain the correct current value.
     * i.e. always try the View first before using this value.
     * <p>
     * Updated by the user and/or {@link #setValue(Object)}.
     */
    @Nullable
    T mRawValue;

    @SuppressWarnings("FieldNotUsedInToString")
    @IdRes
    private int mErrorViewId;

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private WeakReference<V> mViewReference;

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private WeakReference<View> mErrorViewReference;

    @Nullable
    private String mErrorText;

    @Nullable
    private Validator<T, V> mValidator;

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private WeakReference<AfterChangedListener> mAfterFieldChangeListener;

    /**
     * Constructor.
     *
     * @param fragmentId  the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     *                    Set to {@code ""} to suppress all access.
     * @param prefKey     The preference key to check if this Field is used or not
     */
    BaseField(@NonNull final FragmentId fragmentId,
              @IdRes final int fieldViewId,
              @NonNull final String fieldKey,
              @NonNull final String prefKey) {
        mFragmentId = fragmentId;
        mFieldViewId = fieldViewId;
        mFieldKey = fieldKey;
        mIsUsedKey = prefKey;
    }

    /**
     * set the field ID's which should follow visibility with this Field.
     *
     * <strong>Dev. note:</strong> this could be done using
     * {@link androidx.constraintlayout.widget.Group}
     * but that means creating a group for EACH field. That would be overkill.
     *
     * @param viewIds labels etc
     */
    @NonNull
    public Field<T, V> addRelatedViews(@NonNull @IdRes final Integer... viewIds) {
        mRelatedViews.addAll(Arrays.asList(viewIds));
        return this;
    }

    /**
     * Set the optional validator for this field.
     *
     * @param validator to use
     */
    @NonNull
    public Field<T, V> setValidator(@NonNull final Validator<T, V> validator) {
        mValidator = validator;
        return this;
    }

    @Override
    public void setAfterFieldChangeListener(@Nullable final AfterChangedListener listener) {
        mAfterFieldChangeListener = listener != null ? new WeakReference<>(listener) : null;
    }

    @Override
    @NonNull
    public FragmentId getFragmentId() {
        return mFragmentId;
    }

    @Override
    @IdRes
    public int getFieldViewId() {
        return mFieldViewId;
    }

    @Override
    public boolean isAutoPopulated() {
        return !mFieldKey.isEmpty();
    }

    /**
     * Set the id for the optional error view.
     *
     * @param id view id
     */
    void setErrorViewId(@IdRes final int id) {
        mErrorViewId = id;
        mRelatedViews.add(id);
    }

    @NonNull
    public V requireView() {
        return Objects.requireNonNull(getView());
    }

    /**
     * Internal to the Field class.
     *
     * @see #putValue(DataManager)
     */
    abstract void internalPutValue(@NonNull final DataManager target);

    @Override
    public void putValue(@NonNull final DataManager target) {
        internalPutValue(target);
        if (mValidator != null) {
            mValidator.validate(this);
        }
    }

    @CallSuper
    @Override
    public void setValue(@Nullable final T value) {
        mRawValue = value;
        if (mValidator != null) {
            mValidator.validate(this);
        }
    }

    /**
     * Set the visibility for the related views.
     *
     * @param parent parent view for all related fields.
     */
    void setRelatedViewsVisibility(@NonNull final View parent) {
        setRelatedViewsVisibility(parent, requireView().getVisibility());
    }

    /**
     * Set the visibility for the related views.
     *
     * @param parent     parent view for all related fields.
     * @param visibility to use
     */
    private void setRelatedViewsVisibility(@NonNull final View parent,
                                           final int visibility) {
        for (final int viewId : mRelatedViews) {
            final View view = parent.findViewById(viewId);
            if (view != null) {
                view.setVisibility(visibility);
            }
        }
    }

    @Override
    public void setVisibility(@NonNull final View parent,
                              final boolean hideEmptyFields,
                              final boolean keepHiddenFieldsHidden) {
        final View view = requireView();
        final int currentVisibility = view.getVisibility();

        if ((view instanceof ImageView)
            || (keepHiddenFieldsHidden && currentVisibility == View.GONE)) {
            // An ImageView always keeps its current visibility.
            // When 'keepHiddenFieldsHidden' is set, hidden fields stay hidden.
            // Either way, the related views follow the main view
            setRelatedViewsVisibility(parent, currentVisibility);

        } else {
            if (hideEmptyFields && isEmpty()) {
                // When 'hideEmptyFields' is set, empty fields are hidden.
                view.setVisibility(View.GONE);
                setRelatedViewsVisibility(parent, View.GONE);

            } else if (isUsed()) {
                // Anything else (in use) should be visible if it's not yet.
                view.setVisibility(View.VISIBLE);
                setRelatedViewsVisibility(parent, View.VISIBLE);
            }
        }
    }

    /**
     * Get the previously set view.
     *
     * @return view
     *
     * @see #requireView()
     */
    @Nullable
    V getView()
            throws NoViewException {
        if (mViewReference != null) {
            return mViewReference.get();
        }
        return null;
    }

    @CallSuper
    @Override
    public void setParentView(@NonNull final View parent) {

        mViewReference = new WeakReference<>(parent.findViewById(mFieldViewId));
        // Unused fields are hidden here BEFORE they are displayed at all.
        if (!isUsed()) {
            requireView().setVisibility(View.GONE);
            setRelatedViewsVisibility(parent, View.GONE);
            return;
        }

        if (mErrorViewId != 0) {
            final View errorView = parent.findViewById(mErrorViewId);
            if (errorView != null) {
                mErrorViewReference = new WeakReference<>(errorView);
                // Restore any previous error text
                if (mErrorText != null) {
                    setError(mErrorText);
                }
            } else {
                mErrorViewReference = null;
            }
        }
    }

    @Override
    public void setError(@Nullable final String errorText) {
        mErrorText = errorText;
        // Don't complain if the view is not there. We can get called when
        // the field is not on display.
        if (mErrorViewReference != null) {
            final View errorView = mErrorViewReference.get();
            if (errorView != null) {
                if (errorView instanceof TextInputLayout) {
                    final TextInputLayout til = (TextInputLayout) errorView;
                    til.setErrorEnabled(errorText != null);
                    til.setError(errorText);
                } else if (errorView instanceof TextView) {
                    final TextView textView = (TextView) errorView;
                    textView.setError(errorText);
                } else {
                    throw new IllegalStateException("Wrong view type: " + errorView);
                }
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return isEmpty(getValue());
    }

    /**
     * Check if the given value is considered to be 'empty'.
     *
     * @return {@code true} if empty.
     */
    abstract boolean isEmpty(@Nullable T value);

    /**
     * Notify an {@link Field} if the value was changed compared
     * to the initial and/or previous.
     */
    public void notifyIfChanged(@Nullable final T previous) {
        final T currentValue = getValue();
        final boolean allEqual =
                // all empty
                (isEmpty(mInitialValue) && isEmpty(previous) && isEmpty(currentValue))
                // or all equal?
                || (Objects.equals(mInitialValue, previous)
                    && Objects.equals(previous, currentValue));

        if (!allEqual) {
            if (mAfterFieldChangeListener != null && mAfterFieldChangeListener.get() != null) {
                mAfterFieldChangeListener.get().onAfterChanged(this);

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
    }

    @Override
    public boolean isUsed() {
        return DBKey.isUsed(mIsUsedKey);
    }

    @Override
    @NonNull
    public String toString() {
        return "BaseField{"
               + "mFieldKey=" + mFieldKey
               + ", mIsUsedKey=" + mIsUsedKey
               + ": mFragmentId=" + mFragmentId
               + ", mRelatedFields=" + mRelatedViews
               + ", mInitialValue=`" + mInitialValue + "`"
               + ", mRawValue=`" + mRawValue + "`"
               + ", mCurrentValue=`" + getValue() + "`"
               + ", mErrorText=`" + mErrorText + "`"
               + ", mValidator=" + mValidator
               + '}';
    }
}
