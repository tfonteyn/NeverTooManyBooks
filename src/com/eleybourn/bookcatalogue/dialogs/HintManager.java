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
import android.app.Dialog;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BCPreferences;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
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
    private final static String TAG = "HintManager";
    /** Preferences prefix for hints */
    private final static String PREF_HINT = TAG + ".Hint.";
    /** All hints managed by this class */
    @SuppressLint("UseSparseArrays")
    private static final Map<Integer, Hint> mHints = new HashMap<>();
    static {
        mHints.put(R.string.hint_booklist_styles_editor, new Hint("BOOKLIST_STYLES_EDITOR"));
        mHints.put(R.string.hint_booklist_style_groups, new Hint("BOOKLIST_STYLE_GROUPS"));
        mHints.put(R.string.hint_booklist_style_properties, new Hint("BOOKLIST_STYLE_PROPERTIES"));
        mHints.put(R.string.hint_booklist_global_properties, new Hint("BOOKLIST_GLOBAL_PROPERTIES"));
        mHints.put(R.string.hint_authors_book_may_appear_more_than_once, new Hint("BOOKLIST_MULTI_AUTHORS"));
        mHints.put(R.string.hint_series_book_may_appear_more_than_once, new Hint("BOOKLIST_MULTI_SERIES"));
        mHints.put(R.string.hint_background_tasks, new Hint("BACKGROUND_TASKS"));
        mHints.put(R.string.hint_background_task_events, new Hint("BACKGROUND_TASK_EVENTS"));
        mHints.put(R.string.hint_startup_screen, new Hint("STARTUP_SCREEN"));
        mHints.put(R.string.explain_goodreads_no_isbn, new Hint("explain_goodreads_no_isbn"));
        mHints.put(R.string.explain_goodreads_no_match, new Hint("explain_goodreads_no_match"));
        mHints.put(R.string.hint_booklist_style_menu, new Hint("hint_booklist_style_menu"));
        mHints.put(R.string.hint_autorotate_camera_images, new Hint("hint_autorotate_camera_images"));
        mHints.put(R.string.hint_view_only_book_details, new Hint("hint_view_only_book_details"));
        mHints.put(R.string.hint_view_only_help, new Hint("hint_view_only_help"));
        mHints.put(R.string.hint_tempus_locum, new Hint("hint_tempus_locum"));
        mHints.put(R.string.hint_book_list, new Hint("hint_book_list"));
        mHints.put(R.string.hint_amazon_links_blurb, new Hint("hint_amazon_links_blurb"));
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

    public static boolean shouldBeShown(final int hintStringId) {
        return mHints.get(hintStringId).shouldBeShown();
    }

    /** Display the passed hint, if the user has not disabled it */
    public static void displayHint(@NonNull final Context context,
                                   @StringRes final int stringId,
                                   @Nullable final Runnable postRun,
                                   @Nullable final Object... args) {
        // Get the hint and return if it has been disabled.
        final Hint h = mHints.get(stringId);
        if (!h.shouldBeShown()) {
            if (postRun != null)
                postRun.run();
            return;
        }

        // Build the hint dialog
        final Dialog dialog = new StandardDialogs.BasicDialog(context, false);
        dialog.setContentView(R.layout.dialog_hint);
        dialog.setTitle(R.string.hint);

        // Setup the message
        final TextView msgField = dialog.findViewById(R.id.hint);
        if (msgField != null) {
            String hintText = BookCatalogueApp.getResourceString(stringId, args);
            msgField.setText(Utils.linkifyHtml(hintText, Linkify.ALL));
            //msg.setText(Html.fromHtml(hintText)); //stringId);
            //Linkify.addLinks(msg, Linkify.ALL);

            // Automatically start a browser (or whatever)
            msgField.setMovementMethod(LinkMovementMethod.getInstance());
        }

        // Handle the 'OK' click
        final Button ok = dialog.findViewById(R.id.confirm);
        ok.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                // Disable hint if checkbox checked
                final CheckBox cb = dialog.findViewById(R.id.hide_hint_checkbox);
                if (cb.isChecked()) {
                    h.setVisibility(false);
                }
            }
        });

        dialog.show();
        h.setHasBeenDisplayed(true);
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
        public final String key;

        /** Indicates that this hint was displayed already in this instance of the app */
        boolean mHasBeenDisplayed = false;

        /**
         * Constructor
         *
         * @param key Preferences key suffix specific to this hint
         */
        private Hint(@NonNull final String key) {
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
         * @param visible Flag indicating future visibility
         */
        public void setVisibility(final boolean visible) {
            BCPreferences.edit().putBoolean(getFullPrefName(), visible).commit();
        }

        /**
         * Check if this hint should be shown
         */
        boolean shouldBeShown() {
            return !hasBeenDisplayed() && BCPreferences.getBoolean(getFullPrefName(), true);
        }

        boolean hasBeenDisplayed() {
            return mHasBeenDisplayed;
        }

        void setHasBeenDisplayed(final boolean hasBeenDisplayed) {
            mHasBeenDisplayed = hasBeenDisplayed;
        }
    }
}
