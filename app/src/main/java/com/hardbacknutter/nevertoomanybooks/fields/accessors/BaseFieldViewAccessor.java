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
package com.hardbacknutter.nevertoomanybooks.fields.accessors;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.Field;

/**
 * Base implementation.
 *
 * @param <T> type of Field value.
 * @param <V> type of Field View.
 */
public abstract class BaseFieldViewAccessor<T, V extends View>
        implements FieldViewAccessor<T, V> {

    /** Allows callbacks to the Field. */
    Field<T, V> mField;

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

    /**
     * The value as originally loaded from the database
     * by {@link #setInitialValue(DataManager)}.
     */
    @Nullable
    private T mInitialValue;
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private WeakReference<V> mViewReference;
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private WeakReference<View> mErrorViewReference;

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private String mErrorText;

    @Override
    public void setField(@NonNull final Field<T, V> field) {
        mField = field;
    }

    @Nullable
    @Override
    public V getView()
            throws NoViewException {
        if (mViewReference != null) {
            return mViewReference.get();
        }
        return null;
    }

    @Override
    public void setView(@NonNull final V view) {
        mViewReference = new WeakReference<>(view);
    }

    @Override
    public void setErrorView(@Nullable final View errorView) {
        if (errorView != null) {
            mErrorViewReference = new WeakReference<>(errorView);
            if (mErrorText != null) {
                setError(mErrorText);
            }
        } else {
            mErrorViewReference = null;
        }
    }

    /**
     * Supports setting the text on an {@link TextInputLayout} or {@link TextView}.
     * Fails silently if the view is not present.
     *
     * @param errorText to show
     */
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
                }
            }
        }
    }

    public void setInitialValue(@Nullable final T value) {
        mInitialValue = value;
        setValue(value);
    }

    public boolean isChanged() {
        // an initial null/empty value, and a current empty value is considered no-change.
        final T currentValue = getValue();
        if (FieldViewAccessor.isEmpty(mInitialValue) && this.isEmpty()) {
            return false;
        }
        return !Objects.equals(mInitialValue, currentValue);
    }

    /**
     * Call back to the field letting it know the value was changed.
     * <p>
     * FIXME: "initial" -(1)-> "new" -(2)-> "initial"; step (1) is broadcast; step (2) is NOT !
     */
    void broadcastChange() {
        if (isChanged()) {
            mField.onChanged();
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "BaseFieldViewAccessor{" +
               mField.getKey() +
               ": mInitialValue=" + mInitialValue +
               ", mRawValue=" + mRawValue +
               ", mCurrentValue=" + getValue() +
               '}';
    }
}
