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
import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Class to manage the display of 'hints' within the application. Each hint dialog has
 * a 'Do not show again' option, that results in an update to the preferences which
 * are checked by this code.
 * <p>
 * To add a new hint, create a string resource and add it to mHints. Then, to display the
 * hint, simply call HintManager.displayHint(a, stringId).
 *
 * @author Philip Warner
 */
public final class HintManager {

    /** Preferences prefix. */
    private static final String TAG = "HintManager.";
    /** Preferences prefix for hints. */
    private static final String PREF_HINT = TAG + "Hint.";
    /** All hints managed by this class. */
    @SuppressLint("UseSparseArrays")
    private static final Map<Integer, Hint> mHints = new HashMap<>();

    static {
        mHints.put(R.string.hint_booklist_style_menu,
                   new Hint("hint_booklist_style_menu"));
        mHints.put(R.string.hint_booklist_styles_editor,
                   new Hint("BOOKLIST_STYLES_EDITOR"));
        mHints.put(R.string.hint_booklist_style_groups,
                   new Hint("BOOKLIST_STYLE_GROUPS"));
        mHints.put(R.string.hint_booklist_style_properties,
                   new Hint("BOOKLIST_STYLE_PROPERTIES"));
        // keep, might need again if re-implemented
        //mHints.put(R.string.hint_booklist_global_properties,
        //         new Hint("BOOKLIST_GLOBAL_PROPERTIES"));

        mHints.put(R.string.hint_authors_book_may_appear_more_than_once,
                   new Hint("BOOKLIST_MULTI_AUTHORS"));
        mHints.put(R.string.hint_series_book_may_appear_more_than_once,
                   new Hint("BOOKLIST_MULTI_SERIES"));

        mHints.put(R.string.hint_background_tasks,
                   new Hint("BACKGROUND_TASKS"));
        mHints.put(R.string.hint_background_task_events,
                   new Hint("BACKGROUND_TASK_EVENTS"));

        mHints.put(R.string.gr_explain_goodreads_no_isbn,
                   new Hint("explain_goodreads_no_isbn"));
        mHints.put(R.string.gr_explain_goodreads_no_match,
                   new Hint("explain_goodreads_no_match"));

        // advert
        //mHints.put(R.string.hint_tempus_locum, new Hint("hint_tempus_locum"));

        mHints.put(R.string.hint_autorotate_camera_images,
                   new Hint("hint_autorotate_camera_images"));
        mHints.put(R.string.hint_view_only_book_details,
                   new Hint("hint_view_only_book_details"));
        mHints.put(R.string.hint_view_only_help,
                   new Hint("hint_view_only_help"));
        mHints.put(R.string.hint_book_list,
                   new Hint("hint_book_list"));
        mHints.put(R.string.hint_amazon_links_blurb,
                   new Hint("hint_amazon_links_blurb"));
        mHints.put(R.string.hint_book_search_by_text,
                   new Hint("hint_book_search_by_text"));
        // v200
        mHints.put(R.string.pt_hint_layer_type,
                   new Hint("hint_pref_layer_type"));
    }

    private HintManager() {
    }

    /** Reset all hints to that they will be displayed again. */
    public static void resetHints() {
        for (Hint h : mHints.values()) {
            h.reset();
        }
    }

    /**
     * Convenience method so the main code is not polluted with the rather long arguments.
     *
     * @param inflater to use.
     */
    public static void showAmazonHint(@NonNull final LayoutInflater inflater) {
        Context context = inflater.getContext();
        displayHint(inflater, R.string.hint_amazon_links_blurb, null,
                    context.getString(R.string.menu_amazon_books_by_author),
                    context.getString(R.string.menu_amazon_books_in_series),
                    context.getString(R.string.menu_amazon_books_by_author_in_series),
                    context.getString(R.string.app_name));
    }

    /**
     * Display the passed hint, if the user has not disabled it.
     */
    public static void displayHint(@NonNull final LayoutInflater inflater,
                                   @StringRes final int stringId,
                                   @Nullable final Runnable postRun,
                                   @Nullable final Object... args) {
        // Get the hint and return if it has been disabled.
        final Hint hint = mHints.get(stringId);
        if (hint == null) {
            // log but ignore.
            Logger.error("displayHint|not found|stringId=" + stringId);
            return;
        }
        if (!hint.shouldBeShown()) {
            if (postRun != null) {
                postRun.run();
            }
            return;
        }
        hint.display(inflater, stringId, args, postRun);
    }

    public interface HintOwner {

        @StringRes
        int getHint();
    }


    /**
     * Class to represent a single Hint.
     */
    private static final class Hint {

        /** Preferences key suffix specific to this hint. */
        @NonNull
        private final String mKey;

        /** Indicates that this hint was displayed already in this instance of the app. */
        private boolean mHasBeenDisplayed;

        /**
         * Constructor.
         *
         * @param key Preferences key suffix specific to this hint
         */
        private Hint(@NonNull final String key) {
            mKey = PREF_HINT + key;
        }

        /**
         * Set the preference to indicate if this hint should be shown again.
         *
         * @param visible Flag indicating future visibility
         */
        private void setVisibility(final boolean visible) {
            Prefs.getPrefs().edit().putBoolean(mKey, visible).apply();
        }

        /**
         * Check if this hint should be shown.
         */
        private boolean shouldBeShown() {
            return !mHasBeenDisplayed && Prefs.getPrefs().getBoolean(mKey, true);
        }

        /**
         * display the hint.
         *
         * @param inflater  to use
         * @param stringId  for the message
         * @param args      for the message
         * @param postRun   Runnable to start afterwards
         */
        void display(@NonNull final LayoutInflater inflater,
                     @StringRes final int stringId,
                     @Nullable final Object[] args,
                     @Nullable final Runnable postRun) {

            // Build the hint dialog
            final View root = inflater.inflate(R.layout.dialog_hint, null);

            // Setup the message
            final TextView messageView = root.findViewById(R.id.hint);
            if (messageView != null) {
                String hintText = BookCatalogueApp.getResString(stringId, args);
                // allow links
                messageView.setText(Utils.linkifyHtml(hintText));
                // clicking a link, start a browser (or whatever)
                messageView.setMovementMethod(LinkMovementMethod.getInstance());
            }

            final AlertDialog dialog = new AlertDialog.Builder(inflater.getContext())
                    .setView(root)
                    .setTitle(R.string.hint)
                    .create();

            root.findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(@NonNull final View v) {
                    dialog.dismiss();
                    if (postRun != null) {
                        postRun.run();
                    }
                }
            });

            root.findViewById(R.id.hint_do_not_show_again).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(@NonNull final View v) {
                    dialog.dismiss();
                    setVisibility(false);
                    if (postRun != null) {
                        postRun.run();
                    }
                }
            });
            dialog.show();
            mHasBeenDisplayed = true;
        }

        public void reset() {
            setVisibility(true);
            mHasBeenDisplayed = false;
        }
    }
}
