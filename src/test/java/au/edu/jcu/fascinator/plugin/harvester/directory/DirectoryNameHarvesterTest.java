/* 
 * The Fascinator - Directory Harvester Plugin
 * Copyright (C) 2012 James Cook University
 * 
 * author - Jay van Schyndel
 * 
 * Acknowledgements: This Harvester Test is based on 
 * - FileSystemHarvesterTest - com.googlecode.fascinator.harvester.filesystem
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
 */
package au.edu.jcu.fascinator.plugin.harvester.directory;

import com.googlecode.fascinator.api.PluginManager;
import com.googlecode.fascinator.api.harvester.Harvester;
import com.googlecode.fascinator.api.storage.Storage;

import java.io.File;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the directory name harvester plugin
 * 
 * @author Jay van Schyndel
 */
public class DirectoryNameHarvesterTest {

    private Storage ram;

    private File testDir, cacheDir, testCreatedDir;

    /**
     * Sets the "test.dir" and "test.cache.dir" system property for use in the
     * JSON configuration
     * 
     * @throws Exception
     *             if any error occurs
     */
    @Before
    public void setup() throws Exception {
        File baseDir = new File(
                DirectoryNameHarvesterTest.class.getResource("/").toURI());
        testDir = new File(baseDir, "");
        cacheDir = new File(baseDir, "dir-harvest-cache");
        cacheDir.mkdirs();
        System.setProperty("test.dir", testDir.getAbsolutePath());
        System.setProperty("test.cache.dir", cacheDir.getAbsolutePath());

        ram = PluginManager.getStorage("ram");
        ram.init("{}");
    }

    @After
    public void cleanup() throws Exception {
        try {
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver").newInstance();
            DriverManager.getConnection("jdbc:derby:;shutdown=true");
        } catch (SQLException ex) {
            // Expected, derby throws exceptions even during success
        }
        FileUtils.deleteQuietly(testCreatedDir);
        FileUtils.deleteQuietly(cacheDir);
    }

    /**
     * Tests a non recursive harvest
     * 
     * @throws Exception
     *             if any error occurs
     */
    @Test
    public void testEdgar() throws Exception {
        DirectoryNameHarvester fsh = getHarvester("/dir-config-edgar.json");
        Set<String> items = fsh.getObjectIdList();
        Assert.assertEquals(8, items.size());
        Assert.assertFalse(fsh.hasMoreObjects());
        fsh.shutdown();
    }
    
    /**
     * Tests a non recursive harvest
     * 
     * @throws Exception
     *             if any error occurs
     */
    @Test
    public void getObjectIdList() throws Exception {
        DirectoryNameHarvester fsh = getHarvester("/dir-config.json");
        Set<String> items = fsh.getObjectIdList();
        Assert.assertEquals(8, items.size());
        Assert.assertFalse(fsh.hasMoreObjects());
        fsh.shutdown();
    }

    /**
     * Tests a non recursive harvest
     * 
     * @throws Exception
     *             if any error occurs
     */
    @Test
    public void getObjectIdListMultipleTargets() throws Exception {
        DirectoryNameHarvester fsh = getHarvester("/dir-config-multi-target.json");
        Set<String> items = fsh.getObjectIdList();
        Assert.assertEquals(16, items.size());
        Assert.assertFalse(fsh.hasMoreObjects());
        fsh.shutdown();
    }
    
    /**
     * Tests a non recursive harvest with checksums with changes on 2nd
     * harvest.
     * 
     * @throws Exception
     *             if any error occurs
     */
    @Test
    public void getObjectIdListWithCachingChanges() throws Exception {
        // the initial harvest will pick up the file
        DirectoryNameHarvester fsh = getHarvester("/dir-config-caching.json");
        Set<String> items = fsh.getObjectIdList();
        Assert.assertEquals(8, items.size());
        fsh.shutdown();

        // next harvest will detect no change
        DirectoryNameHarvester fsh2 = getHarvester("/dir-config-caching.json");
        Set<String> items2 = fsh2.getObjectIdList();
        Assert.assertEquals(0, items2.size());
        fsh2.shutdown();

        // forced harvest
        DirectoryNameHarvester fsh3 = getHarvester("/dir-config-caching-force.json");
        Set<String> items3 = fsh3.getObjectIdList();
        Assert.assertEquals(8, items3.size());
        fsh3.shutdown();
    }

    /**
     * Tests a non recursive harvest with checksums with no file changes on 2nd
     * harvest, and a forced harvest on 3rd
     * 
     * @throws Exception
     *             if any error occurs
     */
    @Test
    public void getObjectIdListWithCachingNoChanges() throws Exception {
        // the initial harvest will pick up the file
        DirectoryNameHarvester fsh = getHarvester("/dir-config-caching.json");
        Set<String> items = fsh.getObjectIdList();
        Assert.assertEquals(8, items.size());
        fsh.shutdown();

        testCreatedDir = new File(testDir, "/Downloads/test-directory");
        testCreatedDir.mkdir();
        
        // next harvest will detect changes
        DirectoryNameHarvester fsh2 = getHarvester("/dir-config-caching.json");
        Set<String> items2 = fsh2.getObjectIdList();
        Assert.assertEquals(2, items2.size());
        fsh2.shutdown();
    }
    
    private DirectoryNameHarvester getHarvester(String filename) throws Exception {
        Harvester fsh = PluginManager.getHarvester("directory", ram);
        fsh.init(new File(getClass().getResource(filename).toURI()));
        return (DirectoryNameHarvester) fsh;
    }
}
