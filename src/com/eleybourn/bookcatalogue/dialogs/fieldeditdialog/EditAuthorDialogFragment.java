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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.eleybourn.bookcatalogue.BookChangedListener;
import com.eleybourn.bookcatalogue.EditAuthorListActivity;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.entities.Author;

/**
 * Dialog to edit an existing single author.
 * <p>
 * Calling point is a List; see {@link EditAuthorListActivity} for book
 */
public class EditAuthorDialogFragment
        extends EditAuthorBaseDialogFragment {

    /** Fragment manager tag. */
    private static final String TAG = EditAuthorDialogFragment.class.getSimpleName();

    /**
     * (syntax sugar for newInstance)
     */
    public static void show(@NonNull final FragmentManager fm,
                            @NonNull final Author author) {
        if (fm.findFragmentByTag(TAG) == null) {
            newInstance(author).show(fm, TAG);
        }
    }

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

        BookChangedListener.onBookChanged(this, 0, BookChangedListener.AUTHOR, null);
    }
}
