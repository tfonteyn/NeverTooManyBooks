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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Represents a single book search using a set of criteria.
 */
class BookSearch {

    private static final String TAG = "BookSearch";
    private static final String DEBUG_DUMP_TIMERS = "debugDumpTimers";

    private static final AtomicInteger SEARCH_ID = new AtomicInteger();

    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;
    private static final String ERROR_UNKNOWN_TASK = "Unknown task=";
    private final int id;
    @NonNull
    private final BookSearchCriteria criteria;

    /** Accumulates the results from <strong>individual</strong> search tasks. */
    private final Map<EngineId, SearchTaskResult> resultsByEngineId =
            new EnumMap<>(EngineId.class);

    /** Accumulates any errors from <strong>individual</strong> search tasks. */
    private final Map<EngineId, Throwable> errorsByEngineId = new EnumMap<>(EngineId.class);

    @NonNull
    private final ResultsAccumulator resultsAccumulator;
    @NonNull
    private final List<Site> sites;

    /** key: task_id. */
    private final Map<Integer, SearchTask> activeTasks = new ConcurrentHashMap<>();
    /** DEBUG timer. */
    private long searchStartTime;
    /** DEBUG timer. */
    private Map<EngineId, Long> searchTasksStartTime;
    /** DEBUG timer. */
    private Map<EngineId, Long> searchTasksEndTime;

    /** Flag indicating searches will be non-concurrent until an ISBN is found. */
    private boolean waitingForIsbnOrCode;

