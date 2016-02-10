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
package org.yamj.core.database.model;

import java.io.Serializable;
import javax.persistence.*;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Type;
import org.yamj.common.type.StatusType;

@Entity
@Table(name = "artwork_generated",
    uniqueConstraints = @UniqueConstraint(name = "UIX_ARTWORK_GENERATED", columnNames = {"located_id", "profile_id"})
)
public class ArtworkGenerated extends AbstractAuditable implements Serializable {

    private static final long serialVersionUID = 2326614430648326340L;

    @ManyToOne(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "located_id", foreignKey = @ForeignKey(name = "FK_ARTWORKGENERATED_LOCATED"))
    private ArtworkLocated artworkLocated;

    @ManyToOne(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "profile_id", foreignKey = @ForeignKey(name = "FK_ARTWORKGENERATED_PROFILE"))
    private ArtworkProfile artworkProfile;

    @Column(name = "cache_filename", nullable = false, length = 500)
    private String cacheFilename;

    @Column(name = "cache_dir", nullable = false, length = 50)
    private String cacheDirectory;

    @Type(type = "statusType")
    @Column(name = "status", length = 30) // TODO implement AbstractStateful if done
    private StatusType status;
    
    // GETTER and SETTER

    public String getCacheDirectory() {
        return cacheDirectory;
    }

    public void setCacheDirectory(String cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    public String getCacheFilename() {
        return cacheFilename;
    }

    public void setCacheFilename(String cacheFilename) {
        this.cacheFilename = cacheFilename;
    }

    public ArtworkLocated getArtworkLocated() {
        return artworkLocated;
    }

    public void setArtworkLocated(ArtworkLocated artworkLocated) {
        this.artworkLocated = artworkLocated;
    }

    public ArtworkProfile getArtworkProfile() {
        return artworkProfile;
    }

    public void setArtworkProfile(ArtworkProfile artworkProfile) {
        this.artworkProfile = artworkProfile;
    }

    public StatusType getStatus() {
        return status;
    }
    
    public void setStatus(StatusType status) {
        this.status = status;
    }

    // TRANSIENT METHODS
    
    public boolean isCached() {
        return !isNotCached();
    }

    public boolean isNotCached() {
        return StringUtils.isBlank(getCacheFilename()) || StringUtils.isBlank(getCacheDirectory());
    }
}
