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
package org.apache.hadoop.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.hadoop.crypto.OpensslCipher;
import org.apache.hadoop.io.compress.zlib.ZlibFactory;
import org.apache.hadoop.util.NativeCodeLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestNativeCodeLoader {
  static final Logger LOG = LoggerFactory.getLogger(TestNativeCodeLoader.class);

  private static boolean requireTestJni() {
    String rtj = System.getProperty("require.test.libhadoop");
    if (rtj == null) return false;
    if (rtj.compareToIgnoreCase("false") == 0) return false;
    return true;
  }

  @Test
  public void testNativeCodeLoaded() {
    if (requireTestJni() == false) {
      LOG.info("TestNativeCodeLoader: libhadoop.so testing is not required.");
      return;
    }
    if (!NativeCodeLoader.isNativeCodeLoaded()) {
      fail("TestNativeCodeLoader: libhadoop.so testing was required, but " +
          "libhadoop.so was not loaded.");
    }
    assertFalse(NativeCodeLoader.getLibraryName().isEmpty());
    // library names are depended on platform and build envs
    // so just check names are available
    assertFalse(ZlibFactory.getLibraryName().isEmpty());
    if (NativeCodeLoader.buildSupportsOpenssl()) {
      assertFalse(OpensslCipher.getLibraryName().isEmpty());
    }
    LOG.info("TestNativeCodeLoader: libhadoop.so is loaded.");
  }
}
