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
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.StyleRes;
import android.util.TypedValue;

import com.eleybourn.bookcatalogue.debug.DebugReport;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.tasks.BCQueueManager;
import com.eleybourn.bookcatalogue.tasks.Terminator;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * BookCatalogue Application implementation. Useful for making globals available and for being a
 * central location for logically application-specific objects such as preferences.
 *
 * @author Philip Warner
 */
@ReportsCrashes(
        mailTo = "philip.warner@rhyme.com.au,eleybourn@gmail.com",
        mode = ReportingInteractionMode.DIALOG,
        customReportContent = {
                ReportField.USER_COMMENT,
                ReportField.USER_APP_START_DATE,
                ReportField.USER_CRASH_DATE,
                ReportField.APP_VERSION_NAME,
                ReportField.APP_VERSION_CODE,
                ReportField.ANDROID_VERSION,
                ReportField.PHONE_MODEL,
                ReportField.CUSTOM_DATA,
                ReportField.STACK_TRACE},
        //optional, displayed as soon as the crash occurs, before collecting data which can take a few seconds
        resToastText = R.string.crash_toast_text,
        resNotifTickerText = R.string.crash_notif_ticker_text,
        resNotifTitle = R.string.crash_notif_title,
        resNotifText = R.string.crash_notif_text,
        resDialogText = R.string.crash_dialog_text,
        // optional. default is your application name
        resDialogTitle = R.string.crash_dialog_title,
        // optional. when defined, adds a user text field input with this text resource as a label
        resDialogCommentPrompt = R.string.crash_dialog_comment_prompt,
        // optional. displays a Toast message when the user accepts to send a report.
        resDialogOkToast = R.string.crash_dialog_ok_toast
)

public class BookCatalogueApp extends Application {
    /** the name used for calls to Context.getSharedPreferences(name, ...) */
    public static final String APP_SHARED_PREFERENCES = "bookCatalogue";

    /**
     * NEWKIND: add new supported themes here and in R.array.supported_themes,
     * the string-array order must match the APP_THEMES order
     * The preferences choice will be build according to the string-array list/order.
     */
    public static final int DEFAULT_THEME = 0;

    private static final int[] APP_THEMES = {
            R.style.AppTheme,
            R.style.AppTheme_Light
    };
    private static final int[] DIALOG_THEMES = {
            R.style.AppTheme_Dialog,
            R.style.AppTheme_Light_Dialog
    };
    private static final int[] DIALOG_ALERT_THEMES = {
            R.style.AppTheme_Dialog_Alert,
            R.style.AppTheme_Dialog_Alert_Light
    };

    private static int mLastTheme;

    /**
     * Tests if the Theme has changed + updates the global setting
     *TODO: check OnSharedPreferenceChangeListener ?
     * @return  true is a change was detected
     */
    public synchronized static boolean hasThemeChanged() {
        int current = BCPreferences.getTheme(DEFAULT_THEME);
        if (current != mLastTheme) {
            mLastTheme = current;
            return true;
        }
        return false;
    }

    @StyleRes
    public static int getThemeResId() {
        return APP_THEMES[mLastTheme];
    }

    @StyleRes
    public static int getDialogThemeResId() {
        return DIALOG_THEMES[mLastTheme];
    }
    @SuppressWarnings("unused")
    @StyleRes
    public static int getDialogAlertThemeResId() {
        return DIALOG_ALERT_THEMES[mLastTheme];
    }

    /** Set of OnLocaleChangedListeners */
    private static final Set<WeakReference<OnLocaleChangedListener>> mOnLocaleChangedListeners = new HashSet<>();
    /** Never store a context in a static, use the instance instead */
    private static BookCatalogueApp mInstance;
    /** Used to sent notifications regarding tasks */
    private static NotificationManager mNotifier;

    private static BCQueueManager mQueueManager = null;
    /** List of supported locales */
    private static List<String> mSupportedLocales = null;
    /** The locale used at startup; so that we can revert to system locale if we want to */
    private static Locale mInitialLocale = Locale.getDefault();
    /** User-specified default locale */
    private static Locale mPreferredLocale = null;
    /** Last locale used so; cached so we can check if it has genuinely changed */
    private static Locale mLastLocale = null;

    /**
     * Tests if the Locale has changed + updates the global setting
     *TODO: check OnSharedPreferenceChangeListener ?
     *
     * @return  true is a change was detected
     */
    public synchronized static boolean hasLocalChanged(@NonNull final Resources res) {
        Locale current = mPreferredLocale;
        if ((current != null && !current.equals(mLastLocale)) || (current == null && mLastLocale != null)) {
            mLastLocale = current;
            applyPreferredLocaleIfNecessary(res);
            return true;
        }
        return false;
    }


