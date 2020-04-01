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

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

/**
 * Stores and retrieves data from an TextView.
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
        extends TextAccessor<T> {

    /** Log tag. */
    private static final String TAG = "EditTextAccessor";

    /**
     * Triggers reformatting (if {@link #mEnableReformat} is set)
     * and propagates the fact a field was changed.
     */
    private TextWatcher mChangedTextWatcher;
    /** Enable or disable the formatting text watcher. */
    private boolean mEnableReformat;

    /**
     * Constructor.
     */
    public EditTextAccessor() {
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
        mEnableReformat = enableReformat;
    }

    @Override
    public void setView(@NonNull final View view) {
        super.setView(view);
        TextView textView = (TextView) view;

        mChangedTextWatcher = new ChangedTextWatcher(mField, textView, mEnableReformat);
        textView.addTextChangedListener(mChangedTextWatcher);
    }

    @Nullable
    @Override
    public T getValue() {
        TextView view = (TextView) getView();
        if (mFormatter != null) {
            if (mFormatter instanceof EditFieldFormatter) {
                // if supported, extract from the View
                return ((EditFieldFormatter<T>) mFormatter).extract(view);
            } else {
                // otherwise use the locale variable
                return super.getValue();
            }
        } else {
            // Without a formatter, we MUST assume <T> to be a String,
            // and SHOULD just get the value from the field as-is.
            // If we get an Exception here then the developer made a boo-boo.
            //noinspection unchecked
            return (T) view.getText().toString().trim();
        }

    }

    @Override
    public void setValue(@NonNull final T value) {
        mRawValue = value;

        TextView view = (TextView) getView();

        // We need to do this in two steps. First format the value as normal.
        String text;
        if (mFormatter != null) {
            try {
                text = mFormatter.format(view.getContext(), mRawValue);

            } catch (@NonNull final ClassCastException e) {
                // Due to the way a Book loads data from the database,
                // it's possible that it gets the column type wrong.
                // See {@link BookCursor} class docs.
                Logger.error(view.getContext(), TAG, e, value);

                text = String.valueOf(value);
            }
        } else {
            text = String.valueOf(value);
        }

        // Second step set the view but ...

        // Disable the ChangedTextWatcher. Any decimal watcher can stay enabled.
        if (mChangedTextWatcher != null) {
            view.removeTextChangedListener(mChangedTextWatcher);
        }

        if (view instanceof AutoCompleteTextView) {
            // prevent auto-completion to kick in / stop the dropdown from opening.
            ((AutoCompleteTextView) view).setText(text, false);
        } else {
            view.setText(text);
        }

        // finally re-enable the watcher
        if (mChangedTextWatcher != null) {
            view.addTextChangedListener(mChangedTextWatcher);
        }
    }

    /**
     * TextWatcher for TextView fields.
     *
     * <ol>
     * <li>Re-formats if needed/allowed</li>
     * <li>clears any previous error</li>
     * <li>propagate the fact that the field changed</li>
     * </ol>
     */
    class ChangedTextWatcher
            implements TextWatcher {

        @NonNull
        private final Field<T> mField;

        @NonNull
        private final TextView mTextView;

        private boolean mOnChangeCalled;

        private long mLastChange;

        /**
         * Constructor.
         *
         * @param field          to watch
         * @param textView       the view used by the field
         * @param enableReformat flag: reformat after every user-change.
         */
        ChangedTextWatcher(@NonNull final Field<T> field,
                           @NonNull final TextView textView,
                           final boolean enableReformat) {
            mField = field;
            //mTextView = (TextView) field.getAccessor().getView();
            mTextView = textView;
            mEnableReformat = enableReformat;
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

        @Override
        public void afterTextChanged(@NonNull final Editable editable) {
            long interval = System.currentTimeMillis() - mLastChange;

            // reformat every 0.5 seconds is good enough and easier on the user.
            if (interval > 500) {
                // reformat if allowed and needed.
                if (mEnableReformat
                    && mFormatter != null
                    && mFormatter instanceof EditFieldFormatter) {

                    T value = ((EditFieldFormatter<T>) mFormatter).extract(mTextView);
                    String formatted = mFormatter.format(mTextView.getContext(), value);

                    // if the new text *can* be formatted and is different
                    if (!editable.toString().trim().equalsIgnoreCase(formatted)) {
                        mTextView.removeTextChangedListener(this);
                        // replace the coded value with the formatted value.
                        editable.replace(0, editable.length(), formatted);
                        mTextView.addTextChangedListener(this);
                    }
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
}
