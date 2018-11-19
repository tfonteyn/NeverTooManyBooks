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

package com.eleybourn.bookcatalogue.dialogs.fieldeditdialogs;

import android.app.Activity;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

import java.util.Locale;

/**
 * Edit the language field.
 *
 * FIXME: as we moved to ISO3 codes... how much use is this ?
 */
public class EditLanguageDialog extends EditStringDialog {

    public EditLanguageDialog(final @NonNull Activity activity,
                              final @NonNull CatalogueDBAdapter db,
                              final @NonNull Runnable onChanged) {
        super(activity, db, onChanged);
    }

    @CallSuper
    public void edit(final @NonNull String lang) {
        String editLang;
        if (lang.length() > 3) {
            editLang = lang;
        } else {
            Locale loc = new Locale(lang);
            editLang = loc.getDisplayLanguage();
        }
        super.edit(editLang, R.layout.dialog_edit_language, R.string.title_edit_language);
    }

    @Override
    protected void confirmEdit(final @NonNull String from, final @NonNull String to) {
        // case sensitive equality
        if (to.equals(from)) {
            return;
        }

        mDb.globalReplaceLanguage(from, LocaleUtils.getISO3Language(to));
        mOnChanged.run();
    }
}