    /**
     * Shared Preferences Listener
     *
     * Currently it just handles Locale changes and propagates it to any listeners.
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch (key) {
                case BCPreferences.PREF_APP_LOCALE:
                    String prefLocale = BCPreferences.getLocale();
                    if (prefLocale != null && !prefLocale.isEmpty()) {
                        mPreferredLocale = localeFromName(prefLocale);
                    } else {
                        mPreferredLocale = getSystemLocal();
                    }
                    applyPreferredLocaleIfNecessary(getBaseContext().getResources());
                    notifyLocaleChanged();
                    break;
                case BCPreferences.PREF_APP_THEME:
                    //TODO: implement global theme change ?
                    break;
                default:
                    break;
            }
        }
    };

    @SuppressWarnings("unused")
    public BookCatalogueApp() {
        super();
        mInstance = this;
    }

    /**
     * There seems to be something fishy in creating locales from full names (like en_AU),
     * so we split it and process it manually.
     *
     * @param name Locale name (eg. 'en_AU')
     *
     * @return Locale corresponding to passed name
     */
    @NonNull
    public static Locale localeFromName(@NonNull final String name) {
        String[] parts;
        if (name.contains("_")) {
            parts = name.split("_");
        } else {
            parts = name.split("-");
        }
        Locale locale;
        switch (parts.length) {
            case 1:
                locale = new Locale(parts[0]);
                break;
            case 2:
                locale = new Locale(parts[0], parts[1]);
                break;
            default:
                locale = new Locale(parts[0], parts[1], parts[2]);
                break;
        }
        return locale;
    }

    @NonNull
    public static Context getAppContext() {
        return mInstance.getApplicationContext();
    }

    /**
     * Add a new OnLocaleChangedListener, and cleanup any dead references.
     */
    public static void registerOnLocaleChangedListener(@NonNull final OnLocaleChangedListener listener) {
        List<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<>();

        boolean alreadyAdded = false;

        for (WeakReference<OnLocaleChangedListener> ref : mOnLocaleChangedListeners) {
            OnLocaleChangedListener localeChangedListener = ref.get();
            if (localeChangedListener == null) {
                toRemove.add(ref);
            } else if (localeChangedListener == listener) {
                alreadyAdded = true;
            }
        }
        if (!alreadyAdded) {
            mOnLocaleChangedListeners.add(new WeakReference<>(listener));
        }

        for (WeakReference<OnLocaleChangedListener> ref : toRemove) {
            mOnLocaleChangedListeners.remove(ref);
        }
    }

    /**
     * Remove the passed OnLocaleChangedListener, and cleanup any dead references.
     */
    public static void unregisterOnLocaleChangedListener(@NonNull final OnLocaleChangedListener listener) {
        List<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<>();

        for (WeakReference<OnLocaleChangedListener> ref : mOnLocaleChangedListeners) {
            OnLocaleChangedListener localeChangedListener = ref.get();
            if ((localeChangedListener == null) || (localeChangedListener == listener)) {
                toRemove.add(ref);
            }
        }
        for (WeakReference<OnLocaleChangedListener> ref : toRemove) {
            mOnLocaleChangedListeners.remove(ref);
        }
    }

    /**
     * Utility routine to get the current QueueManager.
     *
     * @return QueueManager object
     */
    public static BCQueueManager getQueueManager() {
        return mQueueManager;
    }

