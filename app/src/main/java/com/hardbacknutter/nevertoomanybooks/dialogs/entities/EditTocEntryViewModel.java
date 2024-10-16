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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

@SuppressWarnings("noinspection WeakerAccess")
public class EditTocEntryViewModel
        extends ViewModel {

    private static final String TAG = "EditTocEntryViewModel";
    static final String BKEY_ANTHOLOGY = TAG + ":anthology";
    static final String BKEY_TOC_ENTRY = TAG + ":tocEntry";
    static final String BKEY_POSITION = TAG + ":pos";

    private final PartialDateParser partialDateParser = new PartialDateParser();
    @Nullable
    private String bookTitle;

    /** The one we're editing. */
    private TocEntry tocEntry;

    /** the position of the tocEntry in the TOC list. */
    private int editPosition;

    /** Helper to show/hide the author edit field. */
    private boolean isAnthology;

    /** Current edit. */
    private TocEntry currentEdit;

    /**
     * Current edit. Not handled in {@link #currentEdit} as we only
     * want to run our name parser {@link Author#from(String)} ONCE.
     * <p>
     * The original author name is simply read from the {@link #tocEntry}.
     */
    private String currentAuthorName;

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Fragment#requireArguments()}
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        if (tocEntry == null) {
            tocEntry = Objects.requireNonNull(args.getParcelable(BKEY_TOC_ENTRY), BKEY_TOC_ENTRY);
            editPosition = args.getInt(BKEY_POSITION, 0);
            isAnthology = args.getBoolean(BKEY_ANTHOLOGY, false);
            bookTitle = args.getString(DBKey.TITLE);

            currentEdit = new TocEntry(new Author(tocEntry.getPrimaryAuthor(), true),
                                       tocEntry.getTitle(),
                                       tocEntry.getFirstPublicationDate());

            currentAuthorName = tocEntry.getPrimaryAuthor().getLabel(context);
        }
    }

    @Nullable
    public String getBookTitle() {
        return bookTitle;
    }

    @NonNull
    public TocEntry getTocEntry() {
        return tocEntry;
    }

    public int getEditPosition() {
        return editPosition;
    }

    public boolean isAnthology() {
        return isAnthology;
    }

    @NonNull
    public TocEntry getCurrentEdit() {
        return currentEdit;
    }

    public void setTitle(@NonNull final String title) {
        currentEdit.setTitle(title);
    }

    public void setFirstPublicationDate(@NonNull final CharSequence dateStr) {
        currentEdit.setFirstPublicationDate(partialDateParser.parse(dateStr)
                                                             .orElse(PartialDate.NOT_SET));
    }

    public String getCurrentAuthorName() {
        return currentAuthorName;
    }

    public void setCurrentAuthorName(@NonNull final String currentAuthorName) {
        this.currentAuthorName = currentAuthorName;
    }

    boolean isModified(@NonNull final Context context) {
        return !(tocEntry.getTitle().equals(currentEdit.getTitle())
                 && tocEntry.getFirstPublicationDate().equals(currentEdit.getFirstPublicationDate())
                 && tocEntry.getPrimaryAuthor().getLabel(context).equals(currentAuthorName));
    }

    /**
     * We do not update the database in this class;
     * instead we simply copy the current-edit into the edited object
     * and send it back to the launcher.
     * TOCs are updated in bulk/list per Book.
     */
    void copyChanges() {
        tocEntry.setTitle(currentEdit.getTitle());
        tocEntry.setFirstPublicationDate(currentEdit.getFirstPublicationDate());
        if (isAnthology) {
            tocEntry.setPrimaryAuthor(Author.from(currentAuthorName));
        }
    }
}
