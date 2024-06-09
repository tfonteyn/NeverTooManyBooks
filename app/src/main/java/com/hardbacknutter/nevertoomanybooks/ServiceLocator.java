/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.content.res.Resources;
import android.os.Bundle;
import android.os.LocaleList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.network.NetworkChecker;
import com.hardbacknutter.nevertoomanybooks.core.network.NetworkCheckerImpl;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
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
import com.hardbacknutter.nevertoomanybooks.database.dao.DeletedBooksDao;
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
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
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
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.DeletedBooksDaoImpl;
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
import com.hardbacknutter.nevertoomanybooks.settings.FieldVisibilityPreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocaleImpl;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;
import com.hardbacknutter.nevertoomanybooks.utils.notifier.Notifier;
import com.hardbacknutter.nevertoomanybooks.utils.notifier.NotifierImpl;
import com.hardbacknutter.util.logger.FileLogger;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

public class ServiceLocator {

    /**
     * Sub directory of {@link Context#getFilesDir()}.
     * Database backup taken during app upgrades.
     */
    private static final String DIR_UPGRADES = "upgrades";

    /**
     * Singleton.
     *
     * @see #create
     */
    @SuppressWarnings("StaticVariableMayNotBeInitialized")
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
    private NetworkChecker networkChecker;

    @Nullable
    private StylesHelper stylesHelper;

    @Nullable
    private FieldVisibility fieldVisibility;

    @Nullable
    private ReorderHelper reorderHelper;

    @Nullable
    private Languages languages;

    @Nullable
    private CoverStorage coverStorage;

    @Nullable
    private CookieManager cookieManager;

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
    private DeletedBooksDao deletedBooksDao;
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
    @VisibleForTesting
    ServiceLocator(@NonNull final Context context) {
        appContext = context.getApplicationContext();
    }

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public static void create(@NonNull final Context context) {
        synchronized (ServiceLocator.class) {
            if (sInstance == null) {
                sInstance = new ServiceLocator(context);
            }
        }
    }

    /**
     * Test constructor.
     *
     * @param serviceLocator mock instance
     */
    @VisibleForTesting
    public static void create(@NonNull final ServiceLocator serviceLocator) {
        synchronized (ServiceLocator.class) {
            sInstance = serviceLocator;
        }
    }

    /**
     * Retrieve the singleton instance.
     *
     * @return singleton
     */
    @NonNull
    public static ServiceLocator getInstance() {
        //noinspection StaticVariableUsedBeforeInitialization
        return sInstance;
    }

    /**
     * Get the Application Context <strong>with the device Locale</strong>.
     *
     * @return raw Application Context
     */
    @NonNull
    public Context getAppContext() {
        return appContext;
    }

    /**
     * Create a "new Bundle()".
     * <p>
     * Dev. note: the only reason for this method is so we can mock creating a new Bundle.
     *
     * @return new Bundle instance
     */
    @NonNull
    public Bundle newBundle() {
        return new Bundle();
    }

    /**
     * Return the device {@link LocaleList}.
     * <p>
     * Dev. note: the only reason for this method is so we can mock getting the LocaleList.
     *
     * @return LocaleList
     */
    @NonNull
    public LocaleList getSystemLocaleList() {
        return Resources.getSystem().getConfiguration().getLocales();
    }

    @NonNull
    public NetworkChecker getNetworkChecker() {
        if (networkChecker == null) {
            networkChecker = new NetworkCheckerImpl(this::getAppContext);
        }
        return networkChecker;
    }

