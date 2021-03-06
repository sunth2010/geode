/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.geode.management.internal.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.PartitionResolver;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.util.CacheListenerAdapter;
import org.apache.geode.compression.SnappyCompressor;
import org.apache.geode.internal.cache.PartitionedRegion;
import org.apache.geode.internal.cache.RegionEntryContext;
import org.apache.geode.test.compiler.JarBuilder;
import org.apache.geode.test.dunit.rules.ClusterStartupRule;
import org.apache.geode.test.dunit.rules.MemberVM;
import org.apache.geode.test.junit.categories.DistributedTest;
import org.apache.geode.test.junit.categories.RegionsTest;
import org.apache.geode.test.junit.rules.GfshCommandRule;
import org.apache.geode.test.junit.rules.serializable.SerializableTestName;

@Category({DistributedTest.class, RegionsTest.class})
public class CreateRegionCommandDUnitTest {

  private static MemberVM locator, server1, server2;

  public static class TestCacheListener extends CacheListenerAdapter implements Serializable {
  }

  public static class AnotherTestCacheListener extends CacheListenerAdapter
      implements Serializable {
  }

  @ClassRule
  public static ClusterStartupRule lsRule = new ClusterStartupRule();

  @ClassRule
  public static GfshCommandRule gfsh = new GfshCommandRule();

  @Rule
  public TestName testName = new SerializableTestName();

  @Rule
  public TemporaryFolder tmpDir = new TemporaryFolder();

  @BeforeClass
  public static void before() throws Exception {
    locator = lsRule.startLocatorVM(0);
    server1 = lsRule.startServerVM(1, "group1", locator.getPort());
    server2 = lsRule.startServerVM(2, "group2", locator.getPort());

    gfsh.connectAndVerify(locator);
  }

  @Test
  public void testCreateRegionWithGoodCompressor() throws Exception {
    String regionName = testName.getMethodName();
    gfsh.executeAndAssertThat("create region --name=" + regionName
        + " --type=REPLICATE --compressor=" + RegionEntryContext.DEFAULT_COMPRESSION_PROVIDER)
        .statusIsSuccess();

    server1.invoke(() -> {
      Cache cache = ClusterStartupRule.getCache();
      Region region = cache.getRegion(regionName);
      assertThat(region).isNotNull();
      assertThat(region.getAttributes().getCompressor())
          .isEqualTo(SnappyCompressor.getDefaultInstance());
    });
  }

  @Test
  public void testCreateRegionWithBadCompressor() throws Exception {
    String regionName = testName.getMethodName();
    gfsh.executeAndAssertThat(
        "create region --name=" + regionName + " --type=REPLICATE --compressor=BAD_COMPRESSOR")
        .statusIsError();

    server1.invoke(() -> {
      Cache cache = ClusterStartupRule.getCache();
      Region region = cache.getRegion(regionName);
      assertThat(region).isNull();
    });
  }

  @Test
  public void testCreateRegionWithNoCompressor() throws Exception {
    String regionName = testName.getMethodName();
    gfsh.executeAndAssertThat("create region --name=" + regionName + " --type=REPLICATE")
        .statusIsSuccess();

    server1.invoke(() -> {
      Cache cache = ClusterStartupRule.getCache();
      Region region = cache.getRegion(regionName);
      assertThat(region).isNotNull();
      assertThat(region.getAttributes().getCompressor()).isNull();
    });
  }

