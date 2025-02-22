/**
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

package org.apache.hadoop.mapred;

import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.mapred.MRCaching.TestResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

/**
 * A JUnit test to test caching with DFS
 * 
 */
@Disabled
public class TestMiniMRDFSCaching {

  @Test
  public void testWithDFS() throws IOException {
    MiniMRCluster mr = null;
    MiniDFSCluster dfs = null;
    FileSystem fileSys = null;
    try {
      JobConf conf = new JobConf();
      dfs = new MiniDFSCluster.Builder(conf).build();
      fileSys = dfs.getFileSystem();
      mr = new MiniMRCluster(2, fileSys.getUri().toString(), 4);
      MRCaching.setupCache("/cachedir", fileSys);
      // run the wordcount example with caching
      TestResult ret = MRCaching.launchMRCache("/testing/wc/input",
                                            "/testing/wc/output",
                                            "/cachedir",
                                            mr.createJobConf(),
                                            "The quick brown fox\nhas many silly\n"
                                            + "red fox sox\n");
      assertTrue(ret.isOutputOk, "Archives not matching");
      // launch MR cache with symlinks
      ret = MRCaching.launchMRCache("/testing/wc/input",
                                    "/testing/wc/output",
                                    "/cachedir",
                                    mr.createJobConf(),
                                    "The quick brown fox\nhas many silly\n"
                                    + "red fox sox\n");
      assertTrue(ret.isOutputOk, "Archives not matching");
    } finally {
      if (fileSys != null) {
        fileSys.close();
      }
      if (dfs != null) {
        dfs.shutdown();
      }
      if (mr != null) {
        mr.shutdown();
      }
    }
  }
}
