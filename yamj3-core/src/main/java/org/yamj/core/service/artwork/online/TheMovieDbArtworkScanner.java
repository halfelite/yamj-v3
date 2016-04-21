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
package org.yamj.core.service.artwork.online;

import static org.yamj.plugin.api.common.Constants.LANGUAGE_EN;
import static org.yamj.plugin.api.common.Constants.SOURCE_TMDB;

import com.omertron.themoviedbapi.MovieDbException;
import com.omertron.themoviedbapi.TheMovieDbApi;
import com.omertron.themoviedbapi.enumeration.ArtworkType;
import com.omertron.themoviedbapi.model.artwork.Artwork;
import com.omertron.themoviedbapi.model.collection.Collection;
import com.omertron.themoviedbapi.results.ResultList;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Service;
import org.yamj.common.type.MetaDataType;
import org.yamj.core.config.LocaleService;
import org.yamj.core.database.model.*;
import org.yamj.core.service.metadata.online.TheMovieDbScanner;
import org.yamj.core.service.various.IdentifierService;
import org.yamj.core.tools.ExceptionTools;
import org.yamj.plugin.api.artwork.ArtworkDTO;
import org.yamj.plugin.api.artwork.ArtworkTools;

@Service("tmdbArtworkScanner")
public class TheMovieDbArtworkScanner implements IMovieArtworkScanner, ISeriesArtworkScanner, IBoxedSetArtworkScanner, IPersonArtworkScanner {

    private static final Logger LOG = LoggerFactory.getLogger(TheMovieDbArtworkScanner.class);
    private static final String DEFAULT_SIZE = "original";
    private static final String NO_LANGUAGE = StringUtils.EMPTY;
    private static final String API_ERROR = "TheMovieDb error";
    
    @Autowired
    private LocaleService localeService;
    @Autowired
    private TheMovieDbScanner tmdbScanner;
    @Autowired
    private TheMovieDbApi tmdbApi;
    @Autowired
    private Cache tmdbArtworkCache;
    @Autowired
    private IdentifierService identifierService;
    
    private String getDefaultLanguage() {
        return localeService.getLocaleForConfig("themoviedb").getLanguage();
    }

    @Override
    public String getScannerName() {
        return SOURCE_TMDB;
    }

    @Override
    public List<ArtworkDTO> getPosters(VideoData videoData) {
        String tmdbId = tmdbScanner.getMovieId(videoData);
        return getFilteredArtwork(tmdbId, getDefaultLanguage(), MetaDataType.MOVIE, ArtworkType.POSTER, DEFAULT_SIZE);
    }

    @Override
    public List<ArtworkDTO> getFanarts(VideoData videoData) {
        String tmdbId = tmdbScanner.getMovieId(videoData);
        return getFilteredArtwork(tmdbId, getDefaultLanguage(), MetaDataType.MOVIE, ArtworkType.BACKDROP, DEFAULT_SIZE);
    }

    @Override
    public List<ArtworkDTO> getPosters(Season season) {
        String tmdbId = tmdbScanner.getSeriesId(season.getSeries());
        return getFilteredArtwork(tmdbId, season.getSeason(), -1, getDefaultLanguage(), MetaDataType.SEASON, ArtworkType.POSTER, DEFAULT_SIZE);
    }

    @Override
    public List<ArtworkDTO> getFanarts(Season season) {
        String tmdbId = tmdbScanner.getSeriesId(season.getSeries());
        return getFilteredArtwork(tmdbId, season.getSeason(), -1, getDefaultLanguage(), MetaDataType.SEASON, ArtworkType.BACKDROP, DEFAULT_SIZE);
    }

    @Override
    public List<ArtworkDTO> getPosters(Series series) {
        String tmdbId = tmdbScanner.getSeriesId(series);
        return getFilteredArtwork(tmdbId, getDefaultLanguage(), MetaDataType.SERIES, ArtworkType.POSTER, DEFAULT_SIZE);
    }