  @Test
  public void testCreateRegionWithPartitionResolver() throws Exception {
    String regionName = testName.getMethodName();
    String PR_STRING = "package io.pivotal; "
        + "public class TestPartitionResolver implements org.apache.geode.cache.PartitionResolver { "
        + "   @Override" + "   public void close() {" + "   }" + "   @Override"
        + "   public Object getRoutingObject(org.apache.geode.cache.EntryOperation opDetails) { "
        + "    return null; " + "   }" + "   @Override" + "   public String getName() { "
        + "    return \"TestPartitionResolver\";" + "   }" + " }";
    final File prJarFile = new File(tmpDir.getRoot(), "myPartitionResolver.jar");
    new JarBuilder().buildJar(prJarFile, PR_STRING);

    gfsh.executeAndAssertThat("deploy --jar=" + prJarFile.getAbsolutePath()).statusIsSuccess();

    gfsh.executeAndAssertThat("create region --name=" + regionName
        + " --type=PARTITION --partition-resolver=io.pivotal.TestPartitionResolver")
        .statusIsSuccess();

    server1.invoke(() -> {
      Cache cache = ClusterStartupRule.getCache();
      PartitionedRegion region = (PartitionedRegion) cache.getRegion(regionName);
      PartitionResolver resolver = region.getPartitionAttributes().getPartitionResolver();
      assertThat(resolver).isNotNull();
      assertThat(resolver.getName()).isEqualTo("TestPartitionResolver");
    });
  }

  @Test
  public void testCreateRegionWithInvalidPartitionResolver() throws Exception {
    gfsh.executeAndAssertThat("create region --name=" + testName.getMethodName()
        + " --type=PARTITION --partition-resolver=InvalidPartitionResolver").statusIsError();
  }

  @Test
  public void testCreateRegionForReplicatedRegionWithPartitionResolver() {
    String regionName = testName.getMethodName();
    gfsh.executeAndAssertThat("create region --name=" + regionName
        + " --type=REPLICATE --partition-resolver=InvalidPartitionResolver")
        .containsOutput("\"/" + regionName + "\" is not a Partitioned Region").statusIsError();
  }

  @Test
  public void overrideListenerFromTemplate() throws Exception {
    gfsh.executeAndAssertThat("create region --name=/TEMPLATE --type=PARTITION_REDUNDANT"
        + " --cache-listener=" + TestCacheListener.class.getName()).statusIsSuccess();

    gfsh.executeAndAssertThat("create region --name=/COPY --template-region=/TEMPLATE"
        + " --cache-listener=" + AnotherTestCacheListener.class.getName()).statusIsSuccess();

    server1.getVM().invoke(() -> {
      Region copy = ClusterStartupRule.getCache().getRegion("/COPY");

      assertThat(Arrays.stream(copy.getAttributes().getCacheListeners())
          .map(c -> c.getClass().getName()).collect(Collectors.toSet()))
              .containsExactly(AnotherTestCacheListener.class.getName());
    });

    gfsh.executeAndAssertThat("destroy region --name=/COPY").statusIsSuccess();
    gfsh.executeAndAssertThat("destroy region --name=/TEMPLATE").statusIsSuccess();
  }

  @Test
  public void ensureOverridingCallbacksFromTemplateDoNotRequireClassesOnLocator() throws Exception {
    final File prJarFile = new File(tmpDir.getRoot(), "myCacheListener.jar");
    new JarBuilder().buildJar(prJarFile, getUniversalClassCode("MyCallback"),
        getUniversalClassCode("MyNewCallback"));
    gfsh.executeAndAssertThat("deploy --jar=" + prJarFile.getAbsolutePath()).statusIsSuccess();

    String myCallback = "io.pivotal.MyCallback";
    gfsh.executeAndAssertThat(
        String
            .format(
                "create region --name=/TEMPLATE --type=PARTITION_REDUNDANT "
                    + "--cache-listener=%1$s " + "--cache-loader=%1$s " + "--cache-writer=%1$s "
                    + "--compressor=%1$s " + "--key-constraint=%1$s " + "--value-constraint=%1$s",
                myCallback))
        .statusIsSuccess();

    String myNewCallback = "io.pivotal.MyNewCallback";
    gfsh.executeAndAssertThat(String
        .format("create region --name=/COPY --template-region=/TEMPLATE " + "--cache-listener=%1$s "
            + "--cache-loader=%1$s " + "--cache-writer=%1$s " + "--compressor=%1$s "
            + "--key-constraint=%1$s " + "--value-constraint=%1$s", myNewCallback))
        .statusIsSuccess();

    server1.getVM().invoke(() -> {
      Region copy = ClusterStartupRule.getCache().getRegion("/COPY");

      assertThat(Arrays.stream(copy.getAttributes().getCacheListeners())
          .map(c -> c.getClass().getName()).collect(Collectors.toSet()))
              .containsExactly(myNewCallback);

      assertThat(copy.getAttributes().getCacheLoader().getClass().getName())
          .isEqualTo(myNewCallback);

      assertThat(copy.getAttributes().getCacheWriter().getClass().getName())
          .isEqualTo(myNewCallback);

      assertThat(copy.getAttributes().getCompressor().getClass().getName())
          .isEqualTo(myNewCallback);

      assertThat(copy.getAttributes().getKeyConstraint().getName()).isEqualTo(myNewCallback);

      assertThat(copy.getAttributes().getValueConstraint().getName()).isEqualTo(myNewCallback);
    });

    gfsh.executeAndAssertThat("destroy region --name=/COPY").statusIsSuccess();
    gfsh.executeAndAssertThat("destroy region --name=/TEMPLATE").statusIsSuccess();
  }

