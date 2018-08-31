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
import android.util.TypedValue;

import com.eleybourn.bookcatalogue.debug.DebugReport;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.BcQueueManager;
import com.eleybourn.bookcatalogue.utils.Terminator;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.acra.collector.CrashReportData;
import org.acra.sender.ReportSenderException;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/**
 * BookCatalogue Application implementation. Useful for making globals available
 * and for being a central location for logically application-specific objects such
 * as preferences.
 *
 * @author Philip Warner
 */
@ReportsCrashes(formKey = "", // will not be used
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
        //resNotifIcon = android.R.drawable.stat_notify_error, // optional. default is a warning sign
        resDialogText = R.string.crash_dialog_text,
        resDialogIcon = android.R.drawable.ic_dialog_info, //optional. default is a warning sign
        resDialogTitle = R.string.crash_dialog_title, // optional. default is your application name
        resDialogCommentPrompt = R.string.crash_dialog_comment_prompt, // optional. when defined, adds a user text field input with this text resource as a label
        resDialogOkToast = R.string.crash_dialog_ok_toast // optional. displays a Toast message when the user accepts to send a report.
)

public class BookCatalogueApp extends Application {

    /**
     * Set of OnLocaleChangedListeners
     */
    private static final HashSet<WeakReference<OnLocaleChangedListener>> mOnLocaleChangedListeners = new HashSet<>();
    /**
     * don't store the app context, use the instance instead
     */
    private static BookCatalogueApp mInstance;

    /**
     * Used to sent notifications regarding tasks
     */
    private static NotificationManager mNotifier;
    private static BcQueueManager mQueueManager = null;
    /**
     * The locale used at startup; so that we can revert to system locale if we want to
     */
    private static Locale mInitialLocale = null;
    /**
     * User-specified default locale
     */
    private static Locale mPreferredLocale = null;
    /**
     * List of supported locales
     */
    private static ArrayList<String> mSupportedLocales = null;
    /**
     * Shared Preferences Listener
     * <p>
     * Currently it just handles Locale changes and propagates it to any listeners.
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch (key) {
                case BookCataloguePreferences.PREF_APP_LOCALE:
                    String prefLocale = BookCataloguePreferences.getLocale();
                    if (prefLocale != null && !prefLocale.isEmpty()) {
                        mPreferredLocale = localeFromName(prefLocale);
                    } else {
                        mPreferredLocale = getSystemLocal();
                    }
                    applyPreferredLocaleIfNecessary(getBaseContext().getResources());
                    notifyLocaleChanged();
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * Constructor.
     */
    public BookCatalogueApp() {
        super();
        mInstance = this;
        mInitialLocale = Locale.getDefault();
    }

    /**
     * There seems to be something fishy in creating locales from full names (like en_AU),
     * so we split it and process it manually.
     *
     * @param name Locale name (eg. 'en_AU')
     *
     * @return Locale corresponding to passed name
     */
    public static Locale localeFromName(String name) {
        String[] parts;
        if (name.contains("_")) {
            parts = name.split("_");
        } else {
            parts = name.split("-");
        }
        Locale l;
        switch (parts.length) {
            case 1:
                l = new Locale(parts[0]);
                break;
            case 2:
                l = new Locale(parts[0], parts[1]);
                break;
            default:
                l = new Locale(parts[0], parts[1], parts[2]);
                break;
        }
        return l;
    }

    public static Context getAppContext() {
        return mInstance.getApplicationContext();
    }

    /**
     * Add a new OnLocaleChangedListener, and cleanup any dead references.
     */
    public static void registerOnLocaleChangedListener(OnLocaleChangedListener listener) {
        ArrayList<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<>();

        boolean alreadyAdded = false;

        for (WeakReference<OnLocaleChangedListener> ref : mOnLocaleChangedListeners) {
            OnLocaleChangedListener l = ref.get();
            if (l == null)
                toRemove.add(ref);
            else if (l == listener)
                alreadyAdded = true;
        }
        if (!alreadyAdded)
            mOnLocaleChangedListeners.add(new WeakReference<>(listener));

        for (WeakReference<OnLocaleChangedListener> ref : toRemove) {
            mOnLocaleChangedListeners.remove(ref);
        }
    }

