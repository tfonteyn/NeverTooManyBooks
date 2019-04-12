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

import android.app.Activity;
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
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceManager;

import java.util.Locale;
import java.util.Set;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.DebugReport;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Application implementation. Useful for making globals available and for being a
 * central location for logically application-specific objects such as preferences.
 *
 * @author Philip Warner
 */
@ReportsCrashes(
        //mailTo = "philip.warner@rhyme.com.au,eleybourn@gmail.com",
        mailTo = "test@local.net",
        mode = ReportingInteractionMode.DIALOG,
        customReportContent = {
                ReportField.APP_VERSION_CODE,
                ReportField.APP_VERSION_NAME,
                ReportField.PACKAGE_NAME,
                ReportField.PHONE_MODEL,
                ReportField.ANDROID_VERSION,
                ReportField.BUILD,
                ReportField.PRODUCT,
                ReportField.TOTAL_MEM_SIZE,
                ReportField.AVAILABLE_MEM_SIZE,

                ReportField.CUSTOM_DATA,
                ReportField.STACK_TRACE,
                ReportField.DISPLAY,

                ReportField.USER_COMMENT,
                ReportField.USER_APP_START_DATE,
                ReportField.USER_CRASH_DATE,
                ReportField.THREAD_DETAILS
//                ReportField.APPLICATION_LOG,
        },
        //optional, displayed as soon as the crash occurs,
        // before collecting data which can take a few seconds
        resToastText = R.string.acra_resToastText,
        resNotifTickerText = R.string.acra_resNotifTickerText,
        resNotifTitle = R.string.acra_resNotifTitle,
        resNotifText = R.string.acra_resNotifText,
        resDialogText = R.string.acra_resDialogText,
        // optional. default is your application name
        resDialogTitle = R.string.acra_resDialogTitle,
        // optional. when defined, adds a user text field input with this text resource as a label
        resDialogCommentPrompt = R.string.acra_resDialogCommentPrompt,
        // optional. displays a message when the user accepts to send a report.
        resDialogOkToast = R.string.acra_resDialogOkToast
//        ,applicationLogFile = ""
//        ,applicationLogFileLines = 1000
)
public class App
        extends Application {

    /** Legacy preferences name, pre-v200. */
    public static final String PREF_LEGACY_BOOK_CATALOGUE = "bookCatalogue";
    /** we really only use the one. */
    private static final int NOTIFICATION_ID = 0;

    /**
     * internal; check if an Activity should do a 'recreate()'.
     * See {@link BaseActivity#onResume}.
     */
    private static final String PREF_ACTIVITY_RECREATE_STATUS = "App.RecreateActivityStatus";
    /** Activity is in need of recreating. */
    private static final int ACTIVITY_NEEDS_RECREATING = 1;
    /** Checked in onResume() so not to start tasks etc. */
    private static final int ACTIVITY_IS_RECREATING = 2;

    /**
     * NEWKIND: APP THEME.
     * 1. add it to themes.xml
     * 2. add it to {@link R.array#pv_ui_theme},
     * the string-array order must match the APP_THEMES order
     * The preferences choice will be build according to the string-array list/order.
     */
    private static final int DEFAULT_THEME = 1;
    /** As defined in res/themes.xml. */
    private static final int[] APP_THEMES = {
            R.style.AppTheme_Dark,
            R.style.AppTheme_Light_Blue,
            R.style.AppTheme_Light_Red,
            };

    /**
     * Give static methods access to our singleton.
     * Note: never store a context in a static, use the instance instead
     */
    private static App sInstance;
    /** Used to sent notifications regarding tasks. */
    private static NotificationManager sNotifier;
    /** Cache the User-specified theme currently in use. */
    private static int sCurrentTheme;

    /** create a singleton. */
    @SuppressWarnings("unused")
    public App() {
        sInstance = this;
    }

    /**
     *
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
     * Reads the application version from the manifest.
     *
     * @return the versionCode.
     */
    public static long getVersion() {
        // versionCode deprecated and new method in API: 28, till then ignore...
        PackageInfo packageInfo = getPackageInfo(0);
        if (packageInfo != null) {
            return (long) packageInfo.versionCode;
        }
        return 0;
    }

    /** @return the name of this application's package. */
    public static String getAppPackageName() {
        return sInstance.getApplicationContext().getPackageName();
    }

    /** @return A PackageInfo object containing information about the package. */
    @Nullable
    public static PackageInfo getPackageInfo(final int flags) {
        PackageInfo packageInfo = null;
        try {
            Context context = sInstance.getApplicationContext();
            // Get app info from the manifest
            PackageManager manager = context.getPackageManager();
            packageInfo = manager.getPackageInfo(context.getPackageName(), flags);
        } catch (PackageManager.NameNotFoundException ignore) {

        }
        return packageInfo;
    }

    /**
     * Show a notification while this app is running.
     */
    public static void showNotification(@NonNull final Context context,
                                        @StringRes final int titleId,
                                        @NonNull final String message) {

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
     * Get the textSize attribute of the standard "TextAppearance_Small" style.
     * API 23 required to use it directly: {@link TextView#setTextAppearance(int)}
     *
     * @param context of caller
     *
     * @return the size
     */
    public static int getTextAppearanceSmallTextSizeInPixels(@NonNull final Context context) {

        int[] attrs = {android.R.attr.textSize};
        TypedArray ta = context.obtainStyledAttributes(android.R.style.TextAppearance_Small,
                                                       attrs);
        int size = ta.getDimensionPixelSize(0, 14);
        ta.recycle();
        return size;
    }

    /**
     * Using the global app theme.
     *
     * @param attr resource id to get
     *
     * @return resolved attribute
     */
    @SuppressWarnings("unused")
    public static int getAttr(@AttrRes final int attr) {
        return getAttr(sInstance.getApplicationContext().getTheme(), attr);
    }

    /**
     * @param theme allows to override the app theme, e.g. with Dialog Themes
     * @param attr  resource id to get
     *
     * @return resolved attribute
     */
    public static int getAttr(@NonNull final Resources.Theme theme,
                              @AttrRes final int attr) {
        TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);
        return tv.resourceId;
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
        } catch (PackageManager.NameNotFoundException e) {
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
     * Get a global preference String.
     *
     * @return the string, can be empty but never null
     */
    @NonNull
    public static String getPrefString(@NonNull final String key) {
        String sValue = getPrefs().getString(key, null);
        return sValue != null ? sValue : "";
    }

    /**
     * {@link ListPreference} store the selected value as a String.
     * But they are really Integer values. Hence this transmogrification....
     *
     * @return int (stored as String) global preference
     */
    public static int getListPreference(@NonNull final String key,
                                        final int defaultValue) {
        String sValue = getPrefs().getString(key, null);
        if (sValue == null || sValue.isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(sValue);
    }

    /**
     * {@link MultiSelectListPreference} store the selected value as a StringSet.
     * But they are really Integer values. Hence this transmogrification....
     *
     * @return int (stored as StringSet) global preference
     */
    public static Integer getMultiSelectListPreference(@NonNull final String key,
                                                       final int defaultValue) {
        Set<String> sValue = getPrefs().getStringSet(key, null);
        if (sValue == null || sValue.isEmpty()) {
            return defaultValue;
        }
        return Utils.toInteger(sValue);
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
     * @return <tt>true</tt> if the theme was changed
     */
    public static boolean applyTheme(@NonNull final Activity activity) {
        int theme = App.getListPreference(Prefs.pk_ui_theme, DEFAULT_THEME);

        if (theme != sCurrentTheme) {
            sCurrentTheme = theme;
            activity.setTheme(APP_THEMES[sCurrentTheme]);
            return true;
        }
        return false;
    }

    public static void setNeedsRecreating() {
        getPrefs().edit().putInt(PREF_ACTIVITY_RECREATE_STATUS, ACTIVITY_NEEDS_RECREATING).apply();
    }

    public static boolean isInNeedOfRecreating() {
        return getPrefs().getInt(PREF_ACTIVITY_RECREATE_STATUS, 0) == ACTIVITY_NEEDS_RECREATING;
    }

    public static void setIsRecreating() {
        getPrefs().edit().putInt(PREF_ACTIVITY_RECREATE_STATUS, ACTIVITY_IS_RECREATING).apply();
    }

    public static boolean isRecreating() {
        return getPrefs().getInt(PREF_ACTIVITY_RECREATE_STATUS, 0) == ACTIVITY_IS_RECREATING;
    }

    public static void clearRecreateFlag() {
        getPrefs().edit().remove(PREF_ACTIVITY_RECREATE_STATUS).apply();
    }

    /**
     * Initialize ACRA for a given Application.
     *
     * @param base The new base context for this wrapper.
     */
    @Override
    @CallSuper
    protected void attachBaseContext(@NonNull final Context base) {
        super.attachBaseContext(base);

        ACRA.init(this);
        ACRA.getErrorReporter().putCustomData("TrackerEventsInfo", Tracker.getEventsInfo());
        ACRA.getErrorReporter().putCustomData("Signed-By", DebugReport.signedBy(this));
    }

    /**
     * Most real initialization should go here, since before this point, the App is still
     * 'Under Construction'.
     */
    @Override
    @CallSuper
    public void onCreate() {
        // Get the preferred locale as soon as possible
        setSystemLocale();

        // cache the preferred theme.
        sCurrentTheme = App.getListPreference(Prefs.pk_ui_theme, DEFAULT_THEME);

        // Create the notifier
        sNotifier = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        super.onCreate();
    }

    private void setSystemLocale() {
        try {
            LocaleUtils.init(Locale.getDefault());
            LocaleUtils.applyPreferred(this);
        } catch (RuntimeException e) {
            // Not much we can do...we want locale set early, but not fatal if it fails.
            Logger.error(this, e);
        }
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

        if (BuildConfig.DEBUG) {
            //API 24: newConfig.getLocales().get(0)
            Logger.debug(this,
                  "onConfigurationChanged",
                           "" + newConfig.locale);
        }

    }
}