    /**
     * Constructor.
     *
     * @param sites              to search
     * @param criteria           to search for
     * @param resultsAccumulator injected accumulator
     */
    BookSearch(@NonNull final List<Site> sites,
               @NonNull final BookSearchCriteria criteria,
               @NonNull final ResultsAccumulator resultsAccumulator) {
        this.id = SEARCH_ID.incrementAndGet();
        // Take a copy!
        this.sites = List.copyOf(sites);
        this.criteria = criteria;
        this.resultsAccumulator = resultsAccumulator;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            searchTasksStartTime = new EnumMap<>(EngineId.class);
            searchTasksEndTime = new EnumMap<>(EngineId.class);

            searchStartTime = System.nanoTime();
        }
    }

    int getId() {
        return id;
    }

    @NonNull
    BookSearchCriteria getCriteria() {
        return criteria;
    }

    @NonNull
    public List<Site> getSites() {
        return sites;
    }

    boolean isWaitingForIsbnOrCode() {
        return waitingForIsbnOrCode;
    }

    void setWaitingForIsbnOrCode(final boolean waitingForIsbnOrCode) {
        this.waitingForIsbnOrCode = waitingForIsbnOrCode;
    }

    void addResult(@NonNull final EngineId engineId,
                   @NonNull final SearchEngine.SearchBy searchBy,
                   @Nullable final Book book) {
        final SearchTaskResult result = new SearchTaskResult(engineId, searchBy, book);
        synchronized (resultsByEngineId) {
            resultsByEngineId.put(engineId, result);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean hasResult(@NonNull final EngineId engineId) {
        synchronized (resultsByEngineId) {
            return resultsByEngineId.containsKey(engineId);
        }
    }

    void addError(@NonNull final EngineId engineId,
                  @Nullable final Throwable error) {
        synchronized (errorsByEngineId) {
            errorsByEngineId.put(engineId, error);
        }
    }

    /**
     * Collect all data. This is called when all individual site searches have finished.
     *
     * @param context         Current context
     * @param engineLocaleMap engine Locale's
     *
     * @return the search result
     */
    @NonNull
    BookSearchResult finish(@NonNull final Context context,
                            @NonNull final Map<EngineId, Locale> engineLocaleMap) {
        // debug: measure the time the searches took, don't include the post-processing
        final long processTime = System.nanoTime();

        final Book book = accumulateResults(context, engineLocaleMap);
        final BookSearchResult result = new BookSearchResult(id, book, criteria.getScanMode(),
                                                             errorsByEngineId);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            debugDumpTimers(processTime);
        }

        return result;
    }

    /**
     * Called when all is said and done.
     * Accumulate data from the given sites.
     *
     * @param context         Current context
     * @param engineLocaleMap engine Locale's
     *
     * @return the accumulated book data bundle
     */
    @NonNull
    private Book accumulateResults(@NonNull final Context context,
                                   @NonNull final Map<EngineId, Locale> engineLocaleMap) {
        final Book book = new Book();
        final List<EngineId> sitesInOrder;

        // Determine the set of sites for which we have results in the order the search completed
        final Set<EngineId> completedOrder = determineCompletedOrder();

        // Now convert the 'completed' order to the 'best' order
        if (criteria.hasValidIsbn()) {
            // When searching by ISBN, determine the best order use the site-data found.
            sitesInOrder = determineBestOrder(completedOrder);
            // Add the ISBN we initially searched for.
            // This avoids overwriting with a potentially different isbn from the sites
            book.setIsbn(criteria.getRawIsbnText());
        } else {
            // We did not have an ISBN as a search criteria; use the default order
            sitesInOrder = new ArrayList<>(completedOrder);
        }

        // Filter the results so we end up with the non-null and 'present' results only,
        // and convert the list to an ORDERED map with SearchEngine/Data pairs
        //noinspection DataFlowIssue
        final List<Pair<Locale, Book>> results = sitesInOrder
                .stream()
                .map(resultsByEngineId::get)
                .filter(Objects::nonNull)
                .filter(result -> result.getResult().isPresent())
                .map(result -> new Pair<>(
                        engineLocaleMap.get(result.getEngineId()),
                        result.getResult().get()))
                .collect(Collectors.toList());

        // Merge the data we have in the order as decided upon above.
        // no synchronisation needed, at this point all other threads have finished.
        resultsAccumulator.process(context, results, book);

        // If we did not get an ISBN, use the one we originally searched for.
        final String isbnStr = book.getString(DBKey.ISBN, null);
        if (isbnStr == null || isbnStr.isEmpty()) {
            book.setIsbn(criteria.getRawIsbnText());
        }

        // If we did not get a title, use the one we originally searched for.
        final String title = book.getString(DBKey.TITLE, null);
        if (title == null || title.isEmpty()) {
            book.setTitle(criteria.getTitle());
        }

        return book;
    }

    /**
     * Determine the set of sites for which we have results.
     *
     * @return sites ordered by completion time.
     */
    @NonNull
    private Set<EngineId> determineCompletedOrder() {
        final Set<EngineId> completedOrder = sites
                .stream()
                .filter(Site::isActive)
                .map(Site::getEngineId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 2023-04-21: bugfix: We MUST merge with engine keys for which results were found.
        // We need to do this when the user was for example searching by native-web-id
        // on a site which was NOT on the active list.
        // We use this somewhat convoluted method to make sure the priority order is kept.
        // Keep in mind that the results are NOT ordered by 'best order' YET, but in the order
        // in which the engines completed the searches.
        completedOrder.addAll(resultsByEngineId.keySet());
        return completedOrder;
    }

    /**
     * Determine the order in which to apply the results from the list of sites.
     * <p>
     * The order will be based on the site order as set by the user AND the actual results.
     *
     * @param activeEngines all engines we searched
     *
     * @return the list of sites in the 'best' order for further processing
     */
    @NonNull
    private List<EngineId> determineBestOrder(@NonNull final Set<EngineId> activeEngines) {
        final List<EngineId> sitesInOrder = new ArrayList<>();
        final Collection<EngineId> sitesWithoutIsbn = new ArrayList<>();

        final boolean strictIsbn = criteria.isStrictIsbn();
        final Optional<ISBN> oIsbn = criteria.getIsbn();

        activeEngines.forEach(engineId -> {
            // no synchronisation needed, at this point all other threads have finished.
            final SearchTaskResult siteData = resultsByEngineId.get(engineId);
            if (siteData != null) {
                siteData.getResult().ifPresent(result -> {

                    if (siteData.getSearchBy() == SearchEngine.SearchBy.ExternalId
                        && !strictIsbn) {
                        // We searched by website id and didn't insist on an exact ISBN
                        // so we SHOULD be pretty sure about the data...
                        sitesInOrder.add(engineId);

                    } else if (result.hasIsbn()) {
                        // We did a general search with an ISBN; check if it matches
                        final String isbnFoundStr = result.getIsbn();
                        if (!isbnFoundStr.isEmpty()
                            && oIsbn.isPresent()
                            && oIsbn.get().equals(new ISBN(isbnFoundStr, strictIsbn))) {
                            sitesInOrder.add(engineId);
                        } else {
                            // The ISBN found does not match the ISBN we searched for;
                            // 2023-05-30: don't just skip; add it to the lesser reliables
                            sitesWithoutIsbn.add(engineId);

                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                                LoggerFactory.getLogger()
                                             .d(TAG, "accumulateResults",
                                                "isbn=" + oIsbn.orElse(null),
                                                "isbnFound=" + isbnFoundStr);
                            }
                        }
                    } else {
                        // The result did not have an ISBN at all.
                        sitesWithoutIsbn.add(engineId);
                    }
                });
            }
        });

        // finally add the less reliable ones at the end of the list.
        sitesInOrder.addAll(sitesWithoutIsbn);
        return sitesInOrder;
    }

    /**
     * Remove the task from the active list, and return it.
     *
     * @param taskId to remove
     *
     * @return the removed task
     */
    @NonNull
    SearchTask removeTask(final int taskId) {
        return Objects.requireNonNull(activeTasks.remove(taskId),
                                      () -> ERROR_UNKNOWN_TASK + taskId);
    }

    @Nullable
    SearchTask getTask(final int taskId) {
        return activeTasks.get(taskId);
    }

    void addTask(@NonNull final SearchTask task) {
        activeTasks.put(task.getTaskId(), task);
    }

    boolean isActive() {
        return !activeTasks.isEmpty();
    }

    void cancel() {
        activeTasks.values().forEach(SearchTask::cancel);
    }

    void debugSearchTaskStarting(@NonNull final EngineId engineId,
                                 final int taskId,
                                 final boolean waitForIsbnOrCode) {
        if (DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "startSearch",
                                        "bookSearch=" + id,
                                        "new-task=" + taskId,
                                        "searchEngine=" + engineId.name(),
                                        "waitForIsbnOrCode=" + waitForIsbnOrCode);
        }
        if (DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            searchTasksStartTime.put(engineId, System.nanoTime());
        }
    }

    void debugSearchTaskFinished(final int taskId,
                                 final EngineId engineId) {
        if (DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            searchTasksEndTime.put(engineId, System.nanoTime());
        }

        if (DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "onSearchTaskFinished",
                                        "searchId=" + id,
                                        "taskId=" + taskId,
                                        engineId.name());
            final Logger logger = LoggerFactory.getLogger();
            synchronized (activeTasks) {
                for (final SearchTask task : activeTasks.values()) {
                    logger.d(TAG, "onSearchTaskFinished|running=" + task.getTaskId()
                                  + '|' + task.getSearchEngine().getEngineId().name());
                }
            }
        }
    }

    private void debugDumpTimers(final long processTime) {
        final Logger logger = LoggerFactory.getLogger();

        for (final Map.Entry<EngineId, Long> entry : searchTasksStartTime.entrySet()) {
            final EngineId engineId = entry.getKey();
            final String engineName = engineId.name();

            final long start = entry.getValue();
            final Long end = searchTasksEndTime.get(engineId);

            if (end != null) {
                logger.d(TAG, DEBUG_DUMP_TIMERS,
                         String.format(Locale.ENGLISH, "engine=%20s:%10d ms",
                                       engineName,
                                       (end - start) / NANO_TO_MILLIS));
            } else {
                logger.d(TAG, DEBUG_DUMP_TIMERS,
                         String.format(Locale.ENGLISH, "engine=%20s|never finished",
                                       engineName));
            }
        }

        logger.d(TAG, DEBUG_DUMP_TIMERS,
                 String.format(Locale.ENGLISH, "total search time: %10d ms",
                               (processTime - searchStartTime)
                               / NANO_TO_MILLIS));

        logger.d(TAG, DEBUG_DUMP_TIMERS,
                 String.format(Locale.ENGLISH, "processing time: %10d ms",
                               (System.nanoTime() - processTime)
                               / NANO_TO_MILLIS));
    }

    /**
     * The result of a single {@link SearchTask}.
     * <p>
     * Encapsulates where a result came from + how the search was done + the result itself.
     */
    private static class SearchTaskResult {

        @Nullable
        private final Book result;
        @NonNull
        private final EngineId engineId;
        @NonNull
        private final SearchEngine.SearchBy searchBy;

        SearchTaskResult(@NonNull final EngineId engineId,
                         @NonNull final SearchEngine.SearchBy searchBy,
                         @Nullable final Book result) {
            this.engineId = engineId;
            this.searchBy = searchBy;
            this.result = result;
        }

        @NonNull
        EngineId getEngineId() {
            return engineId;
        }

        @NonNull
        SearchEngine.SearchBy getSearchBy() {
            return searchBy;
        }

        @NonNull
        Optional<Book> getResult() {
            if (result == null || result.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(result);
        }
    }
}
