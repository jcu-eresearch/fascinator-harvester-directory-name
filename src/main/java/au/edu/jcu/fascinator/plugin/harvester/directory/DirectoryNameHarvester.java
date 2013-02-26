/*
 * The Fascinator - Directory Name Harvester Plugin
 * Copyright (C) 2012 James Cook University
 *
 * Acknowledgements: This Harvester plugin is based on two plugins
 * - CSVHarvester - com.googlecode.fascinator.harvester.csv
 *      - author Greg Pendlebury
 *      - Queensland Cyber Infrastructure Foundation (http://www.qcif.edu.au/)
 * - FileSystemHarvester - com.googlecode.fascinator.harvester.filesystem
 *      - author - Oliver Lucido
 *      - University of Southern Queensland (http://www.usq.edu.au/)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package au.edu.jcu.fascinator.plugin.harvester.directory;

import com.googlecode.fascinator.api.harvester.HarvesterException;
import com.googlecode.fascinator.api.storage.DigitalObject;
import com.googlecode.fascinator.api.storage.Payload;
import com.googlecode.fascinator.api.storage.StorageException;
import com.googlecode.fascinator.common.JsonObject;
import com.googlecode.fascinator.common.JsonSimple;
import com.googlecode.fascinator.common.harvester.impl.GenericHarvester;
import com.googlecode.fascinator.common.storage.StorageUtils;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.simple.JSONArray;

/*
 *    <p>
 *        This plugin harvests subdirectory names in a specified
 *        directory in the file system. It can use a cache to do
 *        incremental harvests, which only harvest directories that have been
 *        added since the last time it was run.
 *
 *        The directory name harvested is used in conjunction with a default
 *        metadata file (default-metadata.json) to create metadata about
 *        the directory.
 *
 *        Each directory harvested, may contain an override metadata file
 *        (metadata.json) to replace the default metadata or even add to the
 *       default metadata.
 *
 *        This harvester was built to harvest bird species where standard
 *        metadata applied to each species, but an override file may exist to
 *        provide replacement or additional metadata details.
 *
 *        Both the default metadata and override metadata files can contain
 *        multiple types of metadata to be created for each species. A unique
 *        record is created for each metadata type detected.
 *
 *        The caching used is the same as that from the File System Harvester.
 *    </p>
 *
 *    <h3>Configuration</h3>
 *    <p>
 *    <table border="1">
 *        <tr>
 *            <th>Option</th>
 *            <th>Description</th>
 *            <th>Required</th>
 *            <th>Default</th>
 *        </tr>
 *
 *        <tr>
 *            <td>baseDir</td>
 *            <td>Path of subdirectories to be harvested</td>
 *            <td><b>Yes</b></td>
 *            <td>${user.home}/Documents/public/</td>
 *        </tr>
 *
 *        <tr>
 *            <td>recordIDPrefix</td>
 *            <td>A prefix applied to all records created</td>
 *            <td><b>No</b></td>
 *            <td></td>
 *        </tr>
 *
 *        <tr>
 *            <td>force</td>
 *            <td>Force harvest the specified directory or file again even when it's not
 *                modified (ignore cache)</td>
 *            <td>No</td>
 *            <td>false</td>
 *        </tr>
 *
 *        <tr>
 *            <td>caching</td>
 *            <td>Caching method to use. Valid entries are 'basic' and 'hashed'</td>
 *            <td>No</td>
 *            <td>null</td>
 *        </tr>
 *        <tr>
 *            <td>cacheId</td>
 *            <td>The cache ID to use in the database if caching is in use.</td>
 *            <td>Yes (if valid 'caching' value is provided)</td>
 *            <td>null</td>
 *        </tr>
 *        <tr><td>payloadId</td>
 *            <td>The payload identifier used to store the JSON data</td>
 *            <td>No</td>
 *            <td>defaults to "metadata.json"</td>
 *        </tr>
 *        <tr>
 *            <td>derbyHome</td>
 *            <td>Path to use for the file store of the database. Should match other Derby
 *                paths provided in the configuration file for the application.</td>
 *            <td>Yes (if valid 'caching' value is provided)</td>
 *            <td>null</td>
 *        </tr>
 *    </table>
 *
 *    <h3>Caching</h3>
 *    With regards to the underlying cache you have three options for configuration:
 *    <ol>
 *        <li>No caching: All files will always be be harvested. Be aware that
 *            without caching there is no support for deletion.</li>
 *        <li><b>Basic</b> caching: The file is considered 'cached' if the last
 *            modified date matches the database entry. On some operating systems (like
 *            linux) this can provide a minimum of around 2 seconds of granularity. For
 *            most purposes this is sufficient, and this cache is the most efficient.</li>
 *        <li><b>Hashed</b> caching: The entire contents of the file are SHA hashed
 *            and the hash is stored in the database. The file is considered cached if the
 *            old hash matches the new hash. This approach will only trigger a harvest if
 *            the contents of the file really change, but it is quite slow across large
 *            data sets and large files.</li>
 *    </ol>
 *    Deletion support is not provided.
 *
 *    <h3>Examples</h3>
 *    <ol>
 *        <li>
 *            Harvesting ${user.home}/Documents/public/Downloads directory. The
 *            harvest includes the sub directories in the Downloads directory, and does
 *            not re-harvest unmodified directories if they exist in the cache
 *            database under the 'default' cache. Below is a sample configuration
 *            file: dir-config.json. This configuration specifies a single target
 *            directory, has caching setup, specifies the names of the default files
 *            and that each directory being harvested contains two types of metadata.
 *            This means that two records will be created for each directory (bird species)
 *            processed.
 *
 *            <pre>
 *    "harvester": {
 *        "type": "directory",
 *        "directory": {
 *            "targets": [ {
 *                "baseDir": "${test.dir}/Downloads",
 *                "recordIDPrefix": "jcu.edu.au/tdh/collection/"
 *            } ]
 *        },
 *        "file-system": {
 *            "caching": "basic",
 *            "cacheId": "config-caching"
 *        },
 *        "default-files": {
 *            "default-metadata-filename" : "default-metadata.json",
 *            "override-metadata-filename": "metadata.json"
 *        },
 *        "metadata-types": [
 *            {"type" : "occurrences"},
 *            {"type" : "suitability"}
 *        ]
 *    },
 *    "database-service": {
 *        "derbyHome" : "${test.cache.dir}"
 *    }
 *            </pre>
 *
 *        </li>
 *        <li>
 *            Below is an example directory structure with the default and override metadata files listed. Actions taken by the harvester are detailed.
 *            <pre>
 *Downloads                                                     (dir)
 *|----default-metadata.json                                    (file) The metadata types and the metadata contained
 *|                                                                    in this file is applied to all birds species
 *|----Australian Raven - Corvus coronoides                     (dir)  A record is created for this folder name with
 *|                                                                    the default metadata for each type.
 *|----Crimson Rosella - Platycercus (Platycercus) elegans      (dir)  A record is created for this folder name with
 *|    |                                                               the default metadata overridden by metadata.json for each type.
 *|    ----metadata.json                                        (file) Override metadata file for Crimson Rosella
 *|----Pale-headed Rosella - Platycercus (Violania) adscitus    (dir)  A record is created for this folder name with
 *|                                                                    the default metadata for each type.
 *|----Southern Cassowary - Casuaris casuaris                   (dir)  A record is created for this folder name with
 *        |                                                               the default metadata overridden by metadata.json for each type.
 *       ----metadata.json                                        (file) Override metadata file for Southern Cassowary
 *            </pre>
 *        </li>
 *        <li>
 *        Below is a sample default metadata file.
 *        <pre>
 *{
 *    "harvester": {
 *        "type": "directory",
 *        "default-metadata":  {
 *            "occurrences": [ {
 *                "metadata1": "Observed Occurrences",
 *                "metadata2": "Predicted Occurrences",
 *                "metadata3": "Conservation",
 *                "metadata4": "Biodiversity",
 *                "metadata5": "Ornithologists rules"
 *            } ],
 *            "suitability": [ {
 *                "metadata1": "Habitat not suitable",
 *                "metadata2": "Species, present, no opinion on habitat quality available",
 *                "metadata3": "Good habitat in a vegetatino zone core for that species",
 *                "metadata4": "The Bird Observers Council of Australia, have great social gatherings around the country",
 *                "metadata5": "These metadata are all configurable"
 *            } ]
 *        }
 *    }
 *}
 *        </pre>
 *        </li>
 *        <li>
 *            Below is a sample override metadata file.
 *            <pre>
 *{
 *    "harvester": {
 *        "type": "directory",
 *        "metadata": {
 *            "occurrences": [ {
 *                "metadata2": "Override default value: The cassowary can be dangerous. BEWARE!!.",
 *                "metadata6": "This metadata should be added to the default ones, as it's new",
 *                "metadata7": "New, should also be added."
 *                } ],
 *            "suitability": [ {
 *                "metadata1": "Override default value: cassowaryss are cute.",
 *                "metadata3": "This metadata should be added to the default ones, as it's new",
 *                "metadata6": "New, should also be added.",
 *                "metadataTony" :"The habitat is not suitable in the long term for this species"
 *               } ]
 *        }
 *   }
 *}
 *            </pre>
 *        </li>
 *    </ol>
 *
 *    <h3>Wiki Link</h3>
 *    <p>
 *        <b>None</b>
 *    </p>
 *
 *   @author Jay van Schyndel
 *
 */
