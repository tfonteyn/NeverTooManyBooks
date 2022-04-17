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
import android.text.method.DigitsKeyListener;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtTextWatcher;

/**
 * For Locales which use ',' as the decimal separator, the input panel only allows '.'.
 * See class docs: {@link com.hardbacknutter.nevertoomanybooks.utils.ParseUtils}.
 * <p>
 * A {@code null} or {@code 0} value is always displayed as an empty {@code String}.
 */
public class DecimalEditTextAccessor
        extends EditTextAccessor<Number, EditText> {

    /**
     * Constructor.
     *
     * @param formatter to use
     */
    public DecimalEditTextAccessor(@NonNull final FieldFormatter<Number> formatter) {
        super(formatter, false);
    }

    @Override
    public void setView(@NonNull final EditText view) {
        super.setView(view);
        // do not keep a strong reference to the watcher
        view.addTextChangedListener(new DecimalTextWatcher(view));
    }

    public boolean isEmpty(@Nullable final Number value) {
        return value == null || value.doubleValue() == 0.0d;
    }

    /**
     * TextWatcher for TextView fields. Sets the Field value after each change.
     */
    private static class DecimalTextWatcher
            implements ExtTextWatcher {

        private static final String DIGITS = "0123456789";
        @NonNull
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
            final Context context = mView.getContext();
            final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
            final DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(userLocale);
            final DecimalFormatSymbols symbols = nf.getDecimalFormatSymbols();
            mDecimalSeparator = Character.toString(symbols.getDecimalSeparator());

            enableListener(mView.getText());
        }

        @Override
        public void afterTextChanged(@NonNull final Editable editable) {
            // we're not going to change the Editable, no need to toggle this listener
            enableListener(editable);
        }

        private void enableListener(@Nullable final Editable editable) {
            // allow only one decimal separator
            if (editable != null && editable.toString().contains(mDecimalSeparator)) {
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
//                        new DecimalFormatSymbols(AppLocale.getUserLocale(context));
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
}
