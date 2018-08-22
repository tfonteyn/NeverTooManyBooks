package com.eleybourn.bookcatalogue.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.scanner.ScannerManager;
import com.eleybourn.bookcatalogue.scanner.ZxingScanner;
import com.eleybourn.bookcatalogue.scanner.pic2shop.Scan;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to wrap common storage related functions.
 *
 * TODO: explicitly check/ask for needed permissions.
 * 
 * @author Philip Warner
 */
public class StorageUtils {
	private static final String UTF8 = "utf8";
	private static final int BUFFER_SIZE = 8192;

    private static final String DATABASE_NAME = "book_catalogue";

    // relative filename
    private static final String DIRECTORY_NAME = "bookCatalogue";

	// fully qualified filenames
	private static final String EXTERNAL_FILE_PATH = Environment.getExternalStorageDirectory() + File.separator + DIRECTORY_NAME;
	private static final String ERRORLOG_FILE = EXTERNAL_FILE_PATH + File.separator + "error.log";
	private static final String NOMEDIA_FILE_PATH = EXTERNAL_FILE_PATH + File.separator + ".nomedia";

	private static boolean mSharedDirExists;

	public static String getErrorLog() {
		return ERRORLOG_FILE;
	}

	public static String getDatabaseName() {
		return DATABASE_NAME;
	}

    public static final int PERM_RESULT_WRITE_EXTERNAL_STORAGE = 0;

    public static void checkPermissions(Activity a) {
        if (ContextCompat.checkSelfPermission(a, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(a, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERM_RESULT_WRITE_EXTERNAL_STORAGE);
        }
    }
    /**
     * A .nomedia file will be created which will stop the thumbnails showing up in the gallery (thanks Brandon)
     */
    public static void createNoMediaFile() {
        try {
            new File(NOMEDIA_FILE_PATH).createNewFile();
        } catch (IOException ignore) {
        }
    }

	/**
	 * Make sure the external shared directory exists
     *
	 */
	private static void initSharedDirectory() {
	    File dir = new File(EXTERNAL_FILE_PATH);
        if (dir.exists() && dir.isDirectory())
            return;

	    try {
            mSharedDirExists = dir.mkdirs() || dir.isDirectory();
        } catch (SecurityException e) {
            mSharedDirExists  = false;
            Logger.logError(e);
        }

        if (!mSharedDirExists) {
            Logger.logError("Could not write to shared storage. Please restart and grant permission when asked.");
            return;
        }
		createNoMediaFile();
	}

	/**
	 * Get a File, don't check on existence or creation
	 */
	public static File getFile(String fileName) {
		return getFile(null, fileName);
	}

    /**
     * Get a File, don't check on existence or creation
     */
    public static File getFile(String dir, String fileName) {
        if (!mSharedDirExists) {
            initSharedDirectory();
        }
        String subdirName = (dir == null || dir.isEmpty()) ? "" : File.separator + dir;

        return new File(EXTERNAL_FILE_PATH + subdirName + File.separator + fileName);
    }

	/**
	 * @return the shared root Directory object, create if needed
	 */
	public static File getSharedDirectory() {
        if (!mSharedDirExists) {
            initSharedDirectory();
        }
        return new File(EXTERNAL_FILE_PATH);
	}

	/**
     *  (create and) get the subdir relative to the shared dir
     *
     * @param subDirectoryName  the name of the sub dir to append, example: "mynewdir[/myoptionaldir]"
     *
     * @return a sub directory object
	 */
	public static File getDirectory(String subDirectoryName) {
        File dir = new File(EXTERNAL_FILE_PATH + File.separator + subDirectoryName);
        dir.mkdirs();
        return dir;
	}

    /**
     *
     * @param db        file to backup
     * @param suffix    suffix to apply to the directory name
     */
	public static void backupDbFile(SQLiteDatabase db, String suffix) {
		try {
			final String fileName = DIRECTORY_NAME + suffix;

			//check if it exists
			File existing = getFile(fileName);
			if (existing.exists()) {
				existing.renameTo(getFile(fileName + ".bak"));
			}

            InputStream dbOrig = new FileInputStream(db.getPath());
			OutputStream dbCopy = new FileOutputStream(getFile(fileName));

			byte[] buffer = new byte[1024];
			int length;
			while ((length = dbOrig.read(buffer))>0) {
				dbCopy.write(buffer, 0, length);
			}
			
			dbCopy.flush();
			dbCopy.close();
			dbOrig.close();
			
		} catch (Exception e) {
			Logger.logError(e);
		}
	}



	/**
	 * Compare two files based on date. Used for sorting file list by date.
	 * 
	 * @author Philip Warner
	 */
	public static class FileDateComparator implements Comparator<File> {
		/** Ascending is >= 0, Descending is < 0. */
		private final int mDirection;
		/**
		 * Constructor
		 */
		FileDateComparator(int direction) {
			mDirection = direction < 0 ? -1 : 1;
		}
		/**
		 * Compare based on modified date
		 */
		@Override
		public int compare(File lhs, File rhs) {
			final long l = lhs.lastModified();
			final long r = rhs.lastModified();
			if (l < r)
				return -mDirection;
			else if (l > r)
				return mDirection;
			else
				return 0;
		}		
	}

	/**
	 * Scan all mount points for '/bookCatalogue' directory and collect a list
	 * of all CSV files.
	 */
	public static ArrayList<File> findExportFiles() {
		//StringBuilder info = new StringBuilder();

		ArrayList<File> files = new ArrayList<>();
		Pattern mountPointPat = Pattern.compile("^\\s*[^\\s]+\\s+([^\\s]+)");
		BufferedReader in = null;
		// Make a filter for files ending in .csv
		FilenameFilter csvFilter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				final String fl = filename.toLowerCase();
				return (fl.endsWith(".csv"));
				//ENHANCE: Allow for other files? Backups? || fl.endsWith(".csv.bak"));
			}
		}; 

