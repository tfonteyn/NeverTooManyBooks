/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.search;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;

public class SearchBookInput {

    private static final String TAG = "SearchBookInput";
    /** The {@link ScanMode} to start in. */
    private static final String BKEY_SCANNER_MODE = TAG + ":scanMode";


    @Nullable
    private final By by;
    @NonNull
    private final String styleUuid;
    @Nullable
    private final ScanMode scanMode;

    public SearchBookInput(@NonNull final By by,
                           @NonNull final Style style) {
        this.styleUuid = style.getUuid();

        this.by = by;
        switch (by) {
            case Scan:
                scanMode = ScanMode.getScannerModeSingle();
                break;
            case ScanBatch:
                scanMode = ScanMode.Batch;
                break;
            default:
                scanMode = null;
                break;
        }
    }

    private SearchBookInput(@Nullable final ScanMode scanMode,
                            @NonNull final String styleUuid) {
        this.scanMode = scanMode;
        this.styleUuid = styleUuid;
        this.by = null;
    }

    @NonNull
    static SearchBookInput fromBundle(@NonNull final Bundle args) {
        @SuppressWarnings("deprecation")
        final ScanMode scanMode = args.getParcelable(BKEY_SCANNER_MODE);
        final String styleUuid = Objects.requireNonNull(args.getString(Style.BKEY_UUID));
        return new SearchBookInput(scanMode, styleUuid);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(2);
        args.putString(Style.BKEY_UUID, styleUuid);
        if (scanMode != null) {
            args.putParcelable(BKEY_SCANNER_MODE, scanMode);
        }

        return args;
    }

    @Nullable
    ScanMode getScanMode() {
        return scanMode;
    }

    /**
     * ONLY TO BE USED BY THE CONTRACT createIntent method.
     *
     * @return how to search
     *
     * @throws NullPointerException if used from the Fragment/ViewModel
     */
    @NonNull
    public By getBy() {
        return Objects.requireNonNull(by);
    }

    @NonNull
    String getStyleUuid() {
        return styleUuid;
    }

    @Override
    @NonNull
    public String toString() {
        return "SearchBookInput{"
               + "by=" + by
               + ", styleUuid='" + styleUuid + '\''
               + ", scanMode=" + scanMode
               + '}';
    }

    public enum By {
        ProductCode,
        Scan,
        ScanBatch,
        Text,
        ExternalId
    }
}
