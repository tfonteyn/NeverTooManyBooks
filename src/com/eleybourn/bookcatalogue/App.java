/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceManager;

import java.util.Locale;
import java.util.Set;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.annotation.AcraCore;
import org.acra.annotation.AcraDialog;
import org.acra.annotation.AcraMailSender;
import org.acra.annotation.AcraToast;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.DebugReport;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.settings.Prefs;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

/**
 * Application implementation. Useful for making globals available and for being a
 * central location for logically application-specific objects such as preferences.
 *
 * @author Philip Warner
 */
@AcraMailSender(
        //mailTo = "philip.warner@rhyme.com.au,eleybourn@gmail.com",
        mailTo = "test@local.net")
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
@AcraCore(reportContent = {
        ReportField.APP_VERSION_CODE,
        ReportField.APP_VERSION_NAME,
        ReportField.PACKAGE_NAME,
        ReportField.PHONE_MODEL,
        ReportField.ANDROID_VERSION,
        ReportField.BUILD,
        ReportField.BRAND,
        ReportField.PRODUCT,
        ReportField.TOTAL_MEM_SIZE,
        ReportField.AVAILABLE_MEM_SIZE,

        ReportField.CUSTOM_DATA,
        ReportField.STACK_TRACE,
        ReportField.STACK_TRACE_HASH,
        ReportField.DISPLAY,

        ReportField.USER_COMMENT,
        ReportField.USER_APP_START_DATE,
        ReportField.USER_CRASH_DATE,
        ReportField.THREAD_DETAILS},

        resReportSendSuccessToast = R.string.acra_resReportSendSuccessToast,
        resReportSendFailureToast = R.string.error_email_failed)
public class App
        extends Application {

    /**
     * Users can select which fields they use / don't want to use.
     * <p>
     * Each field has an entry in the Preferences.
     * <p>
     * The key is suffixed with the name of the field.
     */
    public static final String PREFS_FIELD_VISIBILITY = "fields.visibility.";

    /** don't assume / allow the day-night theme to have a different integer id. */
    private static final int THEME_DAY_NIGHT = 0;

    /** we really only use the one. */
    private static final int NOTIFICATION_ID = 0;

    /** Activity is in need of recreating. */
    private static final int ACTIVITY_NEEDS_RECREATING = 1;
    /** Checked in onResume() so not to start tasks etc. */
    private static final int ACTIVITY_IS_RECREATING = 2;

    /**
     * NEWKIND: APP THEME.
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
    private static final int DEFAULT_THEME = 0;

    /**
     * As defined in res/themes.xml.
     * <p>
     * MODE_NIGHT_AUTO_BATTERY  <item>Set by Battery Saver</item>
     * MODE_NIGHT_FOLLOW_SYSTEM <item>Use system default</item>  API28+
     * <item>Day / Night</item>
     * MODE_NIGHT_YES
     * <item>Dark</item>
     * MODE_NIGHT_NO
     * <item>Light</item>
     */
    private static final int[] APP_THEMES = {
            R.style.AppTheme_DayNight,
            R.style.AppTheme_Dark,
            R.style.AppTheme_Light,
            };
    /**
     * internal; check if an Activity should do a 'recreate()'.
     * See {@link BaseActivity} in the onResume method.
     */
    private static int sActivityRecreateStatus;
    /**
     * Give static methods access to our singleton.
     * <b>Note:</b> never store a context in a static, use the instance instead
     */
    private static App sInstance;
    /** Used to sent notifications regarding tasks. */
    private static NotificationManager sNotifier;
    /** Cache the User-specified theme currently in use. '-1' to force an update at App startup. */
    private static int sCurrentTheme = -1;

    /** create a singleton. */
    @SuppressWarnings("unused")
    public App() {
        sInstance = this;
    }

    /**
     * WARNING: try not to use this to get resource strings!
     * Doing so can return inconsistent translations.
     * Only use when you're absolutely sure there is no other option.
     *
     * @return Application Context.
     */
    @NonNull
    public static Context getAppContext() {
        return sInstance.getApplicationContext();
    }

    /**
     * @return the name of this application's package.
     */
    public static String getAppPackageName() {
        return sInstance.getApplicationContext().getPackageName();
    }

    /**
     * @param flags option flags for {@link PackageManager#getPackageInfo(String, int)}
     *
     * @return A PackageInfo object containing information about the package.
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
     * Show a notification while this app is running.
     *
     * @param context Current context
     * @param titleId string resource for the title
     * @param message the message to display
     */
    public static void showNotification(@NonNull final Context context,
                                        @StringRes final int titleId,
                                        @NonNull final String message) {

        // Create the notifier if not done yet.
        if (sNotifier == null) {
            sNotifier = (NotificationManager)
                    sInstance.getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
        }

        Intent intent = new Intent(context, StartupActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        Notification notification = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_info_outline)
                .setContentTitle(context.getString(titleId))
                .setContentText(message)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        sNotifier.notify(NOTIFICATION_ID, notification);
    }

    /**
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return resource id
     */
    @IdRes
    public static int getAttr(@NonNull final Context context,
                              @AttrRes final int attr) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(attr, tv, true);
        return tv.resourceId;
    }

    /**
     * @param context Current context
     * @param attr    attribute id to resolve
     *
     * @return A single color value in the form 0xAARRGGBB.
     */
    @SuppressWarnings("unused")
    @ColorInt
    public static int getColor(@NonNull final Context context,
                               @AttrRes final int attr) {
        Resources.Theme theme = context.getTheme();
        TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);
        //API: 23
        return context.getResources().getColor(tv.resourceId, theme);