		ArrayList<File> dirs = new ArrayList<>();

		//info.append("Getting mounted file systems\n");
		// Scan all mounted file systems
		try {
			in = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/mounts")),1024);
			String line;
			while ((line = in.readLine()) != null) {
				//info.append("   checking " + line + "\n");
				Matcher m = mountPointPat.matcher(line);
				// Get the mount point
				if (m.find()) {
					// See if it has a bookCatalogue directory
					File dir = new File(m.group(1) + File.separator + DIRECTORY_NAME);
					//info.append("       matched " + dir.getAbsolutePath() + "\n");
					dirs.add(dir);
				} else {
					//info.append("       NO match\n");
				}
			}
		} catch (IOException e) {
			Logger.logError(e, "Failed to open/scan/read /proc/mounts");
		} finally {
			if (in != null)
				try {
					in.close();					
				} catch (Exception ignored) {}
		}

		// Sometimes (Android 6?) the /proc/mount search seems to fail, so we revert to environment vars
		//info.append("Found " + dirs.size() + " directories\n");
		try {
			String loc1 = System.getenv("EXTERNAL_STORAGE");
			if (loc1 != null) {
				File dir = new File(loc1 + File.separator + DIRECTORY_NAME);
				dirs.add(dir);
				//info.append("Loc1 added " + dir.getAbsolutePath() + "\n");
			} else {
				//info.append("Loc1 ignored: " + loc1 + "\n");
			}

			String loc2 = System.getenv("SECONDARY_STORAGE");
			if (loc2 != null && !loc2.equals(loc1)) {
				File dir = new File(loc2 + File.separator + DIRECTORY_NAME);
				dirs.add(dir);
				//info.append("Loc2 added " + dir.getAbsolutePath() + "\n");
			} else {
				//info.append("Loc2 ignored: " + loc2 + "\n");
			}
		} catch (Exception e) {
			Logger.logError(e, "Failed to get external storage from environment variables");
		}

		HashSet<String> paths = new HashSet<>();

