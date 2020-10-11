/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ChangeListener;
import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Dialog to edit an <strong>in-line in Books table</strong> Genre.
 */
public class EditGenreDialogFragment
        extends EditStringBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditGenreDialogFrag";

    /**
     * No-arg constructor for OS use.
     */
    public EditGenreDialogFragment() {
        super(R.string.lbl_genre, R.string.lbl_genre, ChangeListener.GENRE);
    }

    /**
     * Constructor.
     *
     * @param requestKey for use with the FragmentResultListener
     * @param text       to edit.
     *
     * @return instance
     */
    public static DialogFragment newInstance(@SuppressWarnings("SameParameterValue")
                                             @NonNull final String requestKey,
                                             @NonNull final String text) {
        final DialogFragment frag = new EditGenreDialogFragment();
        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, requestKey);
        args.putString(BKEY_TEXT, text);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    protected List<String> getList() {
        //noinspection ConstantConditions
        return mDb.getGenres();
    }

    @Override
    @Nullable
    Bundle onSave(@NonNull final String originalText,
                  @NonNull final String currentText) {
        //noinspection ConstantConditions
        mDb.updateGenre(originalText, currentText);
        return null;
    }
}
