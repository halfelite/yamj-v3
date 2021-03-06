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
package org.yamj.core.api.json;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.yamj.common.type.MetaDataType;
import org.yamj.common.type.StatusType;
import org.yamj.core.api.model.ApiStatus;
import org.yamj.core.api.model.dto.*;
import org.yamj.core.api.options.*;
import org.yamj.core.api.wrapper.ApiWrapperList;
import org.yamj.core.api.wrapper.ApiWrapperSingle;
import org.yamj.core.database.model.Genre;
import org.yamj.core.database.model.Studio;
import org.yamj.core.database.service.JsonApiStorageService;
import org.yamj.core.scheduling.*;

@RestController
@RequestMapping(value = "/api", produces = "application/json; charset=utf-8")
public class CommonController {

    private static final Logger LOG = LoggerFactory.getLogger(CommonController.class);
    private static final String INVALID_META_DATA_TYPE = "Invalid meta data type '";
    
    @Autowired
    private JsonApiStorageService jsonApiStorageService;
    @Autowired 
    private MetadataScanScheduler metadataScanScheduler;
    @Autowired 
    private ArtworkScanScheduler artworkScanScheduler;
    @Autowired
    private TrailerScanScheduler trailerScanScheduler;
    @Autowired
    private TrailerProcessScheduler trailerProcessScheduler;

