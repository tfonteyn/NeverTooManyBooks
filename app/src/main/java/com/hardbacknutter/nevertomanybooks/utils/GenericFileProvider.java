package com.hardbacknutter.nevertomanybooks.utils;

import androidx.core.content.FileProvider;

import com.hardbacknutter.nevertomanybooks.App;

/**
 * This does not really need to be a class as after all we only really want the AUTHORITY string.
 * But let's keep an open mind to future enhancements (if any).
 */
public class GenericFileProvider
        extends FileProvider {

    /**
     * Matches the Manifest entry.
     * <p>
     * android:authorities="${packageName}.GenericFileProvider"
     */
    public static final String AUTHORITY =
            App.getAppContext().getPackageName() + ".GenericFileProvider";
}
