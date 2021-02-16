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
import androidx.annotation.StringRes;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.config.ToastConfigurationBuilder;
import org.acra.file.Directory;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;

public class App
        extends Application {

    private static final int ACRA_LOGFILE_LINES = 1_000;
    private static final String EMAIL_ACRA_ATTACHMENTS = "NeverTooManyBooks-acra-report.txt";

    /** Singleton. */
    private static App sInstance;

    /**
     * Get the Application Context <strong>using the device Locale</strong>.
     *
     * @return app context
     */
    @NonNull
    public static Context getAppContext() {
        return sInstance.getApplicationContext();
    }

    /**
     * Get the Application Context <strong>using the device Locale</strong>.
     * This is purely for readability / debug.
     * <p>
     * If a background task/runnable/... needs a context, it should call this method.
     *
     * @return app context
     */
    @NonNull
    public static Context getTaskContext() {
        return sInstance.getApplicationContext();
    }

    public static String getLogString(@StringRes final int stringId) {
        return sInstance.getString(stringId);
    }

    @Override
    @CallSuper
    protected void attachBaseContext(@NonNull final Context base) {
        // create singleton self reference.
        sInstance = this;

        super.attachBaseContext(base);

        initAcra();

        // https://developer.android.com/reference/android/os/StrictMode
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STRICT_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                                               .detectAll()
                                               .penaltyLog()
                                               .build());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        SearchEngineRegistry.create(getApplicationContext());
    }

    /**
     * Initialize ACRA reporting.
     */
    private void initAcra() {
        final CoreConfigurationBuilder builder = new CoreConfigurationBuilder(this)
                .setResReportSendSuccessToast(R.string.acra_resReportSendSuccessToast)
                .setResReportSendFailureToast(R.string.error_email_failed)
                .setApplicationLogFileDir(Directory.EXTERNAL_FILES)
                .setApplicationLogFile(Logger.LOG_PATH)
                .setApplicationLogFileLines(ACRA_LOGFILE_LINES)
                // TODO: comment-out unneeded fields
                .setReportContent(
                        // Device
                        ReportField.PHONE_MODEL,
                        ReportField.BRAND,
                        ReportField.PRODUCT,
                        // ReportField.DEVICE_FEATURES,
                        ReportField.DISPLAY,
                        ReportField.ANDROID_VERSION,
                        ReportField.BUILD,
                        ReportField.ENVIRONMENT,
                        ReportField.TOTAL_MEM_SIZE,
                        ReportField.AVAILABLE_MEM_SIZE,

                        // Privacy: do not use ReportField.DEVICE_ID,
                        ReportField.INSTALLATION_ID,
                        ReportField.REPORT_ID,

                        // Application
                        ReportField.APP_VERSION_CODE,
                        ReportField.APP_VERSION_NAME,
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
               .setMailTo(BuildConfig.EMAIL_ACRA)
               .setReportFileName(EMAIL_ACRA_ATTACHMENTS);

        // Optional, displayed as soon as the crash occurs,
        // before collecting data which can take a few seconds
        builder.getPluginConfigurationBuilder(ToastConfigurationBuilder.class)
               .setResText(R.string.acra_resToastText);

        builder.getPluginConfigurationBuilder(DialogConfigurationBuilder.class)
               .setResText(R.string.acra_resDialogText)
               .setResTitle(R.string.app_name)
               .setResTheme(R.style.Theme_App)
               .setResIcon(R.drawable.ic_baseline_warning_24)
               .setResCommentPrompt(R.string.acra_resDialogCommentPrompt);


        ACRA.init(this, builder);

        ACRA.getErrorReporter().putCustomData("Signed-By", PackageInfoWrapper
                .createWithSignatures(this).getSignedBy());
    }
}