    //<editor-fold defaultstate="collapsed" desc="Alphabetical Methods">
    @RequestMapping(value = "/alphabetical/list", method = RequestMethod.GET)
    public ApiWrapperList<ApiNameDTO> getAlphabeticals(@ModelAttribute("options") OptionsMultiType options) {
        LOG.debug("Getting alphabetical list - Options: {}", options);
        return new ApiWrapperList<ApiNameDTO>(options).setResults(jsonApiStorageService.getAlphabeticals(options));
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Watched Methods">
    @RequestMapping(value = "/watched/{type}/{id}", method = {RequestMethod.GET, RequestMethod.PUT})
    public ApiStatus markWatched(@PathVariable("type") String type, @PathVariable("id") Long id) {
        if (id <= 0L) {
            return ApiStatus.INVALID_ID;
        }

        final MetaDataType metaDataType = MetaDataType.fromString(type);
        if (!metaDataType.isWithVideos()) {
            return ApiStatus.badRequest(INVALID_META_DATA_TYPE + type + "' for watching videos");
        }

        return jsonApiStorageService.updateWatchedSingle(metaDataType, id, true);
    }

    @RequestMapping(value = "/unwatched/{type}/{id}", method = {RequestMethod.GET, RequestMethod.PUT})
    public ApiStatus markUnwatched(@PathVariable("type") String type, @PathVariable("id") Long id) {
        if (id <= 0L) {
            return ApiStatus.INVALID_ID;
        }
        
        final MetaDataType metaDataType = MetaDataType.fromString(type);
        if (!metaDataType.isWithVideos()) {
            return ApiStatus.badRequest(INVALID_META_DATA_TYPE + type + "' for unwatching videos");
        }

        return jsonApiStorageService.updateWatchedSingle(metaDataType, id, false);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Rescan Methods">
    @RequestMapping(value = "/rescan/{type}/{id}", method = {RequestMethod.GET, RequestMethod.PUT})
    public ApiStatus rescanMetaData(@PathVariable("type") String type, @PathVariable("id") Long id) {
        if (id <= 0L) {
            return ApiStatus.INVALID_ID;
        }

        final MetaDataType metaDataType = MetaDataType.fromString(type);
        if (!metaDataType.isRescanMetaData()) {
            return ApiStatus.badRequest(INVALID_META_DATA_TYPE + type + "' for rescan");
        }

        ApiStatus apiStatus = jsonApiStorageService.rescanMetaData(metaDataType, id);
        if (apiStatus.isSuccessful()) {
            this.metadataScanScheduler.trigger();
        }
        return apiStatus;
    }

    @RequestMapping(value = "/rescan/{type}/artwork/{id}", method = {RequestMethod.GET, RequestMethod.PUT})
    public ApiStatus rescanArtwork(@PathVariable("type") String type, @PathVariable("id") Long id) {
        if (id <= 0L) {
            return ApiStatus.INVALID_ID;
        }

        final MetaDataType metaDataType = MetaDataType.fromString(type);
        if (!metaDataType.isWithArtwork()) {
            return ApiStatus.badRequest(INVALID_META_DATA_TYPE + type + "' for artwork rescan");
        }

        ApiStatus apiStatus = jsonApiStorageService.rescanArtwork(metaDataType, id);
        if (apiStatus.isSuccessful()) {
            this.artworkScanScheduler.trigger();
        }
        return apiStatus;
    }

    @RequestMapping(value = "/rescan/{type}/trailer/{id}", method = {RequestMethod.GET, RequestMethod.PUT})
    public ApiStatus rescanTrailer(@PathVariable("type") String type, @PathVariable("id") Long id) {

        final MetaDataType metaDataType = MetaDataType.fromString(type);
        if (!metaDataType.isWithTrailer()) {
            return ApiStatus.badRequest(INVALID_META_DATA_TYPE + type + "' for trailer rescan");
        }

        ApiStatus apiStatus = jsonApiStorageService.rescanTrailer(metaDataType, id);
        if (apiStatus.isSuccessful()) {
            this.trailerScanScheduler.trigger();
        }
        return apiStatus;
    }

    @RequestMapping(value = "/rescan/all", method = {RequestMethod.GET, RequestMethod.PUT})
    public ApiStatus rescanAll() {
        ApiStatus apiStatus = jsonApiStorageService.rescanAll();
        if (apiStatus.isSuccessful()) {
            this.metadataScanScheduler.trigger();
        }
        return apiStatus;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Trailer Methods">
    @RequestMapping(value = "/trailer/delete/{id}", method = {RequestMethod.GET, RequestMethod.PUT})
    public ApiStatus trailerDelete(@PathVariable("id") Long id) {
        if (id <= 0L) {
            return ApiStatus.INVALID_ID;
        }

        return jsonApiStorageService.setTrailerStatus(id, StatusType.DELETED);
    }

    @RequestMapping(value = "/trailer/ignore/{id}", method = {RequestMethod.GET, RequestMethod.PUT})
    public ApiStatus trailerIgnore( @PathVariable("id") Long id) {
        if (id <= 0L) {
            return ApiStatus.INVALID_ID;
        }

        return jsonApiStorageService.setTrailerStatus(id, StatusType.IGNORE);
    }

    @RequestMapping(value = "/trailer/download/{id}", method = {RequestMethod.GET, RequestMethod.PUT})
    public ApiStatus trailerDownload( @PathVariable("id") Long id) {
        if (id <= 0L) {
            return ApiStatus.INVALID_ID;
        }

        ApiStatus apiStatus = jsonApiStorageService.setTrailerStatus(id, StatusType.UPDATED);
        if (apiStatus.isSuccessful()) {
            trailerProcessScheduler.trigger();
        }
        return apiStatus;
    }

    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Genre Methods">
    @RequestMapping(value = "/genre", method = RequestMethod.GET)
    public ApiWrapperList<ApiGenreDTO> getGenreFilename(@RequestParam(required = true, defaultValue = "") String filename) {
        LOG.debug("Getting genres for filename '{}'", filename);
        
        ApiWrapperList<ApiGenreDTO> wrapper = new ApiWrapperList<>();
        return wrapper.setResults(jsonApiStorageService.getGenreFilename(filename));
    }

    @RequestMapping(value = "/genre/{name}", method = RequestMethod.GET)
    public ApiWrapperSingle<ApiGenreDTO> getGenre(@PathVariable("name") String name) {
        ApiWrapperSingle<ApiGenreDTO> wrapper = new ApiWrapperSingle<>();

        Genre genre;
        if (StringUtils.isNumeric(name)) {
            LOG.debug("Getting genre with ID {}", name);
            genre = jsonApiStorageService.getGenre(Long.parseLong(name));
        } else {
            LOG.debug("Getting genre with name '{}'", name);
            genre = jsonApiStorageService.getGenre(name);
        }


        ApiGenreDTO result = null;
        if (genre != null) {
            result = new ApiGenreDTO();
            result.setId(genre.getId());
            result.setName(genre.getName());
            if (genre.getTargetApi() != null) {
                result.setTarget(genre.getTargetApi());
            } else if (genre.getTargetXml() != null) {
                result.setTarget(genre.getTargetXml());
            } else {
                result.setTarget(genre.getName());
            }
        }
        
        return wrapper.setResult(result);
    }

    @RequestMapping(value = "/genres/list", method = RequestMethod.GET)
    public ApiWrapperList<ApiGenreDTO> getGenres(@ModelAttribute("options") OptionsSingleType options) {
        LOG.debug("Getting genre list: used={}, full={}", options.getUsed(), options.getFull());

        ApiWrapperList<ApiGenreDTO> wrapper = new ApiWrapperList<>(options);
        return wrapper.setResults(jsonApiStorageService.getGenres(wrapper));
    }

    @RequestMapping(value = "/genres/add", method = {RequestMethod.GET,RequestMethod.POST})
    public ApiStatus genreAdd(
            @RequestParam(required = true, defaultValue = "") String name,
            @RequestParam(required = true, defaultValue = "") String target) {

        if (StringUtils.isBlank(name) || StringUtils.isBlank(target)) {
            return ApiStatus.badRequest("Invalid name/target specified, genre not added");
        }

        LOG.info("Adding genre '{}' with target '{}'", name, target);

        ApiStatus status;
        if (this.jsonApiStorageService.addGenre(name, target)) {
            status = ApiStatus.ok("Successfully added genre '" + name + "' with target '" + target + "'");
        } else {
            status = ApiStatus.conflict("Genre '" + name + "' already exists");
        }
        return status;
    }

    @RequestMapping(value = "/genres/update", method = {RequestMethod.GET,RequestMethod.PUT})
    public ApiStatus genreUpdate(
            @RequestParam(required = true, defaultValue = "") String name,
            @RequestParam(required = false, defaultValue = "") String target) {

        if (StringUtils.isBlank(name)) {
            return ApiStatus.badRequest("Invalid name specified, genre not updated");
        }
        
        LOG.info("Updating genre '{}' with target '{}'", name, target);

        boolean result;
        if (StringUtils.isNumeric(name)) {
            result = this.jsonApiStorageService.updateGenre(Long.valueOf(name), target);
        } else {
            result = this.jsonApiStorageService.updateGenre(name, target);
        }

        ApiStatus status;
        if (result) {
            status = ApiStatus.ok("Successfully updated genre '" + name + "' with target '" + target + "'");
        } else {
            status = ApiStatus.notFound("Genre '" + name + "' does not exist");
        }
        return status;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Studio Methods">
    @RequestMapping(value = "/studio/{name}", method = RequestMethod.GET)
    public ApiWrapperSingle<Studio> getStudio(@PathVariable("name") String name) {
        ApiWrapperSingle<Studio> wrapper = new ApiWrapperSingle<>();

        Studio studio;
        if (StringUtils.isNumeric(name)) {
            LOG.debug("Getting studio with ID {}", name);
            studio = jsonApiStorageService.getStudio(Long.parseLong(name));
        } else {
            LOG.debug("Getting studio '{}'", name);
            studio = jsonApiStorageService.getStudio(name);
        }
        
        return wrapper.setResult(studio);
    }

    @RequestMapping(value = "/studios/list", method = RequestMethod.GET)
    public ApiWrapperList<Studio> getStudios(@ModelAttribute("options") OptionsSingleType options) {
        LOG.debug("Getting studio list - Options: {}", options);

        ApiWrapperList<Studio> wrapper = new ApiWrapperList<>(options);
        return wrapper.setResults(jsonApiStorageService.getStudios(wrapper));
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Country Methods">
    @RequestMapping(value = "/country", method = RequestMethod.GET)
    public ApiWrapperList<ApiCountryDTO> getCountryFilename(
        @RequestParam(required = true, defaultValue = "") String filename,
        @RequestParam(required = false) String language)
   {
        LOG.debug("Getting countries for filename '{}'", filename);

        ApiWrapperList<ApiCountryDTO> wrapper = new ApiWrapperList<>();
        return wrapper.setResults(jsonApiStorageService.getCountryFilename(filename, language));
    }

    @RequestMapping(value = "/country/{countryCode}", method = RequestMethod.GET)
    public ApiWrapperSingle<ApiCountryDTO> getCountry(
        @PathVariable String countryCode,
        @RequestParam(required = false) String language)
    {
        ApiCountryDTO country;
        if (StringUtils.isNumeric(countryCode)) {
            LOG.info("Getting country with ID {}", countryCode);
            country = jsonApiStorageService.getCountry(Long.parseLong(countryCode), language);
        } else {
            LOG.info("Getting country with country code {}", countryCode);
            country = jsonApiStorageService.getCountry(countryCode, language);
        }

        ApiWrapperSingle<ApiCountryDTO> wrapper = new ApiWrapperSingle<>();
        return wrapper.setResult(country);
   }

    @RequestMapping(value = "/countries/list", method = RequestMethod.GET)
    public ApiWrapperList<ApiCountryDTO> getCountries(@ModelAttribute("options") OptionsSingleType options) {
        LOG.debug("Getting country list - Options: {}", options);

        ApiWrapperList<ApiCountryDTO> wrapper = new ApiWrapperList<>(options);
        return wrapper.setResults(jsonApiStorageService.getCountries(wrapper));
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Award Methods">
    @RequestMapping(value = "/awards/list", method = RequestMethod.GET)
    public ApiWrapperList<ApiAwardDTO> getAwards(@ModelAttribute("options") OptionsSingleType options) {
        LOG.debug("Getting award list - Options: {}", options);

        ApiWrapperList<ApiAwardDTO> wrapper = new ApiWrapperList<>(options);
        return wrapper.setResults(jsonApiStorageService.getAwards(wrapper));
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Certification Methods">
    @RequestMapping(value ="/certifications/list", method = RequestMethod.GET)
    public ApiWrapperList<ApiCertificationDTO> getCertifications(@ModelAttribute("options") OptionsSingleType options) {
        LOG.debug("Getting certifications list - Options: {}", options);

        ApiWrapperList<ApiCertificationDTO> wrapper = new ApiWrapperList<>(options);
        return wrapper.setResults(jsonApiStorageService.getCertifications(wrapper));
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="VideoSource Methods">
    @RequestMapping(value = "/videosources/list", method = RequestMethod.GET)
    public ApiWrapperList<ApiNameDTO> getVideoSources(@ModelAttribute("options") OptionsSingleType options) {
        LOG.debug("Getting video sources list - Options: {}", options);

        ApiWrapperList<ApiNameDTO> wrapper = new ApiWrapperList<>(options);
        return wrapper.setResults(jsonApiStorageService.getVideoSources(wrapper));
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Rating Methods">
    @RequestMapping(value = "/ratings/list", method = RequestMethod.GET)
    public ApiWrapperList<ApiRatingDTO> getRatings(@ModelAttribute("options") OptionsRating options) {
        LOG.debug("Getting ratings list - Options: {}", options);

        ApiWrapperList<ApiRatingDTO> wrapper = new ApiWrapperList<>(options);
        return wrapper.setResults(jsonApiStorageService.getRatings(wrapper));
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Boxed-Set Methods">
    @RequestMapping(value = "/boxset/list", method = RequestMethod.GET)
    public ApiWrapperList<ApiBoxedSetDTO> getBoxSets(@ModelAttribute("options") OptionsBoxedSet options) {
        LOG.debug("Getting boxset list - Options: {}", options);

        ApiWrapperList<ApiBoxedSetDTO> wrapper = new ApiWrapperList<>(options);
        return wrapper.setResults(jsonApiStorageService.getBoxedSets(wrapper));
    }

    @RequestMapping(value = "/boxset/{id}", method = RequestMethod.GET)
    public ApiWrapperSingle<ApiBoxedSetDTO> getBoxSet(@ModelAttribute("options") OptionsBoxedSet options) {
        LOG.debug("Getting boxset - Options: {}", options);

        ApiWrapperSingle<ApiBoxedSetDTO> wrapper = new ApiWrapperSingle<>(options);
        return wrapper.setResult(jsonApiStorageService.getBoxedSet(wrapper));
    }
    //</editor-fold>
}
