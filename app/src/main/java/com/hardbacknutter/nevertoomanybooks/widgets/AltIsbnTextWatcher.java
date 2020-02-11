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

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

public class AltIsbnTextWatcher
        implements TextWatcher {

    @NonNull
    private final TextView mIsbnView;
    @NonNull
    private final Button mAltIsbnButton;

    @Nullable
    private String mAltIsbn;

    /**
     * Constructor.
     *
     * @param isbnView      the view to watch
     * @param altIsbnButton the view to update/swap with the alternative ISBN number.
     */
    public AltIsbnTextWatcher(@NonNull final TextView isbnView,
                              @NonNull final Button altIsbnButton) {
        mIsbnView = isbnView;
        mAltIsbnButton = altIsbnButton;

        // a click will swap numbers
        mAltIsbnButton.setOnClickListener(view -> mIsbnView.setText(mAltIsbn));
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
        final String isbnStr = s.toString();
        switch (isbnStr.length()) {
            case 13: {
                final ISBN isbn = ISBN.createISBN(isbnStr);
                if (isbn.isIsbn10Compat()) {
                    mAltIsbn = isbn.asText(ISBN.Type.ISBN10);
                    mAltIsbnButton.setEnabled(true);
                    return;
                }
                break;
            }
            case 10: {
                final ISBN isbn = ISBN.createISBN(isbnStr);
                if (isbn.isValid(true)) {
                    mAltIsbn = isbn.asText(ISBN.Type.ISBN13);
                    mAltIsbnButton.setEnabled(true);
                    return;
                }
                break;
            }

            default:
                break;
        }
        mAltIsbnButton.setEnabled(false);
    }
}
