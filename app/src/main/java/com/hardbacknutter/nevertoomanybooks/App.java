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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Application;
import android.content.Context;
import android.os.Debug;
import android.os.StrictMode;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.core.Logger;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.debug.AcraCustomDialog;
import com.hardbacknutter.nevertoomanybooks.debug.FileLogger;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;
import com.hardbacknutter.nevertoomanybooks.utils.NightMode;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.data.StringFormat;
import org.acra.file.Directory;

public class App
        extends Application {

    private static final String TAG = "App";

    private static final int ACRA_LOGFILE_LINES = 1_000;
    private static final String APPLICATION_LOG_FILE =
            FileLogger.DIR_LOG + File.separatorChar + FileLogger.ERROR_LOG_FILE;

    /** Flag to indicate the startup can skip a full init. */
    private boolean hotStart;

    boolean isHotStart() {
        return hotStart;
    }

    void setHotStart() {
        hotStart = true;
    }

    @Override
    @CallSuper
    protected void attachBaseContext(@NonNull final Context base) {
        super.attachBaseContext(base);

        if (!ACRA.isACRASenderServiceProcess()) {
            initAcra();
        }

        // https://developer.android.com/reference/android/os/StrictMode
        if (BuildConfig.DEBUG  /* always */) {
            if (DEBUG_SWITCHES.STRICT_MODE_THREADING) {
                StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                                                   .detectAll()
                                                   .penaltyLog()
                                                   .build());
            }

            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                                           .detectLeakedSqlLiteObjects()
                                           .detectLeakedClosableObjects()
                                           .detectActivityLeaks()
                                           .penaltyLog()
                                           .build());

            // Android 13 (maybe earlier versions as well) will show a
            // StrictMode policy violation: android.os.strictmode.LeakedClosableViolation:
            // A resource was acquired at attached stack trace but never released.
            // See java.io.Closeable for information on avoiding resource leaks. Callsite: close
            //
            // Reproduce: start the app, click on a book to open details.
            //
            // This is a bug in
            // android.app.ActivityThread#handleAttachStartupAgents
            // fixed in Android 14 (and maybe in later 13 releases)
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        final File logDir = new File(getFilesDir(), FileLogger.DIR_LOG);
        if (!logDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            logDir.mkdirs();
        }
        LoggerFactory.setLogger(new FileLogger(logDir));

        ServiceLocator.create(getApplicationContext());

        if (!ACRA.isACRASenderServiceProcess()) {
            final Languages languages = ServiceLocator.getInstance().getLanguages();
            SearchEngineConfig.createRegistry(getApplicationContext(), languages);
        }

        // always set the mode, so it's valid for acra as well.
        NightMode.apply(this);
    }

    /**
     * Initialize ACRA reporting.
     */
    private void initAcra() {
        ACRA.init(this, new CoreConfigurationBuilder()
                .withBuildConfigClass(BuildConfig.class)
                .withReportFormat(StringFormat.JSON)
                .withApplicationLogFileDir(Directory.FILES)
                .withApplicationLogFile(APPLICATION_LOG_FILE)
                .withApplicationLogFileLines(ACRA_LOGFILE_LINES)
                .withExcludeMatchingSharedPreferencesKeys(".*password.*")
                .withReportContent(
                        // Device
                        ReportField.PHONE_MODEL,
                        ReportField.BRAND,
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

                        ReportField.USER_COMMENT)

                .withPluginConfigurations(
                        new DialogConfigurationBuilder()
                                .withEnabled(true)
                                .withReportDialogClass(AcraCustomDialog.class)
                                .build()
                )
        );

        ACRA.getErrorReporter().putCustomData("Signed-By", PackageInfoWrapper
                .createWithSignatures(this)
                .getSignedBy()
                .orElse("Not signed"));

        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            if (throwable instanceof OutOfMemoryError) {
                final Logger logger = LoggerFactory.getLogger();
                try {
                    final File file = new File(logger.getLogDir(), "ntmb.hprof");
                    Debug.dumpHprofData(file.getAbsolutePath());
                } catch (@NonNull final IOException e) {
                    logger.e(TAG, e);
                }
            }

            ACRA.getErrorReporter().handleException(throwable, true);
        });
    }
}
