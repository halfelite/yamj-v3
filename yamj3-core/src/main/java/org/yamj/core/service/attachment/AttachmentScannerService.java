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
package org.yamj.core.service.attachment;

import static org.yamj.plugin.api.Constants.LANGUAGE_EN;

import java.io.*;
import java.util.*;
import javax.annotation.PostConstruct;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Service;
import org.yamj.common.tools.PropertyTools;
import org.yamj.core.config.ConfigServiceWrapper;
import org.yamj.core.database.model.Artwork;
import org.yamj.core.database.model.StageFile;
import org.yamj.core.service.file.FileTools;
import org.yamj.core.service.various.StagingService;
import org.yamj.plugin.api.model.type.ArtworkType;
import org.yamj.plugin.api.model.type.ImageType;

/**
 * Scans and extracts attachments within a file i.e. matroska files.
 */
@Service("attachmentScannerService")
public class AttachmentScannerService {

    private static final Logger LOG = LoggerFactory.getLogger(AttachmentScannerService.class);
    
    // mkvToolnix command line, depend on OS
    private static final File MT_PATH = new File(PropertyTools.getProperty("mkvtoolnix.home", "./mkvToolnix/"));
    private static final List<String> MT_INFO_EXE = new ArrayList<>();
    private static final List<String> MT_EXTRACT_EXE = new ArrayList<>();
    private static final String MT_INFO_FILENAME_WINDOWS = "mkvinfo.exe";
    private static final String MT_INFO_FILENAME_LINUX = "mkvinfo";
    private static final String MT_EXTRACT_FILENAME_WINDOWS = "mkvextract.exe";
    private static final String MT_EXTRACT_FILENAME_LINUX = "mkvextract";
    // flag to indicate if scanner is activated
    private boolean isActivated = false;
    // valid MIME types
    private Set<String> validMimeTypesText;
    private Map<String, ImageType> validMimeTypesImage;

    @Autowired
    private Cache attachmentCache;
    @Autowired
    private StagingService stagingService;
    @Autowired
    private ConfigServiceWrapper configServiceWrapper;
   
    @PostConstruct
    public void init() {
        LOG.debug("Initialize attachment scanner service");

        boolean isWindows = System.getProperty("os.name").contains("Windows");
        LOG.debug("MKV Toolnix Path : {}", MT_PATH);

        File mkvInfoFile;
        File mkvExtractFile;
        if (isWindows) {
            mkvInfoFile = new File(MT_PATH.getAbsolutePath() + File.separator + MT_INFO_FILENAME_WINDOWS);
            mkvExtractFile = new File(MT_PATH.getAbsolutePath() + File.separator + MT_EXTRACT_FILENAME_WINDOWS);
        } else {
            mkvInfoFile = new File(MT_PATH.getAbsolutePath() + File.separator + MT_INFO_FILENAME_LINUX);
            mkvExtractFile = new File(MT_PATH.getAbsolutePath() + File.separator + MT_EXTRACT_FILENAME_LINUX);
        }
        
        if (!mkvInfoFile.canExecute()) {
            LOG.info( "Couldn't find MKV toolnix executable tool 'mkvinfo'");
            isActivated = false;
        } else if (!mkvExtractFile.canExecute()) {
            LOG.info( "Couldn't find MKV toolnix executable tool 'mkvextract'");
            isActivated = false;
        } else {
            isActivated = true;
            
            // activate tools
            if (isWindows) {
                MT_INFO_EXE.clear();
                MT_INFO_EXE.add("cmd.exe");
                MT_INFO_EXE.add("/E:1900");
                MT_INFO_EXE.add("/C");
                MT_INFO_EXE.add(mkvInfoFile.getName());
                MT_INFO_EXE.add("--ui-language");
                MT_INFO_EXE.add(LANGUAGE_EN);
                
                MT_EXTRACT_EXE.clear();
                MT_EXTRACT_EXE.add("cmd.exe");
                MT_EXTRACT_EXE.add("/E:1900");
                MT_EXTRACT_EXE.add("/C");
                MT_EXTRACT_EXE.add(mkvExtractFile.getName());
            } else {
                MT_INFO_EXE.clear();
                MT_INFO_EXE.add("./" + mkvInfoFile.getName());
                MT_INFO_EXE.add("--ui-language");
                MT_INFO_EXE.add("en_US");
    
                MT_EXTRACT_EXE.clear();
                MT_EXTRACT_EXE.add("./" + mkvExtractFile.getName());
            }
            
            // add valid mime types (text)
            validMimeTypesText = new HashSet<>(3);
            validMimeTypesText.add("text/xml");
            validMimeTypesText.add("application/xml");
            validMimeTypesText.add("text/html");

            // add valid mime types (image)
            validMimeTypesImage = new HashMap<>(4);
            validMimeTypesImage.put("image/jpeg", ImageType.JPG);
            validMimeTypesImage.put("image/png", ImageType.PNG);
            validMimeTypesImage.put("image/gif", ImageType.GIF);
            validMimeTypesImage.put("image/x-ms-bmp", ImageType.BMP);
        }
    }

