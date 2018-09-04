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

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;

import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.OnTaskFinishListener;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UpgradeMessageManager;

import java.lang.ref.WeakReference;

/**
 * Single Activity to be the 'Main' activity for the app. I does app-startup stuff which is initially
 * to start the 'real' main activity.
 *
 * FIXME: now that MainMenu is gone.... see what this note means ? if anything
 * Note that calling the desired main activity first resulted in MainMenu's 'singleInstance' property
 * NOT being honoured. So we call MainMenu anyway, but set a flag in the Intent to indicate this is
 * a startup. This approach mostly works, but results in an apparent mis-ordering of the activity
 * stack, which we can live with for now.
 * 
 * @author Philip Warner
 */
public class StartupActivity extends AppCompatActivity {
	private static final String TAG = "StartupActivity";

	/** FIXME: This is nasty, now we use fragments, StartupActivity should be a FragmentActivity and load the right fragment
	 * then we could do away with the whole isRoot/willBeRoot thing
	 * Check usage in the code of this string in the intent extra
	 */
	public static final String BKEY_IS_TASK_ROOT = "willBeTaskRoot";
	private static final String BKEY_STARTUP = "startup";

	/** Flag to indicate FTS rebuild is required at startup */
	private static final String PREF_FTS_REBUILD_REQUIRED = TAG + ".FtsRebuildRequired";
	private static final String PREF_AUTHOR_SERIES_FIX_UP_REQUIRED = TAG + ".FAuthorSeriesFixupRequired";
	
	private static final String STATE_OPENED = "state_opened";
	/** Number of times the app has been started */
	private static final String PREF_START_COUNT = "Startup.StartCount";

	/** Number of app startup's between offers to backup */
	private static final int BACKUP_PROMPT_WAIT = 5;
	
	/** Number of app startup's between displaying the Amazon hint */
	private static final int AMAZON_PROMPT_WAIT = 7;

	/** Indicates the upgrade message has been shown */
	private static boolean mUpgradeMessageShown = false;
	/** Flag set to true on first call */
	private static boolean mIsReallyStartup = true;
	/** Flag indicating a StartupActivity has been created in this session */
	private static boolean mHasBeenCalled = false;

	/** Queue for executing startup tasks, if any */
	private SimpleTaskQueue mTaskQueue = null;
	/** Progress Dialog for startup tasks */
	private ProgressDialog mProgress = null;
	/** Flag indicating THIS instance was really the startup instance */
	private boolean mWasReallyStartup = false;
	
	/** Flag indicating an export is required after startup */
	private boolean mExportRequired = false;

	/** Flag indicating Amazon hint could be shown */
	private static boolean mShowAmazonHint = false;

	// Database connection
	//CatalogueDBAdapter mDb = null;

	/** Handler to post run'ables to UI thread */
	private final Handler mHandler = new Handler();
	/**UI thread */
	private Thread mUiThread;

	/** Set the flag to indicate an FTS rebuild is required */
	public static void scheduleFtsRebuild() {
		BookCataloguePreferences.setBoolean(PREF_FTS_REBUILD_REQUIRED, true);
	}

	/** Set the flag to indicate an FTS rebuild is required */
	public static void scheduleAuthorSeriesFixUp() {
		BookCataloguePreferences.setBoolean(PREF_AUTHOR_SERIES_FIX_UP_REQUIRED, true);
	}
	
	private static WeakReference<StartupActivity> mStartupActivity = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (BuildConfig.DEBUG) {
			System.out.println("Startup isTaskRoot() = " + isTaskRoot());
		}

		mHasBeenCalled = true;
		mUiThread = Thread.currentThread();

