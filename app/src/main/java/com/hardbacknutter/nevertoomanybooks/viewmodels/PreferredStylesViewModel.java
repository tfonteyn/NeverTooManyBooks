/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;

public class PreferredStylesViewModel
        extends ViewModel {

    /** Database Access. */
    private DAO mDb;

    /** the selected style at onCreate time. */
    private long mInitialStyleId;

    /** Flag set when anything is changed. Includes moving styles up/down, on/off, ... */
    private boolean mIsDirty;

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
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (mDb == null) {
            mDb = new DAO();
            mList = new ArrayList<>(BooklistStyle.Helper.getStyles(mDb, true).values());

            mInitialStyleId = args.getLong(UniqueId.BKEY_STYLE_ID);
            if (mInitialStyleId == 0) {
                throw new UnexpectedValueException(mInitialStyleId);
            }
        }
    }

    /**
     * Check if <strong>anything at all</strong> was changed.
     *
     * @return {@code true} if changes made
     */
    public boolean isDirty() {
        return mIsDirty;
    }

    /**
     * Get the style id that was the selected style when this object was created.
     *
     * @return id
     */
    public long getInitialStyleId() {
        return mInitialStyleId;
    }

    @NonNull
    public ArrayList<BooklistStyle> getList() {
        return mList;
    }

    @Nullable
    public BooklistStyle getBooklistStyle(final long styleId) {

        for (BooklistStyle style : mList) {
            if (style.getId() == styleId) {
                return style;
            }
        }
        return null;
    }

    /**
     * Called after a style has been edited.
     *
     * @param style the (potentially) modified style
     *
     * @return position of the style in the list
     */
    public int handleStyleChange(@NonNull final BooklistStyle style) {
        mIsDirty = true;

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
            // New Style added. Put at top and set as user-preferred
            mList.add(0, style);
            style.setPreferred(true);
            editedRow = 0;

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
            style.save(mDb);
        }

        return editedRow;
    }

    public void deleteStyle(@NonNull final Context context,
                            @NonNull final BooklistStyle style) {
        mIsDirty = true;
        style.delete(context, mDb);
        mList.remove(style);
    }

    public void saveMenuOrder() {
        mIsDirty = true;
        BooklistStyle.Helper.saveMenuOrder(mList);
    }

    public void purgeBLNS(final long id) {
        mDb.purgeNodeStatesByStyle(id);
    }
}
