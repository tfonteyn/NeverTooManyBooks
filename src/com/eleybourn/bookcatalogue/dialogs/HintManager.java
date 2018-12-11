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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Class to manage the display of 'hints' within the application. Each hint dialog has
 * a 'Do not show again' option, that results in an update to the preferences which
 * are checked by this code.
 *
 * To add a new hint, create a string resource and add it to mHints. Then, to display the
 * hint, simply call HintManager.displayHint(a, stringId).
 *
 * @author Philip Warner
 */
public class HintManager {
    /** Preferences prefix */
    private final static String TAG = "HintManager.";
    /** Preferences prefix for hints */
    private final static String PREF_HINT = TAG + "Hint.";
    /** All hints managed by this class */
    @SuppressLint("UseSparseArrays")
    private static final  Map<Integer, Hint> mHints = new HashMap<>();

    static {
        mHints.put(R.string.hint_booklist_styles_editor, new Hint("BOOKLIST_STYLES_EDITOR"));
        mHints.put(R.string.hint_booklist_style_groups, new Hint("BOOKLIST_STYLE_GROUPS"));
        mHints.put(R.string.hint_booklist_style_properties, new Hint("BOOKLIST_STYLE_PROPERTIES"));
        mHints.put(R.string.hint_booklist_global_properties, new Hint("BOOKLIST_GLOBAL_PROPERTIES"));

        mHints.put(R.string.hint_authors_book_may_appear_more_than_once, new Hint("BOOKLIST_MULTI_AUTHORS"));
        mHints.put(R.string.hint_series_book_may_appear_more_than_once, new Hint("BOOKLIST_MULTI_SERIES"));

        mHints.put(R.string.hint_background_tasks, new Hint("BACKGROUND_TASKS"));
        mHints.put(R.string.hint_background_task_events, new Hint("BACKGROUND_TASK_EVENTS"));

        mHints.put(R.string.gr_explain_goodreads_no_isbn, new Hint("explain_goodreads_no_isbn"));
        mHints.put(R.string.gr_explain_goodreads_no_match, new Hint("explain_goodreads_no_match"));

        //mHints.put(R.string.hint_tempus_locum, new Hint("hint_tempus_locum"));

        mHints.put(R.string.hint_booklist_style_menu, new Hint("hint_booklist_style_menu"));
        mHints.put(R.string.hint_autorotate_camera_images, new Hint("hint_autorotate_camera_images"));
        mHints.put(R.string.hint_view_only_book_details, new Hint("hint_view_only_book_details"));
        mHints.put(R.string.hint_view_only_help, new Hint("hint_view_only_help"));
        mHints.put(R.string.hint_book_list, new Hint("hint_book_list"));
        mHints.put(R.string.hint_amazon_links_blurb, new Hint("hint_amazon_links_blurb"));
        mHints.put(R.string.hint_book_search_by_text, new Hint("hint_book_search_by_text"));
        // v83
        mHints.put(R.string.hint_pref_layer_type, new Hint("hint_pref_layer_type"));
    }

    private HintManager() {
    }

    /** Reset all hints to that they will be displayed again */
    public static void resetHints() {
        for (Hint h : mHints.values()) {
            h.setVisibility(true);
            h.setHasBeenDisplayed(false);
        }
    }

    public static boolean shouldBeShown(final @StringRes int id) {
        return mHints.get(id).shouldBeShown();
    }

    /** Display the passed hint, if the user has not disabled it */
    public static void displayHint(final @NonNull LayoutInflater inflater,
                                   final @StringRes int stringId,
                                   final @Nullable Runnable postRun,
                                   final @Nullable Object... args) {
        // Get the hint and return if it has been disabled.
        final Hint hint = mHints.get(stringId);
        if (hint == null) {
            Logger.error("displayHint|not found|stringId=" + stringId);
            return;
        }
        if (!hint.shouldBeShown()) {
            if (postRun != null)
                postRun.run();
            return;
        }

        // Build the hint dialog
        final View root = inflater.inflate(R.layout.dialog_hint, null);

        // Setup the message
        final TextView msgField = root.findViewById(R.id.hint);
        if (msgField != null) {
            String hintText = BookCatalogueApp.getResourceString(stringId, args);
            msgField.setText(Utils.linkifyHtml(hintText));

            // Automatically start a browser (or whatever)
            msgField.setMovementMethod(LinkMovementMethod.getInstance());
        }

        final AlertDialog dialog = new AlertDialog.Builder(inflater.getContext())
                .setView(root)
                .setTitle(R.string.hint)
                .create();

        //noinspection ConstantConditions
        root.findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        //noinspection ConstantConditions
        root.findViewById(R.id.hint_do_not_show_again).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                hint.setVisibility(false);
            }
        });
        dialog.show();
        hint.setHasBeenDisplayed(true);
    }

    public interface HintOwner {
        int getHint();
    }


    /**
     * Class to represent a single Hint.
     *
     * @author Philip Warner
     */
    private static class Hint {
        /** Preferences key suffix specific to this hint */
        @NonNull
        public final String key;

        /** Indicates that this hint was displayed already in this instance of the app */
        boolean mHasBeenDisplayed = false;

        /**
         * Constructor
         *
         * @param key Preferences key suffix specific to this hint
         */
        private Hint(final @NonNull String key) {
            this.key = key;
        }

        /**
         * Get the preference name for this hint
         *
         * @return Fully qualified preference name
         */
        @NonNull
        String getFullPrefName() {
            return PREF_HINT + key;
        }

        /**
         * Set the preference to indicate if this hint should be shown again
         *
         * @param visible Options indicating future visibility
         */
        public void setVisibility(final boolean visible) {
            BookCatalogueApp.getSharedPreferences().edit().putBoolean(getFullPrefName(), visible).apply();
        }

        /**
         * Check if this hint should be shown
         */
        boolean shouldBeShown() {
            return !hasBeenDisplayed() && BookCatalogueApp.getBooleanPreference(getFullPrefName(), true);
        }

        boolean hasBeenDisplayed() {
            return mHasBeenDisplayed;
        }

        void setHasBeenDisplayed(final boolean hasBeenDisplayed) {
            mHasBeenDisplayed = hasBeenDisplayed;
        }
    }
}
