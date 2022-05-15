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
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Styles;

@SuppressWarnings("WeakerAccess")
public class PreferredStylesViewModel
        extends ViewModel {

    /** Styles manager. */
    private Styles styles;

    /** Flag set when anything is changed. Includes moving styles up/down, on/off, ... */
    private boolean dirty;

    /** Currently selected row. */
    private int mSelectedPosition = RecyclerView.NO_POSITION;

    /** The *in-memory* list of styles. */
    private List<ListStyle> list;

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@NonNull final Context context,
              @NonNull final Bundle args) {
        if (styles == null) {
            styles = ServiceLocator.getInstance().getStyles();
            list = styles.getStyles(context, true);

            final String uuid = Objects.requireNonNull(args.getString(ListStyle.BKEY_UUID),
                                                       ListStyle.BKEY_UUID);
            mSelectedPosition = findSelectedPosition(uuid);
        }
    }

    /**
     * Find the position in the list of the style with the given uuid,
     *
     * @param uuid to find
     *
     * @return position
     */
    private int findSelectedPosition(@NonNull final String uuid) {
        for (int i = 0; i < list.size(); i++) {
            final ListStyle style = list.get(i);
            if (style.getUuid().equals(uuid)) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    /**
     * Check if <strong>anything at all</strong> was changed.
     *
     * @return {@code true} if changes made
     */
    boolean isDirty() {
        return dirty;
    }

    void setDirty(@SuppressWarnings("SameParameterValue") final boolean isDirty) {
        dirty = isDirty;
    }

    @NonNull
    List<ListStyle> getList() {
        return list;
    }

    /**
     * Get the currently selected ListStyle.
     *
     * @return ListStyle, or {@code null} if none selected (which should never happen... flw)
     */
    @Nullable
    ListStyle getSelectedStyle() {
        if (mSelectedPosition != RecyclerView.NO_POSITION) {
            return list.get(mSelectedPosition);
        }
        return null;
    }

    @NonNull
    ListStyle getStyle(final int position) {
        return Objects.requireNonNull(list.get(position), String.valueOf(position));
    }

    @Nullable
    ListStyle getStyle(@NonNull final Context context,
                       @Nullable final String uuid) {
        if (uuid != null && !uuid.isEmpty()) {
            return styles.getStyle(context, uuid);
        } else {
            return null;
        }
    }

    int getSelectedPosition() {
        return mSelectedPosition;
    }

    void setSelectedPosition(final int position) {
        mSelectedPosition = position;
    }

    /**
     * Look up and down in the list to find a 'preferred' row, and set it 'selected'
     *
     * @return the new 'selected' position
     */
    int findPreferredAndSelect(final int position) {
        // first try 'above'
        mSelectedPosition = findPreferredPosition(-1);
        if (mSelectedPosition == RecyclerView.NO_POSITION) {
            // if none found, try 'below'
            mSelectedPosition = findPreferredPosition(+1);
        }

        // if no such row found, use the current row regardless
        if (mSelectedPosition == RecyclerView.NO_POSITION) {
            mSelectedPosition = position;
        }

        return mSelectedPosition;
    }

    /**
     * Look up and down in the list to find a 'preferred' row.
     *
     * @param direction must be either {@code -1} or {@code +1}
     *
     * @return the new position, or {@link RecyclerView#NO_POSITION} if none could be found.
     */
    private int findPreferredPosition(final int direction) {
        int newPosition = mSelectedPosition;
        while (true) {
            // move one up or down.
            newPosition = newPosition + direction;

            // breached the upper or lower limit ?
            if (newPosition < 0 || newPosition >= list.size()) {
                return RecyclerView.NO_POSITION;
            }

            if (getStyle(newPosition).isPreferred()) {
                return newPosition;
            }
        }
    }

    void onItemMove(final int fromPosition,
                    final int toPosition) {
        if (fromPosition == mSelectedPosition) {
            // we're moving the selected row.
            mSelectedPosition = toPosition;

        } else if (toPosition == mSelectedPosition) {
            if (fromPosition > mSelectedPosition) {
                // push down
                mSelectedPosition++;
            } else {
                // push up
                mSelectedPosition--;
            }
        }
    }

    /**
     * Save the preferred ListStyle menu list.
     */
    void updateMenuOrder() {
        if (dirty) {
            styles.updateMenuOrder(list);
        }
    }

    /**
     * Called after a style has been edited.
     * Calculates the new position in the list and sets it as selected.
     *
     * @param style        the modified style
     * @param templateUuid uuid of the original style we cloned (different from current)
     *                     or edited (same as current).
     */
    void onStyleEdited(@NonNull final ListStyle style,
                       @NonNull final String templateUuid) {
        dirty = true;

        // Now (re)organise the list of styles.

        // based on the uuid, find the style in the list.
        // Don't use 'indexOf' (use id instead), as the incoming style object
        // was parcelled along the way, which *might* have changed it.
        int editedRow = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == style.getId()) {
                editedRow = i;
                break;
            }
        }

        if (editedRow < 0) {
            // Not in the list; we're adding a new Style.
            // Put it at the top and set as user-preferred
            list.add(0, style);
            style.setPreferred(true);
            // save the preferred state
            styles.update(style);
            editedRow = 0;

        } else {
            // We edited an existing Style.
            // Check if we edited in-place or cloned a style
            final ListStyle origStyle = list.get(editedRow);
            if (origStyle.equals(style)) {
                // just a style edited in-place, update the list with the new object
                list.set(editedRow, style);

            } else {
                // Check the type of the ORIGINAL (i.e. template) style.
                if (origStyle.isUserDefined()) {
                    // It's a clone of an user-defined style.
                    // Put it directly after the user-defined original
                    list.add(editedRow, style);

                } else {
                    // It's a clone of a builtin style
                    if (origStyle.isPreferred()) {
                        // if the original style was a preferred style,
                        // replace the original row with the new one
                        list.set(editedRow, style);

                        // Make the new one preferred and update it
                        style.setPreferred(true);
                        styles.update(style);

                        // And demote the original and update it
                        origStyle.setPreferred(false);
                        styles.update(origStyle);

                        list.add(origStyle);

                    } else {
                        // Put it directly after the original
                        list.add(editedRow, style);
                    }
                }
            }
        }

        // Not sure if this check is really needed... or already covered above
        // if the style was cloned from a builtin style,
        if (BuiltinStyle.isBuiltin(templateUuid)) {
            // then we're assuming the user wanted to 'replace' the builtin style,
            // so remove the builtin style from the preferred styles.
            list.stream()
                .filter(s -> s.getUuid().equalsIgnoreCase(templateUuid))
                .findFirst()
                .ifPresent(s -> {
                    // demote the preferred state and update it
                    s.setPreferred(false);
                    styles.update(s);
                });
        }

        mSelectedPosition = editedRow;
    }

    /**
     * Update the given ListStyle.
     *
     * @param style to update
     */
    void updateStyle(@NonNull final ListStyle style) {
        styles.update(style);
    }

    /**
     * Delete the given ListStyle.
     *
     * @param style to delete
     */
    void deleteStyle(@NonNull final ListStyle style) {
        styles.delete(style);
        list.remove(style);
        mSelectedPosition = RecyclerView.NO_POSITION;
    }

    /**
     * User explicitly wants to purge the BLNS for the given ListStyle.
     *
     * @param styleId to purge
     */
    void purgeBLNS(final long styleId) {
        styles.purgeNodeStatesByStyle(styleId);
    }
}