    @Override
    public List<ArtworkDTO> getFanarts(Series series) {
        String tmdbId = tmdbScanner.getSeriesId(series);
        return getFilteredArtwork(tmdbId, getDefaultLanguage(), MetaDataType.SERIES, ArtworkType.BACKDROP, DEFAULT_SIZE);
    }

    @Override
    public List<ArtworkDTO> getBanners(Season season) {
        return Collections.emptyList();
    }

    @Override
    public List<ArtworkDTO> getBanners(Series series) {
        return Collections.emptyList();
    }

    @Override
    public List<ArtworkDTO> getVideoImages(VideoData videoData) {
        String tmdbId = tmdbScanner.getSeriesId(videoData.getSeason().getSeries());
        return getFilteredArtwork(tmdbId, videoData.getSeason().getSeason(), videoData.getEpisode(), getDefaultLanguage(), MetaDataType.EPISODE, ArtworkType.STILL, DEFAULT_SIZE);
    }
    
    @Override
    public List<ArtworkDTO> getPhotos(Person person) {
        String tmdbId = tmdbScanner.getPersonId(person);
        return getFilteredArtwork(tmdbId, NO_LANGUAGE, MetaDataType.PERSON, ArtworkType.PROFILE, DEFAULT_SIZE);
    }

    @Override
    public List<ArtworkDTO> getPosters(BoxedSet boxedSet) {
        String tmdbId = boxedSet.getSourceDbId(getScannerName());
        String defaultLanguage = getDefaultLanguage();
        
        if (StringUtils.isNumeric(tmdbId)) {
            return getFilteredArtwork(tmdbId, defaultLanguage, MetaDataType.BOXSET, ArtworkType.POSTER, DEFAULT_SIZE);
        }
        
        Collection collection = findCollection(boxedSet, defaultLanguage);
        if (collection != null) {
            boxedSet.setSourceDbId(getScannerName(), Integer.toString(collection.getId()));
            return this.getFilteredArtwork(collection.getId(), defaultLanguage, MetaDataType.BOXSET, ArtworkType.POSTER, DEFAULT_SIZE);
        }
        
        return Collections.emptyList();
    }

    @Override
    public List<ArtworkDTO> getFanarts(BoxedSet boxedSet) {
        String tmdbId = boxedSet.getSourceDbId(getScannerName());
        String defaultLanguage = getDefaultLanguage();
        
        if (StringUtils.isNumeric(tmdbId)) {
            return getFilteredArtwork(tmdbId, defaultLanguage, MetaDataType.BOXSET, ArtworkType.BACKDROP, DEFAULT_SIZE);
        }
        
        Collection collection = findCollection(boxedSet, defaultLanguage);
        if (collection != null) {
            boxedSet.setSourceDbId(getScannerName(), Integer.toString(collection.getId()));
            return this.getFilteredArtwork(collection.getId(), defaultLanguage, MetaDataType.BOXSET, ArtworkType.BACKDROP, DEFAULT_SIZE);
        }
        
        return Collections.emptyList();
    }

    @Override
    public List<ArtworkDTO> getBanners(BoxedSet boxedSet) {
        return Collections.emptyList();
    }

    public Collection findCollection(BoxedSet boxedSet, String language) {
        try {
            ResultList<Collection> resultList = tmdbApi.searchCollection(boxedSet.getName(), 0, language);
            if (resultList.isEmpty() && !StringUtils.equalsIgnoreCase(language, LANGUAGE_EN)) {
                resultList = tmdbApi.searchCollection(boxedSet.getName(), 0, LANGUAGE_EN);
            }

            for (Collection collection : resultList.getResults()) {
                if (StringUtils.isBlank(collection.getTitle())) {
                    continue;
                }

                // 1. check name
                String boxedSetIdentifier = boxedSet.getIdentifier();
                String collectionIdentifier = identifierService.cleanIdentifier(collection.getTitle());
                if (StringUtils.equalsIgnoreCase(boxedSetIdentifier, collectionIdentifier)) {
                    // found matching collection
                    return collection;
                }

                
                // 2. TODO find matching collection based on the collection members (not supported by TMDbApi until now)
            }
        } catch (MovieDbException ex) {
            LOG.error("Failed retrieving collection for boxed set: {}", boxedSet.getName());
            LOG.warn(API_ERROR, ex);
        }
        
        return null; //NOSONAR
    }

