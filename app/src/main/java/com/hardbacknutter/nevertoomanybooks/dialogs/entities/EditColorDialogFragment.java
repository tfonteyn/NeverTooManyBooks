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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.R;

public class EditColorDialogFragment
        extends EditStringBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditColorDialogFragment";

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

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_edit_color, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //noinspection ConstantConditions
        init(BookChangedListener.COLOR, mDb.getColors());
    }

    @Override
    @Nullable
    Bundle onSave() {
        //noinspection ConstantConditions
        mDb.updateColor(mOriginalText, mCurrentText);
        return null;
    }
}
