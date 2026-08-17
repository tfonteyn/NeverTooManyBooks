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
import android.text.InputType;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetContentUriForReadingContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetDirectoryUriContract;
import com.hardbacknutter.nevertoomanybooks.searchengines.CommonSettingsFactory;
import com.hardbacknutter.nevertoomanybooks.settings.BaseSettingsFragment;
import com.hardbacknutter.nevertoomanybooks.settings.CalibreConnectionValidationHelper;
import com.hardbacknutter.nevertoomanybooks.settings.widgets.HostUrlValidator;
import com.hardbacknutter.prefslib.BooleanSetting;
import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.SettingsDataStore;
import com.hardbacknutter.prefslib.SettingsManager;
import com.hardbacknutter.prefslib.SharedPreferencesDataStore;
import com.hardbacknutter.prefslib.StringSetting;

@Keep
public class CalibrePreferencesFragment
        extends BaseSettingsFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "CalibrePreferencesFrag";

    private static final String PSK_CA_FROM_FILE = "psk_ca_from_file";
    private static final String PSK_PICK_FOLDER = "psk_pick_folder";

    private final ActivityResultLauncher<String> openCaUriLauncher =
            registerForActivityResult(new GetContentUriForReadingContract(),
                                      o -> o.ifPresent(this::onOpenCaUri));

    /** Let the user pick the 'root' folder for storing Calibre downloads. */
    private ActivityResultLauncher<Uri> pickFolderLauncher;

    private HostUrlValidator hostUrlValidator;

    private BooleanSetting pSyncEnabled;
    private StringSetting pHostUrl;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hostUrlValidator = new HostUrlValidator();
    }

    @NonNull
    protected SettingsManager.Builder onCreateSettings() {
        final SettingsDataStore store = new SharedPreferencesDataStore(
                ServiceLocator.getInstance().getSharedPreferences());
        //noinspection DataFlowIssue
        final SettingsManager.Builder factory = new SettingsManager.Builder(getContext(), store);

        factory.header(R.string.lbl_calibre_content_server);

        factory.bool(CalibreHandler.PK_ENABLED,
                     R.string.option_enable_sync_options,
                     R.string.disabled, R.string.enabled,
                     this::onChangeEnableSync, null);

        factory.action(PSK_PICK_FOLDER,
                       R.string.option_download_folder,
                       this::onPickFolder, p -> {
                    p.setIcon(R.drawable.folder_24px);
                    p.setSummaryProvider(this::getDownloadFolderSummary);
                });

        factory.text(CalibreContentServer.PK_HOST_URL,
                     R.string.lbl_website_address, null, p -> {
                    p.setIcon(R.drawable.link_24px);
                    p.setInputType(InputType.TYPE_CLASS_TEXT
                                   | InputType.TYPE_TEXT_VARIATION_URI);
                    p.setSummaryProvider(c -> hostUrlValidator.getSummary(c, p.getValue()));
                });

        factory.action(PSK_CA_FROM_FILE,
                       R.string.lbl_certificate_ca,
                       this::onPickCA, p -> {
                    p.setIcon(R.drawable.security_24px);
                    p.setSummaryProvider(this::getCaSummary);
                });

        factory.bool(CalibreNetworkConfig.PK_USE_THROTTLER,
                     R.string.lbl_limit_request_speed,
                     null, p -> {
                    p.setSummary(R.string.lbl_limit_request_speed_info);
                });

        final String pk = CalibreContentServer.PREFERENCE_KEY;
        CommonSettingsFactory.credentials(factory, pk);
        CommonSettingsFactory.timeouts(factory, pk);

        return factory;
    }

    private boolean onPickFolder(@NonNull final Setting setting) {
        //noinspection DataFlowIssue
        pickFolderLauncher.launch(CalibreContentServer.getFolderUri(getContext())
                                                      .orElse(null));
        return true;
    }

    private boolean onPickCA(@NonNull final Setting setting) {
        openCaUriLauncher.launch("*/*");
        return true;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final SettingsManager settingsManager = getSettingsManager();
        pSyncEnabled = settingsManager.requireSetting(CalibreHandler.PK_ENABLED);
        pHostUrl = settingsManager.requireSetting(CalibreContentServer.PK_HOST_URL);

        new CalibreConnectionValidationHelper(
                this, getProgressFrame(),
                () -> pSyncEnabled.isChecked(),
                hostUrlValidator, () -> pHostUrl.getValue(),
                this::popBackStackOrFinish);

        pickFolderLauncher = registerForActivityResult(
                new GetDirectoryUriContract(), o -> {
                    //noinspection DataFlowIssue
                    o.ifPresent(uri -> CalibreContentServer.setFolderUri(getContext(), uri));
                    // Refresh the summary
                    //noinspection DataFlowIssue
                    getSettingsManager().reload(getContext(), PSK_PICK_FOLDER);
                });
    }

    private boolean onChangeEnableSync(@NonNull final Setting setting,
                                       @Nullable final Object newValue) {
        final boolean enable = newValue != null && (boolean) newValue;
        // Simple flip the state of all settings except PK_ENABLED itself.

        final SettingsManager settingsManager = getSettingsManager();
        final List<String> keys = settingsManager
                .getSettings()
                .stream()
                .map(Setting::getKey)
                .filter(key -> !key.equals(CalibreHandler.PK_ENABLED))
                .collect(Collectors.toList());
        settingsManager.setEnabled(enable, keys);
        return true;
    }

    private void onOpenCaUri(@NonNull final Uri uri) {
        final Context context = getContext();
        //noinspection DataFlowIssue
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is != null) {
                final X509Certificate ca;
                try (BufferedInputStream bis = new BufferedInputStream(is)) {
                    ca = (X509Certificate) CertificateFactory
                            .getInstance("X.509").generateCertificate(bis);
                }
                CalibreContentServer.setCertificate(context, ca);
            }
        } catch (@NonNull final IOException | CertificateException e) {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.error_import_failed, Snackbar.LENGTH_LONG)
                    .show();
        }

        // Refresh the summary
        getSettingsManager().reload(context, PSK_CA_FROM_FILE);
    }

    @NonNull
    private String getDownloadFolderSummary(@NonNull final Context context) {
        final Uri uri = CalibreContentServer.getFolderUri(context).orElse(null);
        if (uri != null) {
            final DocumentFile df = DocumentFile.fromTreeUri(context, uri);
            if (df != null) {
                // Normally this will always return a name
                String name = df.getName();
                // This was seen on API 26 running in the emulator when selecting the 'download'
                //TEST: could this be due to having TWO download folders ? (device+sdcard)
                if (name == null) {
                    // not nice, but better than nothing...
                    name = uri.getLastPathSegment();
                }
                // The name SHOULD always be non-null now... flw
                if (name != null) {
                    return name;
                }
            }
        }
        // a valid "not set", or as fallback if anything goes bad above.
        return context.getString(R.string.preference_not_set);
    }

    @NonNull
    private String getCaSummary(@NonNull final Context context) {
        try {
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

            return context.getString(R.string.lbl_certificate_issued_to,
                                     ca.getSubjectX500Principal().getName())
                   + '\n'
                   + context.getString(R.string.lbl_certificate_issued_by,
                                       ca.getIssuerX500Principal().getName())
                   + '\n'
                   + context.getString(R.string.lbl_certificate_validity_period, from, until);

        } catch (@NonNull final CertificateException e) {
            return context.getString(R.string.error_certificate_invalid);

        } catch (@NonNull final IOException e) {
            return context.getString(R.string.preference_not_set);
        }
    }
}
