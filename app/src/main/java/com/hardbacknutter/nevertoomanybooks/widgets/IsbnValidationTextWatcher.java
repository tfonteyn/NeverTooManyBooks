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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

/**
 * Makes the start-drawable visible or invisible depending on validity of the ISBN entered.
 * <p>
 * Usage:
 * {@code
 *     IsbnValidationTextWatcher watcher = new IsbnValidationTextWatcher(mTextView, strictIsbn);
 *     mTextView.addTextChangedListener(watcher);
 * }
 */
public class IsbnValidationTextWatcher
        implements TextWatcher {

    @NonNull
    private final TextView mTextView;

    private boolean mStrictIsbn;

    /**
     * Constructor.
     *
     * @param textView   the view to watch
     * @param strictIsbn Flag: {@code true} to strictly allow ISBN codes.
     */
    public IsbnValidationTextWatcher(@NonNull final TextView textView,
                                     final boolean strictIsbn) {
        mTextView = textView;
        mStrictIsbn = strictIsbn;
        // validate text which is already present at this point
        validate(mTextView.getText().toString().trim());
    }

    public void setStrictIsbn(final boolean strictIsbn) {
        mStrictIsbn = strictIsbn;
        // validate text which is already present at this point
        validate(mTextView.getText().toString().trim());
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
        validate(s.toString());
    }

    private void validate(@NonNull final String codeStr) {
        final int len = codeStr.length();
        boolean valid = false;
        if (len == 10 || len == 13
            || (len == 12 && !mStrictIsbn)) {
            final ISBN code = new ISBN(codeStr, mStrictIsbn);
            valid = code.isValid(mStrictIsbn);
        }

        final Drawable[] ds = mTextView.getCompoundDrawablesRelative();
        if (ds[0] != null) {
            ds[0].setAlpha(valid ? 255 : 0);
        }
    }
}