    /**
     * Get a list of the artwork for a movie.
     *
     * This will get all the artwork for a specified language and the blank
     * languages as well
     *
     * @param tmdbId
     * @param language
     * @param metaDataType
     * @param artworkType
     * @param artworkSize
     * @return
     */
    private List<ArtworkDTO> getFilteredArtwork(String tmdbId, String language, MetaDataType metaDataType, ArtworkType artworkType, String artworkSize) {
        return this.getFilteredArtwork(tmdbId, -1, -1, language, metaDataType, artworkType, artworkSize);
    }

    /**
     * Get a list of the artwork for a movie.
     *
     * This will get all the artwork for a specified language and the blank
     * languages as well
     * 
     * @param tmdbId
     * @param season
     * @param episode
     * @param language
     * @param metaDataType
     * @param artworkType
     * @param artworkSize
     * @return
     */
    private List<ArtworkDTO> getFilteredArtwork(String tmdbId, int season, int episode, String language, MetaDataType metaDataType, ArtworkType artworkType, String artworkSize) {
        if (StringUtils.isNumeric(tmdbId)) {
            return this.getFilteredArtwork(Integer.parseInt(tmdbId), season, episode, language, metaDataType, artworkType, artworkSize);
        }
        return Collections.emptyList();
    }

    /**
     * Get a list of the artwork for a movie.
     *
     * This will get all the artwork for a specified language and the blank
     * languages as well
     *
     * @param tmdbId
     * @param season
     * @param episode
     * @param language
     * @param metaDataType
     * @param artworkType
     * @param artworkSize
     * @return
     */
    private List<ArtworkDTO> getFilteredArtwork(int tmdbId, String language, MetaDataType metaDataType, ArtworkType artworkType, String artworkSize) {
        return this.getFilteredArtwork(tmdbId, -1, -1, language, metaDataType, artworkType, artworkSize);
    }
    
    /**
     * Get a list of the artwork for a movie.
     *
     * This will get all the artwork for a specified language and the blank
     * languages as well
     *
     * @param tmdbId
     * @param season
     * @param episode
     * @param language
     * @param metaDataType
     * @param artworkType
     * @param artworkSize
     * @return
     */
    private List<ArtworkDTO> getFilteredArtwork(int tmdbId, int season, int episode, String language, MetaDataType metaDataType, ArtworkType artworkType, String artworkSize) {
        List<ArtworkDTO> dtos = new ArrayList<>();
        try {
            ResultList<Artwork> results = getArtworksFromTMDb(tmdbId, season, episode, metaDataType);
            
            if (results == null || results.isEmpty()) {
                LOG.debug("Got no {} artworks from TMDb for id {}", artworkType, tmdbId);
            } else {
                List<Artwork> artworkList = results.getResults();
                LOG.debug("Got {} {} artworks from TMDb for id {}", artworkList.size(), artworkType, tmdbId);
                
                for (Artwork artwork : artworkList) {
                    if (artwork.getArtworkType() == artworkType
                        && (StringUtils.isBlank(artwork.getLanguage()) // no language
                            || "xx".equalsIgnoreCase(artwork.getLanguage()) // another marker for no language
                            || artwork.getLanguage().equalsIgnoreCase(language))) // defined language
                    {
                        this.addArtworkDTO(dtos, artwork, artworkType, artworkSize);
                    }
                }
                
                if (dtos.isEmpty() && !LANGUAGE_EN.equalsIgnoreCase(language)) {
                    // retrieve by English
                    for (Artwork artwork : artworkList) {
                        if (artwork.getArtworkType() == artworkType && StringUtils.equalsIgnoreCase(artwork.getLanguage(), LANGUAGE_EN)) {
                            this.addArtworkDTO(dtos, artwork, artworkType, artworkSize);
                        }
                    }
                }
                
                LOG.debug("Found {} {} artworks for TMDb id {} and language '{}'", dtos.size(), artworkType, tmdbId, language);
            }
        } catch (MovieDbException ex) {
            LOG.error("Failed retrieving {} artworks for movie id {}: {}", artworkType, tmdbId, ex.getMessage());
            if (!ExceptionTools.is404(ex)) {
                LOG.warn(API_ERROR, ex);
            }
        }
        
        return dtos;
    }
    
