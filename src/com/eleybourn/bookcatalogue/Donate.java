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
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Logger;

/**
 * This is the Donate page.
 * URL's are hardcoded and should not be changed.
 *
 * @author Evan Leybourn
 */
public class Donate extends BaseActivity {
    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_donate;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setTitle(R.string.app_name);
            setupPage();
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    private void setupPage() {
        OnClickListener payPalClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=WHD6PFWXXTPX8&lc=AU&item_name=BookCatalogue&item_number=BCPP&currency_code=USD&bn=PP%2dDonationsBF%3abtn_donateCC_LG%2egif%3aNonHosted"));
                startActivity(intent);
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
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.amazon.com/gp/registry/wishlist/2A2E48ONH64HM?tag=bookcatalogue-20"));
                startActivity(intent);
            }
        });
    }
}