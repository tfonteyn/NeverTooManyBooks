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
 * <h2>Calibre</h2>
 * <h3>0. {@link com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreSyncFragment}</h3>
 * allows the user to start one of:
 * <ol>
 *     <li>{@link com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibraryMappingFragment}</li>
 *     <li>{@link com.hardbacknutter.nevertoomanybooks.sync.SyncReaderFragment}</li>
 *     <li>{@link com.hardbacknutter.nevertoomanybooks.sync.SyncWriterFragment}</li>
 * </ol>
 * <h3>1. {@link com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibraryMappingFragment}</h3>
 * when done, the user is returned to step {@code 0}.
 * <h3>2. {@link com.hardbacknutter.nevertoomanybooks.sync.SyncReaderFragment}</h3>
 * uses {@link com.hardbacknutter.nevertoomanybooks.sync.SyncReaderViewModel}
 * which in turn uses {@link com.hardbacknutter.nevertoomanybooks.sync.SyncReaderHelper}.
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.sync.SyncReaderHelper#getSyncServer()}
 * passed through {@link com.hardbacknutter.nevertoomanybooks.sync.SyncReaderViewModel#getSyncServer()}:
 * <ol>
 * <li>used to adapt the UI as needed</li>
 * <li>makes the
 *  {@link com.hardbacknutter.nevertoomanybooks.io.DataReader} available with the implementation
 *  {@link com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServerReader}</li>
 * </ol>
 * This uses the standard reader/writer approach.
 *
 *
 * <h3>3. {@link com.hardbacknutter.nevertoomanybooks.sync.SyncWriterFragment}</h3>
 * uses {@link com.hardbacknutter.nevertoomanybooks.sync.SyncWriterViewModel}
 * which in turn uses {@link com.hardbacknutter.nevertoomanybooks.sync.SyncWriterHelper}.
 */
package com.hardbacknutter.nevertoomanybooks.sync;
