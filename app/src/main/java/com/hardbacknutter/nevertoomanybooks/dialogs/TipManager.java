/*
 * @Copyright 2019 HardBackNutter
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
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.LinkifyUtils;

/**
 * Original 'hints' renamed to 'tips' to avoid confusion with "android:hint".
 * This is only in code. The texts shown to the user have not changed.
 * <p>
 * Class to manage the display of 'tips' within the application. Each tip dialog has
 * a 'Do not show again' option, that results in an update to the preferences which
 * are checked by this code.
 * <p>
 * To add a new tip, create a string resource and add it to ALL.
 * To display the tip, simply call {@link #display}.
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

    static {
        ALL.put(R.string.tip_booklist_style_menu,
                new Tip("booklist_style_menu"));
        ALL.put(R.string.tip_booklist_styles_editor,
                new Tip("booklist_styles_editor"));
        ALL.put(R.string.tip_booklist_style_groups,
                new Tip("booklist_style_groups"));
        ALL.put(R.string.tip_booklist_style_properties,
                new Tip("booklist_style_properties"));
        // keep, might need again if re-implemented
        //ALL.put(R.string.tip_booklist_global_properties,
        //         new Tip("booklist_global_properties"));

        ALL.put(R.string.tip_authors_book_may_appear_more_than_once,
                new Tip("authors_book_may_appear_more_than_once"));
        ALL.put(R.string.tip_series_book_may_appear_more_than_once,
                new Tip("series_book_may_appear_more_than_once"));

        ALL.put(R.string.tip_background_tasks,
                new Tip("background_tasks"));
        ALL.put(R.string.tip_background_task_events,
                new Tip("background_task_events"));

        ALL.put(R.string.gr_info_no_isbn,
                new Tip("gr_explain_no_isbn"));
        ALL.put(R.string.gr_info_no_match,
                new Tip("gr_explain_no_match"));

        ALL.put(R.string.tip_autorotate_camera_images,
                new Tip("autorotate_camera_images"));
        ALL.put(R.string.tip_view_only_help,
                new Tip("view_only_help"));
        ALL.put(R.string.tip_book_list,
                new Tip("book_list"));
        ALL.put(R.string.tip_book_search_by_text,
                new Tip("book_search_by_text"));
        ALL.put(R.string.pt_thumbnail_cropper_layer_type_summary,
                new Tip("thumbnail_cropper_layer_type_summary"));
        ALL.put(R.string.tip_update_fields_from_internet,
                new Tip("update_fields_from_internet"));
        ALL.put(R.string.tip_authors_works,
                new Tip("authors_works", R.layout.dialog_tip_author_works));
    }

    private TipManager() {
    }

    /**
     * Reset all tips to that they will be displayed again.
     *
     * @param context Current context
     */
    public static void reset(@NonNull final Context context) {
        // remove all. This has the benefit of removing any obsolete keys.
        reset(context, PREF_TIP);

        for (int t = 0; t < ALL.size(); t++) {
            ALL.valueAt(t).mHasBeenDisplayed = false;
        }
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
        Locale locale = App.getSystemLocale();
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
     * @param context  Current context
     * @param stringId identifier for "from where" we want the tip to be displayed.
     *                 This allows two different places in the code use the same tip,
     *                 but one place being 'disable the tip' and another 'show'.
     * @param postRun  Optional Runnable to run after the tip was dismissed
     *                 (or not displayed at all).
     * @param args     Optional arguments for the tip string
     */
    public static void display(@NonNull final Context context,
                               @StringRes final int stringId,
                               @Nullable final Runnable postRun,
                               @Nullable final Object... args) {
        // Get the tip and return if it has been disabled.
        final Tip tip = ALL.get(stringId);
        if (tip == null) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "display|stringId=" + stringId);
            }
            return;
        }
        if (!tip.shouldBeShown(context)) {
            if (postRun != null) {
                postRun.run();
            }
            return;
        }
        tip.display(context, stringId, args, postRun);
    }

    public interface TipOwner {

        @StringRes
        int getTip();
    }

    /**
     * Class to represent a single Tip.
     */
    private static final class Tip {

        /** Preferences key suffix specific to this tip. */
        @NonNull
        private final String mKey;
        /** Layout for this Tip. */
        private final int mLayoutId;

        /** Indicates that this tip was displayed already in this instance of the app. */
        private boolean mHasBeenDisplayed;

        /**
         * Constructor.
         *
         * @param key Preferences key suffix specific to this tip
         */
        private Tip(@NonNull final String key) {
            mKey = PREF_TIP + key;
            mLayoutId = R.layout.dialog_tip;
        }

        /**
         * Constructor. Using the specified layout instead of a standard string.
         *
         * @param key      Preferences key suffix specific to this tip
         * @param layoutId to use
         */
        @SuppressWarnings("SameParameterValue")
        private Tip(@NonNull final String key,
                    @LayoutRes final int layoutId) {
            mKey = PREF_TIP + key;
            mLayoutId = layoutId;
        }

        /**
         * Check if this tip should be shown.
         *
         * @param context Current context
         *
         * @return {@code true} if this Tip should be displayed
         */
        private boolean shouldBeShown(@NonNull final Context context) {
            return !mHasBeenDisplayed && PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getBoolean(mKey, true);
        }

        /**
         * display the tip.
         *
         * @param context  Current context
         * @param stringId for the message
         * @param args     for the message
         * @param postRun  Runnable to start afterwards
         */
        void display(@NonNull final Context context,
                     @StringRes final int stringId,
                     @Nullable final Object[] args,
                     @Nullable final Runnable postRun) {

            // Build the tip dialog
            final View root = LayoutInflater.from(context).inflate(mLayoutId, null);

            // Setup the message; this is optional
            final TextView messageView = root.findViewById(R.id.content);
            if (messageView != null) {
                String tipText = context.getString(stringId, args);
                // allow links, start a browser (or whatever)
                messageView.setText(LinkifyUtils.fromHtml(tipText));
                messageView.setMovementMethod(LinkMovementMethod.getInstance());
            }

            new AlertDialog.Builder(context)
                    .setView(root)
                    .setTitle(R.string.tip_dialog_title)
                    .setNeutralButton(R.string.btn_disable_message, (dialog, which) -> {
                        PreferenceManager.getDefaultSharedPreferences(context)
                                         .edit().putBoolean(mKey, false).apply();
                        if (postRun != null) {
                            postRun.run();
                        }
                    })
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
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
