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

package com.hardbacknutter.prefslib.internal;

import android.annotation.SuppressLint;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.prefslib.HeaderSetting;
import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.SettingsGroup;

public class SettingsAdapter
        extends RecyclerView.Adapter<SettingViewHolder> {

    @NonNull
    private final UICallback listener;

    private final boolean sortRoot;

    @NonNull
    private final Deque<List<VHSetting>> backStack = new ArrayDeque<>();
    /** The full set as currently handled by this adapter. */
    @NonNull
    private final List<VHSetting> settings;
    /** The visible items. */
    @NonNull
    private final List<VHSetting> displayedList = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param settings to list
     * @param sortRoot Whether to sort the first category of settings
     *                 if they have no header.
     *                 Ignored if the first element is a header.
     * @param listener for change and click events
     */
    public SettingsAdapter(@NonNull final List<Setting> settings,
                           final boolean sortRoot,
                           @NonNull final UICallback listener) {
        this.listener = listener;
        this.sortRoot = sortRoot;

        this.settings = VHSetting.wrap(sortSettings(settings));
        updateDisplayedList();
    }

    private void updateDisplayedList() {
        displayedList.clear();
        displayedList.addAll(settings.stream()
                                     .filter(VHSetting::isVisible)
                                     .collect(Collectors.toList()));
    }

    public void navigateTo(@NonNull final SettingsGroup group) {
        // Preserve the current list
        backStack.push(settings);

        setItems(VHSetting.wrap(sortSettings(group.getSubSettings())));
    }

    /**
     * If a {@link SettingsGroup} is used,
     * this method should be called when the user tapped 'back'.
     *
     * @return {@code true} when handled
     *         {@code false} there was nothing to pop,
     *         the Activity should typically close
     */
    public boolean popGroup() {
        if (backStack.isEmpty()) {
            return false;
        }

        // Retrieve and use the previous
        setItems(backStack.pop());
        return true;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setItems(@NonNull final List<VHSetting> vhSettings) {
        this.settings.clear();
        this.settings.addAll(vhSettings);
        updateDisplayedList();
        notifyDataSetChanged();
    }

    /**
     * Enable or disable a {@link Setting}.
     * <p>
     * Silently ignores any key which is not present.
     *
     * @param enabled flag
     * @param keys    the {@link Setting}s to modify.
     */
    public void setEnabled(final boolean enabled,
                           final List<String> keys) {
        final Set<CharSequence> keySet = new HashSet<>(keys);
        for (final VHSetting p : settings) {
            final String key = p.getSetting().getKey();
            if (keySet.contains(key)) {
                // Has the state changed?
                if (p.isEnabled() != enabled) {
                    p.setEnabled(enabled);
                    // If it's displaying, force update it
                    findPosition(key).ifPresent(this::notifyItemChanged);
                }
            }
        }
    }

    /**
     * Set the visibility of list of {@link Setting}.
     * <p>
     * Silently ignores any key which is not present.
     *
     * @param pairs the {@link Setting}s to modify.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setVisible(final Map<String, Boolean> pairs) {
        boolean hasChanged = false;

        final Set<String> keySet = pairs.keySet();

        for (final VHSetting p : settings) {
            final String key = p.getSetting().getKey();
            if (keySet.contains(key)) {
                // Has the state changed?
                final boolean newState = Boolean.TRUE.equals(pairs.get(key));
                if (p.isVisible() != newState) {
                    p.setVisible(newState);
                    hasChanged = true;
                }
            }
        }

        if (hasChanged) {
            updateDisplayedList();
            notifyDataSetChanged();
        }
    }

    /**
     * Get all visible children of the given header.
     * <p>
     * If no header for the given key is found, an empty list is returned.
     *
     * @param headerKey to check
     *
     * @return keys
     */
    @NonNull
    public List<String> getVisibleChildren(@NonNull final CharSequence headerKey) {
        return getChildren(headerKey)
                .stream()
                .filter(VHSetting::isVisible)
                .map(VHSetting::getSetting)
                .map(Setting::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Get all children of the given header.
     * <p>
     * If no header for the given key is found, an empty list is returned.
     *
     * @param headerKey to use; should be a {@link HeaderSetting} key.
     *
     * @return list
     */
    @NonNull
    private List<VHSetting> getChildren(@NonNull final CharSequence headerKey) {
        final List<VHSetting> result = new ArrayList<>();
        boolean found = false;

        for (final VHSetting item : settings) {
            if (item.getSetting().getType() == Setting.Type.Header) {
                // If we hit a new header after finding our target, we're done
                if (found) {
                    return result;
                }

                // Check if this is the header we are looking for
                if (item.getSetting().getKey().contentEquals(headerKey)) {
                    found = true;
                    // skip, we don't want to add the header itself
                    continue;
                }
            }

            if (found) {
                result.add(item);
            }
        }

        return found ? result : List.of();
    }

    /**
     * Find the position of a <strong>currently displayed</strong> {@link Setting}.
     *
     * @param key the {@link Setting}.
     *
     * @return position
     */
    @NonNull
    public OptionalInt findPosition(@NonNull final CharSequence key) {
        for (int i = 0; i < displayedList.size(); i++) {
            if (displayedList.get(i).getSetting().getKey().contentEquals(key)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    @NonNull
    @Override
    public SettingViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                final int viewType) {

        return ViewHolderFactory.create(
                Setting.Type.byId(viewType), parent,
                position -> displayedList.get(position).getSetting(), listener);
    }

    @Override
    public void onBindViewHolder(@NonNull final SettingViewHolder holder,
                                 final int position) {
        final VHSetting vhSetting = displayedList.get(position);
        holder.onBind(vhSetting.getSetting(), vhSetting.isEnabled());
    }

    @Override
    public int getItemViewType(final int position) {
        return displayedList.get(position).getSetting().getType().ordinal();
    }

    @Override
    public int getItemCount() {
        return displayedList.size();
    }

    @NonNull
    private List<Setting> sortSettings(@NonNull final List<Setting> settings) {
        final List<Setting> result = new ArrayList<>(settings);
        int start = 0;

        for (int i = 0; i <= result.size(); i++) {
            // Collect until end-of-list or next Header
            if (i == result.size() || result.get(i).getType() == Setting.Type.Header) {
                sortGroup(result, start, i);
                start = i;
            }
        }
        return result;
    }

    /**
     * Sort a sublist.
     *
     * @param list  to sort
     * @param start of the sub-list
     * @param end   of the sub-list
     */
    private void sortGroup(@NonNull final List<Setting> list,
                           final int start,
                           final int end) {
        if (start >= end) {
            return;
        }

        final Setting firstElement = list.get(start);
        final boolean shouldSort;
        final int sortOffset;

        if (firstElement.getType() == Setting.Type.Header) {
            // If it starts with a Header:
            // does it need sorting? and are there at least 2 items?
            shouldSort = ((HeaderSetting) firstElement).isSorted() && (end - start) > 2;
            // The header stays fixed at the top
            sortOffset = 1;
        } else {
            sortOffset = 0;
            shouldSort = sortRoot;
        }

        if (shouldSort) {
            list.subList(start + sortOffset, end).sort(Comparator.comparing(Setting::getTitle));
        }
    }

    private static final class VHSetting {

        @NonNull
        private final Setting setting;

        private boolean enabled = true;
        private boolean visible = true;

        private VHSetting(@NonNull final Setting setting) {
            this.setting = setting;
        }

        @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
        @NonNull
        static List<VHSetting> wrap(@NonNull final List<Setting> list) {
            return list.stream().map(VHSetting::new).collect(Collectors.toList());
        }

        @NonNull
        Setting getSetting() {
            return setting;
        }

        boolean isEnabled() {
            return enabled;
        }

        void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        boolean isVisible() {
            return visible;
        }

        void setVisible(final boolean visible) {
            this.visible = visible;
        }
    }
}
