/*
 * @Copyright 2018-2025 HardBackNutter
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
 * {@link com.hardbacknutter.nevertoomanybooks.widgets.adapters.vp2.ExtFragmentStateAdapter}.
 * <p>
 * This class was copied from androidx.viewpager2; version 1.1.0
 * <a href="https://developer.android.com/jetpack/androidx/releases/viewpager2">
 * viewpager2</a>
 * <a href="https://github.com/androidx/androidx/tree/androidx-main/viewpager2/viewpager2/src/main/java/androidx/viewpager2/adapter">
 *     on github</a>
 * <p>
 * androidx.viewpager2.adapter.FragmentStateAdapter
 * <p>
 * This fixes <a href="https://issuetracker.google.com/issues/309593253">
 * google bug 309593253</a>
 * All modifications annotated with HARDBACKNUTTER.
 * <p>
 * We no longer keep state for all fragments created during the lifetime
 * of this class. Instead we <strong>only</strong> keep state
 * for the current set of live Fragment objects.
 * <p>
 * This means... swipe left, 1x more than the view window, the first fragment AND
 * state is destroyed. Swipe back until that particular fragment becomes
 * the visible fragment and <strong>YOU, THE DEVELOPER</strong> must
 * restore/load/set any state needed.
 * <p>
 * TODO: theoretically we could have just removed all references to the {@code mSavedStates}
 *  collection, but the intention is perhaps allow keeping state for destroyed fragments
 *  for a developer-defined number outside of the current view window.
 *  i.e. if there are F fragments in the view window, keep state for S number of fragments
 *  on each side of the window.
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.widgets.adapters.vp2.FragmentViewHolder}.
 * Copied as-is due to package-only access restrictions.
 */
package com.hardbacknutter.nevertoomanybooks.widgets.adapters.vp2;
