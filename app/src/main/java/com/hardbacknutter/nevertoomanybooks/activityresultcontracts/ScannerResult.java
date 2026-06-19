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

package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A lightweight value class with the minimal info we might
 * need from a scanned barcode.
 */
public final class ScannerResult {
    @NonNull
    private final String text;
    @Nullable
    private final BarcodeFormat barcodeFormat;
    @Nullable
    private final Integer issueNumber;
    @Nullable
    private final String suggestedPrice;
    @Nullable
    private final String extension;

    public ScannerResult(@NonNull final String text,
                         @Nullable final BarcodeFormat barcodeFormat,
                         @Nullable final Integer issueNumber,
                         @Nullable final String suggestedPrice,
                         @Nullable final String extension) {
        this.text = text;
        this.barcodeFormat = barcodeFormat;
        this.issueNumber = issueNumber;
        this.suggestedPrice = suggestedPrice;
        this.extension = extension;
    }

    @NonNull
    public static Optional<ScannerResult> from(@NonNull final Result result) {
        final String barcode = result.getText();
        if (barcode != null) {
            final ScannerResult value;
            final Map<ResultMetadataType, Object> metadata = result.getResultMetadata();
            if (metadata != null) {
                value = new ScannerResult(
                        barcode,
                        result.getBarcodeFormat(),
                        (Integer) metadata.get(ResultMetadataType.ISSUE_NUMBER),
                        (String) metadata.get(ResultMetadataType.SUGGESTED_PRICE),
                        (String) metadata.get(ResultMetadataType.UPC_EAN_EXTENSION));
            } else {
                value = new ScannerResult(barcode, result.getBarcodeFormat(),
                                          null, null, null);
            }
            return Optional.of(value);
        } else {
            return Optional.empty();
        }
    }

    @NonNull
    public String getText() {
        return text;
    }

    @Nullable
    public BarcodeFormat getBarcodeFormat() {
        return barcodeFormat;
    }

    @Nullable
    public Integer getIssueNumber() {
        return issueNumber;
    }

    @Nullable
    public String getSuggestedPrice() {
        return suggestedPrice;
    }

    @Nullable
    public String getExtension() {
        return extension;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ScannerResult that = (ScannerResult) o;
        return Objects.equals(text, that.text)
               && barcodeFormat == that.barcodeFormat
               && Objects.equals(issueNumber, that.issueNumber)
               && Objects.equals(suggestedPrice, that.suggestedPrice)
               && Objects.equals(extension, that.extension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, barcodeFormat, issueNumber, suggestedPrice, extension);
    }

    @Override
    @NonNull
    public String toString() {
        return "ScannerResult{"
               + "text='" + text + '\''
               + ", barcodeFormat=" + barcodeFormat
               + ", issueNumber=" + issueNumber
               + ", suggestedPrice='" + suggestedPrice + '\''
               + ", extension='" + extension + '\''
               + '}';
    }
}