public class DirectoryNameHarvester extends GenericHarvester {

    /**
     * default ignore list
     */
    private static final String DEFAULT_PAYLOAD_ID = "metadata.json";
    /**
     * logging
     */
    private Logger log = LoggerFactory.getLogger(DirectoryNameHarvester.class);
    /**
     * Harvesting targets
     */
    private List<JsonSimple> targets;
    /**
     * Target index
     */
    private Integer targetIndex;
    /**
     * Target index
     */
    private File nextFile;
    /**
     * Stack of queued files to harvest
     */
    private Stack<File> fileStack;
    /**
     * whether or not there are more files to harvest
     */
    private boolean hasMore;
    /**
     * force harvesting all directories
     */
    private boolean force;
    /**
     * Caching
     */
    private DerbyCache cache;
    /**
     * A prefix for generating the object's ID
     */
    private String idPrefix;
    /**
     * Payload ID
     */
    private String payloadId;
    /**
     * Default metadata filename
     */
    private String defaultMetadataFilename;
    /**
     * Default metadata override filename
     */
    private String overrideMetadataFilename;
    /**
     * Default metadata
     */
    private JsonSimple defaultMetadata;
    /**
     * Default metadata
     */
    private JsonSimple overrideMetadata;
    /**
     * Harvesting targets
     */
    private List<JsonSimple> metadataTypes;

