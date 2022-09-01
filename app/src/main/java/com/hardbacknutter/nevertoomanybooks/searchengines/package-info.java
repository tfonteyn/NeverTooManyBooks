/*
 * @Copyright 2018-2022 HardBackNutter
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
 * <ul>
 *     <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.EngineId} :
 *          Static setup of identifier, key, name and class.
 *          This is where a new engine can be added.
 *     </li>
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine} :
 *          The interface that the engine class implements and responsible for the actual searches.
 *      </li>
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig} :
 *          Configuration for the engine class. There is a 1:1 relation with the EngineId.
 *          Keeps a static registry of all configured engines.
 *      </li>
 * </ul>
 * <p>{@link com.hardbacknutter.nevertoomanybooks.searchengines.Site} :
 * Encapsulates a {@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine}
 * instance, the Site Type and the current enabled/disabled state.
 * A search will use such a list to perform searches on the desired sites.
 * Membership is hardcoded at design time (it depends on capabilities of the engine),
 * but the order and enabling/disabling of the individual sites is user configurable.
 * <p>{@link com.hardbacknutter.nevertoomanybooks.searchengines.Site.Type} :
 * <ol>
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.Site.Type#Data} :
 *          search for book data (and cover)</li>
 *      <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.Site.Type#Covers} :
 *          search for book covers only</li>
 *       <li>{@link com.hardbacknutter.nevertoomanybooks.searchengines.Site.Type#AltEditions} :
 *          search for alternative editions of a book.</li>
 * </ol>
 * Site/SearchEngine pairs are unique PER LIST:
 * <ul>
 *     <li>there is only one instance of a Site inside a single List</li>
 *     <li>a SearchEngine is cached by a Site, but re-created each time the Site is Parceled.</li>
 *     <li>two Site for the SAME website (i.e. same engine id),
 *         but in a different List, will have the same SearchEngine class,
 *         but a different instance of that SearchEngine </li>
 * </ul>
 * Example:
 * <ul>
 *      <li>Site A1 with id=AAA, and type Data, will have a SearchEngine_A1</li>
 *      <li>Site B1 with id=BBB, and type Data, will have a SearchEngine_B1</li>
 *      <li>...</li>
 *      <li>Site C1 with id=AAA, and type Covers, will have a SearchEngine_C1</li>
 * </ul>
 */
package com.hardbacknutter.nevertoomanybooks.searchengines;