//        return context.getResources().getColor(tv.resourceId);
    }

    /**
     * @param context Current context
     * @param attr    attribute id to resolve
     *                Must be a type that has "android.R.attr.textSize" value.
     *
     * @return Attribute dimension value multiplied by the appropriate
     * metric and truncated to integer pixels, or -1 if not defined.
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
     * Read a string from the META tags in the Manifest.
     *
     * @param name string to read
     *
     * @return the key, or the empty string if no key found.
     */
    @NonNull
    public static String getManifestString(@Nullable final String name) {
        ApplicationInfo ai;
        try {
            ai = sInstance.getApplicationContext()
                          .getPackageManager()
                          .getApplicationInfo(sInstance.getPackageName(),
                                              PackageManager.GET_META_DATA);
        } catch (@NonNull final PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }
        String result = ai.metaData.getString(name);
        if (result == null) {
            return "";
        }
        return result.trim();
    }

    /**
     * @return the global SharedPreference
     */
    @NonNull
    public static SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(sInstance.getApplicationContext());
    }

    /**
     * @param uuid name of the preference file to get
     *
     * @return the SharedPreference
     */
    @NonNull
    public static SharedPreferences getPrefs(@NonNull final String uuid) {
        return sInstance.getApplicationContext().getSharedPreferences(uuid, MODE_PRIVATE);
    }

    /**
     * Get a global preference String. Null values results are returned as an empty string.
     *
     * @return the preference value string, can be empty, but never {@code null}
     */
    @NonNull
    public static String getPrefString(@NonNull final String key) {
        String value = getPrefs().getString(key, null);
        return value != null ? value : "";
    }

    /**
     * {@link ListPreference} store the selected value as a String.
     * But they are really Integer values. Hence this transmogrification....
     *
     * @return int (stored as String) global preference
     */
    public static int getListPreference(@NonNull final String key,
                                        final int defaultValue) {
        String value = getPrefs().getString(key, null);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    /**
     * {@link MultiSelectListPreference} store the selected value as a StringSet.
     * But they are really Integer values. Hence this transmogrification....
     *
     * @return int (stored as StringSet) global preference
     */
    public static Integer getMultiSelectListPreference(@NonNull final String key,
                                                       final int defaultValue) {
        Set<String> value = getPrefs().getStringSet(key, null);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Prefs.toInteger(value);
    }

    public static boolean isRtl() {
        return TextUtils.getLayoutDirectionFromLocale(LocaleUtils.getPreferredLocal())
                == View.LAYOUT_DIRECTION_RTL;
    }

    /**
     * @return the current Theme resource id.
     */
    @StyleRes
    public static int getThemeResId() {
        return APP_THEMES[sCurrentTheme];
    }

    /**
     * Apply the user's preferred Theme (if it has changed).
     *
     * @param context Current context to apply the theme to.
     *
     * @return {@code true} if the theme was changed
     */
    public static boolean isThemeChanged(@NonNull final Context context) {
        int theme = App.getListPreference(Prefs.pk_ui_theme, DEFAULT_THEME);
        boolean changed = theme != sCurrentTheme;

        sCurrentTheme = theme;

        if (sCurrentTheme == THEME_DAY_NIGHT) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_UNSPECIFIED);
        }

        if (changed) {
            context.setTheme(sCurrentTheme);
        }

        if (BuildConfig.DEBUG) {
            debugDayNightMode();
        }
        return changed;
    }

    public static void setNeedsRecreating() {
        sActivityRecreateStatus = ACTIVITY_NEEDS_RECREATING;
    }

    public static boolean isInNeedOfRecreating() {
        return sActivityRecreateStatus == ACTIVITY_NEEDS_RECREATING;
    }

    public static void setIsRecreating() {
        sActivityRecreateStatus = ACTIVITY_IS_RECREATING;
    }

    public static boolean isRecreating() {
        return sActivityRecreateStatus == ACTIVITY_IS_RECREATING;
    }

    public static void clearRecreateFlag() {
        sActivityRecreateStatus = 0;
    }

    /**
     * Is the field in use; i.e. is it enabled in the user-preferences.
     *
     * @param fieldName to lookup
     *
     * @return {@code true} if the user wants to use this field.
     */
    public static boolean isUsed(@NonNull final String fieldName) {
        return getPrefs().getBoolean(PREFS_FIELD_VISIBILITY + fieldName, true);
    }

    /**
     * DEBUG only.
     */
    private static void debugDayNightMode() {

        switch (sCurrentTheme) {
            case 0:
                Logger.debug(App.class, "debugDayNightMode",
                             "sCurrentTheme=THEME_DAY_NIGHT");
                break;
            case 1:
                Logger.debug(App.class, "debugDayNightMode",
                             "sCurrentTheme=THEME_DARK");
                break;
            case 2:
                Logger.debug(App.class, "debugDayNightMode",
                             "sCurrentTheme=THEME_LIGHT");
                break;
            case 3:
                Logger.debug(App.class, "debugDayNightMode",
                             "sCurrentTheme=THEME_LIGHT2");
                break;
            default:
                Logger.debug(App.class, "debugDayNightMode",
                             "sCurrentTheme=eh?");
                break;
        }


        int defNightMode = AppCompatDelegate.getDefaultNightMode();

        switch (defNightMode) {
            case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:
                Logger.debug(App.class, "debugDayNightMode",
                             "getDefaultNightMode=MODE_NIGHT_FOLLOW_SYSTEM");
                break;
            case AppCompatDelegate.MODE_NIGHT_AUTO_TIME:
                Logger.debug(App.class, "debugDayNightMode",
                             "getDefaultNightMode=MODE_NIGHT_AUTO_TIME");
                break;
            case AppCompatDelegate.MODE_NIGHT_NO:
                Logger.debug(App.class, "debugDayNightMode",
                             "getDefaultNightMode=MODE_NIGHT_NO");
                break;
            case AppCompatDelegate.MODE_NIGHT_YES:
                Logger.debug(App.class, "debugDayNightMode",
                             "getDefaultNightMode=MODE_NIGHT_YES");
                break;
            case AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY:
                Logger.debug(App.class, "debugDayNightMode",
                             "getDefaultNightMode=MODE_NIGHT_AUTO_BATTERY");
                break;
            case AppCompatDelegate.MODE_NIGHT_UNSPECIFIED:
                Logger.debug(App.class, "debugDayNightMode",
                             "getDefaultNightMode=MODE_NIGHT_UNSPECIFIED");
                break;
            default:
                Logger.debug(App.class, "debugDayNightMode",
                             "getDefaultNightMode=Twilight Zone");
                break;

        }
        int currentNightMode = getAppContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        switch (currentNightMode) {
            case Configuration.UI_MODE_NIGHT_NO:
                Logger.debug(App.class, "debugDayNightMode",
                             "currentNightMode=UI_MODE_NIGHT_NO");
                break;
            case Configuration.UI_MODE_NIGHT_YES:
                Logger.debug(App.class, "debugDayNightMode",
                             "currentNightMode=UI_MODE_NIGHT_YES");
                break;
            case Configuration.UI_MODE_NIGHT_UNDEFINED:
                Logger.debug(App.class, "debugDayNightMode",
                             "currentNightMode=UI_MODE_NIGHT_UNDEFINED");
                break;
            default:
                Logger.debug(App.class, "debugDayNightMode",
                             "currentNightMode=Twilight Zone");
                break;
        }
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
        ACRA.getErrorReporter().putCustomData("TrackerEventsInfo", Tracker.getEventsInfo());
        ACRA.getErrorReporter().putCustomData("Signed-By", DebugReport.signedBy(this));
    }

    @Override
    @CallSuper
    public void onCreate() {
        // Get the preferred locale as soon as possible
        setSystemLocale();

        // load the preferred theme from preferences.
        isThemeChanged(App.getAppContext());

        // create the singleton QueueManager
        QueueManager.init();

        super.onCreate();
    }

    /**
     * Ensure to re-apply our internal user-preferred Locale to the Application (this) object.
     *
     * @param newConfig The new device configuration.
     */
    @Override
    @CallSuper
    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        // same as in onCreate
        setSystemLocale();

        // override in the new config
        newConfig.setLocale(LocaleUtils.getPreferredLocal());
        // propagate to registered callbacks.
        super.onConfigurationChanged(newConfig);

        if (BuildConfig.DEBUG /* always */) {
            //API: 24: newConfig.getLocales().get(0)
            Logger.debug(this, "onConfigurationChanged", newConfig.locale);
        }

    }

    private void setSystemLocale() {
        try {
            LocaleUtils.init(Locale.getDefault());
            LocaleUtils.applyPreferred(getBaseContext());
        } catch (@NonNull final RuntimeException e) {
            // Not much we can do...we want locale set early, but not fatal if it fails.
            Logger.error(this, e);
        }
    }
}
