/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.datamanager;

import androidx.annotation.NonNull;

/**
 * Interface supported by a read-only object. Note that this is a 'marker' interface only.
 *
 * @author pjw
 */
public interface DataViewer {

    /**
     * Load current data FROM passed DataManager.
     *
     * @param dataManager the parent collection
     */
    <T extends DataManager> void loadFieldsFrom(@NonNull T /* in/out */ dataManager);
}
