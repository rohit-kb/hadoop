/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.service.conf;

import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.service.ServiceTestUtils;
import org.apache.hadoop.yarn.service.api.records.Component;
import org.apache.hadoop.yarn.service.api.records.Resource;
import org.apache.hadoop.yarn.service.api.records.ResourceInformation;
import org.apache.hadoop.yarn.service.api.records.Service;
import org.apache.hadoop.yarn.service.api.records.ConfigFile;
import org.apache.hadoop.yarn.service.api.records.Configuration;
import org.apache.hadoop.yarn.service.utils.ServiceApiUtil;
import org.apache.hadoop.yarn.service.utils.SliderFileSystem;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.hadoop.yarn.service.conf.ExampleAppJson.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test global configuration resolution.
 */
public class TestAppJsonResolve  {
  protected static final Logger LOG =
      LoggerFactory.getLogger(TestAppJsonResolve.class);

  @Test
  public void testOverride() throws Throwable {
    Service orig = ExampleAppJson.loadResource(OVERRIDE_JSON);

    Configuration global = orig.getConfiguration();
    assertEquals("a", global.getProperty("g1"));
    assertEquals("b", global.getProperty("g2"));
    assertEquals(2, global.getFiles().size());

    Configuration simple = orig.getComponent("simple").getConfiguration();
    assertEquals(0, simple.getProperties().size());
    assertEquals(1, simple.getFiles().size());

    Configuration master = orig.getComponent("master").getConfiguration();
    assertEquals("m", master.getProperty("name"));
    assertEquals("overridden", master.getProperty("g1"));
    assertEquals(0, master.getFiles().size());

    Configuration worker = orig.getComponent("worker").getConfiguration();
    LOG.info("worker = {}", worker);
    assertEquals(3, worker.getProperties().size());
    assertEquals(0, worker.getFiles().size());

    assertEquals("worker", worker.getProperty("name"));
    assertEquals("overridden-by-worker", worker.getProperty("g1"));
    assertNull(worker.getProperty("g2"));
    assertEquals("1000", worker.getProperty("timeout"));

    // here is the resolution
    SliderFileSystem sfs = ServiceTestUtils.initMockFs();
    ServiceApiUtil.validateAndResolveService(orig, sfs, new
        YarnConfiguration());

    global = orig.getConfiguration();
    LOG.info("global = {}", global);
    assertEquals("a", global.getProperty("g1"));
    assertEquals("b", global.getProperty("g2"));
    assertEquals(2, global.getFiles().size());

    simple = orig.getComponent("simple").getConfiguration();
    assertEquals(2, simple.getProperties().size());
    assertEquals("a", simple.getProperty("g1"));
    assertEquals("b", simple.getProperty("g2"));
    assertEquals(2, simple.getFiles().size());

    Set<ConfigFile> files = new HashSet<>();
    Map<String, String> props = new HashMap<>();
    props.put("k1", "overridden");
    props.put("k2", "v2");
    files.add(new ConfigFile().destFile("file1").type(ConfigFile.TypeEnum
        .PROPERTIES).properties(props));
    files.add(new ConfigFile().destFile("file2").type(ConfigFile.TypeEnum
        .XML).properties(Collections.singletonMap("k3", "v3")));
    assertTrue(files.contains(simple.getFiles().get(0)));
    assertTrue(files.contains(simple.getFiles().get(1)));

    master = orig.getComponent("master").getConfiguration();
    LOG.info("master = {}", master);
    assertEquals(3, master.getProperties().size());
    assertEquals("m", master.getProperty("name"));
    assertEquals("overridden", master.getProperty("g1"));
    assertEquals("b", master.getProperty("g2"));
    assertEquals(2, master.getFiles().size());

    props.put("k1", "v1");
    files.clear();
    files.add(new ConfigFile().destFile("file1").type(ConfigFile.TypeEnum
        .PROPERTIES).properties(props));
    files.add(new ConfigFile().destFile("file2").type(ConfigFile.TypeEnum
        .XML).properties(Collections.singletonMap("k3", "v3")));

    assertTrue(files.contains(master.getFiles().get(0)));
    assertTrue(files.contains(master.getFiles().get(1)));

    worker = orig.getComponent("worker").getConfiguration();
    LOG.info("worker = {}", worker);
    assertEquals(4, worker.getProperties().size());

    assertEquals("worker", worker.getProperty("name"));
    assertEquals("overridden-by-worker", worker.getProperty("g1"));
    assertEquals("b", worker.getProperty("g2"));
    assertEquals("1000", worker.getProperty("timeout"));
    assertEquals(2, worker.getFiles().size());

    assertTrue(files.contains(worker.getFiles().get(0)));
    assertTrue(files.contains(worker.getFiles().get(1)));
  }

