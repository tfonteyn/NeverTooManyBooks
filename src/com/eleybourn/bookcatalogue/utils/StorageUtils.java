package com.eleybourn.bookcatalogue.utils;

import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.debug.Logger;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to wrap common storage related functions.
 *
 * @author Philip Warner
 */
public class StorageUtils {
	private StorageUtils() {
	}

	private static final String UTF8 = "utf8";
	private static final int BUFFER_SIZE = 8192;

    private static final String DATABASE_NAME = "book_catalogue";

    // our root directory to be created on the 'external storage'
    public static final String DIRECTORY_NAME = "bookCatalogue";

	// directories
	private static final String EXTERNAL_FILE_PATH = Environment.getExternalStorageDirectory() + File.separator + DIRECTORY_NAME;
    private static final String TEMP_IMAGE_FILE_PATH = EXTERNAL_FILE_PATH + File.separator + "tmp_images";

    // files in above directories
    private static final String ERRORLOG_FILE_PATH = EXTERNAL_FILE_PATH + File.separator + "error.log";
	private static final String NOMEDIA_FILE_PATH = EXTERNAL_FILE_PATH + File.separator + ".nomedia";


	public static String getErrorLog() {
		return ERRORLOG_FILE_PATH;
	}

	public static String getDatabaseName() {
		return DATABASE_NAME;
	}


    private static void createDir(String name) {
        File dir = new File(name);
        boolean ok = dir.mkdirs() || dir.isDirectory();
        if (!ok) {
            Logger.logError("Could not write to shared storage. No permission on: " + name);
        }
    }

	/**
	 * Make sure the external shared directory exists
     * Logs failures themselves, but does NOT fail function
     *
     * Only called from StartupActivity, after permissions have been granted.
	 */
	public static void initSharedDirectories() {
	    File rootDir = new File(EXTERNAL_FILE_PATH);
	    // quick return
        if (rootDir.exists() && rootDir.isDirectory())
            return;

        createDir(EXTERNAL_FILE_PATH);
        createDir(TEMP_IMAGE_FILE_PATH);

        // * A .nomedia file will be created which will stop the thumbnails showing up in the gallery (thanks Brandon)
        try {
			//noinspection ResultOfMethodCallIgnored
			new File(NOMEDIA_FILE_PATH).createNewFile();
        } catch (IOException e) {
            Logger.logError(e,"Failed to create .media file: " + NOMEDIA_FILE_PATH);
        }
    }

	/**
	 * Get a File, don't check on existence or creation
	 */
	public static File getFile(String fileName) {
        if (BuildConfig.DEBUG) {
        	System.out.println("StorageUtils.getFile: Accessing file: " + EXTERNAL_FILE_PATH + File.separator + fileName);
		}
        return new File(EXTERNAL_FILE_PATH + File.separator + fileName);
    }

	/**
	 * @return the shared root Directory object, create if needed
	 */
	public static File getSharedStorage() {
        return new File(EXTERNAL_FILE_PATH);
	}

    /**
     * full path, without trailing File.separator !
     * @return the path
     */
    public static String getSharedStoragePath() {
        return EXTERNAL_FILE_PATH;
    }

    /**
     *
     * @param fileName in the temp image directory
     * @return the file
     */
    public static File getTempImageFile(String fileName) {
        return getFile(TEMP_IMAGE_FILE_PATH + File.separator + fileName);
    }

    /**
     * @return the temp image directory
     */
	public static File getTempImageDirectory() {
        return new File(TEMP_IMAGE_FILE_PATH);
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
	static class FileDateComparator implements Comparator<File> {
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
		StringBuilder info = new StringBuilder();

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

		if (BuildConfig.DEBUG) {
			info.append("Getting mounted file systems\n");
		}
		// Scan all mounted file systems
		try {
			in = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/mounts")),1024);
			String line;
			while ((line = in.readLine()) != null) {
				if( BuildConfig.DEBUG) {
					info.append("   checking ").append(line).append("\n");
				}
				Matcher m = mountPointPat.matcher(line);
				// Get the mount point
				if (m.find()) {
					// See if it has a bookCatalogue directory
					File dir = new File(m.group(1) + File.separator + DIRECTORY_NAME);
					if( BuildConfig.DEBUG) {
						info.append("       matched ").append(dir.getAbsolutePath()).append("\n");
					}
					dirs.add(dir);
				} else {
					if (BuildConfig.DEBUG) {
						info.append("       NO match\n");
					}
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
        if (BuildConfig.DEBUG) {
		    info.append("Found " + dirs.size() + " directories\n");
        }

		try {
			String loc1 = System.getenv("EXTERNAL_STORAGE");
			if (loc1 != null) {
				File dir = new File(loc1 + File.separator + DIRECTORY_NAME);
				dirs.add(dir);
				if (BuildConfig.DEBUG) {
					info.append("Loc1 added ").append(dir.getAbsolutePath()).append("\n");
				}
			} else {
				if (BuildConfig.DEBUG) {
					info.append("Loc1 was null\n");
				}
			}

			String loc2 = System.getenv("SECONDARY_STORAGE");
			if (loc2 != null && !loc2.equals(loc1)) {
				File dir = new File(loc2 + File.separator + DIRECTORY_NAME);
				dirs.add(dir);
				if (BuildConfig.DEBUG) {
					info.append("Loc2 added ").append(dir.getAbsolutePath()).append("\n");
				}
			} else {
				if (BuildConfig.DEBUG) {
					info.append("Loc2 ignored: ").append(loc2).append("\n");
				}
			}
		} catch (Exception e) {
			Logger.logError(e, "Failed to get external storage from environment variables");
		}

		HashSet<String> paths = new HashSet<>();

		if (BuildConfig.DEBUG) {
			info.append("Looking for files in directories\n");
		}
		for(File dir: dirs) {
			try {
				if (dir.exists()) {
					// Scan for csv files
					File[] csvFiles = dir.listFiles(csvFilter);
					if (csvFiles != null) {
						if (BuildConfig.DEBUG) {
							info.append("    found ").append(csvFiles.length).append(" in ").append(dir.getAbsolutePath()).append("\n");
						}
						for (File f : csvFiles) {
							if (BuildConfig.DEBUG) {
								info.append("Found: ").append(f.getAbsolutePath());
							}
							final String cp = f.getCanonicalPath();
							if (paths.contains(cp)) {
								if (BuildConfig.DEBUG) {
									info.append("        already present as ").append(cp).append("\n");
								}
							} else {
								files.add(f);
								paths.add(cp);
								if (BuildConfig.DEBUG) {
									info.append("        added as ").append(cp).append("\n");
								}
							}
						}
					} else {
						if (BuildConfig.DEBUG) {
							info.append("    null returned by listFiles() in ").append(dir.getAbsolutePath()).append("\n");
						}
					}
				} else {
					if (BuildConfig.DEBUG) {
						info.append("    ").append(dir.getAbsolutePath()).append(" does not exist\n");
					}
				}
			} catch (Exception e) {
				Logger.logError(e, "Failed to read directory " + dir.getAbsolutePath());
			}
		}

		if (BuildConfig.DEBUG) {
			Logger.logError(new RuntimeException("INFO"), info.toString());
		}

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

	private static final String[] mPurgeableFilePrefixes = new String[] {
			DIRECTORY_NAME + "DbUpgrade",
			DIRECTORY_NAME + "DbExport",
			"error.log",
			"tmp"};

	/**
	 * Cleanup any purgeable files.
	 */
	public static void cleanupFiles() {
		if (sdCardWritable()) {
	        File dir = getSharedStorage();
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

		File dir = getSharedStorage();
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
