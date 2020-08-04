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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Dialog to edit an <strong>in-line in Books table</strong> Color.
 */
public class EditColorDialogFragment
        extends EditStringBaseDialogFragment{

    /** Fragment/Log tag. */
    public static final String TAG = "EditColorDialogFragment";
    public static final String REQUEST_KEY = TAG + ":rk";

    /**
     * No-arg constructor for OS use.
     */
    public EditColorDialogFragment() {
        super(REQUEST_KEY, R.string.lbl_color, R.string.lbl_color, BookChangedListener.COLOR);
    }

    /**
     * Constructor.
     *
     * @param text to edit.
     *
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final String text) {
        final DialogFragment frag = new EditColorDialogFragment();
        final Bundle args = new Bundle(1);
        args.putString(BKEY_TEXT, text);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    protected List<String> getList() {
        //noinspection ConstantConditions
        return mDb.getColors();
    }

    @Override
    @Nullable
    Bundle onSave(@NonNull final String originalText,
                  @NonNull final String currentText) {
        //noinspection ConstantConditions
        mDb.renameColor(originalText, currentText);
        return null;
    }
}
