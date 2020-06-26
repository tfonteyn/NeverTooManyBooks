/*
 * @Copyright 2020 HardBackNutter
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
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

public class PreferredStylesViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "PreferredStylesViewMode";

    /** Database Access. */
    private DAO mDb;

    /** the selected style at onCreate time. */
    private String mInitialStyleUuid;

    /** Flag set when anything is changed. Includes moving styles up/down, on/off, ... */
    private boolean mIsDirty;

    /** The *in-memory* list of styles. */
    private ArrayList<BooklistStyle> mList;

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        if (mDb == null) {
            mDb = new DAO(TAG);
            mList = new ArrayList<>(BooklistStyle.getStyles(context, mDb, true).values());

            mInitialStyleUuid = args.getString(BooklistStyle.BKEY_STYLE_UUID);
            Objects.requireNonNull(mInitialStyleUuid, ErrorMsg.NULL_STYLE);
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
     * Get the style UUID that was the selected style when this object was created.
     *
     * @return UUID
     */
    @NonNull
    public String getInitialStyleUuid() {
        return mInitialStyleUuid;
    }

    @NonNull
    public ArrayList<BooklistStyle> getList() {
        return mList;
    }

    @Nullable
    private BooklistStyle getBooklistStyle(final long styleId) {

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
     * @param context    Current context
     * @param style      the (potentially) modified style
     * @param templateId id of the original style we cloned (different from current)
     *                   or edited (same as current).
     *
     * @return position of the style in the list
     */
    public int onStyleChange(@NonNull final Context context,
                             @NonNull final BooklistStyle style,
                             final long templateId) {
        mIsDirty = true;

        // Always save a new style to the database first!
        if (style.getId() == 0) {
            // this will also update the style in the list of user styles.
            BooklistStyle.StyleDAO.updateOrInsert(mDb, style);
        } else {
            // (only) update the style in the list of user styles.
            BooklistStyle.StyleDAO.update(style);
        }

        // Now (re)organise the list of styles.

        // based on the uuid, find the style in the list.
        // Don't use 'indexOf' (use id instead), as the incoming style object
        // was parcelled along the way, which *might* have changed it.
        int editedRow = -1;
        for (int i = 0; i < mList.size(); i++) {
            if (mList.get(i).getId() == style.getId()) {
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
            final BooklistStyle origStyle = mList.get(editedRow);
            if (!origStyle.equals(style)) {
                if (origStyle.isUserDefined()) {
                    // A clone of an user-defined. Put it directly after the user-defined
                    mList.add(editedRow, style);
                } else {
                    // Working on a clone of a builtin style
                    if (origStyle.isPreferred(context)) {
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

        // check if the style was cloned from a builtin style.
        if (templateId < 0) {
            // We're assuming the user wanted to 'replace' the builtin style,
            // so remove the builtin style from the preferred styles.
            final BooklistStyle templateStyle = getBooklistStyle(templateId);
            if (templateStyle != null) {
                templateStyle.setPreferred(false);
            }
        }

        return editedRow;
    }

    public void deleteStyle(@NonNull final Context context,
                            @NonNull final BooklistStyle style) {
        mIsDirty = true;
        BooklistStyle.StyleDAO.delete(context, mDb, style);
        mList.remove(style);
    }

    public void saveMenuOrder(@NonNull final Context context) {
        mIsDirty = true;
        BooklistStyle.MenuOrder.save(context, mList);
    }

    public void purgeBLNS(final long id) {
        mDb.purgeNodeStatesByStyle(id);
    }
}
