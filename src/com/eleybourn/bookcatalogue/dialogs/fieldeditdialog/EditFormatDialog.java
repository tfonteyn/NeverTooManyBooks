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
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;

public class EditFormatDialog extends EditStringDialog {
    public EditFormatDialog(final @NonNull Activity activity,
                            final @NonNull CatalogueDBAdapter db,
                            final @NonNull Runnable onChanged) {
        super(activity, db, android.R.layout.simple_dropdown_item_1line, db.getFormats(), onChanged);
    }

    @CallSuper
    public void edit(final @NonNull String currentText) {
        super.edit(currentText, R.layout.dialog_edit_format, R.string.title_edit_format);
    }

    @Override
    protected void confirmEdit(final @NonNull String from, final @NonNull String to) {
        // case sensitive equality
        if (from.equals(to)) {
            return;
        }
        mDb.globalReplaceFormat(from, to);
        mOnChanged.run();
    }
}
