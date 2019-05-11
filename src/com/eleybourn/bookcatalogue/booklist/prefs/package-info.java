/**
 * Problem:
 * <p>
 * The Preferences in {@link androidx.preference} are limited to storing String and
 * Boolean data only..
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
 * PPref
 * => Interface for all of them.
 * <p>
 * PString
 * => {@link androidx.preference.EditTextPreference}
 * PBoolean
 * => {@link androidx.preference.SwitchPreference}
 * <p>
 * PInteger
 * => a value in a List-of-values used by {@link androidx.preference.ListPreference}
 * => an actual Integer (not used as such yet)
 * <p>
 * PBitmask
 * => an integer used as a bitmask, used by {@link androidx.preference.MultiSelectListPreference}
 * <p>
 * PInt
 * => Interface implemented by PInteger, PBitmask to 'set' their values as {@code Integer}
 * <p>
 * PIntList
 * => {@code List<Integer>}: an ordered list of Integer values. Stored as a CSV String
 * <p>
 * PCollection
 * => Interface implemented by PIntList (and potentially future List/Set classes) to 'set'
 * the value as {@code Set<String>}
 */
package com.eleybourn.bookcatalogue.booklist.prefs;