  @Test
  public void testOverrideExternalConfiguration() throws IOException {
    Service orig = ExampleAppJson.loadResource(EXTERNAL_JSON_1);

    Configuration global = orig.getConfiguration();
    assertEquals(0, global.getProperties().size());

    assertEquals(3, orig.getComponents().size());

    Configuration simple = orig.getComponent("simple").getConfiguration();
    assertEquals(0, simple.getProperties().size());

    Configuration master = orig.getComponent("master").getConfiguration();
    assertEquals(1, master.getProperties().size());
    assertEquals("is-overridden", master.getProperty("g3"));

    Configuration other = orig.getComponent("other").getConfiguration();
    assertEquals(0, other.getProperties().size());

    // load the external service
    SliderFileSystem sfs = ServiceTestUtils.initMockFs();
    Service ext = ExampleAppJson.loadResource(APP_JSON);
    ServiceApiUtil.validateAndResolveService(ext, sfs, new
        YarnConfiguration());

    // perform the resolution on original service
    sfs = ServiceTestUtils.initMockFs(ext);
    ServiceApiUtil.validateAndResolveService(orig, sfs, new
        YarnConfiguration());

    global = orig.getConfiguration();
    assertEquals(0, global.getProperties().size());

    assertEquals(4, orig.getComponents().size());

    simple = orig.getComponent("simple").getConfiguration();
    assertEquals(3, simple.getProperties().size());
    assertEquals("a", simple.getProperty("g1"));
    assertEquals("b", simple.getProperty("g2"));
    assertEquals("60",
        simple.getProperty("yarn.service.failure-count-reset.window"));

    master = orig.getComponent("master").getConfiguration();
    assertEquals(5, master.getProperties().size());
    assertEquals("512M", master.getProperty("jvm.heapsize"));
    assertEquals("overridden", master.getProperty("g1"));
    assertEquals("b", master.getProperty("g2"));
    assertEquals("is-overridden", master.getProperty("g3"));
    assertEquals("60",
        simple.getProperty("yarn.service.failure-count-reset.window"));

    Configuration worker = orig.getComponent("worker").getConfiguration();
    LOG.info("worker = {}", worker);
    assertEquals(4, worker.getProperties().size());
    assertEquals("512M", worker.getProperty("jvm.heapsize"));
    assertEquals("overridden-by-worker", worker.getProperty("g1"));
    assertEquals("b", worker.getProperty("g2"));
    assertEquals("60",
        worker.getProperty("yarn.service.failure-count-reset.window"));

    // Validate worker's resources
    Resource workerResource = orig.getComponent("worker").getResource();
    assertEquals(1, workerResource.getCpus().intValue());
    assertEquals(1024, workerResource.calcMemoryMB());
    assertNotNull(workerResource.getAdditional());
    assertEquals(2, workerResource.getAdditional().size());
    assertEquals(3333, workerResource.getAdditional().get(
        "resource-1").getValue().longValue());
    assertEquals("Gi", workerResource.getAdditional().get(
        "resource-1").getUnit());

    assertEquals(5, workerResource.getAdditional().get(
        "yarn.io/gpu").getValue().longValue());
    assertEquals("", workerResource.getAdditional().get(
        "yarn.io/gpu").getUnit());

    other = orig.getComponent("other").getConfiguration();
    assertEquals(0, other.getProperties().size());
  }

  @Test
  public void testSetResourceAttributes() throws IOException {
    Service orig = ExampleAppJson.loadResource(EXTERNAL_JSON_3);
    Component component = orig.getComponent("volume-service");
    assertNotNull(component);
    Map<String, ResourceInformation> adResource = component
        .getResource().getAdditional();
    assertNotNull(adResource);
    assertEquals(1, adResource.size());
    Map.Entry<String, ResourceInformation> volume = adResource
        .entrySet().iterator().next();
    assertEquals("yarn.io/csi-volume", volume.getKey());
    assertEquals(100L, volume.getValue().getValue().longValue());
    assertEquals(2, volume.getValue().getAttributes().size());
    assertEquals(1, volume.getValue().getTags().size());
  }
}