    /**
     * Checks if a file is scanable for attachments. Therefore the file must exist and the extension must be equal to MKV.
     *
     * @param stageFile the file to scan
     * @return true, if file is scanable, else false
     */
    private static boolean isFileScanable(StageFile stageFile) {
        if (!"mkv".equalsIgnoreCase(stageFile.getExtension())) {
            // no MATROSKA file
            return false;
        }
        return FileTools.isFileReadable(stageFile);
    }

    /**
     * Scan artwork attachments
     *
     * @param movie
     */
    public List<Attachment> scan(Artwork artwork) {
        if (!isActivated) {
            return Collections.emptyList();
        }

        if (artwork.getPerson() != null || artwork.getBoxedSet() != null) {
            // no attachments for persons or boxed sets
            return Collections.emptyList();
        }
        
        // find video stage files
        List<StageFile> stageFiles = stagingService.findVideoStageFiles(artwork);
        
        // create attachments
        List<Attachment> artworkAttachments = new ArrayList<>(stageFiles.size());
        for (StageFile stageFile : stageFiles) {
            artworkAttachments.addAll(scanAttachments(stageFile));
        }
        
        // filter attachments
        Iterator<Attachment> iter = artworkAttachments.iterator();
        while (iter.hasNext()) {
            Attachment attachment = iter.next();
            if (!artwork.getArtworkType().name().equals(attachment.getContentType().name())) {
                // remove non matching types
                iter.remove();
            }
        }

        // return attachments for artwork
        LOG.info("Found {} attachments for artwork: {}", artworkAttachments.size(), artwork);
        return artworkAttachments;
    }

    /**
     * Scans a MATROSKA movie file for attachments.
     *
     * @param movieFile the movie file to scan
     */
    @SuppressWarnings("unchecked")
	private List<Attachment> scanAttachments(StageFile stageFile) {
        if (!isFileScanable(stageFile)) {
            Collections.emptyList();
        }
        
        final String cacheKey = Long.toString(stageFile.getId());
        List<Attachment> attachments = attachmentCache.get(cacheKey, List.class);
        if (attachments != null) {
            // attachments stored so just return them
            return attachments;
        }
        
        // create new attachments
        attachments = new ArrayList<>();

        LOG.debug("Scanning file for attachments {}",  stageFile.getFileName());
        int attachmentId = 0;
        try {
            // create the command line
            List<String> commandMkvInfo = new ArrayList<>(MT_INFO_EXE);
            commandMkvInfo.add(stageFile.getFullPath());

            ProcessBuilder pb = new ProcessBuilder(commandMkvInfo);
            pb.directory(MT_PATH);

            Process p = pb.start();
            BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = FileTools.readLine(input);
            while (line != null) {
                if (line.contains("+ Attached")) {
                    // increase the attachment id
                    attachmentId++;
                    // next line contains file name
                    String fileNameLine = FileTools.readLine(input);
                    // next line contains MIME type
                    String mimeTypeLine = FileTools.readLine(input);

                    Attachment attachment = createAttachment(attachmentId, fileNameLine, mimeTypeLine);
                    if (attachment != null) {
                        attachment.setStageFile(stageFile);
                        attachments.add(attachment);
                    }
                }

                line = FileTools.readLine(input);
            }

            if (p.waitFor() != 0) {
                LOG.error("Error during attachment retrieval - ErrorCode={}",  p.exitValue());
            }
        } catch (IOException | InterruptedException ex) {
            LOG.error("Attachment scanner error", ex);
        }
        
        // put into cache
        this.attachmentCache.put(cacheKey, attachments);
        return attachments;
    }

    /**
     * Creates an attachment.
     *
     * @param id
     * @param filename
     * @param mimetype
     * @return Attachment or null
     */
    private Attachment createAttachment(int id, String filename, String mimetype) {
        String fixedFileName = null;
        if (filename.contains("File name:")) {
            fixedFileName = filename.substring(filename.indexOf("File name:") + 10).trim();
        }
        String fixedMimeType = null;
        if (mimetype.contains("Mime type:")) {
            fixedMimeType = mimetype.substring(mimetype.indexOf("Mime type:") + 10).trim();
        }

        AttachmentContent content = determineContent(fixedFileName, fixedMimeType);

        Attachment attachment = null;
        if (content == null) {
            LOG.debug("Failed to dertermine attachment type for '{}' ({})", fixedFileName,  fixedMimeType);
        } else {
            attachment = new Attachment();
            attachment.setType(AttachmentType.MATROSKA); // one and only type at the moment
            attachment.setAttachmentId(id);
            attachment.setContentType(content.getContentType());
            attachment.setImageType(content.getImageType());
            attachment.setPart(content.getPart());
            LOG.trace("Found attachment {}",  attachment);
        }
        return attachment;
    }