  @Test
  public void ensureNewCallbacksFromTemplateDoNotRequireClassesOnLocator() throws Exception {
    final File prJarFile = new File(tmpDir.getRoot(), "myCacheListener.jar");
    new JarBuilder().buildJar(prJarFile, getUniversalClassCode("MyNewCallback"));
    gfsh.executeAndAssertThat("deploy --jar=" + prJarFile.getAbsolutePath()).statusIsSuccess();

    gfsh.executeAndAssertThat("create region --name=/TEMPLATE --type=PARTITION_REDUNDANT")
        .statusIsSuccess();

    String myNewCallback = "io.pivotal.MyNewCallback";
    gfsh.executeAndAssertThat(String
        .format("create region --name=/COPY --template-region=/TEMPLATE " + "--cache-listener=%1$s "
            + "--cache-loader=%1$s " + "--cache-writer=%1$s " + "--compressor=%1$s "
            + "--key-constraint=%1$s " + "--value-constraint=%1$s", myNewCallback))
        .statusIsSuccess();

    server1.getVM().invoke(() -> {
      Region copy = ClusterStartupRule.getCache().getRegion("/COPY");

      assertThat(Arrays.stream(copy.getAttributes().getCacheListeners())
          .map(c -> c.getClass().getName()).collect(Collectors.toSet()))
              .containsExactly(myNewCallback);

      assertThat(copy.getAttributes().getCacheLoader().getClass().getName())
          .isEqualTo(myNewCallback);

      assertThat(copy.getAttributes().getCacheWriter().getClass().getName())
          .isEqualTo(myNewCallback);

      assertThat(copy.getAttributes().getCompressor().getClass().getName())
          .isEqualTo(myNewCallback);

      assertThat(copy.getAttributes().getKeyConstraint().getName()).isEqualTo(myNewCallback);

      assertThat(copy.getAttributes().getValueConstraint().getName()).isEqualTo(myNewCallback);
    });

    gfsh.executeAndAssertThat("destroy region --name=/COPY").statusIsSuccess();
    gfsh.executeAndAssertThat("destroy region --name=/TEMPLATE").statusIsSuccess();
  }

