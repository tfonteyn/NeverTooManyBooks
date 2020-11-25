/*
 * @Copyright 2020 HardBackNutter
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

/**
 * Problem:
 * <p>
 * The Preferences in {@link androidx.preference} are limited to storing
 * String and Boolean data only.
 * <p>
 * Integer, Long, ... needs to be stored as String.
 * <p>
 * Examples:
 * <ul>
 * <li>{@link androidx.preference.SwitchPreference} => boolean</li>
 * <li>{@link androidx.preference.EditTextPreference} => String</li>
 * </ul>
 * <p>
 * But, Preferences that support Lists in some way or form, are stored as {@code Set<String>}
 * <p>
 * Example:
 * <ul>
 * <li>{@link androidx.preference.ListPreference} => store 1 value as String</li>
 * <li>{@link androidx.preference.MultiSelectListPreference}
 * => stores a {@code Set<String>} for the selected values.</li>
 * </ul>
 * At the same time, application code is easier to write/understand if it uses the actual types
 * it needs.
 * <p>
 * So... bring in the classes of this package to transmogrify (Hi Calvin) between the two.
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref} => Interface for all of them.
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.booklist.prefs.PString}
 * => {@link androidx.preference.EditTextPreference}
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBoolean}
 * => {@link androidx.preference.SwitchPreference}
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.booklist.prefs.PIntString}
 * => a value in a List-of-values used by {@link androidx.preference.ListPreference}
 * => an actual {@code Integer} (not used as such yet)
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBitmask}
 * => an {@code Integer} used as a bitmask, used by
 * {@link androidx.preference.MultiSelectListPreference}
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.booklist.prefs.PInt}
 * => {@code Interface} implemented by PIntString, PBitmask to 'set' their values as {@code Integer}
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.booklist.prefs.PIntList}
 * => {@code List<Integer>}: an ordered list of {@code Integer} values. Stored as a CSV String
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.booklist.prefs.PCsvString}
 * => Interface implemented by {@code PIntList} (and potentially future List/Set classes) to 'set'
 * the value as {@code Set<String>}
 */
package com.hardbacknutter.nevertoomanybooks.booklist.prefs;
