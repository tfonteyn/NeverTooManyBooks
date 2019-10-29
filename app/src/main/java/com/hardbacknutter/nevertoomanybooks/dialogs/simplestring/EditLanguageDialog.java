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
package com.hardbacknutter.nevertoomanybooks.dialogs.simplestring;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Edit the language field.
 * <p>
 * Will hardly ever be needed now that we use ISO code.
 * However, if a language was misspelled, auto-translation will fail, and manual edit *will*
 * be needed.
 */
public class EditLanguageDialog
        extends EditStringBaseDialog {

    /**
     * Constructor.
     *
     * @param context  Current context
     * @param db       Database Access
     * @param listener a BookChangedListener
     */
    public EditLanguageDialog(@NonNull final Context context,
                              @NonNull final DAO db,
                              @NonNull final BookChangedListener listener) {
        super(context, db, listener);
    }

    @CallSuper
    public void edit(@NonNull final String lang) {
        String editLang;
        if (lang.length() > 3) {
            editLang = lang;
        } else {
            Locale locale = LocaleUtils.getLocale(lang);
            if (locale == null) {
                editLang = lang;
            } else {
                editLang = locale.getDisplayLanguage();
            }
        }
        super.edit(editLang, R.layout.dialog_edit_language, R.string.lbl_language);
    }

    @Override
    protected void saveChanges(@NonNull final Context context,
                               @NonNull final String from,
                               @NonNull final String to) {
        mDb.updateLanguage(from, LanguageUtils.getISO3FromDisplayName(context, to));
        sendBookChangedMessage(BookChangedListener.LANGUAGE, null);
    }
}
