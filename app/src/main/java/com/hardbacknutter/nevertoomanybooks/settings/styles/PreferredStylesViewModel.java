/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.ViewModel;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;

@SuppressWarnings("WeakerAccess")
public class PreferredStylesViewModel
        extends ViewModel {

    /** Styles helper. */
    private StylesHelper stylesHelper;

    @Nullable
    private String initialStyleUuid;
    @Nullable
    private String selectedStyleUuid;

    /** Flag set when anything is changed. */
    private boolean modified;

    /** The *in-memory* list of styles. */
    private List<Style> styleList;

    /**
     * Pseudo constructor.
     *
     * @param styleUuid to lookup
     */
    void init(@NonNull final String styleUuid) {
        if (stylesHelper == null) {
            stylesHelper = ServiceLocator.getInstance().getStyles();
            styleList = stylesHelper.getStyles(true);

            initialStyleUuid = styleUuid;
            selectedStyleUuid = initialStyleUuid;
        }
    }

    /**
     * Check if <strong>anything at all</strong> was changed.
     *
     * @return {@code true} if changes made
     */
    boolean isModified() {
        return modified;
    }

    void setModified(@SuppressWarnings("SameParameterValue") final boolean modified) {
        this.modified = modified;
    }

    /**
     * Get the {@link Style} at the given position in the list.
     *
     * @param position to get
     *
     * @return style
     */
    @NonNull
    Style getStyle(@IntRange(from = 0) final int position) {
        return Objects.requireNonNull(styleList.get(position), () -> String.valueOf(position));
    }

    /**
     * Get the list for the adapter to display.
     *
     * @return list
     */
    @NonNull
    List<Style> getList() {
        return styleList;
    }

    /**
     * Get the currently selected {@link Style}.
     *
     * @return Style uuid
     */
    @Nullable
    String getSelectedUuid() {
        if (selectedStyleUuid != null) {
            return selectedStyleUuid;
        }

        // the user MIGHT have deleted the initial style.
        // We don't track it, except here.
        if (styleList.stream().anyMatch(style -> style.getUuid().equals(initialStyleUuid))) {
            selectedStyleUuid = initialStyleUuid;
            return initialStyleUuid;
        }

        return null;
    }

    @IntRange(from = RecyclerView.NO_POSITION)
    int getSelectedPosition() {
        if (selectedStyleUuid == null) {
            return RecyclerView.NO_POSITION;
        }
        return findPosition(selectedStyleUuid);
    }

    void setSelectedPosition(@IntRange(from = 0) final int position) {
        selectedStyleUuid = styleList.get(position).getUuid();
    }

    /**
     * Find the best candidate position/style and make that one the 'selected',
     * starting from the currently selected row.
     *
     * @param position current position
     *
     * @return the new 'selected' position
     */
    @IntRange(from = 0)
    int findPreferredAndSelectIt(@IntRange(from = 0) final int position) {
        // first try 'above'
        int newPosition = findPreferredPosition(-1);
        if (newPosition == RecyclerView.NO_POSITION) {
            // if none found, try 'below'
            newPosition = findPreferredPosition(+1);
        }

        // if no such row found, use the current row regardless
        if (newPosition == RecyclerView.NO_POSITION) {
            newPosition = position;
        }

        setSelectedPosition(newPosition);
        return newPosition;
    }

    /**
     * Look up and down in the list to find a 'preferred' row
     * based on the given direction, starting from the currently selected row.
     *
     * @param direction must be either {@code -1} or {@code +1}
     *
     * @return the new position, or {@link RecyclerView#NO_POSITION} if none could be found.
     */
    @IntRange(from = RecyclerView.NO_POSITION)
    private int findPreferredPosition(final int direction) {
        int newPosition = getSelectedPosition();
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

    /**
     * Find the position of the given {@link Style} uuid.
     *
     * @param uuid to find
     *
     * @return row/index where found,
     *         or {@link RecyclerView#NO_POSITION} if not found
     */
    @VisibleForTesting
    @IntRange(from = RecyclerView.NO_POSITION)
    int findPosition(@Nullable final String uuid) {
        if (uuid == null) {
            return RecyclerView.NO_POSITION;
        }
        return IntStream.range(0, styleList.size())
                        .filter(i -> styleList.get(i).getUuid().equals(uuid))
                        .findFirst()
                        // not found, we're probably trying to add a new Style.
                        .orElse(RecyclerView.NO_POSITION);
    }

    /**
     * Called after a {@link Style} has been edited.
     * Inserts/finds the position in the list and set the style as 'selected'.
     *
     * @param context      Current context
     * @param uuid         the modified or cloned style
     * @param templateUuid uuid of the original style we
     *                     1. cloned: different from 'uuid'
     *                     or 2. edited: same as 'uuid'.
     */
    void onStyleEdited(@NonNull final Context context,
                       @NonNull final String uuid,
                       @NonNull final String templateUuid) {

        // If the style was added, it will be in the database,
        // but NOT in the list yet. We want to custom-insert it into
        // the list here below.
        stylesHelper.getStyle(uuid).ifPresent(style -> {
            modified = true;

            // Reorganise the list of styles.
            final Style templateStyle = stylesHelper.getStyle(templateUuid).orElseThrow();
            final int templateRow = findPosition(templateUuid);

            if (templateStyle.getType() == Style.Type.User) {
                // The 'style' is either an edit of a user-defined style,
                // or it's a (new) clone of a user-defined style.

                if (templateStyle.getUuid().equals(style.getUuid())) {
                    // Same UUID, it was an edit-in-place.
                    // Replace the old object with the new one.
                    styleList.set(templateRow, style);
                } else {
                    // Different UUID, it's a clone of a user-defined style.
                    // Insert it directly above the user-defined original.
                    styleList.add(templateRow, style);
                }
                // Promote the new style if the original was a preferred style
                // but leave the original as-is
                style.setPreferred(templateStyle.isPreferred());

                //  preserve the "preferred" status
                stylesHelper.update(context, style);

            } else if (templateStyle.getType() == Style.Type.Builtin) {
                // Insert the cloned style directly above the original
                styleList.add(templateRow, style);
                // Promote the new style if the original was a preferred style
                // and demote the original
                style.setPreferred(templateStyle.isPreferred());
                templateStyle.setPreferred(false);

                //  preserve their "preferred" status
                stylesHelper.update(context, style, templateStyle);
            }

            selectedStyleUuid = style.getUuid();

            // store the order NOW
            updateMenuOrder(context);
        });
    }

    /**
     * Save the preferred {@link Style} menu list.
     *
     * @param context Current context
     */
    void updateMenuOrder(@NonNull final Context context) {
        if (modified) {
            stylesHelper.updateMenuOrder(context, styleList);
        }
    }

    /**
     * Update the given {@link Style}.
     *
     * @param context Current context
     * @param style   to update
     */
    void updateStyle(@NonNull final Context context,
                     @NonNull final Style style) {
        stylesHelper.update(context, style);
    }

    /**
     * Delete the given {@link Style}.
     *
     * @param style to delete
     */
    void deleteStyle(@NonNull final Style style) {
        stylesHelper.delete(style);
        styleList.remove(style);
    }

    /**
     * User explicitly wants to purge the node states for the given {@link Style}.
     *
     * @param style to purge
     */
    void purgeNodeStates(@NonNull final Style style) {
        stylesHelper.purgeNodeStates(style);
    }

    @VisibleForTesting
    void refreshList() {
        styleList.clear();
        styleList.addAll(stylesHelper.getStyles(true));
    }
}
