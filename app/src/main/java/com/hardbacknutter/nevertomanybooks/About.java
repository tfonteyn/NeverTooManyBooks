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

package com.hardbacknutter.nevertomanybooks;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.utils.Utils;

/**
 * This is the About page.
 * It contains details about the app, links to [original authors] website and email.
 */
public class About
        extends BaseActivity {

    /** Manifest string. */
    private static final String BETA_BUILD = "beta.build";

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_about;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);

        // Version Number
        TextView view = findViewById(R.id.version);
        PackageInfo packageInfo = App.getPackageInfo(0);
        if (packageInfo != null) {
            view.setText(packageInfo.versionName);
        }

        String beta = App.getManifestString(BETA_BUILD);
        if (!beta.isEmpty()) {
            view = findViewById(R.id.beta_build);
            view.setVisibility(View.VISIBLE);
            view.setText(beta);
        }

        view = findViewById(R.id.sourcecode6);
        view.setText(Utils.linkifyHtml(
                getString(R.string.url_sourcecode6, getString(R.string.lbl_sourcecode))));
        view.setMovementMethod(LinkMovementMethod.getInstance());


        view = findViewById(R.id.contact1);
        view.setOnClickListener(v -> sendContactEmail(R.string.email_contact1));

        view = findViewById(R.id.contact2);
        view.setOnClickListener(v -> sendContactEmail(R.string.email_contact2));

        // Information to original creators site.
        view = findViewById(R.id.website);
        view.setText(Utils.linkifyHtml(
                getString(R.string.url_website, getString(R.string.lbl_website))));
        view.setMovementMethod(LinkMovementMethod.getInstance());

        view = findViewById(R.id.sourcecode);
        view.setText(Utils.linkifyHtml(
                getString(R.string.url_sourcecode, getString(R.string.lbl_sourcecode))));
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * Prepare a new email.
     *
     * @param emailId resource id for email address
     */
    private void sendContactEmail(@StringRes final int emailId) {
        try {
            String subject = '[' + getString(R.string.app_name) + "] ";
            Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_EMAIL, new String[]{getString(emailId)})
                    .putExtra(Intent.EXTRA_SUBJECT, subject);
            startActivity(Intent.createChooser(intent, getString(R.string.title_send_mail)));
        } catch (@NonNull final ActivityNotFoundException e) {
            Logger.error(this, e);
        }
    }
}
