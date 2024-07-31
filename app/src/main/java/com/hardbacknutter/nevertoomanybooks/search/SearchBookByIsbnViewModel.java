/*
 * @Copyright 2018-2024 HardBackNutter
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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;

public class SearchBookByIsbnViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "SearchBookByIsbnViewModel";

    /** The {@link Scanning} to start in. */
    public static final String BKEY_SCANNER_MODE = TAG + ":scanning";

    /** Storage key into preferences for the current queue. */
    private static final String PREF_SCAN_QUEUE = "scan.queue";

    private static final int BUFFER_SIZE = 65535;

    /** The batch mode queue. */
    private final List<ISBN> scanQueue = new ArrayList<>();

    private final MutableLiveData<List<ISBN>> scanQueueUpdate = new MutableLiveData<>();
    @NonNull
    private final EditBookOutput resultData = new EditBookOutput();
    /** Database Access. */
    private BookDao bookDao;

    private Style style;

    @NonNull
    private Scanning scanning = Scanning.Off;

    /** Only start the scanner automatically upon the very first start of the fragment. */
    private boolean firstStart = true;

    @NonNull
    Intent createResultIntent() {
        return resultData.createResultIntent();
    }

    void onBookEditingDone(@NonNull final EditBookOutput data) {
        resultData.update(data);
    }

    /**
     * Pseudo constructor.
     *
     * @param context    Current context
     * @param strictIsbn Flag: {@code true} to strictly allow ISBN codes.
     * @param args       {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     final boolean strictIsbn,
                     @Nullable final Bundle args) {
        if (bookDao == null) {
            bookDao = ServiceLocator.getInstance().getBookDao();

            final String qs = PreferenceManager.getDefaultSharedPreferences(context)
                                               .getString(PREF_SCAN_QUEUE, "");
            scanQueue.addAll(Arrays.stream(qs.split(","))
                                   .distinct()
                                   .filter(s -> !s.isBlank())
                                   .map(s -> new ISBN(s, strictIsbn))
                                   .collect(Collectors.toList()));

            if (args != null) {
                final Scanning scanning = args.getParcelable(BKEY_SCANNER_MODE);
                if (scanning != null) {
                    this.scanning = scanning;
                }

                // Lookup the provided style or use the default if not found.
                final String styleUuid = args.getString(Style.BKEY_UUID);
                final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
                style = stylesHelper.getStyle(styleUuid).orElseGet(stylesHelper::getDefault);
            }
        }

        scanQueueUpdate.setValue(scanQueue);
    }

    /**
     * Store the current queue as a csv list of ISBN numbers to preferences.
     *
     * @param context Current context
     */
    private void storeQueue(@NonNull final Context context) {
        final String list = scanQueue.stream()
                                     .map(ISBN::asText)
                                     .collect(Collectors.joining(","));
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit().putString(PREF_SCAN_QUEUE, list).apply();
    }

    /**
     * Auto-start scanner the first time this fragment starts.
     *
     * @return flag
     */
    boolean isAutoStart() {
        if (scanning != Scanning.Off && firstStart) {
            firstStart = false;
            return true;
        }
        return false;
    }

    @NonNull
    MutableLiveData<List<ISBN>> onScanQueueUpdate() {
        return scanQueueUpdate;
    }

    void clearQueue(@NonNull final Context context) {
        scanQueue.clear();
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit().remove(PREF_SCAN_QUEUE).apply();
        scanQueueUpdate.setValue(scanQueue);
    }

    void addToQueue(@NonNull final Context context,
                    @NonNull final ISBN code) {
        // Don't add duplicates
        if (!scanQueue.contains(code)) {
            // don't trigger scanQueueUpdate here as we're scanning in a loop
            scanQueue.add(code);
            storeQueue(context);
        }
    }

    void removeFromQueue(@NonNull final Context context,
                         @NonNull final ISBN code) {
        // don't trigger scanQueueUpdate here as we're updating the queue views manually
        scanQueue.remove(code);
        storeQueue(context);
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
     * @return {@code true} on success.
     */
    boolean readQueue(@NonNull final Context context,
                      @NonNull final Uri uri,
                      final boolean strictIsbn) {
        //TODO: should be run as background task, and use LiveData to update the view...
        // ... but it's so fast for any reasonable length list....
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is != null) {
                try (Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                     BufferedReader reader = new BufferedReader(isr, BUFFER_SIZE)) {

                    scanQueue.addAll(
                            reader.lines()
                                  .distinct()
                                  .filter(s -> !s.isBlank())
                                  .map(s -> new ISBN(s, strictIsbn))
                                  .filter(isbn -> isbn.isValid(strictIsbn))
                                  .filter(isbn -> !scanQueue.contains(isbn))
                                  .collect(Collectors.toList()));

                    storeQueue(context);
                    scanQueueUpdate.setValue(scanQueue);

                } catch (@NonNull final UncheckedIOException e) {
                    // caused by lines()
                    return false;
                }
            }

            return true;

        } catch (@NonNull final IOException e) {
            return false;
        }
    }

    @NonNull
    Scanning getScannerMode() {
        return scanning;
    }

    void setScannerMode(@NonNull final Scanning scanning) {
        this.scanning = scanning;

        // If we're starting a new scan, clear the queue.
        if (this.scanning != Scanning.Off) {
            scanQueue.clear();
        }
        scanQueueUpdate.setValue(scanQueue);
    }

    @NonNull
    List<Pair<Long, String>> getBookIdAndTitlesByIsbn(@NonNull final ISBN code) {
        return bookDao.getBookIdAndTitleByIsbn(code);
    }

    @NonNull
    Style getStyle() {
        Objects.requireNonNull(style, "style");
        return style;
    }
}
