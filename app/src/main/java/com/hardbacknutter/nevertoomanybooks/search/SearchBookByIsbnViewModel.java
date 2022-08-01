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
package com.hardbacknutter.nevertoomanybooks.search;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

public class SearchBookByIsbnViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "SearchBookByIsbnViewModel";
    public static final String BKEY_SCAN_MODE = TAG + ":scanMode";

    private static final int BUFFER_SIZE = 65535;

    /** The batch mode queue. */
    private final List<ISBN> scanQueue = new ArrayList<>();

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Bundle resultData = ServiceLocator.newBundle();

    /** Database Access. */
    private BookDao bookDao;

    @NonNull
    private ScanMode scannerMode = ScanMode.Off;

    /** Only start the scanner automatically upon the very first start of the fragment. */
    private boolean firstStart = true;

    @NonNull
    public Bundle getResultData() {
        return resultData;
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@Nullable final Bundle args) {
        if (bookDao == null) {
            bookDao = ServiceLocator.getInstance().getBookDao();

            if (args != null) {
                final ScanMode scanMode = args.getParcelable(BKEY_SCAN_MODE);
                if (scanMode != null) {
                    scannerMode = scanMode;
                }
            }
        }
    }

    /**
     * Auto-start scanner the first time this fragment starts.
     *
     * @return flag
     */
    boolean isAutoStart() {
        if (scannerMode != ScanMode.Off && firstStart) {
            firstStart = false;
            return true;
        }
        return false;
    }

    @NonNull
    List<ISBN> getScanQueue() {
        return scanQueue;
    }

    @NonNull
    ScanMode getScannerMode() {
        return scannerMode;
    }

    void setScannerMode(@NonNull final ScanMode scannerMode) {
        this.scannerMode = scannerMode;
        if (this.scannerMode == ScanMode.Single || this.scannerMode == ScanMode.Batch) {
            scanQueue.clear();
        }
    }

    void addToQueue(@NonNull final ISBN code) {
        if (!scanQueue.contains(code)) {
            scanQueue.add(code);
        }
    }

    /**
     * Import a list of ISBN numbers from a text file.
     * <p>
     * The only format supported for now is a single ISBN on each line of the text file.
     * Whitespace and '-' are taken care of as usual, any other text will either
     * cause the line to be skipped, or the import to fail completely.
     *
     * @param context    Current context
     * @param uri        to read from
     * @param strictIsbn Flag: {@code true} to strictly allow ISBN codes.
     *
     * @throws IOException on generic/other IO failures
     */
    void readQueue(@NonNull final Context context,
                   @NonNull final Uri uri,
                   final boolean strictIsbn)
            throws IOException {
        //TODO: should be run as background task, and use LiveData to update the view...
        // ... but it's so fast for any reasonable length list....
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is != null) {
                try (Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                     BufferedReader reader = new BufferedReader(isr, BUFFER_SIZE)) {

                    scanQueue.addAll(
                            reader.lines()
                                  .distinct()
                                  .map(s -> new ISBN(s, strictIsbn))
                                  .filter(isbn -> isbn.isValid(strictIsbn))
                                  .filter(isbn -> !scanQueue.contains(isbn))
                                  .collect(Collectors.toList()));

                } catch (@NonNull final UncheckedIOException e) {
                    // caused by lines()
                    //noinspection ConstantConditions
                    throw e.getCause();
                }
            }
        }
    }

    @NonNull
    ArrayList<Pair<Long, String>> getBookIdAndTitlesByIsbn(@NonNull final ISBN code) {
        return bookDao.getBookIdAndTitleByIsbn(code);
    }

    public enum ScanMode
            implements Parcelable {
        /** Manual entry by user. */
        Off,
        /** Scan and search/edit loop, until scanning is cancelled. */
        Single,
        /** Scan and queue the code, until scanning is cancelled. */
        Batch;

        /** {@link Parcelable}. */
        public static final Creator<ScanMode> CREATOR = new Creator<>() {
            @Override
            @NonNull
            public ScanMode createFromParcel(@NonNull final Parcel in) {
                return values()[in.readInt()];
            }

            @Override
            @NonNull
            public ScanMode[] newArray(final int size) {
                return new ScanMode[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(ordinal());
        }
    }
}
