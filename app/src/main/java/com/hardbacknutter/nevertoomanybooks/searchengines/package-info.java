/*
 * @Copyright 2018-2021 HardBackNutter
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
 * Classes involved:
 * <ul>
 *     <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites} :
 *          Static setup of identifiers and classes. This is where a new engine can be added.
 *     </li>
 *
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine} :
 *          The interface that the engine class implements and responsible for the actual searches.
 *      </li>
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig} :
 *          The configuration for the engine class.
 *      </li>
 *
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry} :
 *          A registry of all
 *          {@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine}
 *          classes and their
 *          {@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig}.
 *      </li>
 *
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig} :
 *          Encapsulates
 *          {@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig},
 *          resolving complex config items (e.g. Locale).
 *      </li>
 *
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.Site} :
 *          Encapsulates a {@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine}
 *          instance + the current enabled/disabled state.
 *          Implements {@link android.os.Parcelable} as it will be passed around as a member
 *          of a List.
 *          A search will use such a list to perform searches on the desired sites.
 *          Membership is hardcoded at design time (it depends on capabilities of the engine),
 *          but the order and enabling/disabling of the individual sites is user configurable.
 *          Implements {@link android.os.Parcelable} as it will be passed around.
 *      </li>
 *
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.Site.Type} :
 *          There are 3 types of {@link com.hardbacknutter.nevertoomanybooks.searchengines.Site} lists.
 *          <ol>
 *              <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.Site.Type#Data} :
 *                  search for book data (and cover)</li>
 *              <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.Site.Type#Covers} :
 *                  search for book covers only</li>
 *              <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.Site.Type#AltEditions} :
 *                  search for alternative editions of a book.</li>
 *          </ol>
 *      </li>
 * *
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator} and
 *          {@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchTask}
 *          will use all of the above to perform the searches.
 *      </li>
 * </ul>
 * <p>
 * Site/SearchEngine pairs are unique PER LIST:
 * <ul>
 *     <li>there is only one copy of a Site inside a single List</li>
 *     <li>a SearchEngine is cached by a Site, but re-created each time the Site is Parceled.</li>
 *     <li>two Site for the SAME website (i.e. same engine id),
 *         but in a different List, will have the same SearchEngine class,
 *         but a different instance of that SearchEngine </li>
 * </ul>
 * Example:
 * <ul>
 * <li>Site A1 with id=AAA, and type Data, will have a SearchEngine_A1</li>
 * <li>Site B1 with id=BBB, and type Data, will have a SearchEngine_B1</li>
 * <li>...</li>
 * <li>Site C1 with id=AAA, and type Covers, will have a SearchEngine_C1</li>
 * </ul>
 * <p>
 */
package com.hardbacknutter.nevertoomanybooks.searchengines;
