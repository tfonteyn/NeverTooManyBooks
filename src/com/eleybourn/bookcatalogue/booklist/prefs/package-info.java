/**
 * Problem:
 *
 * The Preferences in {@link androidx.preference} are limited to storing String and
 * Boolean data only..
 *
 * Integer, Long, ... needs to be stored as String.
 * Examples:
 * {@link androidx.preference.SwitchPreference}
 * => boolean
 * {@link androidx.preference.EditTextPreference}
 * => String
 *
 * But, Preferences that support Lists in some way or form, are stored as Set<String>.
 * Example:
 * {@link androidx.preference.ListPreference}
 * -> store 1 value as String
 *
 * {@link androidx.preference.MultiSelectListPreference}
 * => stores a Set<String> for the selected values.
 *
 *
 * At the same time, application code is easier to write/understand if it uses the actual types
 * it needs.
 *
 * So... bring in the classes of this package to transmogrify (Hi Calvin) between the two.
 *
 * PPref
 * => Interface for all of them.
 *
 * PString
 * => {@link androidx.preference.EditTextPreference}
 * PBoolean
 * => {@link androidx.preference.SwitchPreference}
 *
 * PInteger
 * => a value in a List-of-values used by {@link androidx.preference.ListPreference}
 * => an actual Integer (not used as such yet)
 *
 * PBitmask
 * => an integer used as a bitmask, used by {@link androidx.preference.MultiSelectListPreference}
 *
 * PInt
 * => Interface implemented by PInteger, PBitmask to 'set' their values as 'Integer'
 *
 * PIntList
 * => List<Integer> : an ordered list of Integer values. Stored as a CSV String
 *
 * PCollection
 * => Interface implemented by PIntList (and potentially future List/Set classes) to 'set'
 * the value as 'Set<String>'
 */
package com.eleybourn.bookcatalogue.booklist.prefs;