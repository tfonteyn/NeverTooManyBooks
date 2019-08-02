/**
 * Problem:
 * <p>
 * The Preferences in {@link androidx.preference} are limited to store String and Boolean data only.
 * <p>
 * Integer, Long, ... needs to be stored as String.
 * Examples:
 * {@link androidx.preference.SwitchPreference}
 * => boolean
 * {@link androidx.preference.EditTextPreference}
 * => String
 * <p>
 * But, Preferences that support Lists in some way or form, are stored as {@code Set<String>}
 * Example:
 * {@link androidx.preference.ListPreference}
 * -> store 1 value as String
 * <p>
 * {@link androidx.preference.MultiSelectListPreference}
 * => stores a {@code Set<String>} for the selected values.
 * <p>
 * At the same time, application code is easier to write/understand if it uses the actual types
 * it needs.
 * <p>
 * So... bring in the classes of this package to transmogrify (Hi Calvin) between the two.
 * <p>
 * {@link com.hardbacknutter.nevertomanybooks.booklist.prefs.PPref} => Interface for all of them.
 * <p>
 * {@link com.hardbacknutter.nevertomanybooks.booklist.prefs.PString}
 * => {@link androidx.preference.EditTextPreference}
 * {@link com.hardbacknutter.nevertomanybooks.booklist.prefs.PBoolean}
 * => {@link androidx.preference.SwitchPreference}
 * <p>
 * {@link com.hardbacknutter.nevertomanybooks.booklist.prefs.PInteger}
 * => a value in a List-of-values used by {@link androidx.preference.ListPreference}
 * => an actual {@code Integer} (not used as such yet)
 * <p>
 * {@link com.hardbacknutter.nevertomanybooks.booklist.prefs.PBitmask}
 * => an {@code Integer} used as a bitmask, used by
 * {@link androidx.preference.MultiSelectListPreference}
 * <p>
 * {@link com.hardbacknutter.nevertomanybooks.booklist.prefs.PInt}
 * => {@code Interface} implemented by PInteger, PBitmask to 'set' their values as {@code Integer}
 * <p>
 * {@link com.hardbacknutter.nevertomanybooks.booklist.prefs.PIntList}
 * => {@code List<Integer>}: an ordered list of {@code Integer} values. Stored as a CSV String
 * <p>
 * {@link com.hardbacknutter.nevertomanybooks.booklist.prefs.PCollection}
 * => Interface implemented by {@code PIntList} (and potentially future List/Set classes) to 'set'
 * the value as {@code Set<String>}
 */
package com.hardbacknutter.nevertomanybooks.booklist.prefs;
