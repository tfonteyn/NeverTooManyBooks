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

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * This is the About page.
 * It contains details about the app, links to my [original authors] website and email.
 * <p>
 * URL's are hardcoded and should not be changed.
 *
 * @author Evan Leybourn
 */
public class About
        extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_about;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);

        /* Version Number */
        TextView view = findViewById(R.id.version);
        PackageInfo packageInfo = BookCatalogueApp.getPackageInfo(0);
        if (packageInfo != null) {
            view.setText(packageInfo.versionName);
        }

        view = findViewById(R.id.website);
        view.setText(Utils.linkifyHtml(
                getString(R.string.url_website, getString(R.string.about_lbl_website))));
        view.setMovementMethod(LinkMovementMethod.getInstance());

        view = findViewById(R.id.sourcecode);
        view.setText(Utils.linkifyHtml(
                getString(R.string.url_sourcecode, getString(R.string.about_lbl_sourcecode))));
        view.setMovementMethod(LinkMovementMethod.getInstance());

        view = findViewById(R.id.contact1);
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                sendContactEmail(R.string.email_contact1);
            }
        });

        view = findViewById(R.id.contact2);
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                sendContactEmail(R.string.email_contact2);
            }
        });

        /* URL as-is for reference

        "https://www.paypal.com/cgi-bin/webscr?cmd=_donations" +
                        "&business=WHD6PFWXXTPX8&lc=AU" +
                        "&item_name=BookCatalogue&item_number=BCPP" +
                        "&currency_code=USD" +
                        "&bn=PP%2dDonationsBF%3abtn_donateCC_LG%2egif%3aNonHosted"
         */
        // url with encoded % characters
        @SuppressLint("DefaultLocale")
        String paypalUrl = String.format(
                "<a href=\"https://www.paypal.com/cgi-bin/webscr?cmd=_donations"
                        + "&business=WHD6PFWXXTPX8&lc=AU"
                        + "&item_name=BookCatalogue&item_number=BCPP"
                        + "&currency_code=USD"
                        + "&bn=PP%%2dDonationsBF%%3abtn_donateCC_LG%%2egif%%3aNonHosted\">%1s</a>",
                getString(R.string.about_donate_paypal));

        view = findViewById(R.id.donate_url);
        view.setText(Utils.linkifyHtml(paypalUrl));
        view.setMovementMethod(LinkMovementMethod.getInstance());

        view = findViewById(R.id.amazon_url);
        String text = String.format(
                "<a href=\"https://www.amazon.com/gp/registry/wishlist/"
                        + "2A2E48ONH64HM?tag=bookcatalogue-20\">%1s</a>",
                getString(R.string.about_donate_amazon));
        view.setText(Utils.linkifyHtml(text));
        view.setMovementMethod(LinkMovementMethod.getInstance());

        view = findViewById(R.id.amazon_links_info);
        text = getString(R.string.hint_amazon_links_blurb,
                         getString(R.string.menu_amazon_books_by_author),
                         getString(R.string.menu_amazon_books_in_series),
                         getString(R.string.menu_amazon_books_by_author_in_series),
                         getString(R.string.app_name));
        view.setText(Utils.linkifyHtml(text));
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    void sendContactEmail(@StringRes final int stringId) {
        try {
            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            emailIntent.setType("text/plain");
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(stringId)});
            String subject = '[' + getString(R.string.app_name) + "] ";
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            startActivity(Intent.createChooser(emailIntent, getString(R.string.send_mail)));
        } catch (ActivityNotFoundException e) {
            Logger.error(e);
        }
    }
}