    @Nullable
    public File getLogDir() {
        final Logger logger = LoggerFactory.getLogger();
        if (logger instanceof FileLogger) {
            return ((FileLogger) logger).getLogDir();
        }
        return null;
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
     * Get the styles cache container.
     *
     * @return singleton
     */
    @NonNull
    public StylesHelper getStyles() {
        synchronized (this) {
            if (stylesHelper == null) {
                stylesHelper = new StylesHelper(this::getAppContext,
                                                this::getStyleDao);
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
                languages = new Languages(this::getAppLocale);
            }
        }
        return languages;
    }

    /**
     * Get the global field visibility settings.
     *
     * @return singleton
     */
    @NonNull
    public FieldVisibility getGlobalFieldVisibility() {
        synchronized (this) {
            if (fieldVisibility == null) {
                fieldVisibility = new FieldVisibility();
                final long current = PreferenceManager
                        .getDefaultSharedPreferences(getAppContext())
                        .getLong(FieldVisibilityPreferenceFragment.PK_FIELD_VISIBILITY,
                                 Long.MAX_VALUE);

                fieldVisibility.setBitValue(current);
            }
        }
        return fieldVisibility;
    }

    /**
     * Convenience method to make it easier to identify where we use the global.
     * (i.e. differentiate between 'isVisible' on a List/Detail screen versus the global.
     *
     * @param dbKey to check
     *
     * @return {@code true} if the field is globally enabled.
     */
    public boolean isFieldEnabled(@NonNull final String dbKey) {
        return getGlobalFieldVisibility().isVisible(dbKey).orElse(true);
    }

    /**
     * Get the cover storage helper.
     *
     * @return singleton
     */
    @NonNull
    public CoverStorage getCoverStorage() {
        synchronized (this) {
            if (coverStorage == null) {
                coverStorage = new CoverStorage(this::getAppContext,
                                                this::getCoverCacheDao);
            }
        }
        return coverStorage;
    }

    /**
     * Get the title/name reordering helper..
     *
     * @return singleton
     */
    @NonNull
    public ReorderHelper getReorderHelper() {
        synchronized (this) {
            if (reorderHelper == null) {
                reorderHelper = new ReorderHelper(this::getAppLocale);
            }
        }
        return reorderHelper;
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

    /**
     * Get the current application locale helper.
     *
     * @return singleton
     */
    @NonNull
    public AppLocale getAppLocale() {
        synchronized (this) {
            if (appLocale == null) {
                // Using SYSTEM Locales versus USER Locales, see:
                // https://medium.com/@hectorricardomendez/how-to-get-the-current-locale-in-android-fc12d8be6242
                // Key-Takeaway #5: To get the list of preferred locales of the device (as defined in
                // the Settings), call Resources.getSystem().getConfiguration().getLocales()
                //
                // although, at this point, it seems LocaleList.getDefault() DOES return
                // the correct list, so we could use that. It's not clear if it matters.
                appLocale = new AppLocaleImpl(getSystemLocaleList(), this::getLanguages);
            }
        }
        return appLocale;
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
                bookDao = new BookDaoImpl(getDb(), getSystemLocaleList().get(0),
                                          this::getAuthorDao,
                                          this::getSeriesDao,
                                          this::getPublisherDao,
                                          this::getBookshelfDao,
                                          this::getTocEntryDao,
                                          this::getLoaneeDao,
                                          this::getCalibreDao,
                                          this::getStripInfoDao,
                                          this::getFtsDao,
                                          this::getCoverStorage,
                                          this::getReorderHelper);
            }
        }
        return bookDao;
    }

    @NonNull
    public DeletedBooksDao getDeletedBooksDao() {
        synchronized (this) {
            if (deletedBooksDao == null) {
                deletedBooksDao = new DeletedBooksDaoImpl(getDb(), this::getBookDao);
            }
        }
        return deletedBooksDao;
    }

    @NonNull
    public BookshelfDao getBookshelfDao() {
        synchronized (this) {
            if (bookshelfDao == null) {
                bookshelfDao = new BookshelfDaoImpl(getDb(),
                                                    this::getStyles);
            }
        }
        return bookshelfDao;
    }

    @NonNull
    public CalibreDao getCalibreDao() {
        synchronized (this) {
            if (calibreDao == null) {
                calibreDao = new CalibreDaoImpl(getDb(), this::getCalibreLibraryDao);
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
                ftsDao = new FtsDaoImpl(getDb(),
                                        this::getStyles);
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
                languageDao = new LanguageDaoImpl(getDb(), this::getLanguages);
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
                maintenanceDao = new MaintenanceDaoImpl(getDb(),
                                                        this::getAuthorDao,
                                                        this::getSeriesDao,
                                                        this::getPublisherDao,
                                                        this::getAppLocale,
                                                        this::getReorderHelper);
            }
        }
        return maintenanceDao;
    }

    @NonNull
    public PublisherDao getPublisherDao() {
        synchronized (this) {
            if (publisherDao == null) {
                publisherDao = new PublisherDaoImpl(getDb(),
                                                    this::getReorderHelper);
            }
        }
        return publisherDao;
    }

    @NonNull
    public SeriesDao getSeriesDao() {
        synchronized (this) {
            if (seriesDao == null) {
                seriesDao = new SeriesDaoImpl(getDb(),
                                              this::getReorderHelper);
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
    private StyleDao getStyleDao() {
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
                tocEntryDao = new TocEntryDaoImpl(getDb(),
                                                  this::getReorderHelper,
                                                  this::getAuthorDao
                );
            }
        }
        return tocEntryDao;
    }

    /**
     * You probably want to use {@link #getCoverStorage()} instead.
     *
     * @return singleton
     */
    @NonNull
    public CoverCacheDao getCoverCacheDao() {
        synchronized (this) {
            if (coverCacheDao == null) {
                coverCacheDao = new CoverCacheDaoImpl(getCacheDb(),
                                                      this::getCoverStorage);
            }
        }
        return coverCacheDao;
    }
}