    /**
     * Directory Harvester Constructor
     */
    public DirectoryNameHarvester() {
        super("directory", "Directory Harvester");
    }

    /**
     * Initialisation of Directory harvester plugin
     *
     * @throws HarvesterException if fails to initialise
     */
    @Override
    public void init() throws HarvesterException {
        // Check for valid targests
        targets = getJsonConfig().getJsonSimpleList("harvester", "directory",
                "targets");
        if (targets.isEmpty()) {
            throw new HarvesterException("No targets specified");
        }

        //obtain the metadata filenames
        JsonSimple defaultFilenames = new JsonSimple(getJsonConfig().getObject("harvester", "default-files"));

        //this filename is expected to exist in the main folder, it provides the
        //standard metadata for all directories
        defaultMetadataFilename = defaultFilenames.getString("null", "default-metadata-filename");
        //this filename is optional, it may exist in the directories being
        //processed. It's contents can override and add to the default metadata
        overrideMetadataFilename = defaultFilenames.getString("null", "override-metadata-filename");

        if (defaultMetadataFilename == null) {
            throw new HarvesterException("No default metadata filename specified");
        }
        if (overrideMetadataFilename == null) {
            throw new HarvesterException("No override metadata filename specified");
        }

        //obtain the metadata types
        metadataTypes = getJsonConfig().getJsonSimpleList("harvester", "metadata-types");

        // Loop processing variables
        fileStack = new Stack<File>();
        targetIndex = null;
        hasMore = true;

        // Caching
        try {
            cache = new DerbyCache(getJsonConfig());
            // Reset flags for deletion support
            cache.resetFlags();
        } catch (Exception ex) {
            log.error("Error instantiating cache: ", ex);
            throw new HarvesterException(ex);
        }

        // Prep the first file
        nextFile = getNextFile();
    }

    /**
     * Get the next file due to be harvested
     *
     * @return The next file to harvest, null if none
     */
    private File getNextFile() {
        File next = null;
        if (fileStack.empty()) {
            next = getNextTarget();
        } else {
            next = fileStack.pop();
        }
        if (next == null) {
            hasMore = false;
        }
        return next;
    }

    /**
     * Retrieve the next file specified as a target in configuration
     *
     * @return The next target file, null if none
     */
    private File getNextTarget() {
        // First execution
        if (targetIndex == null) {
            targetIndex = new Integer(0);
        } else {
            targetIndex++;
        }

        // We're finished
        if (targetIndex >= targets.size()) {
            return null;
        }

        // Get the next target
        JsonSimple target = targets.get(targetIndex);
        String path = target.getString(null, "baseDir");
        if (path == null) {
            log.warn("No path provided for target, skipping!");
            return getNextTarget();

        } else {
            File file = new File(path);
            if (!file.exists()) {
                log.warn("Path '{}' does not exist, skipping!", path);
                return getNextTarget();

            } else {
                log.info("Target file/directory found: '{}'", path);
                updateConfig(target, path);
                return file;
            }
        }
    }

