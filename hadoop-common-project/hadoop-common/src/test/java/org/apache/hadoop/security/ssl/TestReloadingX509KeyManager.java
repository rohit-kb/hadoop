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
package org.apache.hadoop.security.ssl;

import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.test.GenericTestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Timer;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.apache.hadoop.security.ssl.KeyStoreTestUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestReloadingX509KeyManager {

  private static final String BASEDIR = GenericTestUtils.getTempPath(
      TestReloadingX509TrustManager.class.getSimpleName());

  private final GenericTestUtils.LogCapturer reloaderLog = GenericTestUtils.LogCapturer.captureLogs(
      FileMonitoringTimerTask.LOG);

  @BeforeAll
  public static void setUp() throws Exception {
    File base = new File(BASEDIR);
    FileUtil.fullyDelete(base);
    base.mkdirs();
  }

  @Test
  public void testLoadMissingKeyStore() throws Exception {
    assertThrows(IOException.class, () -> {
      String keystoreLocation = BASEDIR + "/testmissing.jks";

      ReloadingX509KeystoreManager tm =
          new ReloadingX509KeystoreManager("jks", keystoreLocation,
          "password",
          "password");
    });
  }

  @Test
  public void testLoadCorruptKeyStore() throws Exception {
    assertThrows(IOException.class, () -> {
      String keystoreLocation = BASEDIR + "/testcorrupt.jks";
      OutputStream os = new FileOutputStream(keystoreLocation);
      os.write(1);
      os.close();

      ReloadingX509KeystoreManager tm =
          new ReloadingX509KeystoreManager("jks", keystoreLocation,
          "password", "password");
    });
  }

  @Test
  @Timeout(value = 3000)
  public void testReload() throws Exception {
    KeyPair kp = generateKeyPair("RSA");
    X509Certificate sCert = generateCertificate("CN=localhost, O=server", kp, 30,
        "SHA1withRSA");
    String keystoreLocation = BASEDIR + "/testreload.jks";
    createKeyStore(keystoreLocation, "password", "cert1", kp.getPrivate(), sCert);

    long reloadInterval = 10;
    Timer fileMonitoringTimer =
        new Timer(FileBasedKeyStoresFactory.SSL_MONITORING_THREAD_NAME, true);
    ReloadingX509KeystoreManager tm =
        new ReloadingX509KeystoreManager("jks", keystoreLocation,
        "password", "password");
    try {
      fileMonitoringTimer.schedule(new FileMonitoringTimerTask(
          Paths.get(keystoreLocation), tm::loadFrom, null), reloadInterval, reloadInterval);
      assertEquals(kp.getPrivate(), tm.getPrivateKey("cert1"));

      // Wait so that the file modification time is different
      Thread.sleep((reloadInterval+ 1000));

      // Change the certificate with a new keypair
      final KeyPair anotherKP = generateKeyPair("RSA");
      sCert = KeyStoreTestUtil.generateCertificate("CN=localhost, O=server", anotherKP, 30,
          "SHA1withRSA");
      createKeyStore(keystoreLocation, "password", "cert1", anotherKP.getPrivate(), sCert);

      GenericTestUtils.waitFor(new Supplier<Boolean>() {
          @Override
          public Boolean get() {
            return tm.getPrivateKey("cert1").equals(kp.getPrivate());
          }
      }, (int) reloadInterval, 100000);
    } finally {
      fileMonitoringTimer.cancel();
    }
  }

  @Test
  @Timeout(value = 30)
  public void testReloadMissingTrustStore() throws Exception {
    KeyPair kp = generateKeyPair("RSA");
    X509Certificate cert1 = generateCertificate("CN=Cert1", kp, 30, "SHA1withRSA");
    String keystoreLocation = BASEDIR + "/testmissing.jks";
    createKeyStore(keystoreLocation, "password", "cert1", kp.getPrivate(), cert1);

    long reloadInterval = 10;
    Timer fileMonitoringTimer =
        new Timer(FileBasedKeyStoresFactory.SSL_MONITORING_THREAD_NAME, true);
    ReloadingX509KeystoreManager tm =
        new ReloadingX509KeystoreManager("jks", keystoreLocation,
        "password",
        "password");

    try {
      fileMonitoringTimer.schedule(new FileMonitoringTimerTask(
          Paths.get(keystoreLocation), tm::loadFrom, null), reloadInterval, reloadInterval);
      assertEquals(kp.getPrivate(), tm.getPrivateKey("cert1"));

      assertFalse(reloaderLog.getOutput().contains(
          FileMonitoringTimerTask.PROCESS_ERROR_MESSAGE));

      // Wait for the first reload to happen so we actually detect a change after the delete
      Thread.sleep((reloadInterval+ 1000));

      new File(keystoreLocation).delete();

      // Wait for the reload to happen and log to get written to
      Thread.sleep((reloadInterval+ 1000));

      waitForFailedReloadAtLeastOnce((int) reloadInterval);

      assertEquals(kp.getPrivate(), tm.getPrivateKey("cert1"));
    } finally {
      reloaderLog.stopCapturing();
      fileMonitoringTimer.cancel();
    }
  }

  @Test
  @Timeout(value = 30)
  public void testReloadCorruptTrustStore() throws Exception {
    KeyPair kp = generateKeyPair("RSA");
    X509Certificate cert1 = generateCertificate("CN=Cert1", kp, 30, "SHA1withRSA");
    String keystoreLocation = BASEDIR + "/testmissing.jks";
    createKeyStore(keystoreLocation, "password", "cert1", kp.getPrivate(), cert1);

    long reloadInterval = 10;
    Timer fileMonitoringTimer =
        new Timer(FileBasedKeyStoresFactory.SSL_MONITORING_THREAD_NAME, true);
    ReloadingX509KeystoreManager tm =
        new ReloadingX509KeystoreManager("jks", keystoreLocation,
        "password",
        "password");

    try {
      fileMonitoringTimer.schedule(new FileMonitoringTimerTask(
          Paths.get(keystoreLocation), tm::loadFrom, null), reloadInterval, reloadInterval);
      assertEquals(kp.getPrivate(), tm.getPrivateKey("cert1"));

      // Wait so that the file modification time is different
      Thread.sleep((reloadInterval + 1000));

      assertFalse(reloaderLog.getOutput().contains(
          FileMonitoringTimerTask.PROCESS_ERROR_MESSAGE));
      OutputStream os = new FileOutputStream(keystoreLocation);
      os.write(1);
      os.close();

      waitForFailedReloadAtLeastOnce((int) reloadInterval);

      assertEquals(kp.getPrivate(), tm.getPrivateKey("cert1"));
    } finally {
      reloaderLog.stopCapturing();
      fileMonitoringTimer.cancel();
    }
  }

  /**Wait for the reloader thread to load the configurations at least once
  * by probing the log of the thread if the reload fails.
  */
  private void waitForFailedReloadAtLeastOnce(int reloadInterval)
      throws InterruptedException, TimeoutException {
    GenericTestUtils.waitFor(new Supplier<Boolean>() {
        @Override
        public Boolean get() {
            return reloaderLog.getOutput().contains(
                    FileMonitoringTimerTask.PROCESS_ERROR_MESSAGE);
        }
    }, reloadInterval, 10 * 1000);
  }
}
