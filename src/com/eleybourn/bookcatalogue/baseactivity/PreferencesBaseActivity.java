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

package com.eleybourn.bookcatalogue.baseactivity;

import android.os.Bundle;
import android.view.ViewGroup;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.properties.Properties;

/**
 * Base class to display simple preference-based options to the user.
 * 
 * @author Philip Warner
 */
abstract public class PreferencesBaseActivity extends BookCatalogueActivity {

	/** Setup the views in the layout */
	abstract protected void setupViews(Properties globalProps);

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
}
