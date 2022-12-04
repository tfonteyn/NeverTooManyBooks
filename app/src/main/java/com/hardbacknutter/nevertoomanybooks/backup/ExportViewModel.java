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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.io.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterViewModel;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;

/**
 * Coordinate between the UI and the {@link ExportHelper}.
 * Handle the export related background tasks.
 */
@SuppressWarnings("WeakerAccess")
public class ExportViewModel
        extends DataWriterViewModel<ExportResults> {

    /**
     * The encodings we currently (fully or limited) support writing.
     */
    public static final ArchiveEncoding[] ENCODINGS = {
            ArchiveEncoding.Zip,
            ArchiveEncoding.Csv,
            ArchiveEncoding.Json,
            ArchiveEncoding.SqLiteDb};

    @NonNull
    private final ExportHelper exportHelper = new ExportHelper();

    /** UI helper. */
    private boolean quickOptionsAlreadyShown;

    boolean isQuickOptionsAlreadyShown() {
        return quickOptionsAlreadyShown;
    }

    void setQuickOptionsAlreadyShown() {
        quickOptionsAlreadyShown = true;
    }

    @NonNull
    ExportHelper getExportHelper() {
        return exportHelper;
    }

    /**
     * Get the {@link ArchiveEncoding} for the given position in the dropdown menu.
     *
     * @param position to get
     *
     * @return encoding
     */
    @NonNull
    ArchiveEncoding getEncoding(final int position) {
        return ENCODINGS[position];
    }

    /**
     * Get the list of options (and initial position) for the drop down menu
     * for the archive format.
     *
     * @param context Current context
     *
     * @return initial position + list
     */
    @NonNull
    Pair<Integer, ArrayList<String>> getFormatOptions(@NonNull final Context context) {
        final ArchiveEncoding currentEncoding = exportHelper.getEncoding();
        int initialPos = 0;
        final ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < ENCODINGS.length; i++) {
            final ArchiveEncoding encoding = ENCODINGS[i];
            if (encoding == currentEncoding) {
                initialPos = i;
            }
            list.add(context.getString(encoding.getSelectorResId()));
        }

        return new Pair<>(initialPos, list);
    }

    @Override
    public boolean isReadyToGo() {
        // Prefs/Styles are always included, so we need to specifically check for books/covers
        final Set<RecordType> recordTypes = exportHelper.getRecordTypes();
        return recordTypes.contains(RecordType.Books) || recordTypes.contains(RecordType.Cover);
    }

    void startExport(@NonNull final Uri uri) {
        exportHelper.setUri(uri);
        startWritingData(exportHelper);
    }
}
