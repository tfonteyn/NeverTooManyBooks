/*
 * @Copyright 2018-2022 HardBackNutter
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
import java.util.Optional;
import java.util.stream.IntStream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;

@SuppressWarnings("WeakerAccess")
public class PreferredStylesViewModel
        extends ViewModel {

    /** Styles helper. */
    private StylesHelper stylesHelper;

    /** Flag set when anything is changed. Includes moving styles up/down, on/off, ... */
    private boolean dirty;

    /** Currently selected row. */
    private int selectedPosition = RecyclerView.NO_POSITION;

    /** The *in-memory* list of styles. */
    private List<Style> styleList;

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@NonNull final Context context,
              @NonNull final Bundle args) {
        if (stylesHelper == null) {
            stylesHelper = ServiceLocator.getInstance().getStyles();
            styleList = stylesHelper.getStyles(context, true);

            final String uuid = SanityCheck.requireValue(args.getString(Style.BKEY_UUID),
                                                         Style.BKEY_UUID);
            selectedPosition = findSelectedPosition(uuid);
        }
    }

    /**
     * Find the position in the list of the style with the given uuid.
     *
     * @param uuid to find
     *
     * @return position
     */
    private int findSelectedPosition(@NonNull final String uuid) {
        for (int i = 0; i < styleList.size(); i++) {
            final Style style = styleList.get(i);
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
    List<Style> getStyleList() {
        return styleList;
    }

    /**
     * Get the currently selected Style.
     *
     * @return Style, or {@code null} if none selected (which should never happen... flw)
     */
    @Nullable
    Style getSelectedStyle() {
        if (selectedPosition != RecyclerView.NO_POSITION) {
            return styleList.get(selectedPosition);
        }
        return null;
    }

    @NonNull
    Style getStyle(final int position) {
        return Objects.requireNonNull(styleList.get(position), () -> String.valueOf(position));
    }

    @NonNull
    Optional<Style> getStyle(@NonNull final Context context,
                             @NonNull final String uuid) {
        return stylesHelper.getStyle(context, uuid);
    }

    int getSelectedPosition() {
        return selectedPosition;
    }

    void setSelectedPosition(final int position) {
        selectedPosition = position;
    }

    /**
     * Find the best candidate position/style and make that one the 'selected'.
     *
     * @param position current position
     *
     * @return the new 'selected' position
     */
    int findPreferredAndSelect(final int position) {
        // first try 'above'
        selectedPosition = findPreferredPosition(-1);
        if (selectedPosition == RecyclerView.NO_POSITION) {
            // if none found, try 'below'
            selectedPosition = findPreferredPosition(+1);
        }

        // if no such row found, use the current row regardless
        if (selectedPosition == RecyclerView.NO_POSITION) {
            selectedPosition = position;
        }

        return selectedPosition;
    }

    /**
     * Look up and down in the list to find a 'preferred' row.
     *
     * @param direction must be either {@code -1} or {@code +1}
     *
     * @return the new position, or {@link RecyclerView#NO_POSITION} if none could be found.
     */
    private int findPreferredPosition(final int direction) {
        int newPosition = selectedPosition;
        while (true) {
            // move one up or down.
            newPosition = newPosition + direction;

            // breached the upper or lower limit ?
            if (newPosition < 0 || newPosition >= styleList.size()) {
                return RecyclerView.NO_POSITION;
            }

            if (getStyle(newPosition).isPreferred()) {
                return newPosition;
            }
        }
    }

    void onItemMove(final int fromPosition,
                    final int toPosition) {
        if (fromPosition == selectedPosition) {
            // we're moving the selected row.
            selectedPosition = toPosition;

        } else if (toPosition == selectedPosition) {
            if (fromPosition > selectedPosition) {
                // push down
                selectedPosition++;
            } else {
                // push up
                selectedPosition--;
            }
        }
    }

    /**
     * Save the preferred Style menu list.
     */
    void updateMenuOrder() {
        if (dirty) {
            stylesHelper.updateMenuOrder(styleList);
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
    void onStyleEdited(@NonNull final Style style,
                       @NonNull final String templateUuid) {
        dirty = true;

        // Now (re)organise the list of styles.

        // based on the uuid, find the style in the list.
        // Don't use 'indexOf' (use id instead), as the incoming style object
        // was parcelled along the way, which *might* have changed it.
        int editedRow = IntStream.range(0, styleList.size())
                                 .filter(i -> styleList.get(i).getId() == style.getId())
                                 .findFirst()
                                 .orElse(-1);

        if (editedRow < 0) {
            // Not in the list; we're adding a new Style.
            // Put it at the top and set as user-preferred
            styleList.add(0, style);
            style.setPreferred(true);
            // save the preferred state
            stylesHelper.update(style);
            editedRow = 0;

        } else {
            // We edited an existing Style.
            // Check if we edited in-place or cloned a style
            final Style origStyle = styleList.get(editedRow);
            if (origStyle.equals(style)) {
                // just a style edited in-place, update the list with the new object
                styleList.set(editedRow, style);

            } else {
                // Check the type of the ORIGINAL (i.e. template) style.
                if (origStyle.isUserDefined()) {
                    // It's a clone of an user-defined style.
                    // Put it directly after the user-defined original
                    styleList.add(editedRow, style);

                } else {
                    // It's a clone of a builtin style
                    if (origStyle.isPreferred()) {
                        // if the original style was a preferred style,
                        // replace the original row with the new one
                        styleList.set(editedRow, style);

                        // Make the new one preferred and update it
                        style.setPreferred(true);
                        stylesHelper.update(style);

                        // And demote the original and update it
                        origStyle.setPreferred(false);
                        stylesHelper.update(origStyle);

                        styleList.add(origStyle);

                    } else {
                        // Put it directly after the original
                        styleList.add(editedRow, style);
                    }
                }
            }
        }

        // Not sure if this check is really needed... or already covered above
        // if the style was cloned from a builtin style,
        if (BuiltinStyle.isBuiltin(templateUuid)) {
            // then we're assuming the user wanted to 'replace' the builtin style,
            // so remove the builtin style from the preferred styles.
            styleList.stream()
                     .filter(s -> s.getUuid().equalsIgnoreCase(templateUuid))
                     .findFirst()
                     .ifPresent(s -> {
                         // demote the preferred state and update it
                         s.setPreferred(false);
                         stylesHelper.update(s);
                     });
        }

        selectedPosition = editedRow;
    }

    /**
     * Update the given Style.
     *
     * @param style to update
     */
    void updateStyle(@NonNull final Style style) {
        stylesHelper.update(style);
    }

    /**
     * Delete the given Style.
     *
     * @param style to delete
     */
    void deleteStyle(@NonNull final Style style) {
        stylesHelper.delete(style);
        styleList.remove(style);
        selectedPosition = RecyclerView.NO_POSITION;
    }

    /**
     * User explicitly wants to purge the BLNS for the given Style.
     *
     * @param styleId to purge
     */
    void purgeBLNS(final long styleId) {
        stylesHelper.purgeNodeStatesByStyle(styleId);
    }
}
