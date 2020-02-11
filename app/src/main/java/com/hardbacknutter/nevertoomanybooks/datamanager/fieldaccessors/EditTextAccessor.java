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
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Implementation that stores and retrieves data from an EditText.
 * <p>
 * Uses {@link FieldFormatter#apply} and {@link FieldFormatter#extract}.
 *
 * @param <T> type of Field value.
 */
public class EditTextAccessor<T>
        extends TextViewAccessor<T> {

    private TextWatcher mReformatTextWatcher;
    private boolean mIsDecimal;
    private boolean mEnableReformat = true;

    /**
     * For Locales which use ',' as the decimal separator, the input panel only allows '.'.
     * See class docs: {@link com.hardbacknutter.nevertoomanybooks.utils.ParseUtils}.
     */
    public void setDecimalInput(final boolean decimalInput) {
        mIsDecimal = decimalInput;
    }

    /**
     * Enable or disable the formatting text watcher.
     * The default is enabled.
     *
     * @param enableReformat flag
     */
    public void setEnableReformat(final boolean enableReformat) {
        mEnableReformat = enableReformat;
    }

    @Override
    public void setView(@NonNull final View view) {
        super.setView(view);
        EditText editText = (EditText) view;

        mReformatTextWatcher = new ReformatTextWatcher<>(mField, editText, mEnableReformat);
        editText.addTextChangedListener(mReformatTextWatcher);

        if (mIsDecimal) {
            // do not keep a reference to the watcher..
            editText.addTextChangedListener(new DecimalTextWatcher(editText));
        }
    }

    @NonNull
    @Override
    public T getValue() {
        EditText view = (EditText) getView();
        if (view != null) {
            if (mFormatter != null) {
                return mFormatter.extract(view);
            } else {
                // all non-String field should have formatters.
                // This means at this point that <T> MUST be a String.
                // If we get an Exception here then the developer made a boo-boo.
                //noinspection unchecked
                return (T) view.getText().toString().trim();
            }
        }

        return super.getValue();
    }

    @Override
    public void setValue(@NonNull final T value) {
        EditText view = (EditText) getView();
        if (view != null) {
            // Disable the TextWatcher!
            synchronized (mReformatTextWatcher) {
                view.removeTextChangedListener(mReformatTextWatcher);

                String oldVal = view.getText().toString().trim();

                super.setValue(value);

                // do not use extract here, we compare formatted/formatted value
                String newVal = view.getText().toString().trim();
                if (!newVal.equals(oldVal)) {
                    if (view instanceof AutoCompleteTextView) {
                        // prevent auto-completion to kick in / stop the dropdown from opening.
                        // this happened if the field had the focus when we'd be populating it.
                        ((AutoCompleteTextView) view).setText(newVal, false);
                    } else {
                        view.setText(newVal);
                    }
                }
                view.addTextChangedListener(mReformatTextWatcher);
            }
        }
    }

    /**
     * TextWatcher for EditText fields. Sets the Field value after each EditText change.
     */
    public static class DecimalTextWatcher
            implements TextWatcher {

        private static final String DIGITS = "0123456789";
        private final String mDecimalSeparator;
        /**
         * Strong reference to View is fine.
         * This watcher will get destroyed when the View gets destroyed.
         * <strong>Note:</strong> do NOT keep a strong reference to the watcher itself!
         */
        @NonNull
        private final EditText mView;

        DecimalTextWatcher(@NonNull final EditText view) {
            mView = view;
            Locale locale = LocaleUtils.getUserLocale(mView.getContext());
            DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(locale);
            DecimalFormatSymbols symbols = nf.getDecimalFormatSymbols();
            mDecimalSeparator = Character.toString(symbols.getDecimalSeparator());
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
            // allow only one decimal separator
            if (editable.toString().contains(mDecimalSeparator)) {
                mView.setKeyListener(DigitsKeyListener.getInstance(DIGITS));
            } else {
                mView.setKeyListener(DigitsKeyListener.getInstance(DIGITS + mDecimalSeparator));
            }

        }

//        public static class DecimalDigitsInputFilter
//                implements InputFilter {
//
//            Pattern mPattern;
//
//            public DecimalDigitsInputFilter(@NonNull final Context context,
//                                            final int digitsBeforeZero,
//                                            final int digitsAfterZero) {
//                DecimalFormatSymbols d =
//                        new DecimalFormatSymbols(LocaleUtils.getUserLocale(context));
//                String s = "\\\\" + d.getDecimalSeparator();
//                mPattern = Pattern.compile(
//                        "[0-9]{0," + (digitsBeforeZero - 1) + "}+"
//                        + "((" + s + "[0-9]{0," + (digitsAfterZero - 1) + "})?)"
//                        + ""
//                        + "|(" + s + ")?");
//            }
//
//            @Override
//            public CharSequence filter(CharSequence source,
//                                       int start,
//                                       int end,
//                                       Spanned dest,
//                                       int dstart,
//                                       int dend) {
//
//                Matcher matcher = mPattern.matcher(dest);
//                if (!matcher.matches()) {
//                    return "";
//                }
//                return null;
//            }
//        }
    }

    /**
     * TextWatcher for EditText fields. Sets the Field value after each EditText change.
     *
     * @param <T> type of Field value.
     */
    public static class ReformatTextWatcher<T>
            implements TextWatcher {

        private static final String TAG = "ReformatTextWatcher";
        @NonNull
        private final Fields.Field<T> mField;
        @Nullable
        private final FieldFormatter<T> mFormatter;

        @NonNull
        private final EditText mEditText;
        private final boolean mEnableReformat;

        private boolean mOnChangeCalled;

        private long lastChange;

        ReformatTextWatcher(@NonNull final Fields.Field<T> field,
                            @NonNull final EditText editText,
                            final boolean enableReformat) {
            mField = field;
            mFormatter = mField.getAccessor().getFormatter();

            mEditText = editText;
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
        public void afterTextChanged(@NonNull final Editable s) {
            long interval = System.currentTimeMillis() - lastChange;
            // react every 0.5 seconds is good enough
            if (interval > 500) {
                // reformat if allowed and needed.
                if (mEnableReformat && mFormatter != null) {
                    T value = mFormatter.extract(mEditText);
                    String formatted = mFormatter.format(mEditText.getContext(), value);

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.FIELD_TEXT_WATCHER) {
                        Log.d(TAG, "s.toString().trim()=`" + s.toString().trim() + '`'
                                   + "|value=`" + value + '`'
                                   + "|formatted=`" + formatted + '`');
                    }
                    // if the new text *can* be formatted and is different
                    if (!s.toString().trim().equalsIgnoreCase(formatted)) {
                        // replace the coded value with the formatted value.
                        s.replace(0, s.length(), formatted);
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
