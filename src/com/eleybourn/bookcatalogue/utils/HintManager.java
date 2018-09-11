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

package com.eleybourn.bookcatalogue.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.dialogs.BasicDialog;

import java.util.Enumeration;
import java.util.Hashtable;

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
	private HintManager() {
	}

	/** Preferences prefix */
	private final static String TAG = "HintManager";
	/** Preferences prefix for hints */
	private final static String PREF_HINT = TAG + ".Hint.";

	/** All hints managed by this class */
	private static final Hints mHints = new Hints()
		.add("BOOKLIST_STYLES_EDITOR", R.string.hint_booklist_styles_editor)
		.add("BOOKLIST_STYLE_GROUPS", R.string.hint_booklist_style_groups)
		.add("BOOKLIST_STYLE_PROPERTIES", R.string.hint_booklist_style_properties)
		.add("BOOKLIST_GLOBAL_PROPERTIES", R.string.hint_booklist_global_properties)
		.add("BOOKLIST_MULTI_AUTHORS", R.string.hint_authors_book_may_appear_more_than_once)
		.add("BOOKLIST_MULTI_SERIES", R.string.hint_series_book_may_appear_more_than_once)
		.add("BACKGROUND_TASKS", R.string.hint_background_tasks)
		.add("BACKGROUND_TASK_EVENTS", R.string.hint_background_task_events)
		.add("STARTUP_SCREEN", R.string.hint_startup_screen)
		.add("explain_goodreads_no_isbn", R.string.explain_goodreads_no_isbn)
		.add("explain_goodreads_no_match", R.string.explain_goodreads_no_match)
		.add("hint_booklist_style_menu", R.string.hint_booklist_style_menu)
		.add("hint_autorotate_camera_images", R.string.hint_autorotate_camera_images)
		.add("hint_view_only_book_details", R.string.hint_view_only_book_details)
		.add("hint_view_only_help", R.string.hint_view_only_help)
		.add("hint_tempus_locum", R.string.hint_tempus_locum)
		.add("hint_book_list", R.string.hint_book_list)
		.add("hint_amazon_links_blurb", R.string.hint_amazon_links_blurb)
		;

	public interface HintOwner {
		int getHint();
	}
	
	/** Reset all hints to that they will be displayed again */
	public static void resetHints() {
		Enumeration<Hint> hints = mHints.getHints();
		while(hints.hasMoreElements()) {
			Hint h = hints.nextElement();
			h.setVisibility(true);
			h.setHasBeenDisplayed(false);
		}
	}

	public static boolean shouldBeShown(int hintStringId) {
		final Hint h = mHints.getHint(hintStringId);
		return h.shouldBeShown();
	}
	
	/** Display the passed hint, if the user has not disabled it */
	public static void displayHint(Context context, int stringId, final Runnable postRun, Object... args) {
		// Get the hint and return if it has been disabled.
		final Hint h = mHints.getHint(stringId);
		if (!h.shouldBeShown()) {
			if (postRun != null)
				postRun.run();
			return;
		}

		// Build the hint dialog
		final Dialog dialog = new BasicDialog(context, false);
		dialog.setContentView(R.layout.dialog_hint);
        dialog.setTitle(R.string.hint);

		// Setup the message
        final TextView msg = dialog.findViewById(R.id.hint);
        String hintText = BookCatalogueApp.getResourceString(stringId, args);
        msg.setText(Utils.linkifyHtml(hintText, Linkify.ALL));
        //msg.setText(Html.fromHtml(hintText)); //stringId);
        //Linkify.addLinks(msg, Linkify.ALL);

        // Automatically start a browser (or whatever)
		msg.setMovementMethod(LinkMovementMethod.getInstance());

        final Button ok = dialog.findViewById(R.id.confirm);
        final CheckBox cb = dialog.findViewById(R.id.hide_hint_checkbox);
		// Handle the 'OK' click
		ok.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
				// Disable hint if checkbox checked
				if (cb.isChecked()) {
					h.setVisibility(false);
				}
			}
		});

		dialog.show();
		h.setHasBeenDisplayed(true);
	}
	
	/**
	 * Class to represent a collection of all defined hints
	 * 
	 * @author Philip Warner
	 */
	private static class Hints {
		/** USed to lookup hint based on string ID */
		private final Hashtable<Integer, Hint> mHintsById = new Hashtable<>();
		/* Used to prevent two hints having the same preference name */
		//private final Hashtable<String, Hint> mHintsByKey = new Hashtable<>();

		/**
		 * Add a hint to the collection
		 * 
		 * @param key			Unique preference suffix for this hint
		 * @param stringId		String ID to display
		 * 
		 * @return				Hints, for chaining
		 */
		public Hints add(String key, int stringId) {
			Hint h = new Hint(key);
			mHintsById.put(stringId, h);
			//mHintsByKey.put(key.trim().toLowerCase(), h);
			return this;
		}

		/**
		 * Return the hint based on string ID
		 */
		Hint getHint(int stringId) {
			Hint h = mHintsById.get(stringId);
			if (h == null)
				throw new RuntimeException("Hint not found for ID " + stringId);
			return h;
		}

		/**
		 * Get an enumeration of all hints.
		 */
		Enumeration<Hint> getHints() {
			return mHintsById.elements();
		}

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
		 * @param key			Preferences key suffix specific to this hint
		 */
		private Hint(String key) {
			this.key = key;
		}
		
		/**
		 * Get the preference name for this hint
		 *
		 * @return		Fully qualified preference name
		 */
		String getFullPrefName() {
			return PREF_HINT + key;
		}

		/**
		 * Set the preference to indicate if this hint should be shown again
		 * 
		 * @param visible	Flag indicating future visibility
		 */
		public void setVisibility(boolean visible) {
			Editor ed = BookCataloguePreferences.edit();
			String name = getFullPrefName();
			ed.putBoolean(name, visible);
			ed.commit();
		}

		/**
		 * Check if this hint should be shown
		 */
		boolean shouldBeShown() {
			return !hasBeenDisplayed() && BookCataloguePreferences.getBoolean(getFullPrefName(), true);
		}

		boolean hasBeenDisplayed() {
			return mHasBeenDisplayed;
		}
		
		void setHasBeenDisplayed(boolean hasBeenDisplayed) {
			mHasBeenDisplayed = hasBeenDisplayed;
		}
	}
}
