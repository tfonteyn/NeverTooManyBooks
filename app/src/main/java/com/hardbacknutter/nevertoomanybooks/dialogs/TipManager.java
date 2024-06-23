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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.method.LinkMovementMethod;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.HtmlFormatter;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Class to manage the display of 'tips' within the application. Each tip dialog has
 * a 'Do not show again' option, that results in an update to the preferences which
 * are checked by this code.
 * <p>
 * Note that tips are displayed as HTML spans. So any special formatting
 * should be done inside a CDATA and use HTML tags.
 */
public final class TipManager {

    private static final String TAG = "TipManager";
    /** Preferences prefix. */
    private static final String PREF_PREFIX = "tips.";
    /** Preferences prefix for all tips. */
    private static final String PREF_TIP = PREF_PREFIX + "tip.";
    private static final TipManager INSTANCE = new TipManager();
    /** Cache for all tips managed by this class. */
    private final SparseArray<Tip> cached = new SparseArray<>();

    private TipManager() {
    }

    /**
     * Retrieve the singleton instance.
     *
     * @return singleton
     */
    @NonNull
    public static TipManager getInstance() {
        return INSTANCE;
    }

    private static void logError(@NonNull final Context context,
                                 @StringRes final int tipId) {
        try {
            final String resourceName = context.getResources().getResourceName(tipId);
            LoggerFactory.getLogger().w(TAG, "Tip not found: " + resourceName);
        } catch (@NonNull final Resources.NotFoundException ignore) {
            // should never get here... flw
        }
    }

    @NonNull
    private Optional<Tip> getTip(@StringRes final int id) {
        Tip tip = cached.get(id);
        if (tip == null) {
            if (id == R.string.tip_booklist_styles_editor) {
                tip = new Tip(id, "booklist_styles_editor");
            } else if (id == R.string.tip_booklist_style_groups) {
                tip = new Tip(id, "booklist_style_groups");
            } else if (id == R.string.tip_booklist_style_defaults) {
                tip = new Tip(id, "booklist_style_defaults");
            } else if (id == R.string.tip_booklist_style_properties) {
                tip = new Tip(id, "booklist_style_properties");
            } else if (id == R.string.tip_autorotate_camera_images) {
                tip = new Tip(id, "autorotate_camera_images");
            } else if (id == R.string.tip_view_only_help) {
                tip = new Tip(id, "view_only_help");
            } else if (id == R.string.tip_book_list) {
                tip = new Tip(id, "book_list");
            } else if (id == R.string.tip_book_search_by_text) {
                tip = new Tip(id, "book_search_by_text");
            } else if (id == R.string.tip_update_fields_from_internet) {
                tip = new Tip(id, "update_fields_from_internet");
            } else if (id == R.string.tip_configure_sites) {
                tip = new Tip(id, "configure_sites");
            } else if (id == R.string.tip_authors_works) {
                tip = new Tip(id, "authors_works")
                        .setLayoutId(R.layout.dialog_tip_author_works);

            } else if (id == R.string.tip_authors_book_may_appear_more_than_once) {
                tip = new Tip(id, "authors_book_may_appear_more_than_once");
            } else if (id == R.string.tip_series_book_may_appear_more_than_once) {
                tip = new Tip(id, "series_book_may_appear_more_than_once");

            } else if (id == R.string.tip_import_isbn_list) {
                tip = new Tip(id, "import_isbn_list");

            } else {
                return Optional.empty();
            }
            cached.put(id, tip);
        }
        return Optional.of(tip);
    }

    /**
     * Reset all tips so that they will be displayed again.
     *
     * @param context Current context
     */
    public void reset(@NonNull final Context context) {
        // remove all. This has the benefit of removing any obsolete keys.
        reset(context, PREF_TIP);
        cached.clear();
    }

