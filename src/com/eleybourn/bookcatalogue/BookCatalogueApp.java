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
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.support.annotation.ArrayRes;
import android.support.annotation.AttrRes;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.TypedValue;

import com.eleybourn.bookcatalogue.debug.DebugReport;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.tasks.BCQueueManager;
import com.eleybourn.bookcatalogue.tasks.Terminator;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.ThemeUtils;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * BookCatalogue Application implementation. Useful for making globals available and for being a
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
                ReportField.THREAD_DETAILS,
//                ReportField.APPLICATION_LOG,
        }
        //optional, displayed as soon as the crash occurs, before collecting data which can take a few seconds
        , resToastText = R.string.crash_message_text
        , resNotifTickerText = R.string.crash_notif_ticker_text
        , resNotifTitle = R.string.crash_notif_title
        , resNotifText = R.string.crash_notif_text
        , resDialogText = R.string.crash_dialog_text
        // optional. default is your application name
        , resDialogTitle = R.string.crash_dialog_title
        // optional. when defined, adds a user text field input with this text resource as a label
        , resDialogCommentPrompt = R.string.crash_dialog_comment_prompt
        // optional. displays a message when the user accepts to send a report.
        , resDialogOkToast = R.string.crash_dialog_ok_message
//        ,applicationLogFile = ""
//        ,applicationLogFileLines = 1000
)

public class BookCatalogueApp extends Application {
    /** the name used for calls to Context.getSharedPreferences(name, ...) */
    private static final String APP_SHARED_PREFERENCES = "bookCatalogue";

    /** Implementation to use for {@link com.eleybourn.bookcatalogue.dialogs.StandardDialogs#showUserMessage} */
    public static final String PREF_APP_USER_MESSAGE = "App.UserMessage";

    /** we really only use the one */
    private static final int NOTIFICATION_ID = 0;

    /** Give static methods access to our singleton. Note: never store a context in a static, use the instance instead */
    private static BookCatalogueApp mInstance;

    /** Used to sent notifications regarding tasks */
    private static NotificationManager mNotifier;
    private static BCQueueManager mQueueManager = null;

