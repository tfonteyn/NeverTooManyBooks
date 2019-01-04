package com.eleybourn.bookcatalogue.booklist.prefs;

import java.util.Set;

import androidx.annotation.NonNull;

/**
 * A Set or a List is always represented by a Set<String> in the SharedPreferences
 * due to limitations of {@link android.preference.ListPreference}
 * and {@link android.preference.MultiSelectListPreference}
 *
 * This call allows by-passing the real type for write-through.
 * Used in importing from a backup.
 */
public interface PSet {

    void set(@NonNull final String uuid,
             @NonNull final Set<String> value);
}
