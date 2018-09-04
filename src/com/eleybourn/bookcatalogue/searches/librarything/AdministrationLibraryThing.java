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

package com.eleybourn.bookcatalogue.searches.librarything;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;

import java.io.File;

/**
 * 
 * This is the Administration page. It contains details about LibraryThing links
 * and how to register for a developer key. At a later data we could also include
 * the user key for maintaining user-specific LibraryThing data.
 * 
 * @author Philip Warner
 */
public class AdministrationLibraryThing extends BookCatalogueActivity {

	@Override
	protected int getLayoutId(){
		return R.layout.activity_admin_librarything;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setTitle(R.string.library_thing);
			setupPage();
		} catch (Exception ignore) {
			Logger.logError(ignore);
		}
	}
	
	private void setupPage() {
		/* LT Reg Link */
		TextView register = findViewById(R.id.register_url);
		register.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(BookCataloguePreferences.WEBSITE_URL_LIBRARYTHING + "/"));
				startActivity(loadweb); 
				return;
			}
		});
		
		/* DevKey Link */
		TextView devkeyLink = findViewById(R.id.devkey_url);
		devkeyLink.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent loadweb = new Intent(Intent.ACTION_VIEW, Uri.parse(BookCataloguePreferences.WEBSITE_URL_LIBRARYTHING + "/services/keys.php"));
				startActivity(loadweb); 
				return;
			}
		});

		EditText devkeyView = findViewById(R.id.devkey);
		SharedPreferences prefs = getSharedPreferences(BookCataloguePreferences.APP_SHARED_PREFERENCES, Context.MODE_PRIVATE);
		devkeyView.setText(prefs.getString(LibraryThingManager.LT_DEVKEY_PREF_NAME, ""));
		
		/* Save Button */
		Button btn = findViewById(R.id.confirm);
		btn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				EditText devkeyView = findViewById(R.id.devkey);
				String devkey = devkeyView.getText().toString();
				SharedPreferences prefs = getSharedPreferences(BookCataloguePreferences.APP_SHARED_PREFERENCES, Context.MODE_PRIVATE);
				SharedPreferences.Editor ed = prefs.edit();
				ed.putString(LibraryThingManager.LT_DEVKEY_PREF_NAME, devkey);
				ed.apply();
				
				if (!devkey.isEmpty()) {
					FragmentTask task = new FragmentTask() {
						/**
						 * Validate the key by getting a known cover
						 */
						@Override
						public void run(SimpleTaskQueueProgressFragment fragment, SimpleTaskContext taskContext) {
							//TEST Library Thing
							Bundle tmp = new Bundle(); 
							LibraryThingManager ltm = new LibraryThingManager(AdministrationLibraryThing.this);
							String fileSpec = ltm.getCoverImage("0451451783", tmp, LibraryThingManager.ImageSizes.SMALL);
							File tmpFile = new File(fileSpec);
							tmpFile.deleteOnExit();
							long length = tmpFile.length();
							if (length < 100) {
								// Queue a toast message
								fragment.showToast(R.string.incorrect_key);
							} else {
								// Queue a toast message
								fragment.showToast(R.string.correct_key);
							}
							//noinspection ResultOfMethodCallIgnored
							tmpFile.delete();
						}

						@Override
						public void onFinish(SimpleTaskQueueProgressFragment fragment, Exception exception) {
						}

					};

					// Get the fragment to display task progress
					SimpleTaskQueueProgressFragment.runTaskWithProgress(AdministrationLibraryThing.this, R.string.connecting_to_web_site, task, true, 0);

				}
				return;
			}
		});

		/* Reset Button */
		Button resetBtn = findViewById(R.id.reset_messages);
		resetBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				SharedPreferences prefs = getSharedPreferences(BookCataloguePreferences.APP_SHARED_PREFERENCES, android.content.Context.MODE_PRIVATE);
				SharedPreferences.Editor ed = prefs.edit();
				for( String key : prefs.getAll().keySet()) {
					if (key.toLowerCase().startsWith(LibraryThingManager.LT_HIDE_ALERT_PREF_NAME.toLowerCase())) 
						ed.remove(key);
				}
				ed.apply();
				return;
			}
		});
		
	}

}
