/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.Groups;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBitmask;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PInteger;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * Except for the clone constructor (which is 'protected') all constructors are private.
 * Use the factory methods instead for clarity.
 */
public class UserStyle
        extends BooklistStyle {

    /** Style display name. */
    public static final String PK_STYLE_NAME = "style.booklist.name";

    /**
     * Constructor for <strong>Global defaults</strong>.
     *
     * @param context Current context
     */
    private UserStyle(@NonNull final Context context) {
        // empty uuid indicates global
        super(context, "", true);

        // negative == builtin; MIN_VALUE because why not....
        id = Integer.MIN_VALUE;

        initPrefs(true);
    }

    /**
     * Constructor for <strong>importing</strong> styles.
     *
     * @param context Current context
     * @param uuid    UUID of the style
     *
     * @see com.hardbacknutter.nevertoomanybooks.backup.json.coders.ListStyleCoder
     */
    private UserStyle(@NonNull final Context context,
                      @NonNull final String uuid) {
        super(context, uuid, true);
        id = 0;

        initPrefs(true);
    }

    /**
     * Constructor for styles <strong>loaded from database</strong>.
     *
     * @param context Current context
     * @param rowData with data
     */
    private UserStyle(@NonNull final Context context,
                      @NonNull final DataHolder rowData) {
        super(context, rowData.getString(DBKey.KEY_STYLE_UUID), true);

        id = rowData.getLong(DBKey.PK_ID);
        preferred = rowData.getBoolean(DBKey.BOOL_STYLE_IS_PREFERRED);
        menuPosition = rowData.getInt(DBKey.KEY_STYLE_MENU_POSITION);

        initPrefs(true);
    }

    /**
     * Copy constructor. Used for cloning.
     * <p>
     * The id and uuid are passed in to allow testing,
     * see {@link #clone(Context)}.
     *
     * @param context Current context
     * @param style   to clone
     * @param id      for the new style
     * @param uuid    for the new style
     */
    protected UserStyle(@NonNull final Context context,
                        @NonNull final BooklistStyle style,
                        final long id,
                        @NonNull final String uuid) {
        super(context, uuid, true);

        this.id = id;

        // Store the new name.
        persistenceLayer.setString(PK_STYLE_NAME, style.getLabel(context));

        // copy the basic settings (i.e. non-preferences)
        preferred = style.isPreferred();
        menuPosition = style.getMenuPosition();

        // clone the preferences
        showAuthorByGivenName = new PBoolean(true, persistenceLayer,
                                             style.showAuthorByGivenName);
        sortAuthorByGivenName = new PBoolean(true, persistenceLayer,
                                             style.sortAuthorByGivenName);

        expansionLevel = new PInteger(true, persistenceLayer, style.expansionLevel);
        showHeaderInfo = new PBitmask(true, persistenceLayer, style.showHeaderInfo);
        useGroupRowPreferredHeight = new PBoolean(true, persistenceLayer,
                                                  style.useGroupRowPreferredHeight);

        textScale = new TextScale(true, persistenceLayer, style.textScale);

        listScreenBookFields = new ListScreenBookFields(true, persistenceLayer,
                                                        style.listScreenBookFields);

        detailScreenBookFields = new DetailScreenBookFields(true, persistenceLayer,
                                                            style.detailScreenBookFields);

        groups = new Groups(true, this, style.groups);
    }

    @NonNull
    public static UserStyle createGlobal(@NonNull final Context context) {
        return new UserStyle(context);
    }

    @NonNull
    public static UserStyle createFromDatabase(@NonNull final Context context,
                                               @NonNull final DataHolder rowData) {
        return new UserStyle(context, rowData);
    }

    @NonNull
    public static UserStyle createFromImport(@NonNull final Context context,
                                             @NonNull final String uuid) {
        return new UserStyle(context, uuid);
    }

    /**
     * Discard this style by deleting the SharedPreferences file.
     * This can only be done for a cloned (new) style which have not been
     * persisted to the database.
     *
     * @param context Current context
     */
    public void discard(@NonNull final Context context) {
        if (getId() != 0) {
            throw new IllegalArgumentException("Style already persisted");
        }
        context.deleteSharedPreferences(getUuid());
    }

    @NonNull
    public String getName() {
        //noinspection ConstantConditions
        return persistenceLayer.getNonGlobalString(PK_STYLE_NAME);
    }

    public void setName(@NonNull final String name) {
        persistenceLayer.setString(PK_STYLE_NAME, name);
    }

    @Override
    public boolean isUserDefined() {
        return true;
    }

    /**
     * Get a flat list with accumulated preferences for this object and it's groups.<br>
     * Provides low-level access to all preferences.<br>
     * This should only be called for export/import.
     *
     * @return list
     */
    @NonNull
    public Collection<PPref<?>> getRawPreferences() {
        final Collection<PPref<?>> list = new ArrayList<>();
        list.add(expansionLevel);
        list.add(useGroupRowPreferredHeight);
        list.add(showHeaderInfo);

        list.add(showAuthorByGivenName);
        list.add(sortAuthorByGivenName);

        list.addAll(textScale.getRawPreferences());
        list.addAll(listScreenBookFields.getRawPreferences());
        list.addAll(detailScreenBookFields.getRawPreferences());

        list.addAll(groups.getRawPreferences());

        return list;
    }


    @Override
    @NonNull
    public String getTypeDescription(@NonNull final Context context) {
        return context.getString(R.string.style_is_user_defined);
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return getName();
    }
}
