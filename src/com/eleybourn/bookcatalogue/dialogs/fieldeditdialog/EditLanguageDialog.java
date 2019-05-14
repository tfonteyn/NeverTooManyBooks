/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.dialogs.fieldeditdialog;

import android.app.Activity;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.Locale;

import com.eleybourn.bookcatalogue.BookChangedListener;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

/**
 * Edit the language field.
 * <p>
 * Will hardly ever be needed now that we have ISO3 code.
 * However, if a language was misspelled, auto-translation will fail, and manual edit *will*
 * be needed.
 */
public class EditLanguageDialog
        extends EditStringBaseDialog {

    public EditLanguageDialog(@NonNull final Activity activity,
                              @NonNull final DAO db,
                              @NonNull final BookChangedListener listener) {
        super(activity, db, listener);
    }

    @CallSuper
    public void edit(@NonNull final String lang) {
        String editLang;
        if (lang.length() > 3) {
            editLang = lang;
        } else {
            Locale loc = new Locale(lang);
            editLang = loc.getDisplayLanguage();
        }
        super.edit(editLang, R.layout.dialog_edit_language, R.string.lbl_language);
    }

    @Override
    protected void saveChanges(@NonNull final String from,
                               @NonNull final String to) {
        mDb.updateLanguage(from, LocaleUtils.getISO3Language(to));
        if (mListener != null) {
            mListener.onBookChanged(0, BookChangedListener.LANGUAGE, null);
        }
    }
}
