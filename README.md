This plugin harvests subdirectory names in a specified directory in the
file system. It can use a cache to do incremental harvests, which only
harvest directories that have been added since the last time it was run.
The directory name harvested is used in conjunction with a default
metadata file (default-metadata.json) to create metadata about the
directory. Each directory harvested, may contain an override metadata
file (metadata.json) to replace the default metadata or even add to the
default metadata. This harvester was built to harvest bird species where
standard metadata applied to each species, but an override file may
exist to provide replacement or additional metadata details. Both the
default metadata and override metadata files can contain multiple types
of metadata to be created for each species. A unique record is created
for each metadata type detected. The caching used is the same as that
from the File System Harvester.


Configuration
-------------
<table>
<tr>
	<th>Option </th>
	<th>Description</th>
	<th>Required</th>
	<th>Default</th>
</tr>
<tr>
	<td>baseDir</td>
	<td>Path of subdirectories to be harvested</td>
	<td>Yes</td>
	<td> ${user.home}/Documents/public/</td>
</tr>
<tr>
	<td>force</td>
	<td>Force harvest the specified directory or file again even when it's not modified (ignore cache) </td>
	<td>No </td>
	<td>false</td>
</tr>
<tr>
	<td>caching </td> 
	<td>Caching method to use. Valid entries are 'basic' and 'hashed' </td>
	<td>No </td>
	<td>null</td>
</tr>
<tr>
	<td>cacheId </td> 
	<td>The cache ID to use in the database if caching is in use. </td>
	<td>Yes (if 'caching' is provided) </td> 
	<td>null </td>
</tr>
<tr>
	<td>payloadId </td> 
	<td>The payload identifier used to store the JSON data </td>
	<td>No </td> 
	<td>defaults to "metadata.json"</td>
</tr>
<tr>
	<td>derbyHome </td>
	<td>Path to use for the file store of the database. Should match other Derby paths provided in the configuration file for the application.</td> 
	<td>Yes (if 'caching' is provided) </td> 
	<td>null</td>
</tr>
</table>
	         
Caching
-------

With regards to the underlying cache you have three options for
configuration:

 1. No caching: All files will always be be harvested. Be aware that
    without caching there is no support for deletion.
 2. *Basic* caching: The file is considered 'cached' if the last
    modified date matches the database entry. On some operating systems
    (like linux) this can provide a minimum of around 2 seconds of
    granularity. For most purposes this is sufficient, and this cache is
    the most efficient.
 3. *Hashed* caching: The entire contents of the file are SHA hashed and
    the hash is stored in the database. The file is considered cached if
    the old hash matches the new hash. This approach will only trigger a
    harvest if the contents of the file really change, but it is quite
    slow across large data sets and large files.

Deletion support is not provided.


Examples
--------

 1. Harvesting ${user.home}/Documents/public/Downloads directory. The
    harvest includes the sub directories in the Downloads directory, and
    does not re-harvest unmodified directories if they exist in the
    cache database under the 'default' cache. Below is a sample
    configuration file: dir-config.json. This configuration specifies a
    single target directory, has caching setup, specifies the names of
    the default files and that each directory being harvested contains
    two types of metadata. This means that two records will be created
    for each directory (bird species) processed.

            "harvester": {
                "type": "directory",
                "directory": {
                    "targets": [ {
                        "baseDir": "${test.dir}/Downloads"
                    } ]
                },
                "file-system": {
                    "caching": "basic",
                    "cacheId": "config-caching"
                },
                "default-files": {
                    "default-metadata-filename" : "default-metadata.json",
                    "override-metadata-filename": "metadata.json"
                },
                "metadata-types": [ 
                    {"type" : "occurrences"},
                    {"type" : "suitability"}
                ]
            },
            "database-service": {
                "derbyHome" : "${test.cache.dir}"
            }
                    

 2. Below is an example directory structure with the default and
    override metadata files listed. Actions taken by the harvester are
    detailed.

        Downloads                                                     (dir)
        |----default-metadata.json                                    (file) The metadata types and the metadata contained 
        |                                                                    in this file is applied to all birds species
        |----Australian Raven - Corvus coronoides                     (dir)  A record is created for this folder name with 
        |                                                                    the default metadata for each type.
        |----Crimson Rosella - Platycercus (Platycercus) elegans      (dir)  A record is created for this folder name with 
        |    |                                                               the default metadata overridden by metadata.json for each type.
        |    ----metadata.json                                        (file) Override metadata file for Crimson Rosella
        |----Pale-headed Rosella - Platycercus (Violania) adscitus    (dir)  A record is created for this folder name with 
        |                                                                    the default metadata for each type.
        |----Southern Cassowary - Casuaris casuaris                   (dir)  A record is created for this folder name with 
             |                                                               the default metadata overridden by metadata.json for each type.
             ----metadata.json                                        (file) Override metadata file for Southern Cassowary
                    

 3. Below is a sample default metadata file.

        {
            "harvester": {
                "type": "directory",
                "default-metadata":  {
                    "occurrences": [ {
                        "metadata1": "Observed Occurrences",
                        "metadata2": "Predicted Occurrences",
                        "metadata3": "Conservation",
                        "metadata4": "Biodiversity",
                        "metadata5": "Ornithologists rules"
                    } ],
                    "suitability": [ {
                        "metadata1": "Habitat not suitable",
                        "metadata2": "Species, present, no opinion on habitat quality available",
                        "metadata3": "Good habitat in a vegetatino zone core for that species",
                        "metadata4": "The Bird Observers Council of Australia, have great social gatherings around the country",
                        "metadata5": "These metadata are all configurable"
                    } ]
                }
            }
        }
                

 4. Below is a sample override metadata file.

        {
            "harvester": {
                "type": "directory",
                "metadata": {
                    "occurrences": [ {
                        "metadata2": "Override default value: The cassowary can be dangerous. BEWARE!!.",
                        "metadata6": "This metadata should be added to the default ones, as it's new",
                        "metadata7": "New, should also be added."
                        } ],
                    "suitability": [ {
                        "metadata1": "Override default value: cassowaries are cute.",
                        "metadata3": "This metadata should be added to the default ones, as it's new",
                        "metadata6": "New, should also be added.",
                        "metadata" :"The habitat is not suitable in the long term for this species"
                        } ]
                }
            }
        }