  @Test
  public void ensureOriginalCallbacksFromTemplateDoNotRequireClassesOnLocator() throws Exception {
    final File prJarFile = new File(tmpDir.getRoot(), "myCacheListener.jar");
    new JarBuilder().buildJar(prJarFile, getUniversalClassCode("MyCallback"));
    gfsh.executeAndAssertThat("deploy --jar=" + prJarFile.getAbsolutePath()).statusIsSuccess();

    String myCallback = "io.pivotal.MyCallback";
    gfsh.executeAndAssertThat(
        String
            .format(
                "create region --name=/TEMPLATE --type=PARTITION_REDUNDANT "
                    + "--cache-listener=%1$s " + "--cache-loader=%1$s " + "--cache-writer=%1$s "
                    + "--compressor=%1$s " + "--key-constraint=%1$s " + "--value-constraint=%1$s",
                myCallback))
        .statusIsSuccess();

    gfsh.executeAndAssertThat("create region --name=/COPY --template-region=/TEMPLATE")
        .statusIsSuccess();

    server1.getVM().invoke(() -> {
      Region copy = ClusterStartupRule.getCache().getRegion("/COPY");

      assertThat(Arrays.stream(copy.getAttributes().getCacheListeners())
          .map(c -> c.getClass().getName()).collect(Collectors.toSet()))
              .containsExactly(myCallback);

      assertThat(copy.getAttributes().getCacheLoader().getClass().getName()).isEqualTo(myCallback);

      assertThat(copy.getAttributes().getCacheWriter().getClass().getName()).isEqualTo(myCallback);

      assertThat(copy.getAttributes().getCompressor().getClass().getName()).isEqualTo(myCallback);

      assertThat(copy.getAttributes().getKeyConstraint().getName()).isEqualTo(myCallback);

      assertThat(copy.getAttributes().getValueConstraint().getName()).isEqualTo(myCallback);
    });

    gfsh.executeAndAssertThat("destroy region --name=/COPY").statusIsSuccess();
    gfsh.executeAndAssertThat("destroy region --name=/TEMPLATE").statusIsSuccess();
  }

  @Test
  public void startWithNonProxyRegion() {
    String regionName = testName.getMethodName();
    gfsh.executeAndAssertThat("create region --type=REPLICATE --group=group1 --name=" + regionName)
        .statusIsSuccess().tableHasRowWithValues("Member", "server-1");
    gfsh.executeAndAssertThat("create region --type=REPLICATE --group=group2 --name=" + regionName)
        .statusIsError().containsOutput("Region /" + regionName + " already exists on the cluster");

    gfsh.executeAndAssertThat("create region --type=PARTITION --group=group2 --name=" + regionName)
        .statusIsError().containsOutput("Region /" + regionName + " already exists on the cluster");

    gfsh.executeAndAssertThat(
        "create region --type=REPLICATE_PROXY --group=group2 --name=" + regionName)
        .statusIsSuccess().tableHasRowWithValues("Member", "server-2");

    locator.waitTillRegionsAreReadyOnServers("/" + regionName, 2);

    gfsh.executeAndAssertThat(
        "create region --type=PARTITION_PROXY --group=group2 --name=" + regionName).statusIsError()
        .containsOutput("The existing region is not a partitioned region");
  }

  @Test
  public void startWithReplicateProxyRegion() {
    String regionName = testName.getMethodName();
    gfsh.executeAndAssertThat(
        "create region --type=REPLICATE_PROXY --group=group1 --name=" + regionName)
        .statusIsSuccess().tableHasRowWithValues("Member", "server-1");
    gfsh.executeAndAssertThat("create region --type=REPLICATE --group=group2 --name=" + regionName)
        .statusIsSuccess().tableHasRowWithValues("Member", "server-2");

    locator.waitTillRegionsAreReadyOnServers("/" + regionName, 2);
    // the following two should fail with name check on locator, not on server
    gfsh.executeAndAssertThat("create region --type=PARTITION --group=group2 --name=" + regionName)
        .statusIsError().containsOutput("Region /" + regionName + " already exists on the cluster");
    gfsh.executeAndAssertThat(
        "create region --type=PARTITION_PROXY --group=group2 --name=" + regionName).statusIsError()
        .containsOutput("The existing region is not a partitioned region");
  }

  @Test
  public void startWithReplicateProxyThenPartitionRegion() {
    String regionName = testName.getMethodName();
    gfsh.executeAndAssertThat(
        "create region --type=REPLICATE_PROXY --group=group1 --name=" + regionName)
        .statusIsSuccess().tableHasRowWithValues("Member", "server-1");
    gfsh.executeAndAssertThat("create region --type=PARTITION --group=group2 --name=" + regionName)
        .statusIsError().containsOutput("The existing region is not a partitioned region");
    gfsh.executeAndAssertThat(
        "create region --type=PARTITION_PROXY --group=group2 --name=" + regionName).statusIsError()
        .containsOutput("The existing region is not a partitioned region");
    gfsh.executeAndAssertThat("create region --type=LOCAL --group=group2 --name=" + regionName)
        .statusIsError();
  }

