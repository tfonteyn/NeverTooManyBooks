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

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

public class AltIsbnTextWatcher
        implements TextWatcher {

    @NonNull
    private final TextView mIsbnView;
    @NonNull
    private final TextView mAltIsbnView;

    /**
     * Constructor.
     *
     * @param isbnView    the view to watch
     * @param altIsbnView the view to update/swap with the alternative ISBN number.
     */
    public AltIsbnTextWatcher(@NonNull final TextView isbnView,
                              @NonNull final TextView altIsbnView) {
        mIsbnView = isbnView;
        mAltIsbnView = altIsbnView;

        // a click on the text view will swap numbers
        mAltIsbnView.setOnClickListener(this::onSwapNumbers);
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
        String isbn1 = s.toString();
        int len = isbn1.length();
        if (len == 10 || len == 13) {
            String altIsbn = ISBN.isbn2isbn(isbn1);
            if (!altIsbn.equals(isbn1)) {
                mAltIsbnView.setText(altIsbn);
                mAltIsbnView.setVisibility(View.VISIBLE);
            }
        } else {
            mAltIsbnView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Swap the content of ISBN and alternative-ISBN texts.
     *
     * @param view that was clicked on
     */
    private void onSwapNumbers(@SuppressWarnings("unused") @NonNull final View view) {
        String isbn = mIsbnView.getText().toString().trim();
        String altIsbn = mAltIsbnView.getText().toString().trim();
        mIsbnView.setText(altIsbn);
        mAltIsbnView.setTag(isbn);
    }
}
