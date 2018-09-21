/*
 * @copyright 2010 Evan Leybourn
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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.debug.Logger;

/**
 * 
 * This is the Administration page. It contains details about the app, links
 * to my website and email, functions to export and import books and functions to 
 * manage bookshelves.
 *
 * URL's are hardcoded and should not be changed.
 *
 * @author Evan Leybourn
 */
public class AdministrationDonate extends BookCatalogueActivity {
	@Override
	protected int getLayoutId(){
		return R.layout.activity_admin_donate;
	}
	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(@Nullable final Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setTitle(R.string.app_name);
			setupPage();
		} catch (Exception e) {
			Logger.logError(e);
		}
	}

	/**
	 * This function builds the Admin page in 4 sections. 
	 * 1. The button to goto the manage bookshelves activity
	 * 2. The button to export the database
	 * 3. The button to import the exported file into the database
	 * 4. The application version and link details
	 * 5. The link to paypal for donation
	 */
	private void setupPage() {
		OnClickListener payPalClick = new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent loadweb = new Intent(Intent.ACTION_VIEW,
							Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=WHD6PFWXXTPX8&lc=AU&item_name=BookCatalogue&item_number=BCPP&currency_code=USD&bn=PP%2dDonationsBF%3abtn_donateCC_LG%2egif%3aNonHosted"));
					startActivity(loadweb);
                }
			};

		/* Donation Link */
		View donate = findViewById(R.id.donate_url);
		donate.setOnClickListener(payPalClick);
		View donate2 = findViewById(R.id.donate_url_image);
		donate2.setOnClickListener(payPalClick);

		/* Donation Link */
		View amazon = findViewById(R.id.amazon_url);
		amazon.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent loadweb = new Intent(Intent.ACTION_VIEW,
						Uri.parse("https://www.amazon.com/gp/registry/wishlist/2A2E48ONH64HM?tag=bookcatalogue-20"));
				startActivity(loadweb);
            }
		});
		
	}

}