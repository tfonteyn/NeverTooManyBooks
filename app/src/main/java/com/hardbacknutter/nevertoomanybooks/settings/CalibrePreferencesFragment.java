/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.url.CalibreContentServer;

@Keep
public class CalibrePreferencesFragment
        extends BasePreferenceFragment {

    public static final String TAG = "CalibrePreferencesFrag";
    private static final String PSK_CA_FROM_FILE = "psk_ca_from_file";

    private final ActivityResultLauncher<String> mOpenUriLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onOpenUri);

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_calibre, rootKey);

        final EditTextPreference hostUrlPref = findPreference(CalibreContentServer.PK_HOST_URL);
        if (hostUrlPref != null) {
            hostUrlPref.setOnBindEditTextListener(TextView::setSingleLine);
        }

        final Preference caPref = findPreference(PSK_CA_FROM_FILE);
        if (caPref != null) {
            setCertificateSummary(caPref);
            caPref.setOnPreferenceClickListener(
                    preference -> {
                        mOpenUriLauncher.launch("*/*");
                        return true;
                    });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mToolbar.setTitle(R.string.lbl_settings);
        mToolbar.setSubtitle(R.string.site_calibre);
    }

    @Override
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences preferences,
                                          @NonNull final String key) {
        super.onSharedPreferenceChanged(preferences, key);
    }

    private void onOpenUri(@Nullable final Uri uri) {
        if (uri != null) {
            final Preference caPref = findPreference(PSK_CA_FROM_FILE);

            //noinspection ConstantConditions
            try (InputStream is = getContext().getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    final X509Certificate ca;
                    try (BufferedInputStream bis = new BufferedInputStream(is)) {
                        ca = (X509Certificate) CertificateFactory
                                .getInstance("X.509").generateCertificate(bis);
                        ca.checkValidity();
                    }

                    try (FileOutputStream fos = getContext()
                            .openFileOutput(CalibreContentServer.CA_FILE, Context.MODE_PRIVATE)) {
                        fos.write(ca.getEncoded());
                    }

                    //noinspection ConstantConditions
                    caPref.setSummary(ca.getSubjectX500Principal().getName());
                }
            } catch (@NonNull final IOException | CertificateException e) {
                //noinspection ConstantConditions
                caPref.setSummary(R.string.error_certificate_invalid);
            }
        }
    }

    /**
     * Read the existing CA file from storage, and set the preference summary.
     *
     * @param preference to use
     */
    private void setCertificateSummary(@NonNull final Preference preference) {
        //noinspection ConstantConditions
        try (InputStream is = getContext().openFileInput(CalibreContentServer.CA_FILE)) {
            final X509Certificate ca;
            try (BufferedInputStream bis = new BufferedInputStream(is)) {
                ca = (X509Certificate) CertificateFactory
                        .getInstance("X.509").generateCertificate(bis);
                ca.checkValidity();
                preference.setSummary(ca.getSubjectX500Principal().getName());
            }
        } catch (@NonNull final CertificateException e) {
            preference.setSummary(R.string.error_certificate_invalid);

        } catch (@NonNull final IOException e) {
            preference.setSummary(R.string.hint_empty_field);
        }
    }
}
