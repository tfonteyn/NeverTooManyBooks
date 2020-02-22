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
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * For Locales which use ',' as the decimal separator, the input panel only allows '.'.
 * See class docs: {@link com.hardbacknutter.nevertoomanybooks.utils.ParseUtils}.
 */
public class DecimalEditTextAccessor
        extends EditTextAccessor<Number> {

    public DecimalEditTextAccessor(@Nullable final FieldFormatter<Number> formatter,
                                   final boolean enableReformat) {
        super(formatter, enableReformat);
    }

    @Override
    public void setView(@NonNull final View view) {
        super.setView(view);
        TextView textView = (TextView) view;
        // do not keep a strong reference to the watcher
        textView.addTextChangedListener(new DecimalTextWatcher(textView));
    }

    /**
     * TextWatcher for TextView fields. Sets the Field value after each change.
     */
    private static class DecimalTextWatcher
            implements TextWatcher {

        private static final String DIGITS = "0123456789";
        private final String mDecimalSeparator;
        /**
         * Strong reference to View is fine.
         * This watcher will get destroyed when the View gets destroyed.
         * <strong>Note:</strong> do NOT keep a strong reference to the watcher itself!
         */
        @NonNull
        private final TextView mView;

        DecimalTextWatcher(@NonNull final TextView view) {
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
}
