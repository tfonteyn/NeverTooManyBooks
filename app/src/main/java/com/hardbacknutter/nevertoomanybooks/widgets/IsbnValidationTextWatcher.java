/*
 * @Copyright 2019 HardBackNutter
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
 * Usage: {@code  mIsbnView.addTextChangedListener(new IsbnValidationTextWatcher(mIsbnView)); }
 */
public class IsbnValidationTextWatcher
        implements TextWatcher {

    @NonNull
    private final TextView mIsbnView;

    /**
     * Constructor.
     *
     * @param isbnView the view to watch
     */
    public IsbnValidationTextWatcher(@NonNull final TextView isbnView) {
        mIsbnView = isbnView;
        // validate text which is already present at this point
        validate(mIsbnView.getText().toString().trim());
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

    public void validate(@NonNull final String isbn) {
        int len = isbn.length();
        boolean valid = (len == 10 || len == 13) && ISBN.isValid(isbn);
        Drawable[] ds = mIsbnView.getCompoundDrawablesRelative();
        if (ds[0] != null) {
            ds[0].setAlpha(valid ? 255 : 0);
        }
    }
}