    /**
     * Determines the content of the attachment by file name and mime type.
     *
     * @param inFileName
     * @param inMimeType
     * @return the content, may be null if determination failed
     */
    private AttachmentContent determineContent(String inFileName, String inMimeType) {
        if (inFileName == null) {
            return null;
        }
        if (inMimeType == null) {
            return null;
        }
        final String fileName = inFileName.toLowerCase();
        final String mimeType = inMimeType.toLowerCase();

        if (validMimeTypesText.contains(mimeType)) {
            // NFO text file
            if ("nfo".equalsIgnoreCase(FilenameUtils.getExtension(fileName))) {
                return new AttachmentContent(ContentType.NFO, null);
            }
        } else if (validMimeTypesImage.containsKey(mimeType)) {
            final ImageType imageType = validMimeTypesImage.get(mimeType);
            
            String check = FilenameUtils.removeExtension(fileName);
            // check for SET image
            boolean isSetImage = false;
            if (check.endsWith(".set")) {
                isSetImage = true;
                // fix check to look for image type
                // just removing extension which is ".set" in this moment
                check = FilenameUtils.removeExtension(check);
            }
            
            for (String posterToken : this.configServiceWrapper.getArtworkTokens(ArtworkType.POSTER)) {
                if (isMatching(check, posterToken)) {
                    final ContentType contentType = isSetImage ? ContentType.SET_POSTER : ContentType.POSTER;
                    // fileName = <any>.<posterToken>[.set].<extension>
                    return new AttachmentContent(contentType, imageType);
                }
            }
            for (String fanartToken : this.configServiceWrapper.getArtworkTokens(ArtworkType.FANART)) {
                if (isMatching(check, fanartToken)) {
                    final ContentType contentType = isSetImage ? ContentType.SET_FANART : ContentType.FANART;
                    // fileName = <any>.<fanartToken>[.set].<extension>
                    return new AttachmentContent(contentType, imageType);
                }
            }
            for (String bannerToken : this.configServiceWrapper.getArtworkTokens(ArtworkType.BANNER)) {
                if (isMatching(check, bannerToken)) {
                    final ContentType contentType = isSetImage ? ContentType.SET_BANNER : ContentType.BANNER;
                    // fileName = <any>.<bannerToken>[.set].<extension>
                    return new AttachmentContent(contentType, imageType);
                }
            }
            for (String videoimageToken : this.configServiceWrapper.getArtworkTokens(ArtworkType.VIDEOIMAGE)) {
                if (isMatching(check, videoimageToken)) {
                    // fileName = <any>.<videoimageToken>.<extension>
                    return new AttachmentContent(ContentType.VIDEOIMAGE, imageType);
                }
                // TODO determination of episode/part
            }
        }

        // no content type determined
        return null;
    }

    private static boolean isMatching(final String check, final String token) {
        if (check.equals(token)) {
            return true;
        }
        if (check.endsWith("."+token) || check.endsWith("-"+token)) {
            return true;
        }
        return false;
    }
    
    public boolean extractArtwort(File dst, StageFile stageFile, int attachmentId) {
        if (!FileTools.isFileReadable(stageFile)) {
            return false;
        }

        LOG.trace("Extract attachement {} from stage file {}",  attachmentId, stageFile.getFullPath());
        
        boolean stored = true;
        try {
            // Create the command line
            List<String> commandMedia = new ArrayList<>(MT_EXTRACT_EXE);
            commandMedia.add("attachments");
            commandMedia.add(stageFile.getFullPath());
            commandMedia.add(attachmentId + ":" + dst.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(commandMedia);
            pb.directory(MT_PATH);
            Process p = pb.start();

            if (p.waitFor() != 0) {
                LOG.error("Error during extraction - ErrorCode={}",  p.exitValue());
                stored = false;
            }
        } catch (IOException | InterruptedException ex) {
            LOG.error("Attachment extraction error", ex);
            stored = false;
        }

        if (!stored) {
            // delete destination file in error case
            try {
                dst.delete();
            } catch (Exception e) { //NOSONAR
                // ignore any error
            }
        }
        
        return stored;
    }
}
