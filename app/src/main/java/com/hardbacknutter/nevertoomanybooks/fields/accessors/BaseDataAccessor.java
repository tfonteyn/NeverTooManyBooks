/*
 * @Copyright 2020 HardBackNutter
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

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.fields.Field;

/**
 * Base implementation.
 *
 * @param <T> type of Field value.
 * @param <V> type of Field View.
 */
public abstract class BaseDataAccessor<T, V extends View>
        implements FieldViewAccessor<T, V> {

    Field<T, V> mField;

    @Nullable
    T mRawValue;

    boolean mIsEditable;
    @Nullable
    private WeakReference<V> mViewReference;
    @Nullable
    private WeakReference<View> mErrorViewReference;
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

    /**
     * Add on onTouchListener that signals a 'dirty' event when touched.
     * This is/should only be added to fields with non-text Views while {@link #mIsEditable}
     * is {@code true}.
     *
     * @param view The view to watch
     */
    @SuppressWarnings("SameReturnValue")
    @SuppressLint("ClickableViewAccessibility")
    void addTouchSignalsDirty(@NonNull final View view) {
        view.setOnTouchListener((v, event) -> {
            if (MotionEvent.ACTION_UP == event.getAction()) {
                mField.onChanged(false);
            }
            return false;
        });
    }
}
