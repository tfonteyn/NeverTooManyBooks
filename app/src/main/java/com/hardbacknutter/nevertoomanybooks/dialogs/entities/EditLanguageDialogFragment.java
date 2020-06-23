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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Dialog to edit an <strong>in-line in Books table</strong> Language.
 * <p>
 * Will hardly ever be needed now that we use ISO code.
 * However, if a language was misspelled, auto-translation will fail,
 * and a manual edit *will* be needed.
 */
public class EditLanguageDialogFragment
        extends EditStringBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditLanguageDialogFrag";

    /**
     * No-arg constructor for OS use.
     */
    public EditLanguageDialogFragment() {
        super(R.string.lbl_language, R.string.lbl_language, BookChangedListener.LANGUAGE);
    }

    /**
     * Constructor.
     *
     * @param context Current context
     * @param text    to edit.
     *
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final Context context,
                                             @NonNull final String text) {
        final String editLang;
        if (text.length() > 3) {
            editLang = text;
        } else {
            final Locale locale = LocaleUtils.getLocale(context, text);
            if (locale == null) {
                editLang = text;
            } else {
                editLang = locale.getDisplayLanguage();
            }
        }

        final DialogFragment frag = new EditLanguageDialogFragment();
        final Bundle args = new Bundle(1);
        args.putString(BKEY_TEXT, editLang);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    protected List<String> getList() {
        //noinspection ConstantConditions
        return mDb.getLanguages(getContext());
    }

    @Override
    @Nullable
    Bundle onSave() {
        //noinspection ConstantConditions
        mDb.renameLanguage(mOriginalText,
                           LanguageUtils.getISO3FromDisplayName(getContext(), mCurrentText));
        return null;
    }
}
