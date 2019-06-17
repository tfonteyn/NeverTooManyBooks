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

package com.eleybourn.bookcatalogue.dialogs.simplestring;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookChangedListener;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;

public class EditLocationDialog
        extends EditStringBaseDialog {

    public EditLocationDialog(@NonNull final Context context,
                              @NonNull final DAO db,
                              @NonNull final BookChangedListener listener) {
        super(context, db, db.getLocations(), listener);
    }

    @CallSuper
    public void edit(@NonNull final String currentText) {
        super.edit(currentText, R.layout.dialog_edit_location, R.string.lbl_location);
    }

    @Override
    protected void saveChanges(@NonNull final String from,
                               @NonNull final String to) {
        mDb.updateLocation(from, to);
        if (mListener != null) {
            mListener.onBookChanged(0, BookChangedListener.LOCATION, null);
        }
    }
}
