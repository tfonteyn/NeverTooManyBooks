package com.eleybourn.bookcatalogue.booklist.prefs;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Set;

/**
 * A Set or a List is always represented by a {@code Set<String>} in the SharedPreferences
 * due to limitations of {@link androidx.preference.ListPreference}
 * and {@link androidx.preference.MultiSelectListPreference}
 * <p>
 * This call allows by-passing the real type for write-through.
 * Used in importing from a backup.
 */
public interface PCollection<E> {

    /**
     * This call allows by-passing the real type for write-through.
     */
    void set(@NonNull Set<String> value);

    @NonNull
    Collection<E> get();
}
