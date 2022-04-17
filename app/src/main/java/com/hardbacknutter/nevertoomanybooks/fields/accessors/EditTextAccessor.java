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

import android.content.Context;
import android.text.Editable;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.fields.EditField;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtTextWatcher;

/**
 * Stores and retrieves data from an {@link EditText}.
 *
 * @param <T> type of Field value.
 */
public class EditTextAccessor<T, V extends EditText>
        extends TextAccessor<T, V>
        implements ExtTextWatcher {

    private static final String TAG = "EditTextAccessor";

    /** Reformat only every 0.5 seconds: this is good enough and easier on the user. */
    private static final int REFORMAT_DELAY_MS = 500;

    /** Enable or disable the formatting text watcher. */
    private final boolean mEnableReformat;

    /** Timer for the text watcher. */
    private long mLastChange;

    /**
     * Constructor.
     */
    public EditTextAccessor() {
        super(null);
        mEnableReformat = false;
    }

    /**
     * Constructor.
     *
     * @param formatter      to use
     * @param enableReformat flag: reformat after every user-change.
     */
    public EditTextAccessor(@NonNull final FieldFormatter<T> formatter,
                            final boolean enableReformat) {
        super(formatter);
        mEnableReformat = enableReformat && formatter instanceof EditFieldFormatter;
    }

    @Override
    public void setView(@NonNull final V view) {
        super.setView(view);
        view.addTextChangedListener(this);
    }

    @Nullable
    @Override
    public T getValue() {
        return mRawValue;
    }

    @Override
    public void setValue(@Nullable final T value) {
        mRawValue = value;

        final V view = getView();
        if (view != null) {
            // We need to do this in two steps. First format the value as normal.
            String text = null;
            if (mFormatter != null) {
                try {
                    text = mFormatter.format(view.getContext(), mRawValue);

                } catch (@NonNull final ClassCastException e) {
                    // Due to the way a Book loads data from the database,
                    // it's possible that it gets the column type wrong.
                    // See {@link TypedCursor} class docs.
                    // Also see {@link SearchCoordinator#accumulateStringData}
                    Logger.error(TAG, e, value);
                }
            }

            // No formatter, or ClassCastException? just stringify the value.
            if (text == null) {
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
        if (mField instanceof EditField) {
            ((EditField<T, V>) (mField)).setError(null);
        }

        if (mEnableReformat) {
            if (System.currentTimeMillis() - mLastChange > REFORMAT_DELAY_MS) {
                //noinspection ConstantConditions
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