    /**
     * Reset a sub set of tips, all starting (in preferences) with the given prefix.
     *
     * @param context Current context
     * @param prefix  to match
     */
    public void reset(@NonNull final Context context,
                      @NonNull final String prefix) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final SharedPreferences.Editor ed = prefs.edit();
        for (final String key : prefs.getAll().keySet()) {
            if (key.toLowerCase(Locale.ENGLISH).startsWith(prefix.toLowerCase(Locale.ENGLISH))) {
                ed.remove(key);
            }
        }
        ed.apply();
    }

    /**
     * Display the passed tip, if the user has not disabled it.
     *
     * @param context Current context
     * @param tipId   the string res id for the tip
     * @param postRun Optional Runnable to run after the tip was dismissed
     *                (or not displayed at all).
     * @param args    Optional arguments for the tip string
     */
    public void display(@NonNull final Context context,
                        @StringRes final int tipId,
                        @Nullable final Runnable postRun,
                        @Nullable final Object... args) {
        getTip(tipId).ifPresentOrElse(
                tip -> tip.display(context, tip.defaultKey, args, postRun),
                () -> logError(context, tipId));
    }

    /**
     * Display the passed tip, if the user has not disabled it.
     *
     * @param context Current context
     * @param tipId   the string res id for the tip
     * @param tipKey  identifier for "from where" we want the tip to be displayed.
     *                This allows two different places in the code use the same tip,
     *                but one place being 'disable the tip' and another 'show'.
     * @param postRun Optional Runnable to run after the tip was dismissed
     *                (or not displayed at all).
     * @param args    Optional arguments for the tip string
     */
    public void display(@NonNull final Context context,
                        @NonNull final String tipKey,
                        @StringRes final int tipId,
                        @Nullable final Runnable postRun,
                        @Nullable final Object... args) {
        getTip(tipId).ifPresentOrElse(
                tip -> tip.display(context, tipKey, args, postRun),
                () -> logError(context, tipId));
    }

    private static final class Tip {

        @StringRes
        private final int id;
        /** Preferences key suffix specific to this tip. */
        @NonNull
        private final String defaultKey;

        /** Layout for this Tip. */
        @LayoutRes
        private int layoutId;

        /** Indicates that this tip was displayed already in this instance of the app. */
        private boolean previouslyDisplayed;

        /**
         * Constructor.
         *
         * @param id         string resource to display
         * @param defaultKey the default key suffix to flag this tip as 'shown' in the preferences
         */
        Tip(@StringRes final int id,
            @NonNull final String defaultKey) {
            this.id = id;
            this.defaultKey = defaultKey;
            layoutId = R.layout.dialog_tip;
        }

        /**
         * Using the specified layout instead of the default.
         *
         * @param layoutId to use
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        Tip setLayoutId(@SuppressWarnings("SameParameterValue") @LayoutRes final int layoutId) {
            this.layoutId = layoutId;
            return this;
        }

        /**
         * Display the tip if not shown before and the user has not disabled it.
         *
         * @param context Current context
         * @param key     Preferences key suffix specific to this tip
         * @param args    for the message
         * @param postRun Runnable to start afterwards
         */
        void display(@NonNull final Context context,
                     @NonNull final String key,
                     @Nullable final Object[] args,
                     @Nullable final Runnable postRun) {
            if (!previouslyDisplayed
                && PreferenceManager.getDefaultSharedPreferences(context)
                                    .getBoolean(PREF_TIP + key, true)) {

                final String text = context.getString(id, args);
                display(context, key, text, postRun);
            } else {
                if (postRun != null) {
                    postRun.run();
                }
            }
        }

        /**
         * Unconditionally display the tip.
         *
         * @param context Current context
         * @param key     Preferences key suffix specific to this tip
         * @param text    to display
         * @param postRun Runnable to start afterwards
         */
        private void display(@NonNull final Context context,
                             @NonNull final String key,
                             @NonNull final String text,
                             @Nullable final Runnable postRun) {

            // Build the tip dialog
            final View root = LayoutInflater.from(context).inflate(layoutId, null);

            // Setup the message; this is an optional View but present in the default layout.
            final TextView messageView = root.findViewById(R.id.content);
            if (messageView != null) {
                // allow links, start a browser (or whatever)
                messageView.setText(HtmlFormatter.linkify(text));
                messageView.setMovementMethod(LinkMovementMethod.getInstance());
            }

            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.info_24px)
                    .setView(root)
                    .setTitle(R.string.tip_dialog_title)
                    .setNeutralButton(R.string.action_disable_message, (d, w) -> {
                        PreferenceManager.getDefaultSharedPreferences(context)
                                         .edit().putBoolean(PREF_TIP + key, false).apply();
                        if (postRun != null) {
                            postRun.run();
                        }
                    })
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        if (postRun != null) {
                            postRun.run();
                        }
                    })
                    .create()
                    .show();

            previouslyDisplayed = true;
        }
    }
}
