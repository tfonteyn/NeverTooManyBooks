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
 * The classes in this package are all related to the "work.editions" from Goodreads.
 * <p>
 * https://www.goodreads.com/api/index#work.editions
 * <p>
 * Multiple people have reported in the forums that a request for access is simply ignored.
 * <p>
 * ENHANCE: access to work.editions from Goodreads
 * <p>
 * The search/display are more or less implemented, but only limited testing was done.
 * Searching for the works themselves works fine, and the list is displayed just fine.
 * <p>
 * However, clicking on an entry needs GoodreadsSearchActivity#onWorkSelected accessing the API.
 * <p>
 * If ever...
 * - GoodreadsSearchActivity provides search + display of works.
 * - GrSearchTask does takes care of the search
 * - SearchWorksApiHandler does the actual search, and delivers the result as a list of
 * - GoodreadsWork
 * <p>
 * Frankly speaking if there ever is access to the API, these classes need a complete revamp
 * to bring them inline with the other/active code.
 * <p>
 * Reactivation would involve:
 * - enabling in the manifest
 * - com.hardbacknutter.nevertoomanybooks.goodreads.tasks.admin.SendBookEvent
 * to add an action to its context menu.
 */
package com.hardbacknutter.nevertoomanybooks.goodreads.editions;
