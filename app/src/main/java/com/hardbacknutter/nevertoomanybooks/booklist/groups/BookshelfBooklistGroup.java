/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist.groups;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.definitions.VirtualDomain;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;

/**
 * All plumbing present, but the 'under each' preference is not exposed to the user yet,
 * because there is no 'position' column for bookshelves.
 * <p>
 * <p>
 * Specialized BooklistGroup representing a {@link Bookshelf} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomain()} returns a customized display domain
 * {@link #getGroupDomains} adds the group/sorted domain based on the OB column.
 */
public class BookshelfBooklistGroup
        extends BooklistGroup {

    /** {@link Parcelable}. */
    public static final Creator<BookshelfBooklistGroup> CREATOR =
            new Creator<BookshelfBooklistGroup>() {
                @Override
                public BookshelfBooklistGroup createFromParcel(@NonNull final Parcel source) {
                    return new BookshelfBooklistGroup(source);
                }

                @Override
                public BookshelfBooklistGroup[] newArray(final int size) {
                    return new BookshelfBooklistGroup[size];
                }
            };

    private static final String PK_SHOW_BOOKS_UNDER_EACH =
            "style.booklist.group.bookshelf.show.all";

    /** Customized domain with display data. */
    @NonNull
    private final VirtualDomain mDisplayDomain;
    /** Show a book under each {@link Bookshelf} it is linked to. */
    private PBoolean mUnderEach;

    /**
     * Constructor.
     *
     * @param style the style
     */
    BookshelfBooklistGroup(@NonNull final BooklistStyle style) {
        super(BOOKSHELF, style);
        mDisplayDomain = createDisplayDomain();

        initPrefs();
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private BookshelfBooklistGroup(@NonNull final Parcel in) {
        super(in);
        mDisplayDomain = createDisplayDomain();

        initPrefs();
        mUnderEach.set(in);
    }

    /**
     * Get the global default for this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if we want to show a book under each of its Bookshelves.
     */
    public static boolean showBooksUnderEachDefault(@NonNull final Context context) {
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(PK_SHOW_BOOKS_UNDER_EACH,
                            false);
    }

    private void initPrefs() {
        mUnderEach = new PBoolean(
                mStylePrefs, mIsUserDefinedStyle, PK_SHOW_BOOKS_UNDER_EACH
        );
    }

    @NonNull
    private VirtualDomain createDisplayDomain() {
        // Not sorted; we sort on the OB domain as defined in the GroupKey.
        return new VirtualDomain(DOM_BOOKSHELF_NAME, TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME));
    }

    @NonNull
    @Override
    public VirtualDomain getDisplayDomain() {
        return mDisplayDomain;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);
        mUnderEach.writeToParcel(dest);
    }

    @NonNull
    @Override
    @CallSuper
    public Map<String, PPref> getPreferences() {
        final Map<String, PPref> map = super.getPreferences();
        map.put(mUnderEach.getKey(), mUnderEach);
        return map;
    }

    @Override
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {

        final PreferenceCategory category = screen.findPreference(Prefs.PSK_STYLE_BOOKSHELF);
        if (category != null) {
            final String[] keys = {PK_SHOW_BOOKS_UNDER_EACH};
            setPreferenceVisibility(category, keys, visible);
        }
    }

    /**
     * Get this preference.
     *
     * @param context Current context
     *
     * @return {@code true} if we want to show a book under each of its Bookshelves.
     */
    public boolean showBooksUnderEach(@NonNull final Context context) {
        return mUnderEach.isTrue(context);
    }

    @Override
    @NonNull
    public String toString() {
        return "BookshelfBooklistGroup{"
               + super.toString()
               + ", mDisplayDomain=" + mDisplayDomain
               + ", mUnderEach=" + mUnderEach
               + '}';
    }
}
