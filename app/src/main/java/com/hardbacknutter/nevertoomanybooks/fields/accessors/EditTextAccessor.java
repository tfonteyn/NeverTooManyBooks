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

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

/**
 * Stores and retrieves data from an EditText.
 *
 * <pre>
 *     {@code
 *             <com.google.android.material.textfield.TextInputLayout
 *             android:id="@+id/lbl_title"
 *             style="@style/Envelope.EditText"
 *             android:hint="@string/lbl_title"
 *             app:layout_constraintEnd_toEndOf="parent"
 *             app:layout_constraintStart_toStartOf="parent"
 *             app:layout_constraintTop_toBottomOf="@id/lbl_author"
 *             >
 *
 *             <com.google.android.material.textfield.TextInputEditText
 *                 android:id="@+id/title"
 *                 style="@style/titleTextEntry"
 *                 android:layout_width="match_parent"
 *                 tools:ignore="Autofill"
 *                 tools:text="@sample/data.json/book/title"
 *                 />
 *
 *         </com.google.android.material.textfield.TextInputLayout>}
 * </pre>
 *
 * @param <T> type of Field value.
 */
public class EditTextAccessor<T>
        extends TextAccessor<T, EditText>
        implements TextWatcher {

    /** Log tag. */
    private static final String TAG = "EditTextAccessor";

    /** Reformat only every 0.5 seconds: this is good enough and easier on the user. */
    private static final int REFORMAT_DELAY_MS = 500;

    /** Enable or disable the formatting text watcher. */
    private final boolean mEnableReformat;

    private boolean mOnChangeCalled;

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
    public void setView(@NonNull final EditText view) {
        super.setView(view);
        view.addTextChangedListener(this);
    }

    @Nullable
    @Override
    public T getValue() {
        final TextView view = getView();
        if (view != null) {
            if (mFormatter != null) {
                if (mFormatter instanceof EditFieldFormatter) {
                    // if supported, extract from the View
                    return ((EditFieldFormatter<T>) mFormatter).extract(view);
                } else {
                    // otherwise use the locale variable
                    return mRawValue;
                }
            } else {
                // Without a formatter, we MUST assume <T> to be a String,
                // and SHOULD just get the value from the field as-is.
                // This DOES mean that an original null value is returned as the empty String.
                // If we get an Exception here then the developer made a boo-boo.
                //noinspection unchecked
                return (T) view.getText().toString().trim();
            }

        } else {
            return mRawValue;
        }
    }

    @Override
    public void setValue(@Nullable final T value) {
        mRawValue = value;

        final TextView view = getView();
        if (view != null) {
            // We need to do this in two steps. First format the value as normal.
            String text = null;
            if (mFormatter != null) {
                try {
                    text = mFormatter.format(view.getContext(), mRawValue);

                } catch (@NonNull final ClassCastException e) {
                    // Due to the way a Book loads data from the database,
                    // it's possible that it gets the column type wrong.
                    // See {@link BookCursor} class docs.
                    Logger.error(view.getContext(), TAG, e, value);
                }
            }

            // No formatter, or ClassCastException.
            if (text == null) {
                text = mRawValue != null ? String.valueOf(mRawValue) : "";
            }

            // Second step set the view but ...

            // Disable the ChangedTextWatcher.
            view.removeTextChangedListener(this);

            if (view instanceof AutoCompleteTextView) {
                // prevent auto-completion to kick in / stop the dropdown from opening.
                ((AutoCompleteTextView) view).setText(text, false);
            } else {
                view.setText(text);
            }

            // finally re-enable the watcher
            view.addTextChangedListener(this);
        }
    }

    @Override
    public void beforeTextChanged(@NonNull final CharSequence s,
                                  final int start,
                                  final int count,
                                  final int after) {
    }

    @Override
    public void onTextChanged(@NonNull final CharSequence s,
                              final int start,
                              final int before,
                              final int count) {
    }

    /**
     * TextWatcher for TextView fields.
     *
     * <ol>
     *      <li>Re-formats if allowed and needed</li>
     *      <li>clears any previous error</li>
     *      <li>propagate the fact that the field changed</li>
     * </ol>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void afterTextChanged(@NonNull final Editable editable) {
        // reformat if allowed and needed
        if (mEnableReformat && System.currentTimeMillis() - mLastChange > REFORMAT_DELAY_MS) {
            // the view will never be null here.
            final TextView view = getView();
            //noinspection ConstantConditions
            final T value = ((EditFieldFormatter<T>) mFormatter).extract(view);
            final String formatted = mFormatter.format(view.getContext(), value);

            // if the new text *can* be formatted and is different
            if (!editable.toString().trim().equalsIgnoreCase(formatted)) {
                view.removeTextChangedListener(this);
                // replace the coded value with the formatted value.
                editable.replace(0, editable.length(), formatted);
                view.addTextChangedListener(this);
            }
        }

        // Clear any previous error. The new content will be re-checked at validation time.
        mField.getAccessor().setError(null);

        // broadcast a change only once.
        if (!mOnChangeCalled) {
            mOnChangeCalled = true;
            mField.onChanged(false);
        }

        mLastChange = System.currentTimeMillis();
    }
}
