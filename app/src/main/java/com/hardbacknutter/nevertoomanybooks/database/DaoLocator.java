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
package com.hardbacknutter.nevertoomanybooks.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.ColorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CoverCacheDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.FormatDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.GenreDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.GoodreadsDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LanguageDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LoaneeDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LocationDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.MaintenanceDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.StyleDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.AuthorDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookshelfDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.CalibreLibraryDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.ColorDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.CoverCacheDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.FormatDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.GenreDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.GoodreadsDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.LanguageDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.LoaneeDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.LocationDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.MaintenanceDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.PublisherDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.SeriesDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.StyleDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.TocEntryDaoImpl;

/**
 * The use and definition of DAO in this project has a long history.
 * Migrating to 'best practices' has been an ongoing effort but is at best a far future goal.
 * The main issue is that all testing must be done with the emulator as we can't easily
 * inject mock doa's for now.
 * <p>
 * This class is the next step as we can mock dao classes before running a test.
 * <p>
 * TODO: {@link BookDao} which cannot be a singleton.
 */
public final class DaoLocator {

    /** Singleton. */
    private static DaoLocator sInstance;

    @Nullable
    private AuthorDao mAuthorDao;
    @Nullable
    private BookshelfDao mBookshelfDao;
    @Nullable
    private CalibreLibraryDao mCalibreLibraryDao;
    @Nullable
    private ColorDao mColorDao;
    @Nullable
    private FormatDao mFormatDao;
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
    private StyleDao mStyleDao;
    @Nullable
    private TocEntryDao mTocEntryDao;


    @Nullable
    private CoverCacheDao mCoverCacheDao;

    private DaoLocator() {
    }

    @NonNull
    public static DaoLocator getInstance() {
        synchronized (DaoLocator.class) {
            if (sInstance == null) {
                sInstance = new DaoLocator();
            }
        }
        return sInstance;
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
    public void setAuthorDao(@Nullable final AuthorDao authorDao) {
        mAuthorDao = authorDao;
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
    public void setBookshelfDao(@Nullable final BookshelfDao bookshelfDao) {
        mBookshelfDao = bookshelfDao;
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
    public void setCalibreLibraryDao(@Nullable final CalibreLibraryDao calibreLibraryDao) {
        mCalibreLibraryDao = calibreLibraryDao;
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
    public void setColorDao(@Nullable final ColorDao colorDao) {
        mColorDao = colorDao;
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
    public void setFormatDao(@Nullable final FormatDao formatDao) {
        mFormatDao = formatDao;
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
    public void setGenreDao(@Nullable final GenreDao genreDao) {
        mGenreDao = genreDao;
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
    public void setGoodreadsDao(@Nullable final GoodreadsDao goodreadsDao) {
        mGoodreadsDao = goodreadsDao;
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
    public void setLanguageDao(@Nullable final LanguageDao languageDao) {
        mLanguageDao = languageDao;
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
    public void setLoaneeDao(@Nullable final LoaneeDao loaneeDao) {
        mLoaneeDao = loaneeDao;
    }

    @NonNull
    public LocationDao getLocationDaoDao() {
        synchronized (this) {
            if (mLocationDao == null) {
                mLocationDao = new LocationDaoImpl();
            }
        }
        return mLocationDao;
    }

    @VisibleForTesting
    public void setLocationDao(@Nullable final LocationDao locationDao) {
        mLocationDao = locationDao;
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
    public void setMaintenanceDao(@Nullable final MaintenanceDao maintenanceDao) {
        mMaintenanceDao = maintenanceDao;
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
    public void setPublisherDao(@Nullable final PublisherDao publisherDao) {
        mPublisherDao = publisherDao;
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
    public void setSeriesDao(@Nullable final SeriesDao seriesDao) {
        mSeriesDao = seriesDao;
    }

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
    public void setStyleDao(@Nullable final StyleDao styleDao) {
        mStyleDao = styleDao;
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
    public void setTocEntryDao(@Nullable final TocEntryDao tocEntryDao) {
        mTocEntryDao = tocEntryDao;
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

    public void setCoverCacheDao(@Nullable final CoverCacheDao coverCacheDao) {
        mCoverCacheDao = coverCacheDao;
    }
}
