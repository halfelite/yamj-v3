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

import com.omertron.imdbapi.ImdbApi;
import com.omertron.imdbapi.model.ImdbPerson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yamj.api.common.http.DigestedResponse;
import org.yamj.core.database.model.Person;
import org.yamj.core.database.model.VideoData;
import org.yamj.core.service.artwork.ArtworkDetailDTO;
import org.yamj.core.service.artwork.ArtworkScannerService;
import org.yamj.core.service.artwork.ArtworkTools;
import org.yamj.core.service.metadata.online.ImdbScanner;
import org.yamj.core.web.PoolingHttpClient;
import org.yamj.core.web.ResponseTools;

@Service("imdbArtworkScanner")
public class ImdbArtworkScanner implements IMoviePosterScanner, IPhotoScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ImdbArtworkScanner.class);

    @Autowired
    private ArtworkScannerService artworkScannerService;
    @Autowired
    private ImdbScanner imdbScanner;
    @Autowired
    private ImdbApi imdbApi;
    @Autowired
    private PoolingHttpClient httpClient;

    @Override
    public String getScannerName() {
        return ImdbScanner.SCANNER_ID;
    }

    @PostConstruct
    public void init() {
        LOG.info("Initialize IMDb artwork scanner");

        // register this scanner
        artworkScannerService.registerArtworkScanner(this);
    }

    @Override
    public List<ArtworkDetailDTO> getPosters(VideoData videoData) {
        String imdbId = imdbScanner.getMovieId(videoData);
        if (StringUtils.isBlank(imdbId)) {
            return null;
        }
        
        List<ArtworkDetailDTO> dtos = new ArrayList<>();
        try {
            DigestedResponse response = this.httpClient.requestContent("http://www.imdb.com/title/" + imdbId);
            if (ResponseTools.isOK(response)) {
                String metaImageString = "<meta property='og:image' content=\"";
                int beginIndex = response.getContent().indexOf(metaImageString);
                if (beginIndex > 0) {
                    beginIndex = beginIndex + metaImageString.length();
                    int endIndex =  response.getContent().indexOf("\"", beginIndex);
                    if (endIndex > 0) {
                        String url = response.getContent().substring(beginIndex, endIndex);
                        dtos.add(new ArtworkDetailDTO(getScannerName(), url, ArtworkTools.getPartialHashCode(url)));
                    }
                }
            } else {
                LOG.warn("Requesting IMDb poster for '{}' failed with status {}", imdbId, response.getStatusCode());
            }
        } catch (Exception ex) {
            LOG.error("Failed retrieving poster URL from IMDb images for id {}: {}", imdbId, ex.getMessage());
            LOG.trace("IMDb service error", ex);
        }
        return dtos;
    }

    @Override
    public List<ArtworkDetailDTO> getPhotos(Person person) {
        String imdbId = imdbScanner.getPersonId(person);
        if (StringUtils.isBlank(imdbId)) {
            return null;
        }
        
        ImdbPerson imdbPerson = imdbApi.getActorDetails(imdbId);
        if (imdbPerson == null || imdbPerson.getImage() == null) {
            return null;
        }
        
        ArtworkDetailDTO dto = new ArtworkDetailDTO(getScannerName(), imdbPerson.getImage().getUrl(), imdbId);
        return Collections.singletonList(dto);
    }
}
