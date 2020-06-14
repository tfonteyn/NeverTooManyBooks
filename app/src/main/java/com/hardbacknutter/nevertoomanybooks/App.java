/*
 * @Copyright 2020 HardBackNutter
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

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.StrictMode;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.annotation.AcraCore;
import org.acra.annotation.AcraDialog;
import org.acra.annotation.AcraMailSender;
import org.acra.annotation.AcraToast;
import org.acra.file.Directory;

import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

@AcraMailSender(
        mailTo = "",
        // mailTo = "debug@email",
        reportFileName = "NeverTooManyBooks-acra-report.txt")
@AcraToast(
        //optional, displayed as soon as the crash occurs,
        // before collecting data which can take a few seconds
        resText = R.string.acra_resToastText)
@AcraDialog(
        resText = R.string.acra_resDialogText,
        resTitle = R.string.app_name,
        resTheme = R.style.Theme_App,
        resIcon = R.drawable.ic_warning,
        resCommentPrompt = R.string.acra_resDialogCommentPrompt)
@AcraCore(
        resReportSendSuccessToast = R.string.acra_resReportSendSuccessToast,
        resReportSendFailureToast = R.string.error_email_failed,
        reportContent = {
                // Device
                ReportField.PHONE_MODEL,
                ReportField.BRAND,
                ReportField.PRODUCT,
                ReportField.DEVICE_FEATURES,
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

                ReportField.USER_COMMENT
        },
        applicationLogFileDir = Directory.EXTERNAL_FILES,
        applicationLogFile = Logger.LOG_PATH,
        applicationLogFileLines = 1000
)
public class App
        extends Application {

    /**
     * Singleton.
     */
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
     * If a {@link android.os.AsyncTask}#doInBackground needs a context, it should call this one.
     *
     * @return app context
     */
    @NonNull
    public static Context getTaskContext() {
        return sInstance.getApplicationContext();
    }

    /**
     * Reads the application version from the manifest.
     *
     * @param context Current context
     *
     * @return the version
     */
    public static long getVersion(@NonNull final Context context) {
        try {
            final PackageInfo info =
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) {
                return info.getLongVersionCode();
            } else {
                //noinspection deprecation
                return info.versionCode;
            }
        } catch (@NonNull final PackageManager.NameNotFoundException ignore) {
            // eh ?
            return 0;
        }

    }

    @Override
    @CallSuper
    protected void attachBaseContext(@NonNull final Context base) {
        // create singleton self reference.
        sInstance = this;

        super.attachBaseContext(base);
        // Initialize ACRA reporting
        ACRA.init(this);
        ACRA.getErrorReporter().putCustomData("Signed-By", DebugReport.signedBy(this));

        // https://developer.android.com/reference/android/os/StrictMode
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STRICT_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                                               .detectAll()
                                               .penaltyLog()
                                               .build());
        }
    }

//    @Override
//    public void onCreate() {
//        super.onCreate();
//        Stetho.initializeWithDefaults(this);
//    }
}
