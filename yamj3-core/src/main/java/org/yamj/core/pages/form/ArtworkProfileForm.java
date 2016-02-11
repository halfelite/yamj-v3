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
package org.yamj.core.pages.form;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.yamj.core.database.model.type.ArtworkType;
import org.yamj.core.database.model.type.ScalingType;

public class ArtworkProfileForm {

    private long id;
    private String profileName;
    private ArtworkType artworkType;
    private String width;
    private String height;
    private boolean applyToMovie = false;
    private boolean applyToSeries = false;
    private boolean applyToSeason = false;
    private boolean applyToBoxedSet = false;
    private ScalingType scalingType;
    private boolean reflection = false;
    private boolean roundedCorners = false;

    public long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public ArtworkType getArtworkType() {
        return artworkType;
    }

    public void setArtworkType(ArtworkType artworkType) {
        this.artworkType = artworkType;
    }

    public String getWidth() {
        return width;
    }

    public void setWidth(String width) {
        this.width = width;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public boolean isApplyToMovie() {
        return applyToMovie;
    }

    public void setApplyToMovie(boolean applyToMovie) {
        this.applyToMovie = applyToMovie;
    }

    public boolean isApplyToSeries() {
        return applyToSeries;
    }

    public void setApplyToSeries(boolean applyToSeries) {
        this.applyToSeries = applyToSeries;
    }

    public boolean isApplyToSeason() {
        return applyToSeason;
    }

    public void setApplyToSeason(boolean applyToSeason) {
        this.applyToSeason = applyToSeason;
    }

    public boolean isApplyToBoxedSet() {
        return applyToBoxedSet;
    }

    public void setApplyToBoxedSet(boolean applyToBoxedSet) {
        this.applyToBoxedSet = applyToBoxedSet;
    }

    public ScalingType getScalingType() {
        return scalingType;
    }

    public void setScalingType(ScalingType scalingType) {
        this.scalingType = scalingType;
    }

    public boolean isReflection() {
        return reflection;
    }

    public void setReflection(boolean reflection) {
        this.reflection = reflection;
    }

    public boolean isRoundedCorners() {
        return roundedCorners;
    }

    public void setRoundedCorners(boolean roundedCorners) {
        this.roundedCorners = roundedCorners;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
