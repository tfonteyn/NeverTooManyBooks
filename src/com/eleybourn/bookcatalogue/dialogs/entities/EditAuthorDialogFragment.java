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

package com.eleybourn.bookcatalogue.dialogs.entities;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookChangedListener;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.EditAuthorListActivity;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;

/**
 * Dialog to edit an existing single author.
 * <p>
 * Calling point is a List; see {@link EditAuthorListActivity} for book
 */
public class EditAuthorDialogFragment
        extends EditAuthorBaseDialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = EditAuthorDialogFragment.class.getSimpleName();

    /**
     * Constructor.
     *
     * @param author to edit.
     */
    public static EditAuthorDialogFragment newInstance(@NonNull final Author author) {
        EditAuthorDialogFragment frag = new EditAuthorDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(DBDefinitions.KEY_AUTHOR, author);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Handle the edits.
     *
     * @param author        the original data.
     * @param newAuthorData a holder for the edited data.
     */
    @Override
    protected void confirmChanges(@NonNull final Author author,
                                  @NonNull final Author newAuthorData) {
        author.copyFrom(newAuthorData);
        mDb.updateOrInsertAuthor(author);

        Bundle data = new Bundle();
        data.putLong(DBDefinitions.KEY_AUTHOR, author.getId());
        if (mBookChangedListener.get() != null) {
            mBookChangedListener.get().onBookChanged(0, BookChangedListener.AUTHOR, data);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onBookChanged",
                             "WeakReference to listener was dead");
            }
        }
    }
}
