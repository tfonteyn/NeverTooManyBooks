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

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;

public class EditGenreDialog
        extends EditStringDialog {

    public EditGenreDialog(@NonNull final Activity activity,
                           @NonNull final DBA db,
                           @NonNull final Runnable onChanged) {
        super(activity, db, onChanged);
    }

    @CallSuper
    public void edit(@NonNull final String s) {
        super.edit(s, R.layout.dialog_edit_genre, R.string.title_edit_genre);
    }

    @Override
    protected void confirmEdit(@NonNull final String from,
                               @NonNull final String to) {
        // case sensitive equality
        if (to.equals(from)) {
            return;
        }
        mDb.globalReplaceGenre(from, to);
        mOnChanged.run();
    }
}
