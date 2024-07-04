/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.os.Build;
import android.os.Debug;
import android.os.StrictMode;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.debug.AcraCustomDialog;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;
import com.hardbacknutter.nevertoomanybooks.utils.theme.NightMode;
import com.hardbacknutter.nevertoomanybooks.utils.theme.ThemeColorController;
import com.hardbacknutter.util.logger.FileLogger;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.data.StringFormat;
import org.acra.file.Directory;

public class App
        extends Application {

    private static final String TAG = "App";

    /** Sub directory of {@link Context#getFilesDir()}. */
    private static final String LOG_DIR = "log";
    /** Base name of the logfile. */
    private static final String LOG_FILE = "error.log";

    private static final int ACRA_LOGFILE_LINES = 1_000;

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
        // Theoretically not needed, as we're not using Acra senders; but paranoia....
        if (!ACRA.isACRASenderServiceProcess()) {
            // Don't double-init
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

            final StrictMode.VmPolicy.Builder vmPolicyBuilder = new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks();

            // https://developer.android.com/about/versions/15/behavior-changes-15#safer-intents
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vmPolicyBuilder.detectUnsafeIntentLaunch();
            }
            StrictMode.setVmPolicy(vmPolicyBuilder
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

        initLogger();

        ServiceLocator.create(getApplicationContext());

        // Theoretically not needed, as we're not using Acra senders; but paranoia....
        if (!ACRA.isACRASenderServiceProcess()) {
            // Only setup the SearchEngines if this is NOT the Acra process.
            final Languages languages = ServiceLocator.getInstance().getLanguages();
            SearchEngineConfig.createRegistry(getApplicationContext(), languages);
        }

        // setup support for custom themes, dynamic colors and day/night modes.
        ThemeColorController.init(this);
        NightMode.init(this);
    }

    private void initLogger() {
        final File logDir = new File(getFilesDir(), LOG_DIR);
        if (!logDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            logDir.mkdirs();
        }
        final FileLogger logger = new FileLogger(logDir, LOG_FILE);
        logger.cycleLogs();
        LoggerFactory.setLogger(logger);
    }

    /**
     * Initialize ACRA reporting.
     */
    private void initAcra() {
        ACRA.init(this, new CoreConfigurationBuilder()
                .withBuildConfigClass(BuildConfig.class)
                .withReportFormat(StringFormat.JSON)
                .withApplicationLogFileDir(Directory.FILES)
                .withApplicationLogFile(LOG_DIR + File.separatorChar + LOG_FILE)
                .withApplicationLogFileLines(ACRA_LOGFILE_LINES)
                // regex's
                .withExcludeMatchingSharedPreferencesKeys(".*password.*", ".*host\\.user.*")
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
                final File logDir = ServiceLocator.getInstance().getLogDir();
                if (logDir != null) {
                    try {
                        final File file = new File(logDir, "ntmb.hprof");
                        Debug.dumpHprofData(file.getAbsolutePath());
                    } catch (@NonNull final IOException e) {
                        LoggerFactory.getLogger().e(TAG, e);
                    }
                }
            }

            ACRA.getErrorReporter().handleException(throwable, true);
        });
    }
}
