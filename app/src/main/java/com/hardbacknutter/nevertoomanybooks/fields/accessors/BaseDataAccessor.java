/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
 */
public abstract class BaseDataAccessor<T>
        implements FieldViewAccessor<T> {

    Field<T> mField;
    @Nullable
    T mRawValue;

    boolean mIsEditable;
    @Nullable
    private WeakReference<View> mViewReference;
    @Nullable
    private WeakReference<View> mErrorViewReference;
    @Nullable
    private String mErrorText;

    @Override
    public void setField(@NonNull final Field<T> field) {
        mField = field;
    }

    @NonNull
    @Override
    public View getView()
            throws NoViewException {
        if (mViewReference != null) {
            View view = mViewReference.get();
            if (view != null) {
                return view;
            }
        }
        throw new NoViewException("field key=" + mField.getKey());
    }

    @Override
    public void setView(@NonNull final View view) {
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
     * Fails silently if the view is no present.
     *
     * @param errorText to show
     */
    @Override
    public void setError(@Nullable final String errorText) {
        mErrorText = errorText;
        // Don't complain if the view is not there. We can get called when
        // the field is not on display.
        if (mErrorViewReference != null) {
            View errorView = mErrorViewReference.get();
            if (errorView != null) {
                if (errorView instanceof TextInputLayout) {
                    TextInputLayout til = (TextInputLayout) errorView;
                    til.setErrorEnabled(errorText != null);
                    til.setError(errorText);
                } else if (errorView instanceof TextView) {
                    TextView textView = (TextView) errorView;
                    textView.setText(errorText);
                }
            }
        }
    }

    /**
     * Add on onTouch listener that signals a 'dirty' event when touched.
     * This is/should only be used for fields with non-text Views
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
