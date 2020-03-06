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
package com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.datamanager.Field;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Implementation that stores and retrieves data from an TextView.
 *
 * @param <T> type of Field value.
 */
public class EditTextAccessor<T>
        extends TextAccessor<T> {

    private static final String TAG = "EditTextAccessor";

    private TextWatcher mReformatTextWatcher;
    /** Enable or disable the formatting text watcher. */
    private boolean mEnableReformat;

    /**
     * Constructor.
     * <p>
     * The reformatting text watcher is enabled.
     */
    public EditTextAccessor() {
        mEnableReformat = true;
    }

    /**
     * Constructor.
     *
     * @param enableReformat flag
     */
    EditTextAccessor(final boolean enableReformat) {
        mEnableReformat = enableReformat;
    }

    /**
     * Constructor.
     * <p>
     * The reformatting text watcher is enabled.
     */
    public EditTextAccessor(@Nullable final FieldFormatter<T> formatter) {
        super(formatter);
        mEnableReformat = true;
    }

    /**
     * Constructor.
     *
     * @param enableReformat flag
     */
    public EditTextAccessor(@Nullable final FieldFormatter<T> formatter,
                     final boolean enableReformat) {
        super(formatter);
        mEnableReformat = enableReformat;
    }

    @Override
    public void setView(@NonNull final View view) {
        super.setView(view);
        TextView textView = (TextView) view;

        // If the user can type in this field, watch it.
        if (mFormatter instanceof EditFieldFormatter) {
            mReformatTextWatcher = new ChangedTextWatcher(mField, textView, mEnableReformat);
            textView.addTextChangedListener(mReformatTextWatcher);
        }
    }

    @NonNull
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
            // all non-String field should have formatters.
            // This means at this point that <T> MUST be a String.
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
        if (mReformatTextWatcher != null) {
            view.removeTextChangedListener(mReformatTextWatcher);
        }

        if (view instanceof AutoCompleteTextView) {
            // prevent auto-completion to kick in / stop the dropdown from opening.
            // this happened if the field had the focus when we'd be populating it.
            ((AutoCompleteTextView) view).setText(text, false);
        } else {
            view.setText(text);
        }

        // finally re-enable the watcher
        if (mReformatTextWatcher != null) {
            view.addTextChangedListener(mReformatTextWatcher);
        }
    }

    /**
     * TextWatcher for TextView fields. Lets the Field know it's changed.
     * If needed, reformats the on-screen value after each change.
     */
    class ChangedTextWatcher
            implements TextWatcher {

        @NonNull
        private final Field<T> mField;

        @NonNull
        private final TextView mTextView;

        private boolean mOnChangeCalled;

        private long lastChange;

        ChangedTextWatcher(@NonNull final Field<T> field,
                           @NonNull final TextView textView,
                           final boolean enableReformat) {
            mField = field;
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
            long interval = System.currentTimeMillis() - lastChange;
            // react every 0.5 seconds is good enough and easier on the user.
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

                // only broadcast a change once.
                if (!mOnChangeCalled) {
                    mOnChangeCalled = true;
                    mField.onChanged();
                }
            }
            lastChange = System.currentTimeMillis();
        }
    }
}
