/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.utils.LinkifyUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * This is the About page.
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
        view.setText(LinkifyUtils.fromHtml(
                getString(R.string.url_sourcecode6, getString(R.string.lbl_about_sourcecode))));
        view.setMovementMethod(LinkMovementMethod.getInstance());


        findViewById(R.id.btn_send_debug).setOnClickListener(v -> sendDebugInfo());

        // Reset Hints
        findViewById(R.id.btn_reset_tips)
                .setOnClickListener(v -> {
                    TipManager.reset(this);
                    UserMessage.show(v, R.string.tip_reset_done);
                });

//        view = findViewById(R.id.contact1);
//        view.setOnClickListener(v -> sendContactEmail(R.string.email_contact1));
//
//        view = findViewById(R.id.contact2);
//        view.setOnClickListener(v -> sendContactEmail(R.string.email_contact2));
//
//        // Information to original creators site.
//        view = findViewById(R.id.website);
//        view.setText(LinkifyUtils.fromHtml(
//                getString(R.string.url_website, getString(R.string.lbl_about_website))));
//        view.setMovementMethod(LinkMovementMethod.getInstance());
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
            Logger.error(this, this, e);
        }
    }

    private void sendDebugInfo() {
        new AlertDialog.Builder(this)
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.debug)
                .setMessage(R.string.debug_send_info_text)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (!DebugReport.sendDebugInfo(this)) {
                        UserMessage.show(this, R.string.error_email_failed);
                    }
                })
                .create()
                .show();
    }
}