  @Test
  public void startWithPartitionProxyThenReplicate() {
    String regionName = testName.getMethodName();
    gfsh.executeAndAssertThat(
        "create region --type=PARTITION_PROXY --group=group1 --name=" + regionName)
        .statusIsSuccess().tableHasRowWithValues("Member", "server-1");
    gfsh.executeAndAssertThat("create region --type=REPLICATE --group=group2 --name=" + regionName)
        .statusIsError().containsOutput("The existing region is not a replicate region");
    gfsh.executeAndAssertThat(
        "create region --type=REPLICATE_PROXY --group=group2 --name=" + regionName).statusIsError()
        .containsOutput("The existing region is not a replicate region");
    gfsh.executeAndAssertThat("create region --type=LOCAL --group=group2 --name=" + regionName)
        .statusIsError();
  }

  @Test
  public void startWithLocalPersistent() {
    String regionName = testName.getMethodName();
    gfsh.executeAndAssertThat(
        "create region --type=LOCAL_PERSISTENT --group=group1 --name=" + regionName)
        .statusIsSuccess().tableHasRowWithValues("Member", "server-1");
    gfsh.executeAndAssertThat("create region --type=REPLICATE --group=group2 --name=" + regionName)
        .statusIsError()
        .containsOutput("Region /startWithLocalPersistent already exists on the cluster");
    gfsh.executeAndAssertThat("create region --type=LOCAL --group=group2 --name=" + regionName)
        .statusIsError()
        .containsOutput("Region /startWithLocalPersistent already exists on the cluster");
  }

  @Test
  public void startWithReplicateHeapLRU() {
    String regionName = testName.getMethodName();
    gfsh.executeAndAssertThat(
        "create region --type=REPLICATE_HEAP_LRU --group=group1 --name=" + regionName)
        .statusIsSuccess().tableHasRowWithValues("Member", "server-1");
    gfsh.executeAndAssertThat("create region --type=REPLICATE --group=group2 --name=" + regionName)
        .statusIsError().containsOutput("already exists on the cluster");
    gfsh.executeAndAssertThat("create region --type=LOCAL --group=group2 --name=" + regionName)
        .statusIsError().containsOutput("already exists on the cluster");
    gfsh.executeAndAssertThat(
        "create region --type=REPLICATE_PROXY --group=group2 --name=" + regionName)
        .statusIsSuccess();
  }

  @Test
  public void startWithReplicateThenPartition() {
    String regionName = testName.getMethodName();
    gfsh.executeAndAssertThat("create region --type=REPLICATE --group=group1 --name=" + regionName)
        .statusIsSuccess().tableHasRowWithValues("Member", "server-1");
    gfsh.executeAndAssertThat("create region --type=PARTITION --group=group2 --name=" + regionName)
        .statusIsError().containsOutput("already exists on the cluster");
    gfsh.executeAndAssertThat(
        "create region --type=PARTITION_PROXY --group=group2 --name=" + regionName).statusIsError()
        .containsOutput("The existing region is not a partitioned region");
    gfsh.executeAndAssertThat("create region --type=LOCAL --group=group2 --name=" + regionName)
        .statusIsError().containsOutput("already exists on the cluster");
    gfsh.executeAndAssertThat(
        "create region --type=REPLICATE_PROXY --group=group1 --name=" + regionName).statusIsError()
        .containsOutput("already exists on these members: server-1");
  }

  @Test
  public void startWithPartitionThenReplicate() {
    String regionName = testName.getMethodName();
    gfsh.executeAndAssertThat("create region --type=PARTITION --group=group1 --name=" + regionName)
        .statusIsSuccess().tableHasRowWithValues("Member", "server-1");
    gfsh.executeAndAssertThat("create region --type=REPLICATE --group=group2 --name=" + regionName)
        .statusIsError().containsOutput("already exists on the cluster");
    gfsh.executeAndAssertThat(
        "create region --type=REPLICATE_PROXY --group=group2 --name=" + regionName).statusIsError()
        .containsOutput("The existing region is not a replicate region");
    gfsh.executeAndAssertThat("create region --type=LOCAL --group=group2 --name=" + regionName)
        .statusIsError().containsOutput("already exists on the cluster");
    gfsh.executeAndAssertThat(
        "create region --type=PARTITION_PROXY --group=group1 --name=" + regionName).statusIsError()
        .containsOutput("already exists on these members: server-1");
  }

