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
import android.text.Editable;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.fields.endicon.ExtEndIconDelegate;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtTextWatcher;

/**
 * Stores and retrieves data from an {@link EditText}.
 *
 * @param <T> type of Field value.
 */
public class EditTextField<T, V extends EditText>
        extends BaseTextField<T, V>
        implements ExtTextWatcher {

    private static final String TAG = "EditTextField";

    /** Reformat only every 0.5 seconds: this is good enough and easier on the user. */
    private static final int REFORMAT_DELAY_MS = 500;

    /** Enable or disable the formatting text watcher. */
    private final boolean mEnableReformat;

    /** Timer for the text watcher. */
    private long mLastChange;

    /**
     * Constructor.
     */
    public EditTextField(@NonNull final FragmentId fragmentId,
                         @IdRes final int fieldViewId,
                         @NonNull final String fieldKey) {
        super(fragmentId, fieldViewId, fieldKey, fieldKey, null);
        mEnableReformat = false;
    }

    /**
     * Constructor.
     *
     * @param formatter      to use
     * @param enableReformat flag: reformat after every user-change.
     */
    public EditTextField(@NonNull final FragmentId fragmentId,
                         @IdRes final int fieldViewId,
                         @NonNull final String fieldKey,
                         @NonNull final FieldFormatter<T> formatter,
                         final boolean enableReformat) {
        super(fragmentId, fieldViewId, fieldKey, fieldKey, formatter);
        mEnableReformat = enableReformat && formatter instanceof EditFieldFormatter;
    }

    public EditTextField(@NonNull final FragmentId fragmentId,
                         @IdRes final int fieldViewId,
                         @NonNull final String fieldKey,
                         @NonNull final String prefKey,
                         @NonNull final FieldFormatter<T> formatter,
                         final boolean enableReformat) {
        super(fragmentId, fieldViewId, fieldKey, prefKey, formatter);
        mEnableReformat = enableReformat && formatter instanceof EditFieldFormatter;
    }

    /**
     * Set the id for the surrounding TextInputLayout (if this field has one).
     *
     * @param viewId view id
     */
    @NonNull
    public EditTextField<T, V> setTextInputLayoutId(@IdRes final int viewId) {
        mTextInputLayoutId = viewId;
        setErrorViewId(viewId);
        return this;
    }

    @NonNull
    public EditTextField<T, V> setEndIconMode(
            @ExtEndIconDelegate.EndIconMode final int endIconMode) {
        mEndIconMode = endIconMode;
        return this;
    }

    @NonNull
    public EditTextField<T, V> setFormatter(@NonNull final FieldFormatter<T> formatter) {
        mFormatter = formatter;
        return this;
    }

    @Override
    public void setParentView(@NonNull final View parent,
                              @NonNull final SharedPreferences global) {
        super.setParentView(parent, global);
        requireView().addTextChangedListener(this);
    }

    @Override
    @Nullable
    public T getValue() {
        return mRawValue;
    }

    @Override
    public void setValue(@Nullable final T value) {
        super.setValue(value);

        final V view = getView();
        if (view != null) {
            // We need to do this in two steps. First format the value as normal.
            String text;
            try {
                text = mFormatter.format(view.getContext(), mRawValue);

            } catch (@NonNull final ClassCastException e) {
                // Due to the way a Book loads data from the database,
                // it's possible that it gets the column type wrong.
                // See {@link TypedCursor} class docs.
                // Also see {@link SearchCoordinator#accumulateStringData}
                Logger.error(TAG, e, value);
                text = mRawValue != null ? String.valueOf(mRawValue) : "";
            }

            // Second step set the view but ...
            // ... disable the ChangedTextWatcher.
            view.removeTextChangedListener(this);
            if (view instanceof AutoCompleteTextView) {
                // ... prevent auto-completion to kick in / stop the dropdown from opening.
                ((AutoCompleteTextView) view).setText(text, false);
            } else {
                // ... or set as is
                view.setText(text);
            }
            // ... finally re-enable the watcher
            view.addTextChangedListener(this);
        }
    }

    /**
     * TextWatcher for TextView fields.
     *
     * <ol>
     *     <li>Update the current in-memory value</li>
     *      <li>clears any previous error</li>
     *      <li>Re-formats if allowed and needed</li>
     *      <li>notify listeners of any change</li>
     * </ol>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void afterTextChanged(@NonNull final Editable editable) {
        final T previous = mRawValue;

        final V view = getView();

        //noinspection ConstantConditions
        final Context context = view.getContext();

        final String text = editable.toString().trim();
        // Update the actual value
        if (mFormatter instanceof EditFieldFormatter) {
            mRawValue = ((EditFieldFormatter<T>) mFormatter).extract(context, text);
        } else {
            // Without a formatter, we MUST assume <T> to be a String.
            // Make sure NOT to replace a 'null' value with an empty string
            if (mRawValue != null || !text.isEmpty()) {
                // If we get an Exception here then the developer made a boo-boo.
                //noinspection unchecked
                mRawValue = (T) text;
            }
        }
        // Clear any previous error. The new content will be re-checked at validation time.
        setError(null);

        if (mEnableReformat) {
            if (System.currentTimeMillis() - mLastChange > REFORMAT_DELAY_MS) {
                final String formatted = mFormatter.format(context, mRawValue);
                // If different, replace the encoded value with the formatted value.
                if (!text.equalsIgnoreCase(formatted)) {
                    view.removeTextChangedListener(this);
                    editable.replace(0, editable.length(), formatted);
                    view.addTextChangedListener(this);
                }
            }
            mLastChange = System.currentTimeMillis();
        }

        notifyIfChanged(previous);
    }
}
