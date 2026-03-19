/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetContentUriForReadingContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetDirectoryUriContract;
import com.hardbacknutter.nevertoomanybooks.settings.ConnectionValidationBasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.widgets.HostUrlValidator;

@Keep
public class CalibrePreferencesFragment
        extends ConnectionValidationBasePreferenceFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "CalibrePreferencesFrag";

    private static final String PSK_CA_FROM_FILE = "psk_ca_from_file";
    private static final String PSK_PICK_FOLDER = "psk_pick_folder";

    /** Let the user pick the 'root' folder for storing Calibre downloads. */
    private ActivityResultLauncher<Uri> pickFolderLauncher;

    private SwitchPreference pSyncEnabled;
    private Preference pDownloadFolder;
    private EditTextPreference pHostUrl;
    private Preference pCACert;

    private final ActivityResultLauncher<String> openCaUriLauncher =
            registerForActivityResult(new GetContentUriForReadingContract(),
                                      o -> o.ifPresent(this::onOpenCaUri));
    private HostUrlValidator hostUrlValidator;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_calibre, rootKey);

        initValidator(R.string.site_calibre);

        //noinspection DataFlowIssue
        pSyncEnabled = findPreference(CalibreHandler.PK_ENABLED);

        //noinspection DataFlowIssue
        pDownloadFolder = findPreference(PSK_PICK_FOLDER);
        //noinspection DataFlowIssue
        setDownloadFolderSummary(pDownloadFolder);
        pDownloadFolder.setOnPreferenceClickListener(preference -> {
            //noinspection DataFlowIssue
            pickFolderLauncher.launch(CalibreContentServer.getFolderUri(getContext())
                                                          .orElse(null));
            return true;
        });

        //noinspection DataFlowIssue
        pHostUrl = findPreference(CalibreContentServer.PK_HOST_URL);
        //noinspection DataFlowIssue
        hostUrlValidator = initHostUrlPreference(pHostUrl);

        //noinspection DataFlowIssue
        pCACert = findPreference(PSK_CA_FROM_FILE);
        //noinspection DataFlowIssue
        pCACert.setSummary(createCaSummary());
        pCACert.setOnPreferenceClickListener(preference -> {
            openCaUriLauncher.launch("*/*");
            return true;
        });

        initCredentialPreferences(CalibreContentServer.PK_HOST_USER,
                                  CalibreContentServer.PK_HOST_PASS);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pickFolderLauncher = registerForActivityResult(
                new GetDirectoryUriContract(), o -> {
                    //noinspection DataFlowIssue
                    o.ifPresent(uri -> CalibreContentServer.setFolderUri(getContext(), uri));
                    setDownloadFolderSummary(pDownloadFolder);
                });
    }

    @Override
    protected boolean shouldProposeValidation() {
        return pSyncEnabled.isChecked();
    }

    @Override
    protected void proposeValidation() {
        if (!hostUrlValidator.isValidUrl(pHostUrl.getText())) {
            hostUrlValidator.showUrlInvalidDialog(pHostUrl.getContext(),
                                                  pHostUrl.getText(),
                                                  this::popBackStackOrFinish);
            return;
        }
        super.proposeValidation();
    }

    /**
     * Read the existing download folder, and set the preference summary.
     *
     * @param preference to use
     */
    private void setDownloadFolderSummary(@NonNull final Preference preference) {
        //noinspection DataFlowIssue
        final Uri uri = CalibreContentServer.getFolderUri(getContext()).orElse(null);
        if (uri == null) {
            preference.setSummary(R.string.preference_not_set);
        } else {
            final DocumentFile df = DocumentFile.fromTreeUri(getContext(), uri);
            if (df != null) {
                // Normally this will always return a name
                String name = df.getName();
                // This was seen on API 26 running in the emulator when selecting the 'download'
                //TEST: could this be due to having TWO download folders ? (device+sdcard)
                if (name == null) {
                    // not nice, but better than nothing...
                    name = uri.getLastPathSegment();
                }
                preference.setSummary(name);
            } else {
                // should never happen... flw
                preference.setSummary(R.string.preference_not_set);
            }
        }
    }

    private void onOpenCaUri(@NonNull final Uri uri) {
        //noinspection DataFlowIssue
        try (InputStream is = getContext().getContentResolver().openInputStream(uri)) {
            if (is != null) {
                final X509Certificate ca;
                try (BufferedInputStream bis = new BufferedInputStream(is)) {
                    ca = (X509Certificate) CertificateFactory
                            .getInstance("X.509").generateCertificate(bis);
                }
                CalibreContentServer.setCertificate(getContext(), ca);
            }
        } catch (@NonNull final IOException | CertificateException e) {
            pCACert.setSummary(R.string.error_certificate_invalid);
            return;
        }

        pCACert.setSummary(createCaSummary());
    }

    /**
     * Read the existing CA file from storage, and create the preference summary.
     *
     * @return text to display as the summary
     */
    @NonNull
    private String createCaSummary() {
        try {
            final Context context = getContext();
            //noinspection DataFlowIssue
            final X509Certificate ca = CalibreContentServer.getCertificate(context);
            ca.checkValidity();

            final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
            final DateTimeFormatter formatter = DateTimeFormatter
                    .ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(userLocales.get(0));

            final String from = formatter.format(ca.getNotBefore()
                                                   .toInstant()
                                                   .atZone(ZoneId.systemDefault())
                                                   .toLocalDate());
            final String until = formatter.format(ca.getNotAfter()
                                                    .toInstant()
                                                    .atZone(ZoneId.systemDefault())
                                                    .toLocalDate());

            return getString(R.string.lbl_certificate_issued_to,
                             ca.getSubjectX500Principal().getName())
                   + '\n'
                   + getString(R.string.lbl_certificate_issued_by,
                               ca.getIssuerX500Principal().getName())
                   + '\n'
                   + getString(R.string.lbl_certificate_validity_period, from, until);

        } catch (@NonNull final CertificateException e) {
            return getString(R.string.error_certificate_invalid);

        } catch (@NonNull final IOException e) {
            return getString(R.string.preference_not_set);
        }
    }
}
