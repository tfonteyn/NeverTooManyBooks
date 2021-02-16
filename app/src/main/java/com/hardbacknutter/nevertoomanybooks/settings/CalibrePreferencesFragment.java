/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreConnectionTestTask;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

@Keep
public class CalibrePreferencesFragment
        extends BasePreferenceFragment {

    public static final String TAG = "CalibrePreferencesFrag";
    private static final String PSK_CA_FROM_FILE = "psk_ca_from_file";
    private static final String PSK_PICK_FOLDER = "psk_pick_folder";

    private final ActivityResultLauncher<String> mOpenCaUriLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onOpenCaUri);

    /** Let the user pick the 'root' folder for storing Calibre downloads. */
    private ActivityResultLauncher<Uri> mPickFolderLauncher;

    private CalibreConnectionTestTask mConnectionTestTask;

    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    final SwitchPreference sp = findPreference(CalibreContentServer.PK_ENABLED);
                    //noinspection ConstantConditions
                    if (sp.isChecked()) {
                        //noinspection ConstantConditions
                        new MaterialAlertDialogBuilder(getContext())
                                .setIcon(R.drawable.ic_baseline_info_24)
                                .setTitle(R.string.lbl_test_connection)
                                .setMessage(R.string.confirm_test_connection)
                                .setNegativeButton(R.string.action_not_now, (d, w) ->
                                        popBackStackOrFinish())
                                .setPositiveButton(android.R.string.ok, (d, w) -> {
                                    d.dismiss();
                                    mConnectionTestTask.start();
                                })
                                .create()
                                .show();
                    } else {
                        popBackStackOrFinish();
                    }
                }
            };

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences_calibre, rootKey);

        EditTextPreference etp;

        etp = findPreference(CalibreContentServer.PK_HOST_URL);
        //noinspection ConstantConditions
        etp.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                                  | InputType.TYPE_TEXT_VARIATION_URI);
            editText.selectAll();
        });
        etp.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());


        etp = findPreference(CalibreContentServer.PK_HOST_USER);
        //noinspection ConstantConditions
        etp.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT);
            editText.selectAll();
        });
        etp.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());


        etp = findPreference(CalibreContentServer.PK_HOST_PASS);
        //noinspection ConstantConditions
        etp.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                                  | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            editText.selectAll();
        });
        etp.setSummaryProvider(preference -> {
            final String value = ((EditTextPreference) preference).getText();
            if (value == null || value.isEmpty()) {
                return getString(R.string.info_not_set);
            } else {
                return "********";
            }
        });


        final Preference caPref = findPreference(PSK_CA_FROM_FILE);
        //noinspection ConstantConditions
        setCertificateSummary(caPref);
        caPref.setOnPreferenceClickListener(preference -> {
            mOpenCaUriLauncher.launch("*/*");
            return true;
        });

        final Preference folderPref = findPreference(PSK_PICK_FOLDER);
        //noinspection ConstantConditions
        setFolderSummary(folderPref);
        folderPref.setOnPreferenceClickListener(preference -> {
            //noinspection ConstantConditions
            mPickFolderLauncher.launch(CalibreContentServer.getFolderUri(getContext())
                                                           .orElse(null));
            return true;
        });
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mPickFolderLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(), uri -> {
                    if (uri != null) {
                        //noinspection ConstantConditions
                        CalibreContentServer.setFolderUri(getContext(), uri);
                    }
                    //noinspection ConstantConditions
                    setFolderSummary(findPreference(PSK_PICK_FOLDER));
                });

        mConnectionTestTask = new ViewModelProvider(this).get(CalibreConnectionTestTask.class);
        mConnectionTestTask.onFailure().observe(getViewLifecycleOwner(), this::onFailure);
        mConnectionTestTask.onFinished().observe(getViewLifecycleOwner(), this::onSuccess);
    }

    private void onSuccess(@NonNull final FinishedMessage<Boolean> message) {
        if (message.isNewEvent() && message.result != null) {
            if (message.result) {
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.info_authorized, Snackbar.LENGTH_SHORT).show();
                getView().postDelayed(this::popBackStackOrFinish, BaseActivity.ERROR_DELAY_MS);
            } else {
                //For now we don't get here, instead we would be in onFailure.
                // But keeping this here to guard against future changes in the task logic
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.httpErrorAuth, Snackbar.LENGTH_LONG).show();
            }
        }
    }

    private void onFailure(@NonNull final FinishedMessage<Exception> message) {
        //noinspection ConstantConditions
        String msg = ExMsg.map(getContext(), TAG, message.result);
        if (msg == null) {
            msg = "";
        } else {
            msg += "\n";
        }

        StandardDialogs.showError(
                getContext(), msg + getString(R.string.error_network_failed_try_again));
    }

    @Override
    public void onResume() {
        super.onResume();
        mToolbar.setTitle(R.string.lbl_settings);
        mToolbar.setSubtitle(R.string.site_calibre);
    }

    /**
     * Read the existing download folder, and set the preference summary.
     *
     * @param preference to use
     */
    private void setFolderSummary(@NonNull final Preference preference) {
        //noinspection ConstantConditions
        final Uri uri = CalibreContentServer.getFolderUri(getContext()).orElse(null);
        if (uri == null) {
            preference.setSummary(R.string.info_not_set);
        } else {
            final DocumentFile df = DocumentFile.fromTreeUri(getContext(), uri);
            if (df != null) {
                // Normally this will always return a name
                String name = df.getName();
                // This was seen on API 26 running in the emulator when selecting the 'download'
                //TEST: could this be due to having TWO download folders ? (device+sdcard)
                if (name == null) {
                    // not nice, but better then nothing...
                    name = uri.getLastPathSegment();
                }
                preference.setSummary(name);
            } else {
                // should never happen... flw
                preference.setSummary(R.string.info_not_set);
            }
        }
    }

    private void onOpenCaUri(@Nullable final Uri uri) {
        if (uri != null) {
            final Preference preference = findPreference(PSK_CA_FROM_FILE);

            //noinspection ConstantConditions
            try (InputStream is = getContext().getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    final X509Certificate ca;
                    try (BufferedInputStream bis = new BufferedInputStream(is)) {
                        ca = (X509Certificate) CertificateFactory
                                .getInstance("X.509").generateCertificate(bis);
                        ca.checkValidity();
                    }
                    CalibreContentServer.setCertificate(getContext(), ca);

                    //noinspection ConstantConditions
                    preference.setSummary("S: " + ca.getSubjectX500Principal().getName()
                                          + "\nI: " + ca.getIssuerX500Principal().getName());
                }
            } catch (@NonNull final IOException | CertificateException e) {
                //noinspection ConstantConditions
                preference.setSummary(R.string.error_certificate_invalid);
            }
        }
    }

    /**
     * Read the existing CA file from storage, and set the preference summary.
     *
     * @param preference to use
     */
    private void setCertificateSummary(@NonNull final Preference preference) {
        try {
            //noinspection ConstantConditions
            final X509Certificate ca = CalibreContentServer.getCertificate(getContext());
            ca.checkValidity();
            preference.setSummary("S: " + ca.getSubjectX500Principal().getName()
                                  + "\nI: " + ca.getIssuerX500Principal().getName());

        } catch (@NonNull final CertificateException e) {
            preference.setSummary(R.string.error_certificate_invalid);

        } catch (@NonNull final IOException e) {
            preference.setSummary(R.string.info_not_set);
        }
    }
}
