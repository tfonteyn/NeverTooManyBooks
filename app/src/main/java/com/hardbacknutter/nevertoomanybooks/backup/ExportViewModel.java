/*
 * @Copyright 2018-2023 HardBackNutter
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
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.utils.UriInfo;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterViewModel;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;

/**
 * Coordinate between the UI and the {@link ExportHelper}.
 * Handle the export related background tasks.
 */
public class ExportViewModel
        extends DataWriterViewModel<ExportResults> {

    /**
     * The encodings we currently (fully or limited) support writing.
     */
    private static final ArchiveEncoding[] ENCODINGS = {
            ArchiveEncoding.Zip,
            ArchiveEncoding.Json,
            ArchiveEncoding.SqLiteDb};

    @Nullable
    private ExportHelper exportHelper;

    /**
     * Pseudo constructor.
     *
     * @param systemLocale to use for ISO date parsing
     */
    public void init(@NonNull final Locale systemLocale) {
        if (exportHelper == null) {
            exportHelper = new ExportHelper(systemLocale);
        }
    }

    @NonNull
    protected ExportHelper getDataWriterHelper() {
        return Objects.requireNonNull(exportHelper);
    }

    /**
     * Is this a backup or an export.
     *
     * @return {@code true} when this is considered a backup,
     *         {@code false} when it's considered an export.
     */
    public boolean isBackup() {
        return getDataWriterHelper().isBackup();
    }

    /**
     * Get the uri to which we'll write.
     *
     * @return uri to write to
     *
     * @throws NullPointerException if the uri was not set previously
     */
    @NonNull
    public Uri getUri() {
        return getDataWriterHelper().getUri();
    }

    @NonNull
    @Override
    public String getDestinationDisplayName(@NonNull final Context context) {
        return new UriInfo(getDataWriterHelper().getUri()).getDisplayName(context);
    }

    @NonNull
    Pair<String, Long> getDestination(@NonNull final Context context) {
        final UriInfo uriInfo = new UriInfo(getDataWriterHelper().getUri());
        return new Pair<>(uriInfo.getDisplayName(context), uriInfo.getSize(context));
    }

    /**
     * Set the type of archive (file) to write to.
     *
     * @param encoding to use
     */
    public void setEncoding(@NonNull final ArchiveEncoding encoding) {
        getDataWriterHelper().setEncoding(encoding);
    }

    /**
     * Get the type of archive (file) to write to.
     *
     * @return encoding
     */
    @NonNull
    public ArchiveEncoding getEncoding() {
        return getDataWriterHelper().getEncoding();
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
    Pair<Integer, List<String>> getFormatOptions(@NonNull final Context context) {
        Objects.requireNonNull(exportHelper);

        final ArchiveEncoding currentEncoding = exportHelper.getEncoding();
        int initialPos = 0;
        final List<String> list = new ArrayList<>();
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
        Objects.requireNonNull(exportHelper);

        // Prefs/Styles are always included, so we need to specifically check for books/covers
        final Set<RecordType> recordTypes = exportHelper.getRecordTypes();
        return recordTypes.contains(RecordType.Books) || recordTypes.contains(RecordType.Cover);
    }

    void startExport(@NonNull final Uri uri) {
        Objects.requireNonNull(exportHelper);

        exportHelper.setUri(uri);
        startWritingData(exportHelper);
    }
}