		// Create a progress dialog; we may not use it...but we need it to be created in the UI thread.
		mProgress = ProgressDialog.show(this, getString(R.string.book_catalogue_startup), getString(R.string.starting_up), true, true, new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				// Cancelling the list cancels the activity.
				StartupActivity.this.finish();
			}});

		mWasReallyStartup = mIsReallyStartup;

		// If it's a real application startup...cleanup old stuff
		if (mWasReallyStartup) {
			mStartupActivity = new WeakReference<>(this);

			updateProgress("Starting");

			// at this point the user will have granted us STORAGE permission,
			// so make sure we have our directories ready
			StorageUtils.initSharedDirectories();

			SimpleTaskQueue q = getQueue();

			// Always enqueue it; it will get a DB and check if required...
			q.enqueue(new RebuildFtsTask());
			q.enqueue(new AnalyzeDbTask());

			// Remove old logs
			Logger.clearLog();
			// Clear the flag
			mIsReallyStartup = false;

			// ENHANCE: add checks for new Events/crashes
		}

		// If no tasks were queued, then move on to stage 2. Otherwise, the completed
		// tasks will cause stage 2 to start.
		if (mTaskQueue == null)
			stage2Startup();
	}

	/**
	 * Kludge to get a reference to the currently running StartupActivity, if defined.
	 * 
	 * @return		Reference or null.
	 */
	public static StartupActivity getActiveActivity() {
		if (mStartupActivity != null)
			return mStartupActivity.get();
		else
			return null;
	}

	/**
	 * Update the progress dialog, if it has not been dismissed.
	 */
	public void updateProgress(final int stringId) {
		updateProgress(getString(stringId));
	}

	/**
	 * Update the progress dialog, if it has not been dismissed.
	 */
	private void updateProgress(final String message) {
		// If mProgress is null, it has been dismissed. Don't update.
		if (mProgress == null) {
			return;
		}

		// If we are in the UI thread, update the progress.
		if (Thread.currentThread().equals(mUiThread)) {
			// There is a small chance that this message could be set to display *after* the activity is finished,
			// so we check and we also trap, log and ignore errors.
			// See http://code.google.com/p/android/issues/detail?id=3953
			if (!isFinishing()) {
				try {
					mProgress.setMessage(message);
					if (!mProgress.isShowing())
						mProgress.show();					
				} catch (Exception e) {
					Logger.logError(e);
				}				
			}
		} else {
			// If we are NOT in the UI thread, queue it to the UI thread.
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					updateProgress(message);
				}});
		}
	}

	/**
	 * Called in the UI thread when any startup task completes. If no more tasks, start stage 2.
	 * Because it is in the UI thread, it is not possible for this code to be called until after 
	 * onCreate() completes, so a race condition is not possible. Equally well, tasks should only
	 * be queued in onCreate().
	 */
	private void taskCompleted(SimpleTask task) {
		if (BuildConfig.DEBUG) {
			System.out.println("Task Completed: " + task.getClass().getSimpleName());
		}
		if (!mTaskQueue.hasActiveTasks()) {
			if (BuildConfig.DEBUG) {
				System.out.println("Task Completed - no more");
			}
			stage2Startup();
		}
	}

	/**
	 * Get (or create) the task queue.
	 */
	private SimpleTaskQueue getQueue() {
		if (mTaskQueue == null) {
			mTaskQueue = new SimpleTaskQueue("startup-tasks", 1);	
			// Listen for task completions
			mTaskQueue.setTaskFinishListener(new OnTaskFinishListener() {
				@Override
				public void onTaskFinish(SimpleTask task, Exception e) {
					taskCompleted(task);
				}});
		}
		return mTaskQueue;
	}

	/**
	 * Called in UI thread after last startup task completes, or if there are no tasks to queue.
	 */
	private void stage2Startup() {
		// Remove the weak reference. Only used by db onUpgrade.
		mStartupActivity.clear();
		// Get rid of the progress dialog
		if (mProgress != null) {
			mProgress.dismiss();
			mProgress = null;
		}

		// Display upgrade message if necessary, otherwise go on to stage 3
		if (mUpgradeMessageShown || UpgradeMessageManager.getUpgradeMessage().isEmpty()) {
			stage3Startup();
		} else {
			upgradePopup(UpgradeMessageManager.getUpgradeMessage());
		}
	}
	
	private void stage3Startup() {
		int opened = BookCataloguePreferences.getInt(STATE_OPENED, BACKUP_PROMPT_WAIT);
		int startCount = BookCataloguePreferences.getInt(PREF_START_COUNT, 0) + 1;

		Editor ed = BookCataloguePreferences.edit();
		if (opened == 0) {
			ed.putInt(STATE_OPENED, BACKUP_PROMPT_WAIT);
		} else {
			ed.putInt(STATE_OPENED, opened - 1);
		}
		ed.putInt(PREF_START_COUNT, startCount);
		ed.commit();

		if ( ( startCount % AMAZON_PROMPT_WAIT) == 0) {
			mShowAmazonHint = true;
		}

		mExportRequired = false;

		if (opened == 0) {
			AlertDialog alertDialog = new AlertDialog.Builder(this)
					.setMessage(R.string.backup_request).create();
			alertDialog.setTitle(R.string.backup_title);
			alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
			alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE,
					getResources().getString(android.R.string.cancel)
					, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			}); 
			alertDialog.setButton(AlertDialog.BUTTON_POSITIVE,
					getResources().getString(android.R.string.ok),
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					mExportRequired = true;
					dialog.dismiss();
				}
			}); 
			alertDialog.setOnDismissListener(new OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog) {
					stage4Startup();
				}});
			alertDialog.show();
		} else {
			stage4Startup();
		}
	}

	/**
	 * Start whatever activity the user expects
	 */
	private void stage4Startup() {
		doMainActivity(BooksOnBookshelf.class);

		if (mExportRequired) {
			AdministrationFunctions.exportToArchive(this);
		}

		// We are done
		finish();		
	}

    private void doMainActivity(Class nextActivityClass) {
        Intent i = new Intent(this, nextActivityClass);
        if (mWasReallyStartup)
            i.putExtra(BKEY_STARTUP, true);

        i.putExtra(BKEY_IS_TASK_ROOT, isTaskRoot());
        startActivity(i);
    }

	/**
	 * This will display a popup with a provided message to the user. This will be
	 * mostly used for upgrade notifications
	 * 
	 * @param message The message to display in the popup
	 */
	private void upgradePopup(String message) {
		AlertDialog alertDialog = new AlertDialog.Builder(this).setMessage(Html.fromHtml(message)).create();
		alertDialog.setTitle(R.string.upgrade_title);
		alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
		alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				UpgradeMessageManager.setMessageAcknowledged();
				stage3Startup();
			}
		});
		alertDialog.show();
		mUpgradeMessageShown = true;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mTaskQueue != null)
			mTaskQueue.finish();
	}
	
	/**
	 * Task to rebuild FTS in background. Can take several seconds, so not done in onUpgrade().
	 * 
	 * @author Philip Warner
	 *
	 */
	public class RebuildFtsTask implements SimpleTask {

		@Override
		public void run(SimpleTaskContext taskContext) {
			// Get a DB to make sure the FTS rebuild flag is set appropriately
			CatalogueDBAdapter db = taskContext.getDb();
			if (BookCataloguePreferences.getBoolean(PREF_FTS_REBUILD_REQUIRED, false)) {
				updateProgress(getString(R.string.rebuilding_search_index));
				db.rebuildFts();
				BookCataloguePreferences.setBoolean(PREF_FTS_REBUILD_REQUIRED, false);
			}
		}

		@Override
		public void onFinish(Exception e) {}

	}

	public class AnalyzeDbTask implements SimpleTask {

		@Override
		public void run(SimpleTaskContext taskContext) {

			CatalogueDBAdapter db = taskContext.getDb();

			updateProgress(getString(R.string.optimizing_databases));
			// Analyze DB
			db.analyzeDb();
			if (BooklistPreferencesActivity.isThumbnailCacheEnabled()) {
                try(CoversDbHelper coversDbHelper = CoversDbHelper.getInstance(StartupActivity.this)) {
                    coversDbHelper.analyze();
                }
			}
			
			if (BookCataloguePreferences.getBoolean(PREF_AUTHOR_SERIES_FIX_UP_REQUIRED, false)) {
				db.fixupAuthorsAndSeries();
				BookCataloguePreferences.setBoolean(PREF_AUTHOR_SERIES_FIX_UP_REQUIRED, false);
			}
		}

		@Override
		public void onFinish(Exception e) {
		}

	}
	
	public static boolean hasBeenCalled() {
		return mHasBeenCalled;
	}
	
	public static boolean getShowAmazonHint() {
		return mShowAmazonHint;
	}

}
