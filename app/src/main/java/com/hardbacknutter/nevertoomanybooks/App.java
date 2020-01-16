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
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.AttrRes;
import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

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

    /**
     * Users can select which fields they use / don't want to use.
     * Each field has an entry in the Preferences.
     * The key is suffixed with the name of the field.
     */
    public static final String PREFS_PREFIX_FIELD_VISIBILITY = "fields.visibility.";

    private static final String TAG = "App";
    /** don't assume / allow the day-night theme to have a different integer ID. */
    private static final int THEME_INVALID = -1;
    private static final int THEME_DAY_NIGHT = 0;
    private static final int THEME_DARK = 1;
    private static final int THEME_LIGHT = 2;

    /** we really only use the one. */
    private static final int NOTIFICATION_ID = 0;

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

    /** Used to sent notifications regarding tasks. */
    private static NotificationManager sNotifier;
    /** Cache the User-specified theme currently in use. '-1' to force an update at App startup. */
    @ThemeId
    private static int sCurrentThemeId = THEME_INVALID;
    /** The Locale used at startup; so that we can revert to system Locale if we want to. */
    private static Locale sSystemInitialLocale;

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
     * Get the Application Context <strong>with the user Locale applied</strong>.
     *
     * @return localised app context
     */
    @NonNull
    public static Context getLocalizedAppContext() {
        return LocaleUtils.applyLocale(sInstance.getApplicationContext());
    }

    /**
     * Get the name of this application's package.
     *
     * @return package name
     */
    public static String getAppPackageName() {
        return sInstance.getPackageName();
    }

    /**
     * Get the PackageInfo object containing information about the package.
     *
     * @param flags option flags for {@link PackageManager#getPackageInfo(String, int)}
     *
     * @return PackageInfo
     */
    @Nullable
    public static PackageInfo getPackageInfo(final int flags) {
        PackageInfo packageInfo = null;
        try {
            Context context = sInstance.getApplicationContext();
            // Get app info from the manifest
            PackageManager manager = context.getPackageManager();
            packageInfo = manager.getPackageInfo(context.getPackageName(), flags);
        } catch (@NonNull final PackageManager.NameNotFoundException ignore) {
        }
        return packageInfo;
    }

    /**
     * Convenience method.
     *
     * @param title   the title to display
     * @param message the message to display
     */
    public static void showNotification(@NonNull final CharSequence title,
                                        @NonNull final CharSequence message) {
        showNotification(sInstance.getApplicationContext(), title, message);
    }

    /**
     * Show a notification while this app is running.
     *
     * @param context Current context
     * @param title   the title to display
     * @param message the message to display
     */
    public static void showNotification(@NonNull final Context context,
                                        @NonNull final CharSequence title,
                                        @NonNull final CharSequence message) {

        // Create the notifier if not done yet.
        if (sNotifier == null) {
            sNotifier = (NotificationManager) sInstance.getApplicationContext()
                                                       .getSystemService(NOTIFICATION_SERVICE);
        }

        Intent intent = new Intent(context, StartupActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        Notification notification = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_info_outline)
                .setContentTitle(title)
                .setContentText(message)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        sNotifier.notify(NOTIFICATION_ID, notification);
    }

    /**
     * Get the resource id for the given attribute.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return resource ID
     */
    public static int getAttrResId(@NonNull final Context context,
                                   @AttrRes final int attr) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(attr, tv, true);
        return tv.resourceId;
    }

    /**
     * Get a color int value for the given attribute.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return A single color value in the form 0xAARRGGBB.
     */
    @ColorInt
    public static int getColorInt(@NonNull final Context context,
                                  @AttrRes final int attr) {
        Resources.Theme theme = context.getTheme();
        TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);
        return context.getResources().getColor(tv.resourceId, theme);
    }

    /**
     * Get the Attribute dimension value multiplied by the appropriate
     * metric and truncated to integer pixels.
     *
     * @param context Current context
     * @param attr    attribute id to resolve
     *                Must be a type that has a {@code android.R.attr.textSize} value.
     *
     * @return size in integer pixels, or -1 if not defined.
     */
    @SuppressWarnings("unused")
    public static int getTextSize(@NonNull final Context context,
                                  @AttrRes final int attr) {
        Resources.Theme theme = context.getTheme();
        TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);

        int[] textSizeAttr = new int[]{android.R.attr.textSize};
        int indexOfAttrTextSize = 0;
        TypedArray ta = context.obtainStyledAttributes(tv.data, textSizeAttr);
        int textSize = ta.getDimensionPixelSize(indexOfAttrTextSize, -1);
        ta.recycle();

        return textSize;
    }

    /**
     * Is the field in use; i.e. is it enabled in the user-preferences.
     *
     * @param fieldName to lookup
     *
     * @return {@code true} if the user wants to use this field.
     */
    public static boolean isUsed(@NonNull final String fieldName) {
        return PreferenceManager.getDefaultSharedPreferences(sInstance.getApplicationContext())
                                .getBoolean(PREFS_PREFIX_FIELD_VISIBILITY + fieldName, true);
    }

    /**
     * Hide the keyboard.
     */
    @SuppressWarnings("unused")
    public static void hideKeyboard(@NonNull final View view) {
        InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Read a string from the META tags in the Manifest.
     *
     * @param key The name of the meta data string to retrieve.
     *
     * @return the key, or the empty string if no key found.
     */
    @NonNull
    public static String getManifestString(@Nullable final String key) {
        ApplicationInfo ai;
        try {
            ai = sInstance.getApplicationContext().getPackageManager()
                          .getApplicationInfo(sInstance.getPackageName(),
                                              PackageManager.GET_META_DATA);
        } catch (@NonNull final PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }

        String result = ai.metaData.getString(key);
        if (result == null) {
            return "";
        }
        return result.trim();
    }

    /**
     * Test if the Theme has changed.
     *
     * @param themeId to check
     *
     * @return {@code true} if the theme was changed
     */
    public static boolean isThemeChanged(@ThemeId final int themeId) {
        // always reload from prefs.
        sCurrentThemeId = PIntString.getListPreference(Prefs.pk_ui_theme, DEFAULT_THEME);
        return themeId != sCurrentThemeId;
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
        sCurrentThemeId = PIntString.getListPreference(Prefs.pk_ui_theme, DEFAULT_THEME);

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

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.THEME) {
            dumpDayNightMode(sCurrentThemeId);
        }

        return sCurrentThemeId;
    }

    /**
     * DEBUG only.
     */
    private static void dumpDayNightMode(@ThemeId final int themeId) {
        if (BuildConfig.DEBUG /* always */) {
            StringBuilder sb = new StringBuilder();

            String varName = "sCurrentThemeId";
            switch (themeId) {
                case THEME_DAY_NIGHT:
                    sb.append(varName).append("=THEME_DAY_NIGHT");
                    break;
                case THEME_DARK:
                    sb.append(varName).append("=THEME_DARK");
                    break;
                case THEME_LIGHT:
                    sb.append(varName).append("=THEME_LIGHT");
                    break;
                case THEME_INVALID:
                    sb.append(varName).append("=THEME_INVALID");
                    break;

                default:
                    sb.append(varName).append("=eh? ").append(themeId);
                    break;

            }

            varName = "getDefaultNightMode";
            switch (AppCompatDelegate.getDefaultNightMode()) {
                case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:
                    sb.append("|").append(varName).append("=MODE_NIGHT_FOLLOW_SYSTEM");
                    break;
                //noinspection deprecation
                case AppCompatDelegate.MODE_NIGHT_AUTO_TIME:
                    sb.append("|").append(varName).append("=MODE_NIGHT_AUTO_TIME");
                    break;
                case AppCompatDelegate.MODE_NIGHT_NO:
                    sb.append("|").append(varName).append("=MODE_NIGHT_NO");
                    break;
                case AppCompatDelegate.MODE_NIGHT_YES:
                    sb.append("|").append(varName).append("=MODE_NIGHT_YES");
                    break;
                case AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY:
                    sb.append("|").append(varName).append("=MODE_NIGHT_AUTO_BATTERY");
                    break;
                case AppCompatDelegate.MODE_NIGHT_UNSPECIFIED:
                    sb.append("|").append(varName).append("=MODE_NIGHT_UNSPECIFIED");
                    break;
                default:
                    sb.append("|").append(varName).append("=Twilight Zone");
                    break;
            }

            int currentNightMode =
                    sInstance.getApplicationContext().getResources()
                             .getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            varName = "currentNightMode";
            switch (currentNightMode) {
                case Configuration.UI_MODE_NIGHT_NO:
                    sb.append("|").append(varName).append("=UI_MODE_NIGHT_NO");
                    break;
                case Configuration.UI_MODE_NIGHT_YES:
                    sb.append("|").append(varName).append("=UI_MODE_NIGHT_YES");
                    break;
                case Configuration.UI_MODE_NIGHT_UNDEFINED:
                    sb.append("|").append(varName).append("=UI_MODE_NIGHT_UNDEFINED");
                    break;
                default:
                    sb.append("|").append(varName).append("=Twilight Zone");
                    break;
            }

            Log.d(TAG, "dumpDayNightMode|" + sb);
        }
    }

    @SuppressWarnings("unused")
    public static boolean isRtl() {
        return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault())
               == View.LAYOUT_DIRECTION_RTL;
    }

    /**
     * Return the device Locale.
     *
     * @return the actual System Locale.
     */
    @NonNull
    public static Locale getSystemLocale() {
        if (sSystemInitialLocale == null) {
            sSystemInitialLocale = Locale.getDefault();
        }
        return sSystemInitialLocale;
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
        Configuration current = context.getResources().getConfiguration();
        Configuration configuration = new Configuration(current);
        String lang = desiredLocale.getLanguage();
        if (lang.length() == 2) {
            configuration.setLocale(desiredLocale);
        } else {
            // any 3-char code might need to be converted to 2-char be able to find the resource.
            configuration.setLocale(new Locale(LanguageUtils.getLocaleIsoFromIso3(lang)));
        }

        Context localizedContext = context.createConfigurationContext(configuration);
        return localizedContext.getResources();
    }

    /**
     * Reads the application version from the manifest.
     *
     * @return the version
     */
    public static long getVersion() {
        PackageInfo packageInfo = getPackageInfo(0);
        if (packageInfo != null) {
            if (Build.VERSION.SDK_INT >= 28) {
                return packageInfo.getLongVersionCode();
            } else {
                //noinspection deprecation
                return (long) packageInfo.versionCode;
            }
        }
        return 0;
    }

    /**
     * DEBUG.
     *
     * @return {@code true} if the current run is a JUnit test.
     */
    public static boolean isJUnitTest() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().startsWith("org.junit.")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Initialize ACRA for a given Application.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    protected void attachBaseContext(@NonNull final Context base) {
        super.attachBaseContext(base);

        ACRA.init(this);
        ACRA.getErrorReporter().putCustomData("Signed-By", DebugReport.signedBy(this));
    }

    /* ########################################################################################## */

    @Override
    public void onCreate() {
        // preserve startup==system Locale
        sSystemInitialLocale = Locale.getDefault();
        super.onCreate();
    }

    /**
     * Ensure to re-apply the user-preferred Locale to the Application (this) object.
     *
     * @param newConfig The new device configuration.
     */
    @Override
    @CallSuper
    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        String localeSpec = LocaleUtils.getPersistedLocaleSpec(sInstance.getApplicationContext());
        // override in the new config
        newConfig.setLocale(LocaleUtils.createLocale(localeSpec));
        // propagate to registered callbacks.
        super.onConfigurationChanged(newConfig);

        if (BuildConfig.DEBUG /* always */) {
            Locale locale;
            if (Build.VERSION.SDK_INT >= 24) {
                locale = newConfig.getLocales().get(0);
            } else {
                locale = newConfig.locale;
            }
            Log.d(TAG, "onConfigurationChanged|" + locale);
        }
    }

    @IntDef({THEME_INVALID, THEME_DAY_NIGHT, THEME_DARK, THEME_LIGHT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ThemeId {

    }
}