  @Test
  public void startWithPartitionProxyRegion() {
    String regionName = testName.getMethodName();
    gfsh.executeAndAssertThat(
        "create region --type=PARTITION_PROXY --group=group1 --name=" + regionName)
        .statusIsSuccess().tableHasRowWithValues("Member", "server-1");
    gfsh.executeAndAssertThat("create region --type=PARTITION --group=group1 --name=" + regionName)
        .statusIsError().containsOutput("already exists on these members: server-1");
    gfsh.executeAndAssertThat("create region --type=PARTITION --group=group2 --name=" + regionName)
        .statusIsSuccess().tableHasRowWithValues("Member", "server-2");

    locator.waitTillRegionsAreReadyOnServers("/" + regionName, 2);
    gfsh.executeAndAssertThat("create region --type=PARTITION --group=group2 --name=" + regionName)
        .statusIsError().containsOutput("Region /" + regionName + " already exists on the cluster");
    gfsh.executeAndAssertThat(
        "create region --type=REPLICATE_PROXY --group=group2 --name=" + regionName).statusIsError()
        .containsOutput("The existing region is not a replicate region");
    gfsh.executeAndAssertThat(
        "create region --type=PARTITION_PROXY --group=group1 --name=" + regionName).statusIsError()
        .containsOutput("already exists on these members: server-1");
  }

  @Test
  public void startWithLocalRegion() {
    String regionName = testName.getMethodName();
    gfsh.executeAndAssertThat("create region --type=LOCAL --group=group1 --name=" + regionName)
        .statusIsSuccess();
    gfsh.executeAndAssertThat("create region --type=REPLICATE --name=" + regionName).statusIsError()
        .containsOutput("Region /startWithLocalRegion already exists on the cluster.");
    gfsh.executeAndAssertThat("create region --type=PARTITION --group=group2 --name=" + regionName)
        .statusIsError()
        .containsOutput("Region /startWithLocalRegion already exists on the cluster.");
    gfsh.executeAndAssertThat(
        "create region --type=PARTITION_PROXY --group=group2 --name=" + regionName).statusIsError()
        .containsOutput("Region /startWithLocalRegion already exists on the cluster");
  }

  private String getUniversalClassCode(String classname) {
    String code = "package io.pivotal;" + "import org.apache.geode.cache.CacheLoader;"
        + "import org.apache.geode.cache.CacheLoaderException;"
        + "import org.apache.geode.cache.CacheWriter;"
        + "import org.apache.geode.cache.CacheWriterException;"
        + "import org.apache.geode.cache.EntryEvent;"
        + "import org.apache.geode.cache.LoaderHelper;"
        + "import org.apache.geode.cache.RegionEvent;"
        + "import org.apache.geode.cache.util.CacheListenerAdapter;"
        + "import org.apache.geode.compression.Compressor;" + "public class " + classname
        + " extends CacheListenerAdapter "
        + "implements CacheWriter, CacheLoader, Compressor, java.io.Serializable {"
        + "public void beforeUpdate(EntryEvent event) throws CacheWriterException {}"
        + "public void beforeCreate(EntryEvent event) throws CacheWriterException {}"
        + "public void beforeDestroy(EntryEvent event) throws CacheWriterException {}"
        + "public void beforeRegionDestroy(RegionEvent event) throws CacheWriterException {}"
        + "public void beforeRegionClear(RegionEvent event) throws CacheWriterException {}"
        + "public Object load(LoaderHelper helper) throws CacheLoaderException { return null; }"
        + "public byte[] compress(byte[] input) { return new byte[0]; }"
        + "public byte[] decompress(byte[] input) { return new byte[0]; }" + "}";
    return code;
  }

}
