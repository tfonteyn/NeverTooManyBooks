/*
 * @Copyright 2018-2023 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.debug;

import android.content.Context;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetContentUriForWritingContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.IntentFactory;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;

import org.acra.dialog.CrashReportDialogHelper;

/**
 * We're not using an acra sender, not much point.
 */
public class AcraCustomDialog
        extends AppCompatActivity {

    private static final String TAG = "AcraCustomDialog";
    private static final String MIME_TYPE = "text/plain";
    private CrashReportDialogHelper crashReportHelper;
    private AcraCustomDialogViewModel vm;
    /** The launcher for picking a Uri to write to. */
    private final ActivityResultLauncher<GetContentUriForWritingContract.Input>
            createDocumentLauncher =
            registerForActivityResult(new GetContentUriForWritingContract(), oUri -> {
                if (oUri.isPresent()) {
                    vm.start(oUri.get(), crashReportHelper);
                } else {
                    finish();
                }
            });

    protected void attachBaseContext(@NonNull final Context base) {
        final Context localizedContext = ServiceLocator.getInstance().getAppLocale().apply(base);
        super.attachBaseContext(localizedContext);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        crashReportHelper = new CrashReportDialogHelper(this, getIntent());

        vm = new ViewModelProvider(this).get(AcraCustomDialogViewModel.class);
        vm.onWriteDataCancelled().observe(this, this::onExportFinished);
        vm.onWriteDataFailure().observe(this, this::onExportFailure);
        vm.onWriteDataFinished().observe(this, this::onExportFinished);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.acra_dialog_message,
                                      getString(R.string.action_create)))
                .setPositiveButton(R.string.action_create, (d, w) -> {
                    // Create the proposed name for the archive. The user can change it.
                    final String fileName = "ntmb-crash-" + LocalDateTime
                            .now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + ".txt";
                    createDocumentLauncher.launch(new GetContentUriForWritingContract
                            .Input(MIME_TYPE, fileName));
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    crashReportHelper.cancelReports();
                    finish();
                })
                .create();

        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private void onExportFinished(@NonNull final LiveDataEvent<Boolean> message) {
        crashReportHelper.cancelReports();

        message.process(result -> {
            if (result) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.acra_report_saved)
                        .setPositiveButton(R.string.action_report, (d, w) -> {
                            startActivity(IntentFactory.createGithubIssueIntent(this));
                            finish();
                        })
                        .setNegativeButton(android.R.string.cancel, (d, w) -> finish())
                        .create()
                        .show();
            } else {
                showMessageAndFinishActivity(getString(R.string.error_unexpected));
            }
        });
    }

    private void onExportFailure(@NonNull final LiveDataEvent<Throwable> message) {
        crashReportHelper.cancelReports();
        message.process(e -> LoggerFactory.getLogger().e(TAG, e));

        showMessageAndFinishActivity(getString(R.string.error_unexpected));
    }

    private void showMessageAndFinishActivity(@NonNull final CharSequence message) {
        // TODO: offer more help/info on what to do if the acra report failed.
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (d, w) -> finish())
                .create()
                .show();
    }
}
