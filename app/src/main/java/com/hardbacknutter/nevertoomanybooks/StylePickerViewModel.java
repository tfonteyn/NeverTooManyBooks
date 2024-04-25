/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

@SuppressWarnings("WeakerAccess")
public class StylePickerViewModel
        extends ViewModel {

    /** The list of styles to display. */
    private final List<String> styleUuids = new ArrayList<>();
    private final List<String> styleLabels = new ArrayList<>();

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    /** Show all styles, or only the preferred styles. */
    private boolean showAllStyles;
    /** Currently selected style. */
    @Nullable
    private String currentStyleUuid;
    /** All styles as loaded from the database. */
    private List<Style> styleList;

    void init(@NonNull final Context context,
              @NonNull final Bundle args) {
        if (requestKey == null) {
            requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                                DialogLauncher.BKEY_REQUEST_KEY);
            // We MUST have a style
            currentStyleUuid = SanityCheck.requireValue(args.getString(Style.BKEY_UUID),
                                                        Style.BKEY_UUID);
            showAllStyles = args.getBoolean(
                    StylePickerDialogFragment.Launcher.BKEY_SHOW_ALL_STYLES, false);
        }

        loadStyles(context);
    }

    /**
     * Fetch the styles.
     */
    void loadStyles(@NonNull final Context context) {
        final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();

        styleList = stylesHelper.getStyles(showAllStyles);
        if (!showAllStyles && currentStyleUuid != null) {
            // make sure the currently selected style is in the list
            if (styleList
                    .stream()
                    .noneMatch(style -> currentStyleUuid.equalsIgnoreCase(style.getUuid()))) {

                stylesHelper.getStyle(currentStyleUuid)
                            .ifPresent(style -> styleList.add(style));
            }
        }

        styleUuids.clear();
        styleLabels.clear();
        styleList.forEach(style -> {
            styleUuids.add(style.getUuid());
            styleLabels.add(style.getLabel(context));
        });
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    @NonNull
    String getCurrentStyleUuid() {
        Objects.requireNonNull(currentStyleUuid, "currentStyleUuid");
        return currentStyleUuid;
    }

    void setCurrentStyleUuid(@NonNull final String currentStyleUuid) {
        this.currentStyleUuid = currentStyleUuid;
    }

    @NonNull
    List<String> getStyleUuids() {
        return styleUuids;
    }

    @NonNull
    List<String> getStyleLabels() {
        return styleLabels;
    }

    @NonNull
    Style findStyle(@NonNull final String styleUuid) {
        final Style selectedStyle =
                styleList.stream()
                         .filter(style -> styleUuid.equalsIgnoreCase(style.getUuid()))
                         .findFirst()
                         .orElseThrow(() -> new IllegalStateException(styleUuid));

        setCurrentStyleUuid(styleUuid);

        return selectedStyle;
    }

    boolean flipShowAllStyles() {
        showAllStyles = !showAllStyles;
        return showAllStyles;
    }
}
