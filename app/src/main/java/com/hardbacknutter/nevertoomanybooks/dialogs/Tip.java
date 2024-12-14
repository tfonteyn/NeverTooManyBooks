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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.HtmlFormatter;

/**
 * Note that tips are displayed as HTML spans. So any special formatting
 * should be done inside a CDATA and use HTML tags.
 */
@SuppressWarnings("NonFinalStaticVariableUsedInClassInitialization")
public enum Tip {
    BOOK_LIST(R.string.tip_book_list,
              "book_list"),
    BOOK_DETAILS(R.string.tip_view_only_help,
                 "view_only_help"),
    AUTHORS_WORKS(R.string.tip_authors_works,
                  "authors_works",
                  R.layout.dialog_tip_author_works),

    BOOK_SEARCH_BY_TEXT(R.string.tip_book_search_by_text,
                        "book_search_by_text"),
    IMPORT_ISBN_LIST(R.string.tip_import_isbn_list,
                     "import_isbn_list"),

    STYLES_EDITOR(R.string.tip_booklist_styles_editor,
                  "booklist_styles_editor"),
    STYLE_GROUPS(R.string.tip_booklist_style_groups,
                 "booklist_style_groups"),
    STYLE_DEFAULTS(R.string.tip_booklist_style_defaults,
                   "booklist_style_defaults"),
    STYLE_PROPERTIES(R.string.tip_booklist_style_properties,
                     "booklist_style_properties"),

    CONFIGURE_SITES(R.string.tip_configure_sites,
                    "configure_sites"),
    CAMERA_AUTOROTATE_IMAGES(R.string.tip_autorotate_camera_images,
                             "autorotate_camera_images"),

    AUTHORS_BOOK_MAY_APPEAR_MORE_THAN_ONCE(R.string.tip_authors_book_may_appear_more_than_once,
                                           "authors_book_may_appear_more_than_once"),
    SERIES_BOOK_MAY_APPEAR_MORE_THAN_ONCE(R.string.tip_series_book_may_appear_more_than_once,
                                          "series_book_may_appear_more_than_once");

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "tips.";
    /** Preferences prefix for all tips. */
    static final String PREF_TIP = PREF_PREFIX + "tip.";
    @StringRes
    private final int msgResId;

    /** Preferences key suffix specific to this tip. */
    @NonNull
    private final String key;

    /** Custom layout for this Tip. Can be used to overrule the TextView default. */
    @LayoutRes
    private final int layoutId;

    Tip(@StringRes final int msgResId,
        @NonNull final String key) {
        this.key = key;
        this.msgResId = msgResId;
        layoutId = 0;
    }

    Tip(@StringRes final int msgResId,
        @NonNull final String key,
        @LayoutRes final int layoutId) {
        this.key = key;
        this.msgResId = msgResId;
        this.layoutId = layoutId;
    }

    boolean isEnabled(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PREF_TIP + key, true);
    }

    /**
     * Create an {@link AlertDialog} based tip.
     *
     * @param context  Current context
     * @param postRun  Runnable to start afterwards
     * @param textArgs for the message
     *
     * @return a dialog ready to be displayed
     */
    @NonNull
    AlertDialog create(@NonNull final Context context,
                       @Nullable final Runnable postRun,
                       @Nullable final Object... textArgs) {
        final View root = inflateLayout(context);
        setMessage(root, context.getString(msgResId, (Object[]) textArgs));

        final AlertDialog alertDialog = new MaterialAlertDialogBuilder(context)
                .setView(root)
                .setOnDismissListener(dialog -> {
                    if (postRun != null) {
                        postRun.run();
                    }
                })
                .create();

        root.findViewById(R.id.btn_positive).setOnClickListener(v -> alertDialog.dismiss());
        root.findViewById(R.id.btn_neutral).setOnClickListener(v -> {
            disable(context);
            alertDialog.dismiss();
        });

        return alertDialog;
    }

    private View inflateLayout(@NonNull final Context context) {
        final LayoutInflater inflater = LayoutInflater.from(context);
        @SuppressLint("InflateParams")
        final View root = inflater.inflate(R.layout.dialog_tip, null, false);
        if (layoutId != 0) {
            final FrameLayout content = root.findViewById(R.id.tip_content);
            content.removeAllViews();
            content.addView(inflater.inflate(layoutId, content, false));
        }
        return root;
    }

    private void setMessage(@NonNull final View root,
                            @Nullable final String text) {
        // Setup the message; this is an optional View but present in the default layout.
        final TextView messageView = root.findViewById(R.id.message);
        if (messageView != null) {
            if (text != null) {
                messageView.setVisibility(View.VISIBLE);
                // allow links, start a browser (or whatever)
                messageView.setText(HtmlFormatter.linkify(text));
                messageView.setMovementMethod(LinkMovementMethod.getInstance());
            } else {
                messageView.setVisibility(View.GONE);
            }
        }
    }

    private void disable(@NonNull final Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit().putBoolean(PREF_TIP + key, false).apply();
    }
}