    private void addArtworkDTO(List<ArtworkDTO> dtos, Artwork artwork, ArtworkType artworkType, String artworkSize) throws MovieDbException {
        URL artworkURL = tmdbApi.createImageUrl(artwork.getFilePath(), artworkSize);
        if (artworkURL == null || artworkURL.toString().endsWith("null")) {
            LOG.warn("{} URL is invalid and will not be used: {}", artworkType, artworkURL);
        } else {
            String url = artworkURL.toString();
            dtos.add(new ArtworkDTO(getScannerName(), url, ArtworkTools.getPartialHashCode(url)));
        }
    }

    private ResultList<Artwork> getArtworksFromTMDb(int tmdbId, int season, int episode, MetaDataType metaDataType) throws MovieDbException {
        ResultList<Artwork> results;
        if (MetaDataType.PERSON == metaDataType) {
            String cacheKey = "person###"+tmdbId;
            results = tmdbArtworkCache.get(cacheKey, ResultList.class);
            if (results == null || results.isEmpty()) {
                results = tmdbApi.getPersonImages(tmdbId);
            }
            tmdbArtworkCache.put(cacheKey, results);
        } else if (MetaDataType.BOXSET == metaDataType) {
            String cacheKey = "boxset###"+tmdbId;
            results = tmdbArtworkCache.get(cacheKey, ResultList.class);
            if (results == null || results.isEmpty()) {
                // use an empty language to get all artwork and then filter it
                results = tmdbApi.getCollectionImages(tmdbId, NO_LANGUAGE);
            }
            tmdbArtworkCache.put(cacheKey, results);
        } else if (MetaDataType.SERIES == metaDataType) {
            String cacheKey = "series###"+tmdbId;
            results = tmdbArtworkCache.get(cacheKey, ResultList.class);
            if (results == null || results.isEmpty()) {
                // use an empty language to get all artwork and then filter it
                results = tmdbApi.getTVImages(tmdbId, NO_LANGUAGE);
            }
            tmdbArtworkCache.put(cacheKey, results);
        } else if (MetaDataType.SEASON == metaDataType) {
            String cacheKey = "season###"+season+"###"+tmdbId;
            results = tmdbArtworkCache.get(cacheKey, ResultList.class);
            if (results == null || results.isEmpty()) {
                // use an empty language to get all artwork and then filter it
                results = tmdbApi.getSeasonImages(tmdbId, season, NO_LANGUAGE);
            }
            tmdbArtworkCache.put(cacheKey, results);
        } else if (MetaDataType.EPISODE == metaDataType) {
            String cacheKey = "episode###"+season+"###"+episode+"###"+tmdbId;
            results = tmdbArtworkCache.get(cacheKey, ResultList.class);
            if (results == null || results.isEmpty()) {
                // use an empty language to get all artwork and then filter it
                results = tmdbApi.getEpisodeImages(tmdbId, season, episode);
            }
            tmdbArtworkCache.put(cacheKey, results);
        } else {
            String cacheKey = "movie###"+tmdbId;
            results = tmdbArtworkCache.get(cacheKey, ResultList.class);
            if (results == null || results.isEmpty()) {
                // use an empty language to get all artwork and then filter it
                results = tmdbApi.getMovieImages(tmdbId, NO_LANGUAGE);
            }
            tmdbArtworkCache.put(cacheKey, results);
        }
        return results;
    }
}