		//info.append("Looking for files in directories\n");
		for(File dir: dirs) {
			try {
				if (dir.exists()) {
					// Scan for csv files
					File[] csvFiles = dir.listFiles(csvFilter);
					if (csvFiles != null) {
						//info.append("    found " + csvFiles.length + " in " + dir.getAbsolutePath() + "\n");
						for (File f : csvFiles) {
							if (BuildConfig.DEBUG) {
								System.out.println("Found: " + f.getAbsolutePath());
							}
							final String cp = f.getCanonicalPath();
							if (paths.contains(cp)) {
								//info.append("        already present as " + cp + "\n");								
							} else {
								files.add(f);
								paths.add(cp);
								//info.append("        added as " + cp + "\n");																
							}
						}
					} else {
						//info.append("    null returned by listFiles() in " + dir.getAbsolutePath() + "\n");
					}
				} else {
					//info.append("    " + dir.getAbsolutePath() + " does not exist\n");
				}
			} catch (Exception e) {
				Logger.logError(e, "Failed to read directory " + dir.getAbsolutePath());
			}
		}

		//Logger.logError(new RuntimeException("INFO"), info.toString());

		// Sort descending based on modified date
		Collections.sort(files, new FileDateComparator(-1));
		return files;
	}

	/**
	 * Check if the sdcard is writable
	 * 
	 * @return	success or failure
	 */
	static public boolean sdCardWritable() {
		/* Test write to the SDCard */
		try {
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(NOMEDIA_FILE_PATH), UTF8), BUFFER_SIZE);
			out.write("");
			out.close();
			return true;
		} catch (IOException e) {
			return false;
		}		
	}

	private static final String[] mPurgeableFilePrefixes = new String[]{DIRECTORY_NAME + "DbUpgrade", DIRECTORY_NAME + "DbExport", "error.log", "tmp"};
	private static final String[] mDebugFilePrefixes = new String[]{DIRECTORY_NAME + "DbUpgrade", DIRECTORY_NAME + "DbExport", "error.log", "export.csv"};

	/**
	 * Collect and send com.eleybourn.bookcatalogue.debug info to a support email address. 
	 * 
	 * THIS SHOULD NOT BE A PUBLICLY AVAILABLE MAILING LIST OR FORUM!
	 */
	public static void sendDebugInfo(Context context, CatalogueDBAdapter dbHelper) {
		// Create a temp DB copy.
		String tmpFileName = DIRECTORY_NAME + "DbExport-tmp.db";
		dbHelper.backupDbFile(tmpFileName);
		File dbFile = getFile(tmpFileName);
		dbFile.deleteOnExit();
		// setup the mail message
		final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);
		emailIntent.setType("plain/text");
		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, context.getString(R.string.debug_email).split(";"));
		String subject = "[" + context.getString(R.string.app_name) + "] " + context.getString(R.string.debug_subject);
		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
		StringBuilder message = new StringBuilder();

        try {
        	// Get app info
            PackageManager manager = context.getPackageManager(); 
			PackageInfo appInfo = manager.getPackageInfo( context.getPackageName(), 0);
			message.append("App: ").append(appInfo.packageName).append("\n")
				.append("Version: ").append(appInfo.versionName).append(" (").append(appInfo.versionCode).append(")\n");
		} catch (Exception e1) {
			// Not much we can do inside error logger...
		}
        
        
        message.append("SDK: ").append(Build.VERSION.RELEASE).append(" (").append(Build.VERSION.SDK_INT).append(" ").append(Build.TAGS).append(")\n")
			.append("Phone Model: ").append(Build.MODEL).append("\n")
			.append("Phone Manufacturer: ").append(Build.MANUFACTURER).append("\n")
			.append("Phone Device: ").append(Build.DEVICE).append("\n")
			.append("Phone Product: ").append(Build.PRODUCT).append("\n")
			.append("Phone Brand: ").append(Build.BRAND).append("\n")
			.append("Phone ID: ").append(Build.ID).append("\n")
			.append("Signed-By: ").append(Utils.signedBy(context)).append("\n")
			.append("\nHistory:\n").append(Tracker.getEventsInfo()).append("\n");

		// Scanners installed
		try {
	        message.append("Pref. Scanner: ").append(BookCatalogueApp.getAppPreferences().getInt(ScannerManager.PREF_PREFERRED_SCANNER, -1)).append("\n");
	        String[] scanners = new String[] { ZxingScanner.ACTION, Scan.ACTION, Scan.Pro.ACTION};
	        for(String scanner:  scanners) {
	            message.append("Scanner [").append(scanner).append("]:\n");
	            final Intent mainIntent = new Intent(scanner, null);
	            final List<ResolveInfo> resolved = context.getPackageManager().queryIntentActivities( mainIntent, 0);
	            if (resolved.size() > 0) {
		            for(ResolveInfo r: resolved) {
		            	message.append("    ");
		            	// Could be activity or service...
		            	if (r.activityInfo != null) {
		            		message.append(r.activityInfo.packageName);
		            	} else if (r.serviceInfo != null) {
		            		message.append(r.serviceInfo.packageName);
		            	} else {
		            		message.append("UNKNOWN");
		            	}
		                message.append(" (priority ").append(r.priority).append(", preference ").append(r.preferredOrder).append(", match ").append(r.match).append(", default=").append(r.isDefault).append(")\n");
		            }
	            } else {
            		message.append("    No packages found\n");
	            }
	        }			
		} catch (Exception e) {
			// Don't lose the other debug info if scanner data dies for some reason
	        message.append("Scanner failure: ").append(e.getMessage()).append("\n");
		}
		message.append("\n");

        message.append("Details:\n\n").append(context.getString(R.string.debug_body).toUpperCase()).append("\n\n");

		emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, message.toString());
		//has to be an ArrayList
		ArrayList<Uri> uris = new ArrayList<>();
		//convert from paths to Android friendly Parcelable Uri's
		ArrayList<String> files = new ArrayList<>();
		
		// Find all files of interest to send
		File dir = getSharedDirectory();
		try {
			for (String name : dir.list()) {
				boolean send = false;
				for(String prefix : mDebugFilePrefixes)
					if (name.startsWith(prefix)) {
						send = true;
						break;
					}
				if (send)
					files.add(name);
			}
			
			// Build the attachment list
			for (String file : files)
			{
				File fileIn = getFile(file);
				if (fileIn.exists() && fileIn.length() > 0) {
					Uri u = Uri.fromFile(fileIn);
					uris.add(u);
				}
			}
	
			// We used to only send it if there are any files to send, but later versions added 
			// useful debugging info. So now we always send.
			emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
			context.startActivity(Intent.createChooser(emailIntent, "Send mail..."));        	

		} catch (NullPointerException e) {
			Logger.logError(e);
			Toast.makeText(context, R.string.export_failed_sdcard, Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Cleanup any purgeable files.
	 */
	public static void cleanupFiles() {
		if (sdCardWritable()) {
	        File dir = getSharedDirectory();
	        for (String name : dir.list()) {
	        	boolean purge = false;
	        	for(String prefix : mPurgeableFilePrefixes)
	        		if (name.startsWith(prefix)) {
	        			purge = true;
	        			break;
	        		}
	        	if (purge)
		        	try {
		        		File file = getFile(name);
			        	boolean success = file.delete();
			        	if (!success) {
			        		Log.e("StorageUtils", "cleanupFiles failed to delete: " + file.getAbsolutePath());
						}
		        	} catch (Exception ignored) {
		        	}
	        }
		}
	}

	/**
	 * Get the total size of purgeable files.
	 * @return	size, in bytes
	 */
	public static long cleanupFilesTotalSize() {
		if (!sdCardWritable())
			return 0;

		long totalSize = 0;

		File dir = getSharedDirectory();
        for (String name : dir.list()) {
        	boolean purge = false;
        	for(String prefix : mPurgeableFilePrefixes)
        		if (name.startsWith(prefix)) {
        			purge = true;
        			break;
        		}
        	if (purge)
	        	try {
	        		File file = getFile(name);
	        		totalSize += file.length();
	        	} catch (Exception ignored) {
	        	}
        }
        return totalSize;
	}
}