    /**
     * Update harvest configuration when switching target path
     *
     * @param tConfig The target configuration
     * @param path    The path to the target
     */
    private void updateConfig(JsonSimple tConfig, String path) {
        force = tConfig.getBoolean(false, "force");
        idPrefix = tConfig.getString("", "recordIDPrefix");
        payloadId = tConfig.getString(DEFAULT_PAYLOAD_ID, "payloadId");

        //load the default-metadata file
        try {
            this.defaultMetadata = new JsonSimple(new File(path, this.defaultMetadataFilename));
        } catch (IOException ex) {
            log.error("Unable to open default metadata file", path + this.defaultMetadataFilename);
        }
    }

    /**
     * Shutdown the plugin
     *
     * @throws HarvesterException if there are errors
     */
    @Override
    public void shutdown() throws HarvesterException {
        if (cache != null) {
            try {
                cache.shutdown();
            } catch (Exception ex) {
                log.error("Error shutting down cache: ", ex);
                throw new HarvesterException(ex);
            }
        }
    }

    /**
     * Harvest the next set of directories, and return their Object IDs
     *
     * @return Set<String> The set of object IDs just harvested
     * @throws HarvesterException if there are errors
     */
    @Override
    public Set<String> getObjectIdList() throws HarvesterException {
        Set<String> fileObjectIdList = new HashSet<String>();

        // We had no valid targets
        if (nextFile == null) {
            hasMore = false;
            return fileObjectIdList;
        }

        // Normal logic
        while (nextFile != null){
            if (nextFile.isDirectory()) {
                File[] children = nextFile.listFiles();
                for (File child : children) {
                    if (child.isDirectory()) {
                            harvestDirectory(fileObjectIdList, child);
                    }
                }
            }

            // Progess the stack and return
            nextFile = getNextFile();
        }

        return fileObjectIdList;
    }

    /**
     * Harvest a directory based on configuration
     *
     * @param list The set of harvested IDs to add to
     * @param file The file to harvest
     * @throws HarvesterException if there are errors
     */

    private void harvestDirectory(Set<String> list, File file)
            throws HarvesterException {

        for (int i = 0; i < this.metadataTypes.size(); i++) {

            // The OID that will be used to store the record
            String oid = DigestUtils.md5Hex(file.getPath() + metadataTypes.get(i));
            // Check if it is in the cache, make sure the cache call come before
            // 'force' in the boolean OR so that the cache entry is 'touched'
            if (cache.hasChanged(oid, file) || force) {
                try {
                    createDigitalObject(list, file, oid, metadataTypes.get(i));
                } catch (StorageException se) {
                    log.warn("Directory not harvested {}: {}", file, se.getMessage());
                }
            }
        }
    }

    /**
     * Check if there are more objects to harvest
     *
     * @return
     * <code>true</code> if there are more,
     * <code>false</code> otherwise
     */
    @Override
    public boolean hasMoreObjects() {
        return hasMore;
    }

    /**
     * Create digital object
     *
     * @param list         The set of harvested IDs to add to
     * @param file         File (directory name) to be transformed to be digital object
     * @param oid          Unique identifier for the object
     * @param metaDataType the metadata type being processed based on configuration setup.
     * @throws HarvesterException if fail to create the object
     * @throws StorageException if fail to save the file to the storage
     */
    private void createDigitalObject(Set<String> list, File file, String oid, JsonSimple metadataType)
            throws HarvesterException, StorageException {

        //obtaining directory name to use as part of the metadata
        String directory = file.getName();

        //read the override metadata file if it exists
        JsonSimple overrideMeta = null;

        try {
            this.overrideMetadata = new JsonSimple(new File(file, this.overrideMetadataFilename));
            overrideMeta = new JsonSimple(this.overrideMetadata.getObject("harvester", "metadata"));
        } catch (IOException ex) {
            log.info("Metadata override file does not exist: ", file + this.overrideMetadataFilename);
        }

        JsonSimple defaultMetadata = new JsonSimple(this.defaultMetadata.getObject("harvester", "default-metadata"));

        //asembling the data
        JsonSimple data = new JsonSimple();
        // new bit: adding the directory name to the data with an accurate key name.
        data.getJsonObject().put("harvest_dir_name", directory);
        // adding the species (the directory name) to the data.  The following line
        // can be removed once the Edgar harvester Python code is changed to use the
        // new keyname.
        data.getJsonObject().put("species", directory);

        //Merging the default metadata with the override metadata
        //obtaining the metadata type
        String type = metadataType.getString("", "type");
        //for the metadata type, obtain the metadata and add it
        JSONArray tempDefault = defaultMetadata.getArray(type);
        JsonObject tempObj = (JsonObject) tempDefault.get(0);
        data.getJsonObject().putAll(tempObj);

        JSONArray tempOverride = null;
        if  (overrideMeta != null){
            tempOverride = overrideMeta.getArray(type);
            tempObj = (JsonObject) tempOverride.get(0);
            data.getJsonObject().putAll(tempObj);
        }

        JsonObject meta = new JsonObject();
        meta.put("dc.identifier", idPrefix + directory + "/" + type);

        storeJsonInObject(data.getJsonObject(), meta, oid);
        list.add(oid);
    }

