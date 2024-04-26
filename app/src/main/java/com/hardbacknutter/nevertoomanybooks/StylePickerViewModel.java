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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.SuperscriptSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

@SuppressWarnings("WeakerAccess")
public class StylePickerViewModel
        extends ViewModel {

    /** The list of styles to display; as loaded from the database. */
    private final List<Style> styleList = new ArrayList<>();
    /** FragmentResultListener request key to use for our response. */
    private String requestKey;
    /** Show all styles, or only the preferred styles. */
    private boolean showAllStyles;
    /** Currently selected style. */
    @Nullable
    private Style currentStyle;

    private SpannableString builtinLabelSuffix;

    /**
     * Pseudo constructor.
     *
     * @param args Bundle with arguments
     */
    void init(@NonNull final Bundle args) {
        if (requestKey == null) {
            requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                                DialogLauncher.BKEY_REQUEST_KEY);
            // We MUST have a style
            currentStyle = ServiceLocator
                    .getInstance()
                    .getStyles()
                    .getStyle(args.getString(Style.BKEY_UUID))
                    .orElseThrow(() -> new IllegalArgumentException(Style.BKEY_UUID));

            showAllStyles = args.getBoolean(StylePickerLauncher.BKEY_SHOW_ALL_STYLES, false);

            builtinLabelSuffix = new SpannableString("*");
            builtinLabelSuffix.setSpan(new SuperscriptSpan(), 0, 1,
                                       Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        loadStyles();
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    private void loadStyles() {
        final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();

        styleList.clear();
        styleList.addAll(stylesHelper.getStyles(showAllStyles));
        if (!showAllStyles && currentStyle != null) {
            // Make sure the currently selected style is in the list
            // This can be the case where for example the selected style is a builtin
            // while the list is set to show only the preferred styles.
            if (styleList.stream().noneMatch(style -> currentStyle.equals(style))) {
                stylesHelper.getStyle(currentStyle.getUuid()).ifPresent(styleList::add);
            }
        }
    }

    @NonNull
    List<Style> getStyles() {
        return styleList;
    }

    /**
     * Get the label for the Style at the given position in the list.
     *
     * @param context  Current context
     * @param position for the Style
     *
     * @return label
     */
    @NonNull
    public CharSequence getLabel(@NonNull final Context context,
                                 final int position) {
        final Style style = styleList.get(position);
        if (style.isPreferred()) {
            return style.getLabel(context);
        } else {
            //TODO: maybe move style '*' suffix logic to the style itself and use universally?
            return context.getString(R.string.a_b, style.getLabel(context), builtinLabelSuffix);
        }
    }

    /**
     * Check if all styles or just the preferred ones should be shown.
     *
     * @return {@code true} for all styles,
     *         {@code false} for only the preferred styles.
     */
    boolean flipShowAllStyles() {
        showAllStyles = !showAllStyles;

        loadStyles();

        return showAllStyles;
    }

    @NonNull
    Style getCurrentStyle() {
        Objects.requireNonNull(currentStyle, "currentStyle");
        return currentStyle;
    }

    void setCurrentStyle(@NonNull final Style style) {
        this.currentStyle = style;
    }
}