    /**
     * Shared Preferences Listener
     */
    @Nullable
    private final SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    switch (key) {
                        case LocaleUtils.PREF_APP_LOCALE:
                            LocaleUtils.loadPreferred();
                            if (LocaleUtils.hasLocalReallyChanged()) {
                                // changing Locale is a global operation, so apply it here.
                                LocaleUtils.apply(getBaseContext().getResources());
                                LocaleUtils.notifyListeners();
                            }
                            break;

                        case ThemeUtils.PREF_APP_THEME:
                            ThemeUtils.loadPreferred();
                            if (ThemeUtils.hasThemeReallyChanged()) {
                                // applying Theme changes is a local operation, up to them
                                ThemeUtils.notifyListeners();
                            }
                            break;

                        default:
                            break;
                    }
                }
            };

    /** create a singleton */
    @SuppressWarnings("unused")
    public BookCatalogueApp() {
        super();
        mInstance = this;
    }

    /**
     * As per {@link ACRA#init} documentation:
     *
     * Initialize ACRA for a given Application.
     *
     * The call to this method should be placed as soon as possible in the {@link Application#attachBaseContext(Context)} method.
     *
     * @param base The new base context for this wrapper.
     */
    @Override
    @CallSuper
    protected void attachBaseContext(final @NonNull Context base) {
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
        try {
            LocaleUtils.loadPreferred();
            LocaleUtils.apply(getBaseContext().getResources());
        } catch (Exception e) {
            // Not much we can do...we want locale set early, but not fatal if it fails.
            Logger.error(e);
        }

        Terminator.init();

        // Create the notifier
        mNotifier = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Start the queue manager
        if (mQueueManager == null) {
            mQueueManager = new BCQueueManager(mInstance.getApplicationContext());
        }

        super.onCreate();

        // Watch the preferences and handle changes as necessary
        getSharedPreferences().registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
    }

    /**
     * Monitor configuration changes (like rotation) to make sure we reset the locale
     * TEST: why update the locale when the config changed ? we're already doing this in SharePreferences listener
     */
    @Override
    @CallSuper
    public void onConfigurationChanged(final @NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        LocaleUtils.apply(getBaseContext().getResources());
    }

    @NonNull
    public static Context getAppContext() {
        return mInstance.getApplicationContext();
    }

    /**
     * Get the current QueueManager.
     *
     * @return QueueManager object
     */
    @NonNull
    public static BCQueueManager getQueueManager() {
        return mQueueManager;
    }

    /**
     * Show a notification while this app is running.
     */
    public static void showNotification(final @NonNull Context context,
                                        final @NonNull String title,
                                        final @NonNull String message) {

        Intent intent = new Intent(context, StartupActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        Notification notification = new Notification.Builder(mInstance.getApplicationContext())
                .setSmallIcon(R.drawable.ic_info_outline)
                .setContentTitle(title)
                .setContentText(message)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                // The PendingIntent to launch our activity if the user selects this notification
                .setContentIntent(PendingIntent.getActivity(mInstance.getApplicationContext(), 0, intent, 0))
                .build();

        mNotifier.notify(NOTIFICATION_ID, notification);
    }

    /**
     * Read a string from the META tags in the Manifest.
     *
     * @param name string to read
     *
     * @return value
     */
    @NonNull
    public static String getManifestString(final @Nullable String name) {
        ApplicationInfo ai;
        try {
            ai = mInstance.getApplicationContext()
                    .getPackageManager()
                    .getApplicationInfo(mInstance.getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Logger.error(e);
            throw new IllegalStateException();
        }
        String result = ai.metaData.getString(name);
        Objects.requireNonNull(result);
        return result.trim();
    }

    /**
     * Using the global app theme.
     *
     * @param attr resource id to get
     *
     * @return resolved attribute
     */
    @SuppressWarnings("unused")
    public static int getAttr(final @AttrRes int attr) {
        return getAttr(mInstance.getApplicationContext().getTheme(), attr);
    }

    /**
     * @param theme allows to override the app theme, f.e. with Dialog Themes
     * @param attr  resource id to get
     *
     * @return resolved attribute
     */
    public static int getAttr(final @NonNull Resources.Theme theme, final @AttrRes int attr) {
        TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);
        return tv.resourceId;
    }

    /**
     * Wrapper to reduce explicit use of the 'context' member.
     *
     * @param resId Resource ID
     *
     * @return Localized resource string
     */
    public static String getResourceString(final @StringRes int resId) {
        return mInstance.getApplicationContext().getString(resId).trim();
    }

    /**
     * Wrapper to reduce explicit use of the 'context' member.
     *
     * @param resId Resource ID
     *
     * @return Localized resource string[]
     */
    public static String[] getResourceStringArray(@ArrayRes final int resId) {
        return mInstance.getApplicationContext().getResources().getStringArray(resId);
    }

    /**
     * Wrapper to reduce explicit use of the 'context' member.
     *
     * @param resId Resource ID
     *
     * @return Localized resource string
     */
    @NonNull
    public static String getResourceString(final @StringRes int resId, final @Nullable Object... objects) {
        return mInstance.getApplicationContext().getString(resId, objects).trim();
    }

    @NonNull
    public static SharedPreferences getSharedPreferences() {
        // no point in storing a local reference, the thing itself is a singleton
        return mInstance.getApplicationContext().getSharedPreferences(BookCatalogueApp.APP_SHARED_PREFERENCES, Context.MODE_PRIVATE);
    }

    /** ClassCastException protected - Get a named boolean preference */
    public static boolean getBooleanPreference(final @NonNull String name, final boolean defaultValue) {
        boolean result;
        try {
            result = getSharedPreferences().getBoolean(name, defaultValue);
        } catch (ClassCastException e) {
            result = defaultValue;
        }
        return result;
    }

    /** ClassCastException protected - Get a named int preference */
    public static int getIntPreference(final @NonNull String name, final int defaultValue) {
        int result;
        try {
            result = getSharedPreferences().getInt(name, defaultValue);
        } catch (ClassCastException e) {
            result = defaultValue;
        }
        return result;
    }

    /** ClassCastException protected - Get a named string preference */
    @Nullable
    public static String getStringPreference(final @Nullable String name, final @Nullable String defaultValue) {
        String result;
        try {
            result = getSharedPreferences().getString(name, defaultValue);
        } catch (ClassCastException e) {
            result = defaultValue;
        }
        return result;
    }

    /**
     * DEBUG method
     */
    @SuppressWarnings("unused")
    public static void dumpPreferences() {
        if (/* always show debug */ BuildConfig.DEBUG) {
            StringBuilder sb = new StringBuilder("\n\nSharedPreferences: ");
            Map<String, ?> map = getSharedPreferences().getAll();
            List<String> keyList = new ArrayList<>(map.keySet());
            String[] keys = keyList.toArray(new String[]{});
            Arrays.sort(keys);

            for (String key : keys) {
                Object value = map.get(key);
                sb.append("\n").append(key).append("=").append(value);
            }
            sb.append("\n\n");
            Logger.info(BookCatalogueApp.class, sb.toString());
        }
    }
}
