package com.eleybourn.bookcatalogue.goodreads;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.goodreads.api.ShelvesListApiHandler;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

public class GoodreadsShelf {

    /** Virtual shelf names used in XML request/responses. */
    public static final String VIRTUAL_CURRENTLY_READING = "currently-reading";
    public static final String VIRTUAL_READ = "read";
    public static final String VIRTUAL_TO_READ = "to-read";

    /** Bundle with shelf related entries. */
    @NonNull
    private final Bundle mBundle;

    public GoodreadsShelf(@NonNull final Bundle bundle) {
        mBundle = bundle;
    }

    /**
     * Create canonical representation based on the best guess as to the Goodreads rules.
     *
     * @param name to bless
     *
     * @return blessed name
     */
    public static String canonicalizeName(@NonNull final String name) {

        StringBuilder canonical = new StringBuilder();
        String lcName = name.toLowerCase(LocaleUtils.getPreferredLocal());
        for (int i = 0; i < lcName.length(); i++) {
            char c = lcName.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                canonical.append(c);
            } else {
                canonical.append('-');
            }
        }
        return canonical.toString();
    }

    @NonNull
    public String getName() {
        //noinspection ConstantConditions
        return mBundle.getString(ShelvesListApiHandler.ShelvesField.NAME);
    }

    boolean isExclusive() {
        return mBundle.getBoolean(ShelvesListApiHandler.ShelvesField.EXCLUSIVE);
    }
}