    /**
     * Remove the passed OnLocaleChangedListener, and cleanup any dead references.
     */
    public static void unregisterOnLocaleChangedListener(OnLocaleChangedListener listener) {
        ArrayList<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<>();

        for (WeakReference<OnLocaleChangedListener> ref : mOnLocaleChangedListeners) {
            OnLocaleChangedListener l = ref.get();
            if ((l == null) || (l == listener))
                toRemove.add(ref);
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
    public static BcQueueManager getQueueManager() {
        return mQueueManager;
    }


    /**
     * Wrapper to reduce explicit use of the 'context' member.
     *
     * @param resId Resource ID
     *
     * @return Localized resource string
     */
    public static String getResourceString(int resId) {
        return getAppContext().getString(resId);
    }

    /**
     * Wrapper to reduce explicit use of the 'context' member.
     *
     * @param resId Resource ID
     *
     * @return Localized resource string[]
     */
    public static String[] getResourceStringArray(int resId) {
        return getAppContext().getResources().getStringArray(resId);
    }

//	/**
//	 * Currently the QueueManager is implemented as a service. This is not clearly necessary
//	 * but has the huge advantage of making a 'context' object available in the Service
//	 * implementation.
//	 * 
//	 * By binding it here, the service will not die when the last Activity is closed. We
//	 * could call StartService to keep it awake indefinitely also, but we do want the binding
//	 * object...so we bind it.
//	 */
//	private void startQueueManager() {
//		doBindService();		
//	}
//
//	/**
//	 * Points to the bound service, once it is started.
//	 */
//	private static BcQueueManager mBoundService = null;
//
//	/**
//	 * Utility routine to get the current QueueManager.
//	 * 
//	 * @return	QueueManager object
//	 */
//	public static BcQueueManager getQueueManager() {
//		return mBoundService;
//	}

    /**
     * Wrapper to reduce explicit use of the 'context' member.
     *
     * @param resId Resource ID
     *
     * @return Localized resource string
     */
    public static String getResourceString(int resId, Object... objects) {
        return getAppContext().getString(resId, objects);
    }

    /**
     * Read a string from the META tags in the Manifest.
     *
     * @param name  string to read
     * @return      value
     */
    public static String getManifestString(String name) {
        ApplicationInfo ai;
        try {
            ai = mInstance.getApplicationContext()
                    .getPackageManager()
                    .getApplicationInfo(mInstance.getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Logger.logError(e);
            throw new NullPointerException("See log for PackageManager.NameNotFoundException");
        }

        return ai.metaData.getString(name);
    }

    /**
     * should be removed together with BCBackground ?
     */
    @Deprecated
    public static int getBackgroundColor() {

        TypedValue tv = new TypedValue();
        mInstance.getTheme().resolveAttribute(android.R.attr.windowBackground, tv, true);
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            // windowBackground is a color
            return tv.data;
        } else {
            // windowBackground is not a color, probably a drawable
            //Drawable d = activity.getResources().getDrawable(tv.resourceId);
            //TODO: is this ever reached ?
            return R.color.background_grey;
        }
    }

    /**
     * Return the Intent that will be used by the notifications manager when a notification
     * is clicked; should bring the app to the foreground.
     */
    public static Intent getAppToForegroundIntent(Context c) {
        Intent i = new Intent(c, StartupActivity.class);
        i.setAction("android.intent.action.MAIN");
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        // No idea what to do with this!
        i.putExtra("bringFg", true);
        return i;
    }

    /**
     * Show a notification while this app is running.
     */
    public static void showNotification(int id, String title, String message, Intent i) {

        Notification notification = new Notification.Builder(getAppContext())
                .setSmallIcon(R.drawable.ic_stat_logo)
                .setContentTitle(title)
                .setContentText(message)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                // The PendingIntent to launch our activity if the user selects this notification
                .setContentIntent(PendingIntent.getActivity(getAppContext(), 0, i, 0))
                .build();

        mNotifier.notify(id, notification);
    }

    /**
     * Get the current preferred locale, or null
     *
     * @return locale, or null
     */
    public static Locale getPreferredLocale() {
        return mPreferredLocale;
    }
//	/**
//	 * Code based on Google sample code to bind the service.
//	 */
//	private ServiceConnection mConnection = new ServiceConnection() {
//	    public void onServiceConnected(ComponentName className, IBinder service) {
//	        // This is called when the connection with the service has been
//	        // established, giving us the service object we can use to
//	        // interact with the service.  Because we have bound to a explicit
//	        // service that we know is running in our own process, we can
//	        // cast its IBinder to a concrete class and directly access it.
//	        mBoundService = (BcQueueManager)((QueueManager.QueueManagerBinder)service).getService();
//
//	        // Tell the user about this for our demo.
//	        //Toast.makeText(BookCatalogueApp.this, "Connected", Toast.LENGTH_SHORT).show();
//	    }
//
//	    public void onServiceDisconnected(ComponentName className) {
//	        // This is called when the connection with the service has been
//	        // unexpectedly disconnected -- that is, its process crashed.
//	        // Because it is running in our same process, we should never
//	        // see this happen.
//	        mBoundService = null;
//	        //Toast.makeText(BookCatalogueApp.this, "Disconnected", Toast.LENGTH_SHORT).show();
//	    }
//	};
//
//	/** Indicates service has been bound. Really. */
//	boolean mIsBound;
//
//	/**
//	 * Establish a connection with the service.  We use an explicit
//	 * class name because we want a specific service implementation that
//	 * we know will be running in our own process (and thus won't be
//	 * supporting component replacement by other applications).
//	 */
//	void doBindService() {
//	    bindService(new Intent(BookCatalogueApp.this, BcQueueManager.class), mConnection, Context.BIND_AUTO_CREATE);
//	    mIsBound = true;
//	}
//	/**
//	 * Detach existiing service connection.
//	 */
//	void doUnbindService() {
//	    if (mIsBound) {
//	        unbindService(mConnection);
//	        mIsBound = false;
//	    }
//	}

    /**
     * Set the current preferred locale in the passed resources.
     *
     * @param res Resources to use
     *
     * @return true if it was actually changed
     */
    public static void applyPreferredLocaleIfNecessary(Resources res) {
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
    public static ArrayList<String> getSupportedLocales() {
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

    public static Locale getSystemLocal() {
        return mInitialLocale;
    }

    /**
     * Most real initialization should go here, since before this point, the App is still
     * 'Under Construction'.
     */
    @Override
    public void onCreate() {
        // Get the preferred locale as soon as possible
        try {
            // Save the original locale
            mInitialLocale = Locale.getDefault();
            // See if user has set a preference
            String prefLocale = BookCataloguePreferences.getLocale();
            //prefLocale = "ru";
            // If we have a preference, set it
            if (prefLocale != null && !prefLocale.isEmpty()) {
                mPreferredLocale = localeFromName(prefLocale);
                applyPreferredLocaleIfNecessary(getBaseContext().getResources());
            }
        } catch (Exception e) {
            // Not much we can do...we want locale set early, but not fatal if it fails.
            Logger.logError(e);
        }

        Terminator.init();

        // Setup ACRA
        ACRA.init(this);
        BcReportSender bcSender = new BcReportSender(this);
        ACRA.getErrorReporter().setReportSender(bcSender);

        // Save the app signer
        ACRA.getErrorReporter().putCustomData("Signed-By", DebugReport.signedBy(this));

        // Create the notifier
        mNotifier = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Start the queue manager
        if (mQueueManager == null)
            mQueueManager = new BcQueueManager(this.getApplicationContext());

        super.onCreate();

        // Watch the preferences and handle changes as necessary
        SharedPreferences p = BookCataloguePreferences.getSharedPreferences();
        p.registerOnSharedPreferenceChangeListener(mPrefsListener);
    }

    /**
     * Send a message to all registered OnLocaleChangedListeners, and cleanup any dead references.
     */
    private void notifyLocaleChanged() {
        ArrayList<WeakReference<OnLocaleChangedListener>> toRemove = new ArrayList<>();

        for (WeakReference<OnLocaleChangedListener> ref : mOnLocaleChangedListeners) {
            OnLocaleChangedListener l = ref.get();
            if (l == null)
                toRemove.add(ref);
            else
                try {
                    l.onLocaleChanged();
                } catch (Exception e) { /* Ignore */ }
        }
        for (WeakReference<OnLocaleChangedListener> ref : toRemove) {
            mOnLocaleChangedListeners.remove(ref);
        }
    }

    /**
     * Monitor configuration changes (like rotation) to make sure we reset the locale.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
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

    public class BcReportSender extends org.acra.sender.EmailIntentSender {

        BcReportSender(Context ctx) {
            super(ctx);
        }

        @SuppressWarnings("EmptyMethod")
        @Override
        public void send(CrashReportData report) throws ReportSenderException {
            //report.put(USER_COMMENT, report.get(USER_COMMENT) + "\n\n" + Tracker.getEventsInfo());
            super.send(report);
        }
    }

}
