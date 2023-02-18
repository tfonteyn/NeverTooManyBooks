/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.Locale;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.booklist.style.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.database.CacheDbHelper;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BedethequeCacheDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreCustomFieldDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.ColorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CoverCacheDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.FormatDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.FtsDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.GenreDao;
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
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BedethequeCacheDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookshelfDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.CalibreCustomFieldDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.CalibreDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.CalibreLibraryDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.ColorDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.CoverCacheDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.FormatDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.FtsDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.GenreDaoImpl;
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
import com.hardbacknutter.nevertoomanybooks.debug.TestFlags;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocaleImpl;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;
import com.hardbacknutter.nevertoomanybooks.utils.notifier.Notifier;
import com.hardbacknutter.nevertoomanybooks.utils.notifier.NotifierImpl;

public final class ServiceLocator {

    /**
     * Sub directory of {@link Context#getFilesDir()}.
     * Database backup taken during app upgrades.
     */
    private static final String DIR_UPGRADES = "upgrades";

    /** Singleton. */
    private static ServiceLocator sInstance;

    /** Either the real Application Context, or the injected context when running in unit tests. */
    @NonNull
    private final Context appContext;

    /** TODO: allow this to be injected. */
    @Nullable
    private DBHelper dbHelper;

    /** TODO: allow this to be injected. */
    @Nullable
    private CacheDbHelper cacheDbHelper;

    @Nullable
    private Logger logger;

    @Nullable
    private StylesHelper stylesHelper;

    @Nullable
    private Languages languages;

    @Nullable
    private CookieManager cookieManager;

    /** Allows injecting a Mock Bundle supplier for JUnit tests. */
    @NonNull
    private Supplier<Bundle> bundleSupplier = Bundle::new;

    @Nullable
    private AppLocale appLocale;

    @Nullable
    private Notifier notifier;

    @Nullable
    private AuthorDao authorDao;
    @Nullable
    private BedethequeCacheDao bedethequeCacheDao;
    @Nullable
    private BookDao bookDao;
    @Nullable
    private BookshelfDao bookshelfDao;
    @Nullable
    private CalibreDao calibreDao;
    @Nullable
    private CalibreLibraryDao calibreLibraryDao;
    @Nullable
    private CalibreCustomFieldDao calibreCustomFieldDao;
    @Nullable
    private ColorDao colorDao;
    @Nullable
    private CoverCacheDao coverCacheDao;
    @Nullable
    private FormatDao formatDao;
    @Nullable
    private FtsDao ftsDao;
    @Nullable
    private GenreDao genreDao;
    @Nullable
    private LanguageDao languageDao;
    @Nullable
    private LoaneeDao loaneeDao;
    @Nullable
    private LocationDao locationDao;
    @Nullable
    private MaintenanceDao maintenanceDao;
    @Nullable
    private PublisherDao publisherDao;
    @Nullable
    private SeriesDao seriesDao;
    @Nullable
    private StripInfoDao stripInfoDao;
    @Nullable
    private StyleDao styleDao;
    @Nullable
    private TocEntryDao tocEntryDao;


