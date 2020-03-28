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

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.annotation.AcraCore;
import org.acra.annotation.AcraDialog;
import org.acra.annotation.AcraMailSender;
import org.acra.annotation.AcraToast;
import org.acra.file.Directory;

import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PIntString;
import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Application implementation.
 * <p>
 * Mainly handles the app Theme and system locale changes.
 */
@AcraMailSender(
        mailTo = "",
//        mailTo = "debug@email",
        reportFileName = "NeverTooManyBooks-acra-report.txt")
@AcraToast(
        //optional, displayed as soon as the crash occurs,
        // before collecting data which can take a few seconds
        resText = R.string.acra_resToastText)
@AcraDialog(
        resText = R.string.acra_resDialogText,
        resTitle = R.string.app_name,
        resTheme = R.style.AppTheme_DayNight,
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
    /** don't assume / allow the day-night theme to have a different integer ID. */
    private static final int THEME_INVALID = -1;
    private static final int THEME_DAY_NIGHT = 0;
    private static final int THEME_DARK = 1;
    private static final int THEME_LIGHT = 2;
    /**
     * NEWTHINGS: APP THEME: adding
     * <ol>
     * <li>add it to themes.xml</li>
     * <li>add it to R.array.pv_ui_theme, the array order must match the APP_THEMES order</li>
     * <li>make sure the integer list in R.array.pv_ui_theme matches the number of themes</li>
     * <li>The default integer must be set in res/xml/preferences.xml on the App.Theme element.</li>
     * <li>The default name must be set in the manifest application tag.</li>
     * </ol>
     * The preferences choice will be build according to the string-array list/order.
     * <p>
     * DEFAULT_THEME: the default to use.
     */
    @ThemeId
    private static final int DEFAULT_THEME = THEME_DAY_NIGHT;
    /**
     * As defined in res/themes.xml.
     * <ul>
     * <li>MODE_NIGHT_AUTO_BATTERY (API < 27) / MODE_NIGHT_FOLLOW_SYSTEM (API 28+)</li>
     * <li>MODE_NIGHT_YES</li>
     * <li>MODE_NIGHT_NO</li>
     * </ul>
     */
    private static final int[] APP_THEMES = {
            R.style.AppTheme_DayNight,
            R.style.AppTheme_Dark,
            R.style.AppTheme_Light,
            };
    /**
     * Give static methods access to our singleton.
     * <strong>Note:</strong> never store a context in a static, use the instance instead
     */
    private static App sInstance;
    /** Cache the User-specified theme currently in use. '-1' to force an update at App startup. */
    @ThemeId
    private static int sCurrentThemeId = THEME_INVALID;

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
     * Load a Resources set for the specified Locale.
     * This is an expensive lookup; we do not cache the Resources here,
     * but it's advisable to cache the strings (map of Locale/String for example) being looked up.
     *
     * @param context       Current context
     * @param desiredLocale the desired Locale, e.g. the Locale of a Book,Series,TOC,...
     *
     * @return the Resources
     */
    @NonNull
    public static Resources getLocalizedResources(@NonNull final Context context,
                                                  @NonNull final Locale desiredLocale) {
        final Configuration current = context.getResources().getConfiguration();
        final Configuration configuration = new Configuration(current);
        final String lang = desiredLocale.getLanguage();
        if (lang.length() == 2) {
            configuration.setLocale(desiredLocale);
        } else {
            // any 3-char code might need to be converted to 2-char be able to find the resource.
            configuration.setLocale(new Locale(LanguageUtils.getLocaleIsoFromISO3(context, lang)));
        }

        final Context localizedContext = context.createConfigurationContext(configuration);
        return localizedContext.getResources();
    }

    /**
     * Apply the user's preferred Theme.
     * <p>
     * The one and only place where this should get called is in {@code Activity.onCreate}
     * <pre>
     * {@code
     *          public void onCreate(@Nullable final Bundle savedInstanceState) {
     *              // apply the user-preferred Theme before super.onCreate is called.
     *              App.applyTheme(this);
     *
     *              super.onCreate(savedInstanceState);
     *          }
     * }
     * </pre>
     *
     * @param activity Current Activity to apply the theme to.
     *
     * @return the applied theme id.
     */
    @ThemeId
    public static int applyTheme(@NonNull final Activity activity) {
        // Always read from prefs.
        sCurrentThemeId = PIntString.getListPreference(activity, Prefs.pk_ui_theme, DEFAULT_THEME);

        // Reminder: ***ALWAYS*** set the mode.
        if (sCurrentThemeId == THEME_DAY_NIGHT) {
            if (Build.VERSION.SDK_INT >= 29) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
            }
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_UNSPECIFIED);
        }

        // Reminder: ***ALWAYS*** set the theme.
        activity.setTheme(APP_THEMES[sCurrentThemeId]);

        return sCurrentThemeId;
    }

    /**
     * Test if the Theme has changed.
     *
     * @param context Current context
     * @param themeId to check
     *
     * @return {@code true} if the theme was changed
     */
    public static boolean isThemeChanged(@NonNull final Context context,
                                         @ThemeId final int themeId) {
        // always reload from prefs.
        sCurrentThemeId = PIntString.getListPreference(context, Prefs.pk_ui_theme, DEFAULT_THEME);
        return themeId != sCurrentThemeId;
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
     * Hide the keyboard.
     */
    @SuppressWarnings("unused")
    public static void hideKeyboard(@NonNull final View view) {
        final InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
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

    @IntDef({THEME_INVALID, THEME_DAY_NIGHT, THEME_DARK, THEME_LIGHT})
    @Retention(RetentionPolicy.SOURCE)
    @interface ThemeId {

    }
}
