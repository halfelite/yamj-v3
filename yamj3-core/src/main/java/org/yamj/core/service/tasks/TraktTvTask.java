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
package org.yamj.core.service.tasks;

import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.yamj.core.service.trakttv.TraktTvService;

/**
 * Task for periodical synchronization with Trakt.TV
 */
@Component
@DependsOn("traktTvService")
public class TraktTvTask implements ITask {

    private static final Logger LOG = LoggerFactory.getLogger(TraktTvTask.class);

    @Autowired
    private ExecutionTaskService executionTaskService;
    @Autowired
    private TraktTvService traktTvService;
    
    @Value("${trakttv.collection.enabled:false}")
    private boolean collectionEnabled;
    @Value("${trakttv.push.enabled:false}")
    private boolean pushEnabled;
    @Value("${trakttv.pull.enabled:false}")
    private boolean pullEnabled;
    
    @Override
    public String getTaskName() {
        return "trakttv";
    }

    @PostConstruct
    public void init() {
        executionTaskService.registerTask(this);
    }

    @Override
    public void execute(String options) {
        if (!collectionEnabled && !pushEnabled && !pullEnabled) {
            // nothing to do
            return;
        }

        LOG.debug("Execute Trakt.TV task");
        final long startTime = System.currentTimeMillis();

        if (traktTvService.isExpired() && !traktTvService.refreshWhenExpired()) {
            // refresh failed, to nothing can be done now
            // --> new authorization should be done
            return;
        }

        // 1. collect
        if (collectionEnabled) {
            traktTvService.collectMovies();
            traktTvService.collectEpisodes();
        } else {
            LOG.debug("Trakt.TV collection is not enabled");
        }
        
        // 2. pull
        boolean pulledMovies = false;
        boolean pulledEpisodes = false;
        if (pullEnabled) {
            pulledMovies = traktTvService.pullWatchedMovies();
            pulledEpisodes = traktTvService.pullWatchedEpisodes();
        } else {
            LOG.debug("Trakt.TV pull is not enabled");
        }

        // 2. push after pull
        if (!pushEnabled) {
            LOG.debug("Trakt.TV push is not enabled");
        } else if (!pullEnabled) {
            LOG.debug("Trakt.TV push only available if pull is enabled");
        } else {
            if (pulledMovies) {
                // push movies after pull
                traktTvService.pushWatchedMovies();
            } else {
                LOG.warn("No push of watched movies when pull not successful");
            }
            
            if (pulledEpisodes) {
                // push episodes after pull
                traktTvService.pushWatchedEpisodes();
            } else {
                LOG.warn("No push of watched episodes when pull not successful");
            }
        }

        LOG.debug("Finished Trakt.TV task after {} ms", System.currentTimeMillis()-startTime);
    }
}
