/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.text.Editable;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.core.widgets.ExtTextWatcher;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

/**
 * For Locales which use ',' as the decimal separator, the input panel only allows '.'.
 * See class docs: {@link com.hardbacknutter.nevertoomanybooks.utils.ParseUtils}.
 * <p>
 * A {@code null} or {@code 0} value is always displayed as an empty {@code String}.
 */
public class DecimalEditTextField
        extends EditTextField<Number, EditText> {

    /**
     * Constructor.
     *
     * @param fragmentId  the hosting {@link FragmentId} for this {@link Field}
     * @param fieldViewId the view id for this {@link Field}
     * @param fieldKey    Key used to access a {@link DataManager}
     *                    Set to {@code ""} to suppress all access.
     * @param formatter   to use
     */
    public DecimalEditTextField(@NonNull final FragmentId fragmentId,
                                @IdRes final int fieldViewId,
                                @NonNull final String fieldKey,
                                @NonNull final FieldFormatter<Number> formatter) {
        super(fragmentId, fieldViewId, fieldKey, formatter, false);
    }

    @Override
    public void setParentView(@NonNull final View parent) {
        super.setParentView(parent);
        // do not keep a strong reference to the watcher
        requireView().addTextChangedListener(new DecimalTextWatcher(requireView()));
    }

    @Override
    boolean isEmpty(@Nullable final Number value) {
        return value == null || value.doubleValue() == 0.0d;
    }

    /**
     * TextWatcher for TextView fields. Sets the Field value after each change.
     */
    private static class DecimalTextWatcher
            implements ExtTextWatcher {

        private static final String DIGITS = "0123456789";
        @NonNull
        private final String decimalSeparator;

        /**
         * Strong reference to View is fine.
         * This watcher will get destroyed when the View gets destroyed.
         * <strong>Note:</strong> do NOT keep a strong reference to the watcher itself!
         */
        @NonNull
        private final EditText editText;

        DecimalTextWatcher(@NonNull final EditText view) {
            editText = view;
            final Context context = editText.getContext();
            final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
            final DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(userLocale);
            final DecimalFormatSymbols symbols = nf.getDecimalFormatSymbols();
            decimalSeparator = Character.toString(symbols.getDecimalSeparator());

            enableListener(editText.getText());
        }

        @Override
        public void afterTextChanged(@NonNull final Editable editable) {
            // we're not going to change the Editable, no need to toggle this listener
            enableListener(editable);
        }

        private void enableListener(@Nullable final Editable editable) {
            // allow only one decimal separator
            if (editable != null && editable.toString().contains(decimalSeparator)) {
                editText.setKeyListener(DigitsKeyListener.getInstance(DIGITS));
            } else {
                editText.setKeyListener(DigitsKeyListener.getInstance(DIGITS + decimalSeparator));
            }
        }

        //        public static class DecimalDigitsInputFilter
        //                implements InputFilter {
        //
        //            Pattern pattern;
        //
        //            public DecimalDigitsInputFilter(@NonNull final Context context,
        //                                            final int digitsBeforeZero,
        //                                            final int digitsAfterZero) {
        //                DecimalFormatSymbols d =
        //                        new DecimalFormatSymbols(AppLocale.getUserLocale(context));
        //                String s = "\\\\" + d.getDecimalSeparator();
        //                pattern = Pattern.compile(
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
        //                Matcher matcher = pattern.matcher(dest);
        //                if (!matcher.matches()) {
        //                    return "";
        //                }
        //                return null;
        //            }
        //        }
    }
}