    /**
     * Private constructor.
     *
     * @param context Current context
     */
    private ServiceLocator(@NonNull final Context context) {
        appContext = context.getApplicationContext();
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

                SearchEngineConfig.createRegistry(context);
            }
        }
    }

    /**
     * Public constructor for testing and recreation when testing.
     *
     * @param context        <strong>Application</strong> or <strong>test</strong> context.
     * @param bundleSupplier to provide new (mock) Bundle instances.
     */
    public static void create(@NonNull final Context context,
                              @NonNull final Supplier<Bundle> bundleSupplier) {
        create(context);
        sInstance.bundleSupplier = bundleSupplier;
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
        return sInstance.appContext;
    }

    @NonNull
    public static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(sInstance.appContext);
    }

    /**
     * Return the device Locale.
     * <p>
     * When running a JUnit test, this method will always return {@code Locale.US}.
     *
     * @return Locale
     */
    @NonNull
    public static Locale getSystemLocale() {
        // While running JUnit tests we cannot get access or mock Resources.getSystem(),
        // ... so we need to cheat.
        if (BuildConfig.DEBUG && TestFlags.isJUnit) {
            return Locale.US;
        }

        return Resources.getSystem().getConfiguration().getLocales().get(0);
    }

    /**
     * Create a "new Bundle()" using a supplier.
     * This allows us to inject mock-bundle's when running a JUnit test.
     * <p>
     * Note: static as we're using this very frequently.
     *
     * @return bundle
     */
    @NonNull
    public static Bundle newBundle() {
        return sInstance.bundleSupplier.get();
    }

    @NonNull
    public Logger getLogger() {
        if (logger == null) {
            logger = new Logger(appContext);
        }
        return logger;
    }

    @NonNull
    public File getUpgradesDir() {
        final File dir = new File(appContext.getFilesDir(), DIR_UPGRADES);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Get a <strong>new</strong>> localized Application Context.
     *
     * @return Application Context using the user preferred Locale
     */
    @NonNull
    public Context getLocalizedAppContext() {
        return getAppLocale().apply(sInstance.appContext);
    }

    /**
     * Called between multiple tests so we get a clean db for each test.
     */
    @VisibleForTesting
    void recreate() {
        if (cacheDbHelper != null) {
            cacheDbHelper.close();
        }
        if (dbHelper != null) {
            dbHelper.close();
        }

        final Context tmpAppContext = sInstance.appContext;
        final Supplier<Bundle> tmpBundleSupplier = sInstance.bundleSupplier;
        //noinspection ConstantConditions
        sInstance = null;
        create(tmpAppContext, tmpBundleSupplier);
    }

    /**
     * Get the styles cache container.
     *
     * @return singleton
     */
    @NonNull
    public StylesHelper getStyles() {
        synchronized (this) {
            if (stylesHelper == null) {
                stylesHelper = new StylesHelper();
            }
        }
        return stylesHelper;
    }

    /**
     * Get the language cache container.
     *
     * @return singleton
     */
    @NonNull
    public Languages getLanguages() {
        synchronized (this) {
            if (languages == null) {
                languages = new Languages();
            }
        }
        return languages;
    }


    /**
     * Client must call this <strong>before</strong> doing its first request (lazy init).
     *
     * @return the global cookie manager.
     */
    @NonNull
    public CookieManager getCookieManager() {
        synchronized (this) {
            if (cookieManager == null) {
                cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
                CookieHandler.setDefault(cookieManager);
            }
        }
        return cookieManager;
    }

    @VisibleForTesting
    public void setCookieManager(@Nullable final CookieManager cookieManager) {
        this.cookieManager = cookieManager;
        CookieHandler.setDefault(this.cookieManager);
    }


    @NonNull
    public AppLocale getAppLocale() {
        synchronized (this) {
            if (appLocale == null) {
                appLocale = new AppLocaleImpl();
            }
        }
        return appLocale;
    }

    @VisibleForTesting
    public void setAppLocale(@Nullable final AppLocale locale) {
        appLocale = locale;
    }


    @NonNull
    public Notifier getNotifier() {
        synchronized (this) {
            if (notifier == null) {
                notifier = new NotifierImpl();
                getAppLocale().registerOnLocaleChangedListener(notifier);
            }
        }
        return notifier;
    }

    @VisibleForTesting
    public void setNotifier(@Nullable final Notifier notifier) {
        if (this.notifier != null) {
            getAppLocale().unregisterOnLocaleChangedListener(this.notifier);
        }
        this.notifier = notifier;
        if (this.notifier != null) {
            getAppLocale().registerOnLocaleChangedListener(this.notifier);
        }
    }

    /**
     * Main entry point for clients to get the main database.
     * <p>
     * <strong>Dev. note:</strong> This method always returns the same object for
     * the duration of the apps life. Our DBHelper caches a single SynchronizedDb,
     * which in turn caches the database from the underlying SQLiteOpenHelper
     * which in turn caches the actual database.
     * Or in short: it's safe to use this as a singleton.
     *
     * @return the database instance
     */
    @NonNull
    public SynchronizedDb getDb() {
        synchronized (this) {
            if (dbHelper == null) {
                dbHelper = new DBHelper(appContext);
            }
        }
        return dbHelper.getDb();
    }

    /**
     * Main entry point for clients to get the cache database.
     *
     * @return the database instance
     *
     * @see #getDb()
     */
    @NonNull
    public SynchronizedDb getCacheDb() {
        synchronized (this) {
            if (cacheDbHelper == null) {
                cacheDbHelper = new CacheDbHelper(appContext, getDb().isCollationCaseSensitive());
            }
        }
        return cacheDbHelper.getDb();
    }

    @NonNull
    public AuthorDao getAuthorDao() {
        synchronized (this) {
            if (authorDao == null) {
                authorDao = new AuthorDaoImpl(getDb());
            }
        }
        return authorDao;
    }

    @NonNull
    public BedethequeCacheDao getBedethequeCacheDao() {
        synchronized (this) {
            if (bedethequeCacheDao == null) {
                bedethequeCacheDao = new BedethequeCacheDaoImpl(getCacheDb());
            }
        }
        return bedethequeCacheDao;
    }

    @NonNull
    public BookDao getBookDao() {
        synchronized (this) {
            if (bookDao == null) {
                bookDao = new BookDaoImpl(getDb());
            }
        }
        return bookDao;
    }

    @VisibleForTesting
    public void setBookDao(@Nullable final BookDao dao) {
        bookDao = dao;
    }

    @NonNull
    public BookshelfDao getBookshelfDao() {
        synchronized (this) {
            if (bookshelfDao == null) {
                bookshelfDao = new BookshelfDaoImpl(getDb());
            }
        }
        return bookshelfDao;
    }

    @NonNull
    public CalibreDao getCalibreDao() {
        synchronized (this) {
            if (calibreDao == null) {
                calibreDao = new CalibreDaoImpl(getDb());
            }
        }
        return calibreDao;
    }

    @NonNull
    public CalibreLibraryDao getCalibreLibraryDao() {
        synchronized (this) {
            if (calibreLibraryDao == null) {
                calibreLibraryDao = new CalibreLibraryDaoImpl(getDb());
            }
        }
        return calibreLibraryDao;
    }

    @NonNull
    public CalibreCustomFieldDao getCalibreCustomFieldDao() {
        synchronized (this) {
            if (calibreCustomFieldDao == null) {
                calibreCustomFieldDao = new CalibreCustomFieldDaoImpl(getDb());
            }
        }
        return calibreCustomFieldDao;
    }

    @NonNull
    public ColorDao getColorDao() {
        synchronized (this) {
            if (colorDao == null) {
                colorDao = new ColorDaoImpl(getDb());
            }
        }
        return colorDao;
    }

    @NonNull
    public FormatDao getFormatDao() {
        synchronized (this) {
            if (formatDao == null) {
                formatDao = new FormatDaoImpl(getDb());
            }
        }
        return formatDao;
    }

    @NonNull
    public FtsDao getFtsDao() {
        synchronized (this) {
            if (ftsDao == null) {
                ftsDao = new FtsDaoImpl(getDb());
            }
        }
        return ftsDao;
    }

    @NonNull
    public GenreDao getGenreDao() {
        synchronized (this) {
            if (genreDao == null) {
                genreDao = new GenreDaoImpl(getDb());
            }
        }
        return genreDao;
    }

    @NonNull
    public LanguageDao getLanguageDao() {
        synchronized (this) {
            if (languageDao == null) {
                languageDao = new LanguageDaoImpl(getDb());
            }
        }
        return languageDao;
    }

    @NonNull
    public LoaneeDao getLoaneeDao() {
        synchronized (this) {
            if (loaneeDao == null) {
                loaneeDao = new LoaneeDaoImpl(getDb());
            }
        }
        return loaneeDao;
    }

    @NonNull
    public LocationDao getLocationDao() {
        synchronized (this) {
            if (locationDao == null) {
                locationDao = new LocationDaoImpl(getDb());
            }
        }
        return locationDao;
    }

    @NonNull
    public MaintenanceDao getMaintenanceDao() {
        synchronized (this) {
            if (maintenanceDao == null) {
                maintenanceDao = new MaintenanceDaoImpl(getDb());
            }
        }
        return maintenanceDao;
    }

    @NonNull
    public PublisherDao getPublisherDao() {
        synchronized (this) {
            if (publisherDao == null) {
                publisherDao = new PublisherDaoImpl(getDb());
            }
        }
        return publisherDao;
    }

    @NonNull
    public SeriesDao getSeriesDao() {
        synchronized (this) {
            if (seriesDao == null) {
                seriesDao = new SeriesDaoImpl(getDb());
            }
        }
        return seriesDao;
    }

    @NonNull
    public StripInfoDao getStripInfoDao() {
        synchronized (this) {
            if (stripInfoDao == null) {
                stripInfoDao = new StripInfoDaoImpl(getDb());
            }
        }
        return stripInfoDao;
    }

    /**
     * You probably want to use {@link #getStyles()} instead.
     *
     * @return singleton
     */
    @NonNull
    public StyleDao getStyleDao() {
        synchronized (this) {
            if (styleDao == null) {
                styleDao = new StyleDaoImpl(getDb());
            }
        }
        return styleDao;
    }

    @NonNull
    public TocEntryDao getTocEntryDao() {
        synchronized (this) {
            if (tocEntryDao == null) {
                tocEntryDao = new TocEntryDaoImpl(getDb());
            }
        }
        return tocEntryDao;
    }

    @NonNull
    public CoverCacheDao getCoverCacheDao() {
        synchronized (this) {
            if (coverCacheDao == null) {
                coverCacheDao = new CoverCacheDaoImpl(getCacheDb());
            }
        }
        return coverCacheDao;
    }
}
