/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyles;
import com.hardbacknutter.nevertoomanybooks.database.DAO;

public class PreferredStylesViewModel
        extends ViewModel {

    /** Database Access. */
    private DAO mDb;

    /** The *in-memory* list of styles. */
    private ArrayList<BooklistStyle> mList;

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
            mDb = null;
        }
    }

    /**
     * Pseudo constructor.
     */
    public void init() {
        if (mDb != null) {
            return;
        }
        mDb = new DAO();
        mList = new ArrayList<>(BooklistStyles.getStyles(mDb, true).values());
    }

    @NonNull
    public ArrayList<BooklistStyle> getList() {
        return mList;
    }

    /**
     * Called after a style has been edited.
     */
    public void handleStyleChange(@NonNull final BooklistStyle style) {
        // based on the uuid, find the style in the list.
        // Don't use 'indexOf' though, as the incoming style object was parcelled along the way.
        int editedRow = -1;
        for (int i = 0; i < mList.size(); i++) {
            if (mList.get(i).equals(style)) {
                editedRow = i;
                break;
            }
        }

        if (editedRow < 0) {
            // New Style added. Put at top and set as preferred
            mList.add(0, style);
            style.setPreferred(true);

        } else {
            // Existing Style edited.
            BooklistStyle origStyle = mList.get(editedRow);
            if (!origStyle.equals(style)) {
                if (origStyle.isUserDefined()) {
                    // A clone of an user-defined. Put it directly after the user-defined
                    mList.add(editedRow, style);
                } else {
                    // Working on a clone of a builtin style
                    if (origStyle.isPreferred()) {
                        // Replace the original row with the new one
                        mList.set(editedRow, style);
                        // Make the new one preferred
                        style.setPreferred(true);
                        // And demote the original
                        origStyle.setPreferred(false);
                        mList.add(origStyle);
                    } else {
                        // Try to put it directly after original
                        mList.add(editedRow, style);
                    }
                }
            } else {
                mList.set(editedRow, style);
            }
        }

        // add to the db if the style is a new one.
        if (style.getId() == 0) {
            mDb.insertBooklistStyle(style);
        }
    }

    public void deleteStyle(@NonNull final BooklistStyle style) {
        style.delete(mDb);
        mList.remove(style);
    }

    public void saveMenuOrder() {
        BooklistStyles.savePreferredStyleMenuOrder(mList);
    }
}
