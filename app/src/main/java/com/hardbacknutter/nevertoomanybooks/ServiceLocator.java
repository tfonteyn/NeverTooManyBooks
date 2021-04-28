/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Styles;
import com.hardbacknutter.nevertoomanybooks.database.CoversDbHelper;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.ColorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CoverCacheDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.FormatDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.FtsDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.GenreDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.GoodreadsDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LanguageDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LoaneeDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LocationDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.MaintenanceDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.StripInfoDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.StyleDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.AuthorDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookshelfDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.CalibreDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.CalibreLibraryDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.ColorDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.CoverCacheDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.FormatDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.FtsDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.GenreDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.GoodreadsDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.LanguageDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.LoaneeDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.LocationDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.MaintenanceDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.PublisherDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.SeriesDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.StripInfoDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.StyleDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.TocEntryDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.TaskQueueDBHelper;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocaleImpl;
import com.hardbacknutter.nevertoomanybooks.utils.Notifier;
import com.hardbacknutter.nevertoomanybooks.utils.NotifierImpl;

/**
 * The use and definition of DAO in this project has a long history.
 * Migrating to 'best practices' has been an ongoing effort but is at best a far future goal.
 * The main issue is that all testing must be done with the emulator as we can't easily
 * inject mock doa's for now.
 * <p>
 * This class is the next step as we can mock Context/db/dao classes before running a test.
 */
public final class ServiceLocator {

    /**
     * Sub directory of {@link Context#getFilesDir()}.
     * log files.
     */
    static final String DIR_LOG = "log";

    /**
     * Sub directory of {@link Context#getFilesDir()}.
     * Database backup taken during app upgrades.
     */
    private static final String DIR_UPGRADES = "upgrades";

    /** Singleton. */
    private static ServiceLocator sInstance;

    /** Either the real Application Context, or the injected context when running in unit tests. */
    @NonNull
    private final Context mAppContext;

    /** NOT an interface. Cannot be injected. */
    @Nullable
    private DBHelper mDBHelper;

    /** NOT an interface. Cannot be injected. */
    @Nullable
    private CoversDbHelper mCoversDbHelper;

    /** NOT an interface. Cannot be injected. */
    @Nullable
    private SQLiteOpenHelper mTaskQueueDBHelper;

    /** NOT an interface. Cannot be injected. The underlying {@link StyleDao} can be injected. */
    @Nullable
    private Styles mStyles;


    /** NOT an interface but CAN be injected for testing. */
    @Nullable
    private CookieManager mCookieManager;


    /** Interfaces. */
    @Nullable
    private AppLocale mAppLocale;

    @Nullable
    private Notifier mNotifier;

    @Nullable
    private AuthorDao mAuthorDao;
    @Nullable
    private BookDao mBookDao;
    @Nullable
    private BookshelfDao mBookshelfDao;
    @Nullable
    private CalibreDao mCalibreDao;
    @Nullable
    private CalibreLibraryDao mCalibreLibraryDao;
    @Nullable
    private ColorDao mColorDao;
    @Nullable
    private CoverCacheDao mCoverCacheDao;
    @Nullable
    private FormatDao mFormatDao;
    @Nullable
    private FtsDao mFtsDao;
    @Nullable
    private GenreDao mGenreDao;
    @Nullable
    private GoodreadsDao mGoodreadsDao;
    @Nullable
    private LanguageDao mLanguageDao;
    @Nullable
    private LoaneeDao mLoaneeDao;
    @Nullable
    private LocationDao mLocationDao;
    @Nullable
    private MaintenanceDao mMaintenanceDao;
    @Nullable
    private PublisherDao mPublisherDao;
    @Nullable
    private SeriesDao mSeriesDao;
    @Nullable
    private StripInfoDao mStripInfoDao;
    @Nullable
    private StyleDao mStyleDao;
    @Nullable
    private TocEntryDao mTocEntryDao;


    /**
     * Private constructor.
     *
     * @param context Current context
     */
    private ServiceLocator(@NonNull final Context context) {
        mAppContext = context.getApplicationContext();
    }

