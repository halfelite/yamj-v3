/*
 *      Copyright (c) 2004-2015 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ).
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3
 *
 */
package org.yamj.core.service.metadata.online;

import com.omertron.thetvdbapi.TheTVDBApi;
import com.omertron.thetvdbapi.TvDbException;
import com.omertron.thetvdbapi.model.Actor;
import com.omertron.thetvdbapi.model.Banners;
import com.omertron.thetvdbapi.model.Episode;
import com.omertron.thetvdbapi.model.Series;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yamj.core.configuration.ConfigService;
import org.yamj.core.tools.LRUTimedCache;
import org.yamj.core.tools.web.ResponseTools;
import org.yamj.core.tools.web.TemporaryUnavailableException;

@Service("tvdbApiWrapper")
public class TheTVDbApiWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(TheTVDbApiWrapper.class);
    private static final int YEAR_MIN = 1900;
    private static final int YEAR_MAX = 2050;
    private final Lock seriesLock = new ReentrantLock(true);
    private final Lock bannersLock = new ReentrantLock(true);
    // make maximal 20 banners objects maximal 30 minutes accessible
    private final LRUTimedCache<String, Banners> bannersCache = new LRUTimedCache<>(20, 1800);
    // make maximal 50 series objects maximal 30 minutes accessible
    private final LRUTimedCache<String, Series> seriesCache = new LRUTimedCache<>(50, 1800);
    // make maximal 30 episode lists maximal 30 minutes accessible
    private final LRUTimedCache<String, List<Episode>> episodesCache = new LRUTimedCache<>(30, 1800);

    @Autowired
    private ConfigService configService;
    @Autowired
    private TheTVDBApi tvdbApi;

    public Banners getBanners(String id) {
        Banners banners = bannersCache.get(id);
        if (banners == null) {
            bannersLock.lock();
            try {
                // second try cause meanwhile the cache could have been filled
                banners = bannersCache.get(id);
                if (banners == null) {
                    // retrieve banners from TheTVDb
                    banners = tvdbApi.getBanners(id);
                    bannersCache.put(id, banners);
                }
            } catch (TvDbException ex) {
                LOG.error("Failed to get banners using TVDb ID {}: {}", id, ex.getMessage());
                LOG.trace("TheTVDb error" , ex);
            } finally {
                bannersLock.unlock();
            }
        }
        return banners;
    }

    /**
     * Get series information using the ID
     *
     * @param id
     * @return
     */
    public Series getSeries(String id) {
        return getSeries(id, false);
    }
        
    /**
     * Get series information using the ID
     *
     * @param throwTempError
     * @return
     */
    public Series getSeries(String id, boolean throwTempError) {
        Series series = seriesCache.get(id);
        if (series == null) {
            seriesLock.lock();
            try {
                String defaultLanguage = configService.getProperty("thetvdb.language", "en");
                String altLanguage = configService.getProperty("thetvdb.language.alternate", "");

                // second try cause meanwhile the cache could have been filled
                series = seriesCache.get(id);
                if (series == null) {
                    // retrieve series from TheTVDb
                    series = tvdbApi.getSeries(id, defaultLanguage);
                    if (series == null && StringUtils.isNotBlank(altLanguage)) {
                        series = tvdbApi.getSeries(id, altLanguage);
                    }

                    if (series == null) {
                        // have a valid series object with empty values
                        series = new com.omertron.thetvdbapi.model.Series();
                    }
                    seriesCache.put(id, series);
                }
            } catch (TvDbException ex) {
                if (throwTempError && ResponseTools.isTemporaryError(ex)) {
                    throw new TemporaryUnavailableException("TheTVDb service temporary not available: " + ex.getResponseCode(), ex);
                }
                LOG.error("Failed to get series using TVDb ID {}: {}", id, ex.getMessage());
                LOG.trace("TheTVDb error" , ex);
                series = new com.omertron.thetvdbapi.model.Series();
            } finally {
                seriesLock.unlock();
            }
        }
        return series;
    }

    /**
     * Get the Series ID by title and year
     *
     * @param title
     * @param year
     * @return
     */
    public String getSeriesId(String title, int year, boolean throwTempError) {
        String tvdbId = null;
        if (StringUtils.isNotBlank(title)) {
            seriesLock.lock();
            try {
                String defaultLanguage = configService.getProperty("thetvdb.language", "en");
                String altLanguage = configService.getProperty("thetvdb.language.alternate", "");

                boolean usedDefault = true;
                List<Series> seriesList = tvdbApi.searchSeries(title, defaultLanguage);

                if (CollectionUtils.isEmpty(seriesList) && StringUtils.isNotBlank(altLanguage)) {
                    seriesList = tvdbApi.searchSeries(title, altLanguage);
                    usedDefault = false;
                }

                if (CollectionUtils.isNotEmpty(seriesList)) {
                    Series series = null;
                    for (Series s : seriesList) {
                        if (s.getFirstAired() != null && !s.getFirstAired().isEmpty() && (year > YEAR_MIN && year < YEAR_MAX)) {
                            DateTime firstAired = DateTime.parse(s.getFirstAired());
                            firstAired.getYear();
                            if (firstAired.getYear() == year) {
                                series = s;
                                break;
                            }
                        } else {
                            series = s;
                            break;
                        }
                    }

                    if (series != null) {
                        tvdbId = series.getId();
                        Series cached = tvdbApi.getSeries(tvdbId, usedDefault ? defaultLanguage : altLanguage);
                        this.seriesCache.put(tvdbId, cached);
                    }
                }
            } catch (TvDbException ex) {
                if (throwTempError && ResponseTools.isTemporaryError(ex)) {
                    throw new TemporaryUnavailableException("TheTVDb service temporary not available: " + ex.getResponseCode(), ex);
                }
                LOG.error("Failed retrieving TVDb id for series '{}': {}", title, ex.getMessage());
                LOG.trace("TheTVDb error" , ex);
            } finally {
                seriesLock.unlock();
            }
        }
        return tvdbId;
    }

    public List<Actor> getActors(String id, boolean throwTempError) {
        try {
            return tvdbApi.getActors(id);
        } catch (TvDbException ex) {
            if (throwTempError && ResponseTools.isTemporaryError(ex)) {
                throw new TemporaryUnavailableException("TheTVDb service temporary not available: " + ex.getResponseCode(), ex);
            }
            LOG.error("Failed to get actors using TVDb ID {}: {}", id, ex.getMessage());
            LOG.trace("TheTVDb error" , ex);
        }
        return null;
    }

    public List<Episode> getSeasonEpisodes(String id, int season) {
        String key = (id + "###" + season);

        List<Episode> episodeList = this.episodesCache.get(key);
        if (episodeList == null || episodeList.isEmpty()) {
            try {
                String defaultLanguage = configService.getProperty("thetvdb.language", "en");
                String altLanguage = configService.getProperty("thetvdb.language.alternate", "");
    
                episodeList = tvdbApi.getSeasonEpisodes(id, season, defaultLanguage);
    
                if (CollectionUtils.isEmpty(episodeList) && StringUtils.isNotBlank(altLanguage)) {
                    episodeList = tvdbApi.getSeasonEpisodes(id, season, altLanguage);
                }
                
                if (CollectionUtils.isNotEmpty(episodeList)) {
                    this.episodesCache.put(key, episodeList);
                }
            } catch (TvDbException ex) {
                LOG.error("Failed to get episodes for TVDb ID {} and season {}: {}", id, season, ex.getMessage());
                LOG.trace("TheTVDb error" , ex);
            }
        }
        return episodeList;
    }
}