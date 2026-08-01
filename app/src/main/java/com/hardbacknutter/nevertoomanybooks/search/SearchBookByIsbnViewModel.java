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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.utils.CameraConfig;

public class SearchBookByIsbnViewModel
        extends ViewModel {

    @NonNull
    private final EditBookOutput resultData = new EditBookOutput();
    /** Database Access. */
    private BookDao bookDao;

    private Style style;

    @NonNull
    private ScanMode scanMode = ScanMode.Off;

    /**
     * Flag indicating the scanner Activity is already started so we don't
     * start it a second time after a device rotation.
     */
    private boolean scannerStarted;

    /** The raw text. The 'isStrict' flag is get/set directly with SharedPreferences. */
    @Nullable
    private String isbnText;

    private CameraConfig cameraConfig;
    private boolean inSettings;

    @Override
    protected void onCleared() {
        cameraConfig.saveSettings();
    }

    @NonNull
    EditBookOutput getResultData() {
        return resultData;
    }

    void onBookEditingDone(@NonNull final EditBookOutput data) {
        resultData.update(data);
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    all arguments
     */
    void init(@NonNull final Context context,
              @Nullable final SearchBookInput args) {
        if (bookDao == null) {
            bookDao = ServiceLocator.getInstance().getBookDao();

            if (args != null) {
                final ScanMode tmpScanMode = args.getScanMode();
                if (tmpScanMode != null) {
                    this.scanMode = tmpScanMode;
                }

                // Lookup the provided style or use the default if not found.
                final String styleUuid = args.getStyleUuid();
                final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
                style = stylesHelper.getStyle(styleUuid).orElseGet(stylesHelper::getDefault);
            }

            cameraConfig = new CameraConfig(context);
        }
    }

    void onSaveBook(@NonNull final Context context,
                    @NonNull final Book book)
            throws DaoWriteException, StorageException {
        // DATE_ACQUIRED is always used
        book.ensureDateAcquired();
        // if BOOK_CONDITION is wanted, assume the user got a new book.
        book.ensureCondition();

        final long id = bookDao.insert(context, book);
        book.setStage(EntityStage.Stage.Clean);
        onBookEditingDone(new EditBookOutput(true, id, 0));
    }

    /**
     * Should the scanner be started when the fragment starts.
     *
     * @return flag
     */
    boolean isStartScanner() {
        return getScannerMode() != ScanMode.Off;
    }

    @NonNull
    Style getStyle() {
        Objects.requireNonNull(style, "style");
        return style;
    }

    @Nullable
    String getIsbnText() {
        return isbnText;
    }

    void setIsbnText(@Nullable final String isbnText) {
        this.isbnText = isbnText;
    }

    /**
     * Return the current scanner status.
     * <p>
     * This is independent of the {@link #getScannerMode()}.
     *
     * @return flag
     *
     * @see #setScannerStarted(boolean)
     */
    boolean isScannerStarted() {
        return scannerStarted;
    }

    /**
     * Remember the current scanner status.
     * <p>
     * This is independent of the {@link #getScannerMode()}.
     *
     * @param started flag
     *
     * @see #isScannerStarted()
     */
    void setScannerStarted(final boolean started) {
        this.scannerStarted = started;
    }

    @NonNull
    ScanMode getScannerMode() {
        return scanMode;
    }

    void setScannerMode(@NonNull final ScanMode scanMode) {
        this.scanMode = scanMode;
    }

    @NonNull
    CameraConfig getCameraConfig() {
        return cameraConfig;
    }

    @NonNull
    List<Pair<Long, String>> getBookIdAndTitlesByIsbn(@NonNull final ProductCode productCode) {
        return bookDao.getBookIdAndTitle(productCode);
    }

    /**
     * Remember that the user has started the settings fragment.
     *
     * @see #onResumeFromSettings()
     */
    void inSettings() {
        this.inSettings = true;
    }

    /**
     * Check if the user is returning from Settings, and <strong>reset</strong> the flag.
     *
     * @return flag
     *
     * @see #inSettings()
     */
    boolean onResumeFromSettings() {
        final boolean tmp = inSettings;
        inSettings = false;
        return tmp;
    }
}
