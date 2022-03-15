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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Application;
import android.content.Context;
import android.os.StrictMode;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.io.File;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.config.ToastConfigurationBuilder;
import org.acra.data.StringFormat;
import org.acra.file.Directory;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;

public class App
        extends Application {

    private static final int ACRA_LOGFILE_LINES = 1_000;
    private static final String EMAIL_ACRA_ATTACHMENTS = "NeverTooManyBooks-acra-report.txt";
    private static final String APPLICATION_LOG_FILE =
            ServiceLocator.DIR_LOG + File.separatorChar + Logger.ERROR_LOG_FILE;

    /** Flag to indicate the startup can skip a full init. */
    private boolean mHotStart;

    public boolean isHotStart() {
        return mHotStart;
    }

    public void setHotStart() {
        mHotStart = true;
    }

    @Override
    @CallSuper
    protected void attachBaseContext(@NonNull final Context base) {
        super.attachBaseContext(base);

        initAcra();

        // https://developer.android.com/reference/android/os/StrictMode
        if (BuildConfig.DEBUG  /* always */) {
            if (DEBUG_SWITCHES.STRICT_MODE_THREADING) {
                StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                                                   .detectAll()
                                                   .penaltyLog()
                                                   .build());
            }
            if (DEBUG_SWITCHES.STRICT_MODE_VM) {
                StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                                               .detectLeakedSqlLiteObjects()
                                               .detectLeakedClosableObjects()
                                               .detectActivityLeaks()
                                               .penaltyLog()
                                               .build());
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // https://www.acra.ch/docs/Troubleshooting-Guide#applicationoncreate
        if (!ACRA.isACRASenderServiceProcess()) {
            ServiceLocator.create(getApplicationContext());
            SearchEngineRegistry.create(getApplicationContext());
        }
    }

    /**
     * Initialize ACRA reporting.
     */
    private void initAcra() {
        final CoreConfigurationBuilder builder = new CoreConfigurationBuilder(this)
                .withBuildConfigClass(BuildConfig.class)
                .withReportFormat(StringFormat.JSON)
                .withReportSendFailureToast(getString(R.string.error_email_failed))
                .withApplicationLogFileDir(Directory.FILES)
                .withApplicationLogFile(APPLICATION_LOG_FILE)
                .withApplicationLogFileLines(ACRA_LOGFILE_LINES)
                .withExcludeMatchingSharedPreferencesKeys(".*password.*")
                .withReportContent(
                        // Device
                        ReportField.PHONE_MODEL,
                        ReportField.BRAND,
                        // ReportField.PRODUCT,
                        // ReportField.DEVICE_FEATURES,
                        ReportField.DISPLAY,
                        ReportField.ANDROID_VERSION,
                        ReportField.BUILD,
                        // ReportField.ENVIRONMENT,
                        ReportField.TOTAL_MEM_SIZE,
                        ReportField.AVAILABLE_MEM_SIZE,

                        // Privacy: do not use ReportField.DEVICE_ID,
                        ReportField.INSTALLATION_ID,
                        ReportField.REPORT_ID,

                        // Application
                        ReportField.APP_VERSION_CODE,
                        ReportField.BUILD_CONFIG,
                        ReportField.FILE_PATH,

                        ReportField.APPLICATION_LOG,
                        ReportField.SHARED_PREFERENCES,
                        ReportField.INITIAL_CONFIGURATION,
                        ReportField.CRASH_CONFIGURATION,
                        ReportField.STACK_TRACE,
                        ReportField.STACK_TRACE_HASH,
                        ReportField.THREAD_DETAILS,

                        ReportField.CUSTOM_DATA,

                        ReportField.USER_APP_START_DATE,
                        ReportField.USER_CRASH_DATE,

                        ReportField.USER_COMMENT);

        builder.getPluginConfigurationBuilder(MailSenderConfigurationBuilder.class)
               .withMailTo(BuildConfig.EMAIL_ACRA)
               .withReportFileName(EMAIL_ACRA_ATTACHMENTS)
               .withEnabled(true);

        // Optional, displayed as soon as the crash occurs,
        // before collecting data which can take a few seconds
        builder.getPluginConfigurationBuilder(ToastConfigurationBuilder.class)
               .withText(getString(R.string.acra_resToastText))
               .withEnabled(true);

        builder.getPluginConfigurationBuilder(DialogConfigurationBuilder.class)
               .withText(getString(R.string.acra_resDialogText))
               .withTitle(getString(R.string.app_name))
               .withCommentPrompt(getString(R.string.acra_resDialogCommentPrompt))
               .withResTheme(R.style.Theme_App)
               .withResIcon(R.drawable.ic_baseline_warning_24)
               .withEnabled(true);


        ACRA.init(this, builder);

        ACRA.getErrorReporter().putCustomData("Signed-By", PackageInfoWrapper
                .createWithSignatures(this).getSignedBy());
    }
}
