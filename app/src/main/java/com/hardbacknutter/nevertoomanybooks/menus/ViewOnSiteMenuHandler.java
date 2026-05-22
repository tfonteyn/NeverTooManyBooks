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

package com.hardbacknutter.nevertoomanybooks.menus;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

abstract class ViewOnSiteMenuHandler<T>
        implements MenuHandler<T> {

    private final Map<Integer, String> menuIds = new HashMap<>();

    @NonNull
    private final Identifier.EntityType entityType;
    @IdRes
    private final int subMenuResId;
    @IdRes
    private final int menuGroupResId;

    private final IdentifierDao dao;

    ViewOnSiteMenuHandler(@NonNull final Identifier.EntityType entityType,
                          @IdRes final int subMenuResId,
                          @IdRes final int menuGroupResId) {
        this.entityType = entityType;
        this.subMenuResId = subMenuResId;
        this.menuGroupResId = menuGroupResId;

        dao = ServiceLocator.getInstance().getIdentifierDao();
    }

    @NonNull
    abstract List<Identifier.Value> getSids(@NonNull T data);

    @NonNull
    abstract Optional<String> getSid(@NonNull T data,
                                     @NonNull String key);

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final MenuInflater inflater,
                             @NonNull final Menu menu,
                             @NonNull final T data) {
        final MenuItem item = menu.findItem(subMenuResId);
        if (item == null) {
            menu.addSubMenu(menuGroupResId, subMenuResId,
                            context.getResources()
                                   .getInteger(R.integer.MENU_ORDER_VIEW_ON_SITE),
                            R.string.option_view_book_at)
                .setIcon(R.drawable.link_24px);
        }
    }

    @Override
    public void onPrepareMenu(@NonNull final Context context,
                              @NonNull final Menu menu,
                              @NonNull final T data) {

        final MenuItem subMenuItem = menu.findItem(subMenuResId);
        // Sanity check
        if (subMenuItem == null) {
            return;
        }

        final SubMenu parent = subMenuItem.getSubMenu();
        // Sanity check
        if (parent == null) {
            return;
        }

        menuIds.clear();
        parent.clear();

        // add to the menu if the Identifier has a valid url
        getSids(data)
                .stream()
                .map(Identifier.Value::getKey)
                .map((String key) -> dao.findByKey(key, entityType))
                .flatMap(Optional::stream)
                .filter(identifier -> identifier.getUri().isPresent())
                .forEach(identifier -> {
                             // generate a random id, and map it to the key
                             final int menuItemId = View.generateViewId();
                             menuIds.put(menuItemId, identifier.getKey());

                             parent.add(menuGroupResId, menuItemId, 0,
                                        identifier.getName())
                                   .setIcon(R.drawable.link_24px);
                         }
                );

        final boolean visible = subMenuItem.getSubMenu().size() > 0;
        subMenuItem.setVisible(visible);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      @IdRes final int menuItemId,
                                      @NonNull final T data) {

        final String key = menuIds.get(menuItemId);
        if (key == null) {
            // Not ours to handle
            return false;
        }

        final Optional<String> oUri = dao
                .findByKey(key, entityType)
                .flatMap(Identifier::getUri);

        // Sanity check, it should be there!
        if (oUri.isPresent()) {
            final Optional<String> oSid = getSid(data, key);
            // Sanity check, it should be there!
            if (oSid.isPresent()) {
                final Uri uri = Uri.parse(String.format(oUri.get(), oSid.get()));
                context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            }
        }
        return false;
    }
}
