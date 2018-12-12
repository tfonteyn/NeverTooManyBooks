package com.eleybourn.bookcatalogue.utils;

import androidx.core.content.FileProvider;

import com.eleybourn.bookcatalogue.BookCatalogueApp;

/**
 * This does not really need to be a class as after all we only really want the AUTHORITY string.
 * But let's keep an open mind to future enhancements (if any).
 */
public class GenericFileProvider extends FileProvider {
    /**
     * Matches the Manifest entry:
     *
     * android:authorities="${packageName}.GenericFileProvider"
     */
    public final static String AUTHORITY = BookCatalogueApp.getAppContext().getPackageName() +
            ".GenericFileProvider";
}
