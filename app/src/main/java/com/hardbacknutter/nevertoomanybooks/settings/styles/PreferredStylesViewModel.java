/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleUtils;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;

public class PreferredStylesViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "PreferredStylesVM";
    /** Database Access. */
    private DAO mDb;
    /** the selected style at onCreate time. */
    private String mInitialStyleUuid;
    /** Flag set when anything is changed. Includes moving styles up/down, on/off, ... */
    private boolean mIsDirty;
    /** The *in-memory* list of styles. */
    private ArrayList<ListStyle> mStyleList;

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
    void init(@NonNull final Context context,
              @NonNull final Bundle args) {
        if (mDb == null) {
            mDb = new DAO(context, TAG);
            mStyleList = StyleUtils.getStyles(context, mDb, true);

            mInitialStyleUuid = Objects.requireNonNull(
                    args.getString(ListStyle.BKEY_STYLE_UUID), "mInitialStyleUuid");
        }
    }

    /**
     * Check if <strong>anything at all</strong> was changed.
     *
     * @return {@code true} if changes made
     */
    boolean isDirty() {
        return mIsDirty;
    }

    void setDirty(@SuppressWarnings("SameParameterValue") final boolean isDirty) {
        mIsDirty = isDirty;
    }

    /**
     * Get the style UUID that was the selected style when this object was created.
     *
     * @return UUID
     */
    @NonNull
    String getInitialStyleUuid() {
        return mInitialStyleUuid;
    }

    @NonNull
    ArrayList<ListStyle> getList() {
        return mStyleList;
    }

    /**
     * Called after a style has been edited.
     *
     * @param style        the (potentially) modified style
     * @param templateUuid uuid of the original style we cloned (different from current)
     *                     or edited (same as current).
     *
     * @return position of the style in the list
     */
    int onStyleEdited(@NonNull final ListStyle style,
                      @NonNull final String templateUuid) {
        mIsDirty = true;

        // Now (re)organise the list of styles.

        // based on the uuid, find the style in the list.
        // Don't use 'indexOf' (use id instead), as the incoming style object
        // was parcelled along the way, which *might* have changed it.
        int editedRow = -1;
        for (int i = 0; i < mStyleList.size(); i++) {
            if (mStyleList.get(i).getId() == style.getId()) {
                editedRow = i;
                break;
            }
        }

        if (editedRow < 0) {
            // Not in the list; we're adding a new Style.
            // Put it at the top and set as user-preferred
            mStyleList.add(0, style);
            style.setPreferred(true);
            // save the preferred state
            StyleUtils.update(mDb, style);
            editedRow = 0;

        } else {
            // We edited an existing Style.
            // Check if we edited in-place or cloned a style
            final ListStyle origStyle = mStyleList.get(editedRow);
            if (origStyle.equals(style)) {
                // just a style edited in-place, update the list with the new object
                mStyleList.set(editedRow, style);

            } else {
                // Check the type of the ORIGINAL (i.e. template) style.
                if (origStyle instanceof UserStyle) {
                    // It's a clone of an user-defined style.
                    // Put it directly after the user-defined original
                    mStyleList.add(editedRow, style);

                } else if (origStyle instanceof BuiltinStyle) {
                    // It's a clone of a builtin style
                    if (origStyle.isPreferred()) {
                        // if the original style was a preferred style,
                        // replace the original row with the new one
                        mStyleList.set(editedRow, style);

                        // Make the new one preferred and update it
                        style.setPreferred(true);
                        StyleUtils.update(mDb, style);

                        // And demote the original and update it
                        origStyle.setPreferred(false);
                        StyleUtils.update(mDb, origStyle);

                        mStyleList.add(origStyle);

                    } else {
                        // Put it directly after the original
                        mStyleList.add(editedRow, style);
                    }
                } else {
                    throw new IllegalStateException("Unhandled style: " + style);
                }
            }
        }

        // Not sure if this check is really needed... or already covered above
        // if the style was cloned from a builtin style,
        if (StyleUtils.BuiltinStyles.isBuiltin(templateUuid)) {
            // then we're assuming the user wanted to 'replace' the builtin style,
            // so remove the builtin style from the preferred styles.
            mStyleList.stream()
                      .filter(s -> s.getUuid().equalsIgnoreCase(templateUuid))
                      .findFirst()
                      .ifPresent(s -> {
                          // demote the preferred state and update it
                          s.setPreferred(false);
                          StyleUtils.update(mDb, s);
                      });
        }

        return editedRow;
    }

    @Nullable
    ListStyle getStyle(@NonNull final Context context,
                       @NonNull final String uuid) {
        return StyleUtils.getStyle(context, mDb, uuid);
    }

    /**
     * Update the given style.
     *
     * @param style to update
     */
    void updateStyle(@NonNull final ListStyle style) {
        StyleUtils.update(mDb, style);
    }

    /**
     * Delete the given style.
     *
     * @param context Current context
     * @param style   to delete
     */
    void deleteStyle(@NonNull final Context context,
                     @NonNull final ListStyle style) {
        StyleUtils.delete(context, mDb, style);
        mStyleList.remove(style);
    }

    /**
     * Save the preferred style menu list.
     */
    void updateMenuOrder() {
        StyleUtils.updateMenuOrder(mDb, mStyleList);
    }

    /**
     * User explicitly wants to purge the BLNS for the given style.
     *
     * @param styleId to purge
     */
    void purgeBLNS(final long styleId) {
        mDb.purgeNodeStatesByStyle(styleId);
    }
}
