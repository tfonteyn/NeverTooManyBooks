/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
 *     <li>{@link com.hardbacknutter.nevertoomanybooks.searches.SearchSites} :
 *          Static setup of identifiers and classes. This is where a new engine can be added.
 *     </li>
 *
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searches.SearchEngine} :
 *          The interface that the engine class implements and responsible for the actual searches.
 *      </li>
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searches.SearchEngine.Configuration} :
 *          The annotation that configures the engine class.
 *      </li>
 *
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry} :
 *          A registry of all {@link com.hardbacknutter.nevertoomanybooks.searches.SearchEngine}
 *          classes and their
 *          {@link com.hardbacknutter.nevertoomanybooks.searches.SearchEngine.Configuration}.
 *      </li>
 *
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry.Config} :
 *          Encapsulates
 *          {@link com.hardbacknutter.nevertoomanybooks.searches.SearchEngine.Configuration},
 *          resolving complex config items (e.g. Locale).
 *      </li>
 *
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searches.Site} :
 *          Encapsulates a {@link com.hardbacknutter.nevertoomanybooks.searches.SearchEngine}
 *          instance + the current enabled/disabled state.
 *          Implements {@link android.os.Parcelable} as it will be passed around as a member
 *          of a List.
 *          A search will use such a list to perform searches on the desired sites.
 *          Membership is hardcoded at design time (it depends on capabilities of the engine),
 *          but the order and enabling/disabling of the individual sites is user configurable.
 *          Implements {@link android.os.Parcelable} as it will be passed around.
 *      </li>
 *
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searches.Site.Type} :
 *          There are 3 types of {@link com.hardbacknutter.nevertoomanybooks.searches.Site} lists.
 *          <ol>
 *              <li>{@link com.hardbacknutter.nevertoomanybooks.searches.Site.Type#Data} :
 *                  search for book data (and cover)</li>
 *              <li>{@link com.hardbacknutter.nevertoomanybooks.searches.Site.Type#Covers} :
 *                  search for book covers only</li>
 *              <li>{@link com.hardbacknutter.nevertoomanybooks.searches.Site.Type#AltEditions} :
 *                  search for alternative editions of a book.</li>
 *          </ol>
 *      </li>
 * *
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator} and
 *          {@link com.hardbacknutter.nevertoomanybooks.searches.SearchTask}
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
package com.hardbacknutter.nevertoomanybooks.searches;
