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
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.Locale;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.annotation.AcraCore;
import org.acra.annotation.AcraDialog;
import org.acra.annotation.AcraMailSender;
import org.acra.annotation.AcraToast;
import org.acra.file.Directory;

import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Application implementation.
 * <p>
 * Mainly handles the app Theme and system locale changes.
 */
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

    /** Log tag. */
    private static final String TAG = "App";
    /**
     * Give static methods access to our singleton.
     * <strong>Note:</strong> never store a context in a static, use the instance instead
     */
    private static App sInstance;

    /** Singleton. */
    @SuppressWarnings("unused")
    public App() {
        sInstance = this;
    }

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
            PackageInfo info =
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) {
                return info.getLongVersionCode();
            } else {
                //noinspection deprecation
                return info.versionCode;
            }
        } catch (@NonNull final PackageManager.NameNotFoundException ignore) {
            // ignore
        }
        // ouch
        return 0;
    }

    /**
     * Initialize ACRA for a given Application.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    @CallSuper
    protected void attachBaseContext(@NonNull final Context base) {
        super.attachBaseContext(base);
        ACRA.init(this);
        ACRA.getErrorReporter().putCustomData("Signed-By", DebugReport.signedBy(this));
    }

//    @Override
//    public void onCreate() {
//        super.onCreate();
//        Stetho.initializeWithDefaults(this);
//    }

    /**
     * Ensure to re-apply the user-preferred Locale to the Application (this) object.
     *
     * @param newConfig The new device configuration.
     */
    @Override
    @CallSuper
    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        final String localeSpec = LocaleUtils
                .getPersistedLocaleSpec(sInstance.getApplicationContext());
        // override in the new config
        newConfig.setLocale(LocaleUtils.createLocale(localeSpec));
        // propagate to registered callbacks.
        super.onConfigurationChanged(newConfig);

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "onConfigurationChanged| Locale.getDefault=" + Locale.getDefault());
            if (Build.VERSION.SDK_INT >= 24) {
                Log.d(TAG, "onConfigurationChanged|newConfig.getLocales().get(0)="
                           + newConfig.getLocales().get(0));
            } else {
                Log.d(TAG, "onConfigurationChanged|newConfig.locale=" + newConfig.locale);
            }
        }
    }

}
