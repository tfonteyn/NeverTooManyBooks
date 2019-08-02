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

package com.hardbacknutter.nevertomanybooks.dialogs.simplestring;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertomanybooks.BookChangedListener;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.database.DAO;

public class EditGenreDialog
        extends EditStringBaseDialog {

    /**
     * Constructor.
     *
     * @param context  Current context
     * @param db       the database
     * @param listener a BookChangedListener
     */
    public EditGenreDialog(@NonNull final Context context,
                           @NonNull final DAO db,
                           @NonNull final BookChangedListener listener) {
        super(context, db, db.getGenres(), listener);
    }

    @CallSuper
    public void edit(@NonNull final String s) {
        super.edit(s, R.layout.dialog_edit_genre, R.string.lbl_genre);
    }

    @Override
    protected void saveChanges(@NonNull final String from,
                               @NonNull final String to) {
        mDb.updateGenre(from, to);
        if (mListener != null) {
            mListener.onBookChanged(0, BookChangedListener.GENRE, null);
        }
    }
}
