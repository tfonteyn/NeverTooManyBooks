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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.method.LinkMovementMethod;
import android.util.Log;
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

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.utils.LinkifyUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Original 'hints' renamed to 'tips' to avoid confusion with "android:hint".
 * This is only in code. The texts shown to the user have not changed.
 * <p>
 * Class to manage the display of 'tips' within the application. Each tip dialog has
 * a 'Do not show again' option, that results in an update to the preferences which
 * are checked by this code.
 * <p>
 * Note that tips are displayed as HTML spans. So any special formatting
 * should be done inside a CDATA and use HTML tags.
 */
public final class TipManager {

    /** Log tag. */
    private static final String TAG = "TipManager";

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "tips.";
    /** Preferences prefix for all tips. */
    private static final String PREF_TIP = PREF_PREFIX + "tip.";

    /** All tips managed by this class. */
    private static final SparseArray<Tip> ALL = new SparseArray<>();

    private TipManager() {
    }

    private static Tip getTip(@StringRes final int id) {
        Tip tip = ALL.get(id);
        if (tip == null) {
            switch (id) {
                case R.string.tip_booklist_style_menu:
                    tip = new Tip(id, "booklist_style_menu");
                    break;
                case R.string.tip_booklist_styles_editor:
                    tip = new Tip(id, "booklist_styles_editor");
                    break;
                case R.string.tip_booklist_style_groups:
                    tip = new Tip(id, "booklist_style_groups");
                    break;
                case R.string.tip_booklist_style_properties:
                    tip = new Tip(id, "booklist_style_properties");
                    break;
                case R.string.tip_authors_book_may_appear_more_than_once:
                    tip = new Tip(id, "authors_book_may_appear_more_than_once");
                    break;
                case R.string.tip_series_book_may_appear_more_than_once:
                    tip = new Tip(id, "series_book_may_appear_more_than_once");
                    break;
                case R.string.tip_background_tasks:
                    tip = new Tip(id, "background_tasks");
                    break;
                case R.string.tip_background_task_events:
                    tip = new Tip(id, "background_task_events");
                    break;

                case R.string.gr_info_no_isbn:
                    tip = new Tip(id, "gr_explain_no_isbn");
                    break;
                case R.string.gr_info_no_match:
                    tip = new Tip(id, "gr_explain_no_match");
                    break;
                case R.string.tip_autorotate_camera_images:
                    tip = new Tip(id, "autorotate_camera_images");
                    break;
                case R.string.tip_view_only_help:
                    tip = new Tip(id, "view_only_help");
                    break;
                case R.string.tip_book_list:
                    tip = new Tip(id, "book_list");
                    break;

                case R.string.tip_book_search_by_text:
                    tip = new Tip(id, "book_search_by_text");
                    break;
                case R.string.pt_cropper_layer_type_summary:
                    tip = new Tip(id, "thumbnail_cropper_layer_type_summary");
                    break;
                case R.string.tip_update_fields_from_internet:
                    tip = new Tip(id, "update_fields_from_internet");
                    break;
                case R.string.tip_authors_works:
                    tip = new Tip(id, "authors_works")
                            .setLayoutId(R.layout.dialog_tip_author_works);
                    break;
//                case R.string.tip_booklist_global_properties:
//                    // keep, might need again if re-implemented
//                    tip = new Tip("booklist_global_properties");
//                    break;
                default:
                    throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + id);
            }
            ALL.put(id, tip);
        }
        return tip;
    }

    /**
     * Reset all tips so that they will be displayed again.
     *
     * @param context Current context
     */
    public static void reset(@NonNull final Context context) {
        // remove all. This has the benefit of removing any obsolete keys.
        reset(context, PREF_TIP);
        ALL.clear();
    }

    /**
     * Reset a sub set of tips, all starting (in preferences) with the given prefix.
     *
     * @param context Current context
     * @param prefix  to match
     */
    public static void reset(@NonNull final Context context,
                             @NonNull final String prefix) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor ed = prefs.edit();
        Locale locale = LocaleUtils.getSystemLocale();
        for (String key : prefs.getAll().keySet()) {
            if (key.toLowerCase(locale).startsWith(prefix.toLowerCase(locale))) {
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
    public static void display(@NonNull final Context context,
                               @StringRes final int tipId,
                               @Nullable final Runnable postRun,
                               @Nullable final Object... args) {
        final Tip tip = getTip(tipId);
        if (tip == null) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "display|tipId=" + tipId);
            }
            return;
        }
        if (!tip.shouldBeShown(context)) {
            if (postRun != null) {
                postRun.run();
            }
            return;
        }
        tip.display(context, args, postRun);
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
    public static void display(@NonNull final Context context,
                               @NonNull final String tipKey,
                               @StringRes final int tipId,
                               @Nullable final Runnable postRun,
                               @Nullable final Object... args) {
        final Tip tip = getTip(tipId);
        if (tip == null) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "display|tipId=" + tipId + ", tipKey=" + tipKey);
            }
            return;
        }
        if (!tip.shouldBeShown(context, tipKey)) {
            if (postRun != null) {
                postRun.run();
            }
            return;
        }
        tip.display(context, tipKey, args, postRun);
    }

    public interface TipOwner {

        @StringRes
        int getTip();
    }

    /**
     * Class to represent a single Tip.
     */
    private static final class Tip {

        @StringRes
        private final int mDefaultStringId;
        /** Preferences key suffix specific to this tip. */
        @NonNull
        private final String mDefaultKey;

        /** Layout for this Tip. */
        @LayoutRes
        private int mLayoutId;

        /** Indicates that this tip was displayed already in this instance of the app. */
        private boolean mHasBeenDisplayed;

        /**
         * Constructor.
         *
         * @param id string resource to display
         */
        private Tip(@StringRes final int id,
                    @NonNull final String defaultKey) {
            mDefaultStringId = id;
            mDefaultKey = defaultKey;
            mLayoutId = R.layout.dialog_tip;
        }

        /**
         * Using the specified layout instead of the default.
         *
         * @param layoutId to use
         */
        Tip setLayoutId(@SuppressWarnings("SameParameterValue") @LayoutRes final int layoutId) {
            mLayoutId = layoutId;
            return this;
        }

        /**
         * Check if this tip should be shown.
         *
         * @param context Current context
         *
         * @return {@code true} if this Tip should be displayed
         */
        private boolean shouldBeShown(@NonNull final Context context) {
            return shouldBeShown(context, mDefaultKey);
        }

        /**
         * Check if this tip should be shown.
         *
         * @param context Current context
         * @param key     Preferences key suffix specific to this tip
         *
         * @return {@code true} if this Tip should be displayed
         */
        private boolean shouldBeShown(@NonNull final Context context,
                                      @NonNull final String key) {
            return !mHasBeenDisplayed && PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getBoolean(PREF_TIP + key, true);
        }

        /**
         * Display the tip.
         *
         * @param context Current context
         * @param args    for the message
         * @param postRun Runnable to start afterwards
         */
        void display(@NonNull final Context context,
                     @Nullable final Object[] args,
                     @Nullable final Runnable postRun) {
            display(context, mDefaultKey, mDefaultStringId, args, postRun);
        }

        /**
         * Display the tip.
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
            display(context, key, mDefaultStringId, args, postRun);
        }

        /**
         * Display the tip.
         *
         * @param context  Current context
         * @param key      Preferences key suffix specific to this tip
         * @param stringId for the message
         * @param args     for the message
         * @param postRun  Runnable to start afterwards
         */
        void display(@NonNull final Context context,
                     @NonNull final String key,
                     @StringRes final int stringId,
                     @Nullable final Object[] args,
                     @Nullable final Runnable postRun) {

            // Build the tip dialog
            final View root = LayoutInflater.from(context).inflate(mLayoutId, null);

            // Setup the message; this is an optional View but present in the default layout.
            final TextView messageView = root.findViewById(R.id.content);
            if (messageView != null) {
                String tipText = context.getString(stringId, args);
                // allow links, start a browser (or whatever)
                messageView.setText(LinkifyUtils.fromHtml(tipText));
                messageView.setMovementMethod(LinkMovementMethod.getInstance());
            }

            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_info)
                    .setView(root)
                    .setTitle(R.string.tip_dialog_title)
                    .setNeutralButton(R.string.btn_disable_message, (d, w) -> {
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

            mHasBeenDisplayed = true;
        }
    }
}