    /**
     * Public constructor.
     *
     * @param context <strong>Application</strong> or <strong>test</strong> context.
     */
    public static void create(@NonNull final Context context) {
        synchronized (ServiceLocator.class) {
            if (sInstance == null) {
                sInstance = new ServiceLocator(context);
                File dir = getLogDir();
                if (!dir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                }

                dir = getUpgradesDir();
                if (!dir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                }
            }
        }
    }

    @NonNull
    public static ServiceLocator getInstance() {
        return sInstance;
    }


    /**
     * Get the Application Context <strong>with the device Locale</strong>.
     *
     * @return raw Application Context
     */
    @NonNull
    public static Context getAppContext() {
        return sInstance.mAppContext;
    }

    /**
     * Get a <strong>new</strong>> localized Application Context
     *
     * @return Application Context using the user preferred Locale
     */
    @NonNull
    public static Context getLocalizedAppContext() {
        return sInstance.getAppLocale().apply(sInstance.mAppContext);
    }


    @NonNull
    public static SharedPreferences getGlobalPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(sInstance.mAppContext);
    }


    /**
     * Return the device Locale.
     * <p>
     * When running a JUnit test, this method will always return {@code Locale.ENGLISH}.
     *
     * @return Locale
     */
    @NonNull
    public static Locale getSystemLocale() {
        // While running JUnit tests we cannot get access or mock Resources.getSystem(),
        // ... so we need to cheat.
        if (BuildConfig.DEBUG /* always */) {
            if (Logger.isJUnitTest) {
                return Locale.US;
            }
        }

        return Resources.getSystem().getConfiguration().getLocales().get(0);
    }


    @NonNull
    public static File getLogDir() {
        return new File(sInstance.mAppContext.getFilesDir(), DIR_LOG);
    }

    @NonNull
    public static File getUpgradesDir() {
        return new File(sInstance.mAppContext.getFilesDir(), DIR_UPGRADES);
    }


    @NonNull
    public Styles getStyles() {
        synchronized (this) {
            if (mStyles == null) {
                mStyles = new Styles();
            }
        }
        return mStyles;
    }


    /**
     * Client must call this <strong>before</strong> doing its first request (lazy init).
     *
     * @return the global cookie manager.
     */
    @NonNull
    public CookieManager getCookieManager() {
        synchronized (this) {
            if (mCookieManager == null) {
                mCookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
                CookieHandler.setDefault(mCookieManager);
            }
        }
        return mCookieManager;
    }

    @VisibleForTesting
    public void setCookieManager(@Nullable final CookieManager cookieManager) {
        mCookieManager = cookieManager;
        CookieHandler.setDefault(mCookieManager);
    }


    @NonNull
    public AppLocale getAppLocale() {
        synchronized (this) {
            if (mAppLocale == null) {
                mAppLocale = new AppLocaleImpl();
            }
        }
        return mAppLocale;
    }

    @VisibleForTesting
    public void setAppLocale(@Nullable final AppLocale locale) {
        mAppLocale = locale;
    }


    @NonNull
    public Notifier getNotifier() {
        synchronized (this) {
            if (mNotifier == null) {
                mNotifier = new NotifierImpl();
                getAppLocale().registerOnLocaleChangedListener(mNotifier);
            }
        }
        return mNotifier;
    }

    @VisibleForTesting
    public void setNotifier(@Nullable final Notifier notifier) {
        if (mNotifier != null) {
            getAppLocale().unregisterOnLocaleChangedListener(mNotifier);
        }
        mNotifier = notifier;
        if (mNotifier != null) {
            getAppLocale().registerOnLocaleChangedListener(mNotifier);
        }
    }


    /**
     * Called during startup. This will trigger the creation/upgrade/open process.
     *
     * @param global Global preferences
     */
    void initialiseDb(@NonNull final SharedPreferences global) {
        mDBHelper = new DBHelper(mAppContext);
        mDBHelper.initialiseDb(global);
    }

    /**
     * Main entry point for clients to get the main database.
     *
     * @return the database instance
     */
    @NonNull
    public SynchronizedDb getDb() {
        //noinspection ConstantConditions
        return mDBHelper.getDb();
    }

    /**
     * Main entry point for clients to get the covers database.
     *
     * @return the database instance
     */
    @NonNull
    public SynchronizedDb getCoversDb() {
        synchronized (this) {
            if (mCoversDbHelper == null) {
                mCoversDbHelper = new CoversDbHelper(mAppContext);
            }
        }
        return mCoversDbHelper.getDb();
    }

    @NonNull
    public SQLiteOpenHelper getTaskQueueDBHelper() {
        synchronized (this) {
            if (mTaskQueueDBHelper == null) {
                mTaskQueueDBHelper = new TaskQueueDBHelper(mAppContext);
            }
        }
        return mTaskQueueDBHelper;
    }


    public boolean isCollationCaseSensitive() {
        //noinspection ConstantConditions
        return mDBHelper.isCollationCaseSensitive();
    }

    void deleteDatabases(@NonNull final Context context) {
        context.deleteDatabase(DBHelper.DATABASE_NAME);
        context.deleteDatabase(CoversDbHelper.DATABASE_NAME);
        context.deleteDatabase(TaskQueueDBHelper.DATABASE_NAME);
    }


    @NonNull
    public AuthorDao getAuthorDao() {
        synchronized (this) {
            if (mAuthorDao == null) {
                mAuthorDao = new AuthorDaoImpl();
            }
        }
        return mAuthorDao;
    }

    @VisibleForTesting
    public void setAuthorDao(@Nullable final AuthorDao dao) {
        mAuthorDao = dao;
    }

    @NonNull
    public BookDao getBookDao() {
        synchronized (this) {
            if (mBookDao == null) {
                mBookDao = new BookDaoImpl();
            }
        }
        return mBookDao;
    }

    @VisibleForTesting
    public void setBookDao(@Nullable final BookDao dao) {
        mBookDao = dao;
    }

    @NonNull
    public BookshelfDao getBookshelfDao() {
        synchronized (this) {
            if (mBookshelfDao == null) {
                mBookshelfDao = new BookshelfDaoImpl();
            }
        }
        return mBookshelfDao;
    }

    @VisibleForTesting
    public void setBookshelfDao(@Nullable final BookshelfDao dao) {
        mBookshelfDao = dao;
    }

    @NonNull
    public CalibreDao getCalibreDao() {
        synchronized (this) {
            if (mCalibreDao == null) {
                mCalibreDao = new CalibreDaoImpl();
            }
        }
        return mCalibreDao;
    }

    @VisibleForTesting
    public void setCalibreDao(@Nullable final CalibreDao dao) {
        mCalibreDao = dao;
    }


    @NonNull
    public CalibreLibraryDao getCalibreLibraryDao() {
        synchronized (this) {
            if (mCalibreLibraryDao == null) {
                mCalibreLibraryDao = new CalibreLibraryDaoImpl();
            }
        }
        return mCalibreLibraryDao;
    }

    @VisibleForTesting
    public void setCalibreLibraryDao(@Nullable final CalibreLibraryDao dao) {
        mCalibreLibraryDao = dao;
    }

    @NonNull
    public ColorDao getColorDao() {
        synchronized (this) {
            if (mColorDao == null) {
                mColorDao = new ColorDaoImpl();
            }
        }
        return mColorDao;
    }

    @VisibleForTesting
    public void setColorDao(@Nullable final ColorDao dao) {
        mColorDao = dao;
    }

    @NonNull
    public FormatDao getFormatDao() {
        synchronized (this) {
            if (mFormatDao == null) {
                mFormatDao = new FormatDaoImpl();
            }
        }
        return mFormatDao;
    }

    @VisibleForTesting
    public void setFormatDao(@Nullable final FormatDao dao) {
        mFormatDao = dao;
    }

    @NonNull
    public FtsDao getFtsDao() {
        synchronized (this) {
            if (mFtsDao == null) {
                mFtsDao = new FtsDaoImpl();
            }
        }
        return mFtsDao;
    }

    @VisibleForTesting
    public void setFtsDao(@Nullable final FtsDao dao) {
        mFtsDao = dao;
    }


    @NonNull
    public GenreDao getGenreDao() {
        synchronized (this) {
            if (mGenreDao == null) {
                mGenreDao = new GenreDaoImpl();
            }
        }
        return mGenreDao;
    }

    @VisibleForTesting
    public void setGenreDao(@Nullable final GenreDao dao) {
        mGenreDao = dao;
    }

    @NonNull
    public GoodreadsDao getGoodreadsDao() {
        synchronized (this) {
            if (mGoodreadsDao == null) {
                mGoodreadsDao = new GoodreadsDaoImpl();
            }
        }
        return mGoodreadsDao;
    }

    @VisibleForTesting
    public void setGoodreadsDao(@Nullable final GoodreadsDao dao) {
        mGoodreadsDao = dao;
    }


    @NonNull
    public LanguageDao getLanguageDao() {
        synchronized (this) {
            if (mLanguageDao == null) {
                mLanguageDao = new LanguageDaoImpl();
            }
        }
        return mLanguageDao;
    }

    @VisibleForTesting
    public void setLanguageDao(@Nullable final LanguageDao dao) {
        mLanguageDao = dao;
    }

    @NonNull
    public LoaneeDao getLoaneeDao() {
        synchronized (this) {
            if (mLoaneeDao == null) {
                mLoaneeDao = new LoaneeDaoImpl();
            }
        }
        return mLoaneeDao;
    }

    @VisibleForTesting
    public void setLoaneeDao(@Nullable final LoaneeDao dao) {
        mLoaneeDao = dao;
    }

    @NonNull
    public LocationDao getLocationDao() {
        synchronized (this) {
            if (mLocationDao == null) {
                mLocationDao = new LocationDaoImpl();
            }
        }
        return mLocationDao;
    }

    @VisibleForTesting
    public void setLocationDao(@Nullable final LocationDao dao) {
        mLocationDao = dao;
    }

    @NonNull
    public MaintenanceDao getMaintenanceDao() {
        synchronized (this) {
            if (mMaintenanceDao == null) {
                mMaintenanceDao = new MaintenanceDaoImpl();
            }
        }
        return mMaintenanceDao;
    }

    @VisibleForTesting
    public void setMaintenanceDao(@Nullable final MaintenanceDao dao) {
        mMaintenanceDao = dao;
    }

    @NonNull
    public PublisherDao getPublisherDao() {
        synchronized (this) {
            if (mPublisherDao == null) {
                mPublisherDao = new PublisherDaoImpl();
            }
        }
        return mPublisherDao;
    }

    @VisibleForTesting
    public void setPublisherDao(@Nullable final PublisherDao dao) {
        mPublisherDao = dao;
    }

    @NonNull
    public SeriesDao getSeriesDao() {
        synchronized (this) {
            if (mSeriesDao == null) {
                mSeriesDao = new SeriesDaoImpl();
            }
        }
        return mSeriesDao;
    }

    @VisibleForTesting
    public void setSeriesDao(@Nullable final SeriesDao dao) {
        mSeriesDao = dao;
    }

    @NonNull
    public StripInfoDao getStripInfoDao() {
        synchronized (this) {
            if (mStripInfoDao == null) {
                mStripInfoDao = new StripInfoDaoImpl();
            }
        }
        return mStripInfoDao;
    }

    @VisibleForTesting
    public void setStripInfoDao(@Nullable final StripInfoDao dao) {
        mStripInfoDao = dao;
    }

    /**
     * You probably want to use {@link #getStyles()} instead.
     *
     * @return singleton
     */
    @NonNull
    public StyleDao getStyleDao() {
        synchronized (this) {
            if (mStyleDao == null) {
                mStyleDao = new StyleDaoImpl();
            }
        }
        return mStyleDao;
    }

    @VisibleForTesting
    public void setStyleDao(@Nullable final StyleDao dao) {
        mStyleDao = dao;
    }

    @NonNull
    public TocEntryDao getTocEntryDao() {
        synchronized (this) {
            if (mTocEntryDao == null) {
                mTocEntryDao = new TocEntryDaoImpl();
            }
        }
        return mTocEntryDao;
    }

    @VisibleForTesting
    public void setTocEntryDao(@Nullable final TocEntryDao dao) {
        mTocEntryDao = dao;
    }

    @NonNull
    public CoverCacheDao getCoverCacheDao() {
        synchronized (this) {
            if (mCoverCacheDao == null) {
                mCoverCacheDao = new CoverCacheDaoImpl();
            }
        }
        return mCoverCacheDao;
    }

    public void setCoverCacheDao(@Nullable final CoverCacheDao dao) {
        mCoverCacheDao = dao;
    }
}
