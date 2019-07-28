/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.dialogs;

import android.annotation.SuppressLint;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import java.util.HashMap;
import java.util.Map;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Original 'hint' renamed to 'tip' to avoid confusion with "android:hint".
 * This is only in code. The texts shown to the user have not changed.
 * <p>
 * Class to manage the display of 'tips' within the application. Each tip dialog has
 * a 'Do not show again' option, that results in an update to the preferences which
 * are checked by this code.
 * <p>
 * To add a new tip, create a string resource and add it to ALL. Then, to display the
 * tip, simply call {@link #display}.
 * <p>
 * Note that tips are displayed as HTML spans. So any special formatting
 * should be done inside a CDATA and use HTML tags.
 *
 * @author Philip Warner
 */
public final class TipManager {

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "TipManager.";
    /** Preferences prefix for all tips. */
    public static final String PREF_TIP = PREF_PREFIX + "Tip.";

    /** All tips managed by this class. */
    @SuppressLint("UseSparseArrays")
    private static final Map<Integer, Tip> ALL = new HashMap<>();

    static {
        ALL.put(R.string.tip_booklist_style_menu,
                new Tip("booklist_style_menu"));
        ALL.put(R.string.tip_booklist_styles_editor,
                new Tip("BOOKLIST_STYLES_EDITOR"));
        ALL.put(R.string.tip_booklist_style_groups,
                new Tip("BOOKLIST_STYLE_GROUPS"));
        ALL.put(R.string.tip_booklist_style_properties,
                new Tip("BOOKLIST_STYLE_PROPERTIES"));
        // keep, might need again if re-implemented
        //ALL.put(R.string.tip_booklist_global_properties,
        //         new Tip("BOOKLIST_GLOBAL_PROPERTIES"));

        ALL.put(R.string.tip_authors_book_may_appear_more_than_once,
                new Tip("BOOKLIST_MULTI_AUTHORS"));
        ALL.put(R.string.tip_series_book_may_appear_more_than_once,
                new Tip("BOOKLIST_MULTI_SERIES"));

        ALL.put(R.string.tip_background_tasks,
                new Tip("BACKGROUND_TASKS"));
        ALL.put(R.string.tip_background_task_events,
                new Tip("BACKGROUND_TASK_EVENTS"));

        ALL.put(R.string.gr_explain_goodreads_no_isbn,
                new Tip("explain_goodreads_no_isbn"));
        ALL.put(R.string.gr_explain_goodreads_no_match,
                new Tip("explain_goodreads_no_match"));

        ALL.put(R.string.tip_autorotate_camera_images,
                new Tip("autorotate_camera_images"));
        ALL.put(R.string.tip_view_only_book_details,
                new Tip("view_only_book_details"));
        ALL.put(R.string.tip_view_only_help,
                new Tip("view_only_help"));
        ALL.put(R.string.tip_book_list,
                new Tip("book_list"));
        ALL.put(R.string.tip_book_search_by_text,
                new Tip("book_search_by_text"));
        // v200
        ALL.put(R.string.pt_thumbnail_cropper_layer_type_summary,
                new Tip("pref_layer_type"));
        ALL.put(R.string.tip_update_fields_from_internet,
                new Tip("update_fields_from_internet"));
        ALL.put(R.string.tip_authors_works,
                new Tip("authors_works", R.layout.dialog_tip_author_works));
    }

    private TipManager() {
    }

    /** Reset all tips to that they will be displayed again. */
    public static void reset() {
        for (Tip h : ALL.values()) {
            h.reset();
        }
    }

    /**
     * Display the passed tip, if the user has not disabled it.
     *
     * @param inflater Inflater to use
     * @param stringId identifier for "from where" we want the tip to be displayed.
     *                 This allows two different places in the code use the same tip,
     *                 but one place being 'disable the tip' and another 'show'.
     * @param postRun  Optional Runnable to run after the tip was dismissed
     *                 (or not displayed at all).
     * @param args     Optional arguments for the tip string
     */
    public static void display(@NonNull final LayoutInflater inflater,
                               @StringRes final int stringId,
                               @Nullable final Runnable postRun,
                               @Nullable final Object... args) {
        // Get the tip and return if it has been disabled.
        final Tip tip = ALL.get(stringId);
        if (tip == null) {
            // log but ignore.
            Logger.warnWithStackTrace(TipManager.class, "display", "not found",
                                      "stringId=" + stringId);
            return;
        }
        if (!tip.shouldBeShown()) {
            if (postRun != null) {
                postRun.run();
            }
            return;
        }
        tip.display(inflater, stringId, args, postRun);
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
         * @param layoutId to use
         */
        private Tip(@NonNull final String key,
                    @LayoutRes int layoutId) {
            mKey = PREF_TIP + key;
            mLayoutId = layoutId;
        }

        /**
         * Check if this tip should be shown.
         */
        private boolean shouldBeShown() {
            return !mHasBeenDisplayed && App.getPrefs().getBoolean(mKey, true);
        }

        /**
         * display the tip.
         *
         * @param inflater to use
         * @param stringId for the message
         * @param args     for the message
         * @param postRun  Runnable to start afterwards
         */
        void display(@NonNull final LayoutInflater inflater,
                     @StringRes final int stringId,
                     @Nullable final Object[] args,
                     @Nullable final Runnable postRun) {

            // Build the tip dialog
            final View root = inflater.inflate(mLayoutId, null);

            // Setup the message; this is optional
            final TextView messageView = root.findViewById(R.id.content);
            if (messageView != null) {
                String tipText = inflater.getContext().getString(stringId, args);
                // allow links
                messageView.setText(Utils.linkifyHtml(tipText));
                // clicking a link, start a browser (or whatever)
                messageView.setMovementMethod(LinkMovementMethod.getInstance());
            }

            final AlertDialog dialog = new AlertDialog.Builder(inflater.getContext())
                    .setView(root)
                    .setTitle(R.string.tip_dialog_title)
                    .setNegativeButton(R.string.btn_disable_message, (d, which) -> {
                        d.dismiss();
                        setShowAgain(false);
                        if (postRun != null) {
                            postRun.run();
                        }
                    })
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        d.dismiss();
                        if (postRun != null) {
                            postRun.run();
                        }
                    })
                    .create();

            dialog.show();
            mHasBeenDisplayed = true;
        }

        /**
         * Set the preference to indicate if this tip should be shown again.
         *
         * @param show Flag indicating future visibility
         */
        private void setShowAgain(final boolean show) {
            App.getPrefs().edit().putBoolean(mKey, show).apply();
        }

        void reset() {
            setShowAgain(true);
            mHasBeenDisplayed = false;
        }
    }
}
