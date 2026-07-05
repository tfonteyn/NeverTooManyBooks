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

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;

@SuppressWarnings("WeakerAccess")
public class SearchBookByExternalIdViewModel
        extends ViewModel {

    @NonNull
    private final EditBookOutput resultData = new EditBookOutput();
    private IdentifierDao identifierDao;
    private Style style;
    /** The currently selected radio button. */
    @IdRes
    private int selectedRbViewId = View.NO_ID;
    /** The current input field content. */
    @Nullable
    private String sid;

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
     * @param args {@link Fragment#requireArguments()}
     */
    void init(@NonNull final Bundle args) {
        if (identifierDao == null) {
            identifierDao = ServiceLocator.getInstance().getIdentifierDao();
            // Lookup the provided style or use the default if not found.
            final String styleUuid = args.getString(Style.BKEY_UUID);
            final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
            style = stylesHelper.getStyle(styleUuid).orElseGet(stylesHelper::getDefault);
        }
    }

    @NonNull
    Optional<Identifier> getIdentifier(@NonNull final EngineId engineId) {
        return engineId.getIdentifierKey(Identifier.EntityType.Book)
                       .flatMap(key -> identifierDao.find(key, Identifier.EntityType.Book));
    }

    @NonNull
    Style getStyle() {
        Objects.requireNonNull(style, "style");
        return style;
    }

    @IdRes
    int getSelectedRbViewId() {
        return selectedRbViewId;
    }

    void setSelectedRbViewId(@IdRes final int selectedRbViewId) {
        this.selectedRbViewId = selectedRbViewId;
    }

    @Nullable
    String getSid() {
        return sid;
    }

    void setSid(@Nullable final String sid) {
        this.sid = sid;
    }
}
