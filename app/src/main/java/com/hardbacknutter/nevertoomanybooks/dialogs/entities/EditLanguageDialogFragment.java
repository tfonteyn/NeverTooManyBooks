/*
 * @Copyright 2018-2021 HardBackNutter
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
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DaoLocator;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

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
        super(R.string.lbl_language, R.string.lbl_language,
              BooksOnBookshelf.RowChangeListener.LANGUAGE);
    }

    /**
     * Launch the dialog.
     *
     * @param text to edit.
     */
    public static void launch(@NonNull final FragmentActivity activity,
                              @NonNull final String text) {
        final String editLang;
        if (text.length() > 3) {
            editLang = text;
        } else {
            final Locale locale = AppLocale.getInstance().getLocale(activity, text);
            if (locale == null) {
                editLang = text;
            } else {
                editLang = locale.getDisplayLanguage();
            }
        }

        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, BooksOnBookshelf.RowChangeListener.REQUEST_KEY);
        args.putString(BKEY_TEXT, editLang);

        final DialogFragment frag = new EditLanguageDialogFragment();
        frag.setArguments(args);
        frag.show(activity.getSupportFragmentManager(), TAG);
    }

    @NonNull
    @Override
    protected List<String> getList() {
        //noinspection ConstantConditions
        return DaoLocator.getInstance().getLanguageDao().getNameList(getContext());
    }

    @Override
    void onSave(@NonNull final String originalText,
                @NonNull final String currentText) {
        //noinspection ConstantConditions
        final Locale userLocale = AppLocale.getInstance().getUserLocale(getContext());
        final String iso = Languages.getInstance()
                                    .getISO3FromDisplayName(getContext(), userLocale, currentText);

        DaoLocator.getInstance().getLanguageDao().rename(originalText, iso);
    }
}