    /**
     * Wrapper to reduce explicit use of the 'context' member.
     *
     * @param resId Resource ID
     *
     * @return Localized resource string
     */
    public static String getResourceString(@StringRes final int resId) {
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
    public static String getResourceString(@StringRes final int resId, @Nullable final Object... objects) {
        return mInstance.getApplicationContext().getString(resId, objects).trim();
    }

    /**
     * Read a string from the META tags in the Manifest.
     *
     * @param name  string to read
     * @return      value
     */
    @NonNull
    public static String getManifestString(@Nullable final String name) {
        ApplicationInfo ai;
        try {
            ai = mInstance.getApplicationContext()
                    .getPackageManager()
                    .getApplicationInfo(mInstance.getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Logger.logError(e);
            throw new IllegalStateException("See log for PackageManager.NameNotFoundException");
        }
        String result = ai.metaData.getString(name);
        if (result == null) {
            throw new IllegalStateException();
        }
        return result.trim();
    }

    /**
     * Show a notification while this app is running.
     */
    public static void showNotification(@NonNull final Context context,
                                        @NonNull final String title,
                                        @NonNull final String message) {

        Intent intent = new Intent(context, StartupActivity.class);
        intent.setAction("android.intent.action.MAIN");
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

        mNotifier.notify(R.id.NOTIFICATION, notification);
    }

    /**
     * Set the current preferred locale in the passed resources.
     *
     * @param res Resources to use
     */
    private static void applyPreferredLocaleIfNecessary(@NonNull final Resources res) {
        if (mPreferredLocale == null || (res.getConfiguration().locale.equals(mPreferredLocale))) {
            return;
        }

        Locale.setDefault(mPreferredLocale);
        Configuration config = new Configuration();
        config.locale = mPreferredLocale;
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    /**
     * Get the list of supported locale names
     *
     * @return ArrayList of locale names
     */
    @NonNull
    public static List<String> getSupportedLocales() {
        if (mSupportedLocales == null) {
            mSupportedLocales = new ArrayList<>();
            mSupportedLocales.add("de_DE");
            mSupportedLocales.add("en_AU");
            mSupportedLocales.add("es_ES");
            mSupportedLocales.add("fr_FR");
            mSupportedLocales.add("it_IT");
            mSupportedLocales.add("nl_NL");
            mSupportedLocales.add("ru_RU");
            mSupportedLocales.add("tr_TR");
            mSupportedLocales.add("el_GR");
        }
        return mSupportedLocales;
    }

    @NonNull
    public static Locale getSystemLocal() {
        return mInitialLocale;
    }

    /** no point in storing a local reference, the thing itself is a singleton */
    @NonNull
    public static SharedPreferences getSharedPreferences() {
        return mInstance.getApplicationContext().getSharedPreferences(APP_SHARED_PREFERENCES, MODE_PRIVATE);
    }

    /**
     * Using the global app theme.
     *
     * @param attr  resource id to get
     *
     * @return resolved attribute
     */
    @SuppressWarnings("unused")
    public static int getAttr(final int attr){
        return getAttr(mInstance.getApplicationContext().getTheme(), attr);
    }

    /**
     *
     * @param theme     allows to override the app theme, f.e. with Dialog Themes
     * @param attr      resource id to get
     *
     * @return resolved attribute
     */
    public static int getAttr(@NonNull final Resources.Theme theme, @IdRes final int attr){
        TypedValue tv = new TypedValue();
        theme.resolveAttribute(attr, tv, true);
        return tv.resourceId;
    }
    /**
     * Most real initialization should go here, since before this point, the App is still
     * 'Under Construction'.
     */
    @Override
    public void onCreate() {
        // Get the preferred locale as soon as possible
        try {
            mInitialLocale = Locale.getDefault();
            String prefLocale = BCPreferences.getLocale();
            if (prefLocale != null && !prefLocale.isEmpty()) {
                mPreferredLocale = localeFromName(prefLocale);
                applyPreferredLocaleIfNecessary(getBaseContext().getResources());
            }
        } catch (Exception e) {
            // Not much we can do...we want locale set early, but not fatal if it fails.
            Logger.logError(e);
        }

        Terminator.init();

        ACRA.init(this);
        ACRA.getErrorReporter().putCustomData("TrackerEventsInfo", Tracker.getEventsInfo());
        ACRA.getErrorReporter().putCustomData("Signed-By", DebugReport.signedBy(this));

        // Create the notifier
        mNotifier = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Start the queue manager
        if (mQueueManager == null) {
            mQueueManager = new BCQueueManager(this.getApplicationContext());
        }

        // Initialise the Theme
        mLastTheme = BCPreferences.getTheme(DEFAULT_THEME);

        super.onCreate();

        // Watch the preferences and handle changes as necessary
        SharedPreferences p = getSharedPreferences();
        p.registerOnSharedPreferenceChangeListener(mPrefsListener);
    }

    /**
     * Send a message to all registered OnLocaleChangedListeners, and cleanup any dead references.
     */
    private void notifyLocaleChanged() {
        List<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<>();

        for (WeakReference<OnLocaleChangedListener> ref : mOnLocaleChangedListeners) {
            OnLocaleChangedListener listener = ref.get();
            if (listener == null) {
                toRemove.add(ref);
            } else {
                try {
                    listener.onLocaleChanged();
                } catch (Exception ignore) {
                }
            }
        }
        for (WeakReference<OnLocaleChangedListener> ref : toRemove) {
            mOnLocaleChangedListeners.remove(ref);
        }
    }

    /**
     * Monitor configuration changes (like rotation) to make sure we reset the locale.
     */
    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mPreferredLocale != null) {
            applyPreferredLocaleIfNecessary(getBaseContext().getResources());
        }
    }

    /**
     * Interface definition
     */
    public interface OnLocaleChangedListener {
        void onLocaleChanged();
    }
}