    /**
     * Store the processed data and metadata in the system.
     *
     * @param dataJson an instantiated JSON object containing data to store
     * @param metaJson an instantiated JSON object containing metadata to store
     * @throws HarvesterException if an error occurs
     */
    private void storeJsonInObject(JsonObject dataJson, JsonObject metaJson,
            String oid) throws HarvesterException {

        //Does the object already exists ?
        DigitalObject object = null;
        try {

            object = getStorage().getObject(oid);
            storeJsonInPayload(dataJson, metaJson, object);

        } catch (StorageException ex) {

            //This is going to be brand new
            try {

                object = StorageUtils.getDigitalObject(getStorage(), oid);
                storeJsonInPayload(dataJson, metaJson, object);

            } catch (StorageException ex2) {

                throw new HarvesterException("Error creating new digital object:", ex2);
            }
        }

        //Set the pending flag
        if (object != null) {
            try {
                object.getMetadata().setProperty("render-pending", "true");
                object.close();
            } catch (Exception ex) {
                log.error("Error setting 'render-pending' flag: ", ex);
            }
        }
    }

    private void storeJsonInPayload(JsonObject dataJson, JsonObject metaJson,
            DigitalObject object) throws HarvesterException {

        Payload payload = null;
        JsonSimple json = new JsonSimple();
        try {
            //New payloads
            payload = object.getPayload(payloadId);
            //log.debug("Updating existing payload: '{}' => '{}'", object.getId(), payloadId);

            //Get the old JSON to merge
            try {
                json = new JsonSimple(payload.open());
            } catch (IOException ex) {
                log.error("Error parsing existing JSON: '{}' => '{}'", object.getId(), payloadId);
                throw new HarvesterException("Error parsing existing JSON: ", ex);
            } finally {
                payload.close();
            }

            //Update storage
            try {
                InputStream in = streamMergedJson(dataJson, metaJson, json);
                object.updatePayload(payloadId, in);
            } catch (IOException ex2) {
                throw new HarvesterException("Error processing JSON data: ", ex2);
            } catch (StorageException ex2) {
                throw new HarvesterException("Error updating payload: ", ex2);
            }

        } catch (StorageException ex) {
            //Create a new Payload
            try {
                //log.debug("Creating new payload: '{}' => '{}'", object.getId(), payloadId);
                InputStream in = streamMergedJson(dataJson, metaJson, json);
                payload = object.createStoredPayload(payloadId, in);
            } catch (IOException ex2) {
                throw new HarvesterException("Error parsing JSON encoding: ", ex2);
            } catch (StorageException ex2) {
                throw new HarvesterException("Error creating new payload: ", ex2);
            }
        }

        //Tidy up before we finish
        if (payload != null) {
            try {
                payload.setContentType("application/json");
                payload.close();
            } catch (Exception ex) {
                log.error("Error setting Payload MIME type and closing:", ex);
            }
        }
    }

    /**
     * Merge the newly processed data with any (possible) existing data already
     * present, also convert the completed JSON merge into a Stream for storage.
     *
     * @param dataJson an instantiated JSON object containing data to store
     * @param metaJson an instantiated JSON object containing metadata to store
     * @param existing an instantiated JsonSimple object with any existing data
     * @throws IOException if any character encoding issues effect the Stream
     */
    private InputStream streamMergedJson(JsonObject dataJson,
            JsonObject metaJson, JsonSimple existing) throws IOException {
        //Overwrite and/or create only nodes we condier new data
        existing.getJsonObject().put("recordIDPrefix", idPrefix);
        JsonObject existingData = existing.writeObject("data");
        existingData.putAll(dataJson);
        JsonObject existingMeta = existing.writeObject("metadata");
        existingMeta.putAll(metaJson);

        //Turn into a stream and return
        String jsonString = existing.toString(true);
        return IOUtils.toInputStream(jsonString, "UTF-8");
    }
}
