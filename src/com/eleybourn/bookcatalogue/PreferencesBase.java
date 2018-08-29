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

package com.eleybourn.bookcatalogue;

import android.os.Bundle;
import android.view.ViewGroup;

import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.properties.Properties;

/**
 * Base class to display simple preference-based options to the user.
 * 
 * @author Philip Warner
 */
public abstract class PreferencesBase extends BookCatalogueActivity {

	/** Setup the views in the layout */
	protected abstract void setupViews(Properties globalProps);

	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);

			Properties globalProps = new Properties();
			setupViews(globalProps);

			ViewGroup styleProps = findViewById(R.id.dynamic_properties);
			globalProps.buildView(getLayoutInflater(), styleProps);

		} catch (Exception e) {
			Logger.logError(e);
		}
		
	}

//	/**
//	 * Utility routine to setup a checkbox based on a preference.
//	 *
//	 * @param cbId		CheckBox ID from XML file
//	 * @param viewId	Containing ViewGroup from XML file (for clicking and highlighting)
//	 * @param key		Preferences key associated with this CheckBox
//	 */
//	protected void addBooleanPreference(final int cbId, int viewId, final String key, final boolean defaultValue) {
//		// Setup the checkbox
//		CheckBox checkbox = this.findViewById(cbId);
//		checkbox.setChecked(BookCataloguePreferences.getBoolean(key, defaultValue));
//		checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
//			@Override
//			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//				BookCataloguePreferences.setBoolean(key, isChecked);
//			}});
//
//		// Allow clicking of entire row.
//		View view = this.findViewById(viewId);
//		// Make line flash when clicked.
//		view.setBackgroundResource(android.R.drawable.list_selector_background);
//		view.setOnClickListener(new OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				CheckBox cb = v.findViewById(cbId);
//				cb.setChecked(!BookCataloguePreferences.getBoolean(key, defaultValue));
//			}});
//	}
//
//	/**
//	 * Add an item that has a creator-define click event
//	 * */
//	public void addClickablePref(final int viewId, final OnClickListener listener) {
//		View view = findViewById(viewId);
//		// Make line flash when clicked.
//		view.setBackgroundResource(android.R.drawable.list_selector_background);
//		view.setOnClickListener(listener);
//	}
}
