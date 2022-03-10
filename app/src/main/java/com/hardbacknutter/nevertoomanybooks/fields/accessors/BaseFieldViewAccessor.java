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
import android.widget.Checkable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import java.lang.ref.WeakReference;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.EditField;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

/**
 * Base implementation.
 *
 * @param <T> type of Field value.
 * @param <V> type of Field View.
 */
public abstract class BaseFieldViewAccessor<T, V extends View>
        implements FieldViewAccessor<T, V> {

    /**
     * The value as originally loaded from the database
     * by {@link #setInitialValue(DataManager)}.
     */
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


    /** Allows callbacks to the Field. */
    Field<T, V> mField;

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private WeakReference<V> mViewReference;

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private WeakReference<View> mErrorViewReference;

    @Nullable
    private String mErrorText;

    /**
     * Check if the given value is considered to be 'empty'.
     * The encapsulated type decides what 'empty' means.
     * <p>
     * An Object is considered to be empty if:
     * <ul>
     *      <li>{@code null}</li>
     *      <li>{@code Money.isZero()}</li>
     *      <li>{@code Number.doubleValue() == 0.0d}</li>
     *      <li>{@code Boolean == false}</li>
     *      <li>{@code Collection.isEmpty}</li>
     *      <li>{@code !Checkable.isChecked()}</li>
     *      <li>{@code String.isEmpty()}</li>
     * </ul>
     *
     * @return {@code true} if empty.
     */
    public static boolean isEmpty(@Nullable final Object o) {
        //noinspection rawtypes
        return o == null
               || o instanceof Money && ((Money) o).isZero()
               || o instanceof Number && ((Number) o).doubleValue() == 0.0d
               || o instanceof Boolean && !(Boolean) o
               || o instanceof Collection && ((Collection) o).isEmpty()
               || o instanceof Checkable && !((Checkable) o).isChecked()
               || o.toString().isEmpty();
    }

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
            // Restore any previous error text
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
                } else {
                    throw new IllegalStateException("Wrong view type: " + errorView);
                }
            }
        }
    }

    public void setInitialValue(@Nullable final T value) {
        mInitialValue = value;
        setValue(value);
    }

    /**
     * Call back to the field letting it know the value was changed.
     * <p>
     * FIXME: "initial" -(1)-> "new" -(2)-> "initial"; step (1) is broadcast; step (2) is NOT !
     */
    void broadcastChange() {
        if (isChanged() && mField instanceof EditField) {
            ((EditField<T, V>) (mField)).onChanged();
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "BaseFieldViewAccessor{"
               + "mField=`" + mField.getKey() + "`"
               + ": mInitialValue=`" + mInitialValue + "`"
               + ", mRawValue=`" + mRawValue + "`"
               + ", mCurrentValue=`" + getValue() + "`"
               + ", mErrorText=`" + mErrorText + "`"
               + '}';
    }
}
