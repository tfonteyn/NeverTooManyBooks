package com.eleybourn.bookcatalogue.scanner;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BCPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * Class to handle details of specific scanner interfaces and return a
 * Scanner object to the caller.
 *
 * @author pjw
 */
public class ScannerManager {
    /** Preference key */
    public static final String PREF_PREFERRED_SCANNER = "ScannerManager.PreferredScanner";

    /** Unique IDs to associate with each supported scanner intent */
    public static final int SCANNER_ZXING_COMPATIBLE = 1;
    public static final int SCANNER_PIC2SHOP = 2;
    public static final int SCANNER_ZXING = 3;

    /** Collection of ScannerFactory objects */
    @SuppressLint("UseSparseArrays")
    private static final Map<Integer, ScannerFactory> myScannerFactories = new HashMap<>();

    /*
     * Build the collection
     */
    static {
        myScannerFactories.put(SCANNER_ZXING_COMPATIBLE, new ScannerFactory() {
            @NonNull
            @Override
            public Scanner newInstance() {
                return new ZxingScanner(false);
            }

            @Override
            public boolean isIntentAvailable() {
                return ZxingScanner.isIntentAvailable(false);
            }
        });

        myScannerFactories.put(SCANNER_ZXING, new ScannerFactory() {
            @NonNull
            @Override
            public Scanner newInstance() {
                return new ZxingScanner(true);
            }

            @Override
            public boolean isIntentAvailable() {
                return ZxingScanner.isIntentAvailable(true);
            }
        });

        myScannerFactories.put(SCANNER_PIC2SHOP, new ScannerFactory() {
            @NonNull
            @Override
            public Scanner newInstance() {
                return new Pic2ShopScanner();
            }

            @Override
            public boolean isIntentAvailable() {
                return Pic2ShopScanner.isIntentAvailable();
            }
        });
    }

    /**
     * Return a Scanner object based on the current environment and user preferences.
     *
     * @return A Scanner
     */
    @NonNull
    public static Scanner getScanner() {
        // Find out what the user prefers if any
        int prefScanner = BCPreferences.getInt(PREF_PREFERRED_SCANNER, SCANNER_ZXING_COMPATIBLE);

        // See if preferred one is present, if so return a new instance
        ScannerFactory psf = myScannerFactories.get(prefScanner);
        if (psf != null && psf.isIntentAvailable()) {
            return psf.newInstance();
        }

        // Search all supported scanners. If none, just return a Zxing one
        for (ScannerFactory sf : myScannerFactories.values()) {
            if (sf != psf && sf.isIntentAvailable()) {
                return sf.newInstance();
            }
        }
        return new ZxingScanner(false);
    }

    /**
     * Support for creating scanner objects on the fly without knowing which ones are available.
     *
     * @author pjw
     */
    private interface ScannerFactory {
        /** Create a new scanner of the related type */
        @NonNull
        Scanner newInstance();

        /** Check if this scanner is available */
        boolean isIntentAvailable();
    }

}
