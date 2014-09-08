/*
 *      Copyright (c) 2004-2014 YAMJ Members
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
package org.yamj.core.service.plugin;

import com.omertron.thetvdbapi.TheTVDBApi;
import com.omertron.thetvdbapi.model.Actor;
import com.omertron.thetvdbapi.model.Banners;
import com.omertron.thetvdbapi.model.Episode;
import com.omertron.thetvdbapi.model.Series;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yamj.core.configuration.ConfigService;
import org.yamj.core.tools.LRUTimedCache;

@Service("tvdbApiWrapper")
public class TheTVDbApiWrapper {

    private static final int YEAR_MIN = 1900;
    private static final int YEAR_MAX = 2050;
    private final Lock seriesLock = new ReentrantLock(true);
    private final Lock bannersLock = new ReentrantLock(true);
    // make maximal 20 banners objects maximal 30 minutes accessible
    private final LRUTimedCache<String, Banners> bannersCache = new LRUTimedCache<String, Banners>(20, 1800);
    // make maximal 50 series objects maximal 30 minutes accessible
    private final LRUTimedCache<String, Series> seriesCache = new LRUTimedCache<String, Series>(50, 1800);
    // make maximal 30 episode lists maximal 30 minutes accessible
    private final LRUTimedCache<String, List<Episode>> episodesCache = new LRUTimedCache<String, List<Episode>>(30, 1800);

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
    public String getSeriesId(String title, int year) {
        String id = "";
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
                        id = series.getId();
                        Series saved = tvdbApi.getSeries(id, (usedDefault ? defaultLanguage : altLanguage));
                        this.seriesCache.put(id, saved);
                    }
                }
            } finally {
                seriesLock.unlock();
            }
        }
        return id;
    }

    public List<Actor> getActors(String id) {
        return tvdbApi.getActors(id);
    }

    public List<Episode> getSeasonEpisodes(String id, int season) {
        String key = (id + "###" + season);

        List<Episode> episodeList = this.episodesCache.get(key);
        if (episodeList == null) {
            String defaultLanguage = configService.getProperty("thetvdb.language", "en");
            String altLanguage = configService.getProperty("thetvdb.language.alternate", "");
            
            episodeList = tvdbApi.getSeasonEpisodes(id, season, defaultLanguage);
            if (CollectionUtils.isEmpty(episodeList) && StringUtils.isNotBlank(altLanguage)) {
                episodeList = tvdbApi.getSeasonEpisodes(id, season, altLanguage);
            }
            if (episodeList == null) {
                episodeList = Collections.emptyList();
            }
            this.episodesCache.put(key, episodeList);
        }
        return episodeList;
    }
}
