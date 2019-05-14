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

import com.eleybourn.bookcatalogue.BookChangedListener;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;

public class EditFormatDialog
        extends EditStringBaseDialog {

    public EditFormatDialog(@NonNull final Activity activity,
                            @NonNull final DAO db,
                            @NonNull final BookChangedListener listener) {
        super(activity, db, db.getFormats(), listener);
    }

    @CallSuper
    public void edit(@NonNull final String currentText) {
        super.edit(currentText, R.layout.dialog_edit_format, R.string.lbl_format);
    }

    @Override
    protected void saveChanges(@NonNull final String from,
                               @NonNull final String to) {
        mDb.updateFormat(from, to);
        if (mListener != null) {
            mListener.onBookChanged(0, BookChangedListener.FORMAT, null);
        }
    }
}
