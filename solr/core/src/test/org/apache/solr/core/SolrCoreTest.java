/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.core;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.ExecutorUtil;
import org.apache.solr.common.util.SolrNamedThreadFactory;
import org.apache.solr.handler.ReplicationHandler;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.SpellCheckComponent;
import org.apache.solr.metrics.SolrMetricManager;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.update.SolrCoreState;
import org.apache.solr.util.RefCounted;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.junit.Test;

public class SolrCoreTest extends SolrTestCaseJ4 {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initCore("solrconfig.xml", "schema.xml");
  }

  @Override
  public void tearDown() throws Exception {
    deleteCore();
    super.tearDown();
  }

  @Test
  public void testRequestHandlerRegistry() {
    SolrCore core = h.getCore();

    EmptyRequestHandler handler1 = new EmptyRequestHandler();
    EmptyRequestHandler handler2 = new EmptyRequestHandler();

    String path = "/this/is A path /that won't be registered!";
    SolrRequestHandler old = core.registerRequestHandler(path, handler1);
    assertNull(old); // should not be anything...
    assertEquals(core.getRequestHandlers().get(path), handler1);
    old = core.registerRequestHandler(path, handler2);
    assertEquals(old, handler1); // should pop out the old one
    assertEquals(core.getRequestHandlers().get(path), handler2);
  }

  @Test
  public void testImplicitPlugins() {
    final SolrCore core = h.getCore();
    final List<PluginInfo> implicitHandlers = core.getImplicitHandlers();

    final Map<String, String> pathToClassMap = new HashMap<>(implicitHandlers.size());
    for (PluginInfo implicitHandler : implicitHandlers) {
      assertEquals(
          "wrong type for " + implicitHandler.toString(),
          SolrRequestHandler.TYPE,
          implicitHandler.type);
      pathToClassMap.put(implicitHandler.name, implicitHandler.className);
    }

    int ihCount = 0;
    {
      ++ihCount;
      assertEquals(pathToClassMap.get("/admin/file"), "solr.ShowFileRequestHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/admin/luke"), "solr.LukeRequestHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/admin/mbeans"), "solr.SolrInfoMBeanHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/admin/ping"), "solr.PingRequestHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/admin/plugins"), "solr.PluginInfoHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/admin/segments"), "solr.SegmentsInfoRequestHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/admin/system"), "solr.SystemInfoHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/config"), "solr.SolrConfigHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/export"), "solr.ExportHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/terms"), "solr.SearchHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/get"), "solr.RealTimeGetHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get(ReplicationHandler.PATH), "solr.ReplicationHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/schema"), "solr.SchemaHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/sql"), "solr.SQLHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/stream"), "solr.StreamHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/graph"), "solr.GraphHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/update"), "solr.UpdateRequestHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/update/csv"), "solr.UpdateRequestHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/update/json"), "solr.UpdateRequestHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/update/json/docs"), "solr.UpdateRequestHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/update/cbor"), "solr.UpdateRequestHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/analysis/document"), "solr.DocumentAnalysisRequestHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/analysis/field"), "solr.FieldAnalysisRequestHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/debug/dump"), "solr.DumpRequestHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("update"), "solr.V2UpdateRequestHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/tasks/cancel"), "solr.QueryCancellationHandler");
      ++ihCount;
      assertEquals(pathToClassMap.get("/tasks/list"), "solr.ActiveTasksListHandler");
    }
    assertEquals("wrong number of implicit handlers", ihCount, implicitHandlers.size());
  }

  @Test
  public void testClose() {
    final CoreContainer cores = h.getCoreContainer();
    SolrCore core = cores.getCore(SolrTestCaseJ4.DEFAULT_TEST_CORENAME);

    ClosingRequestHandler handler1 = new ClosingRequestHandler();
    handler1.inform(core);

    String path = "/this/is A path /that won't be registered 2!!!!!!!!!!!";
    SolrRequestHandler old = core.registerRequestHandler(path, handler1);
    assertNull(old); // should not be anything...
    assertEquals(core.getRequestHandlers().get(path), handler1);
    core.close();
    cores.shutdown();
    assertTrue("Handler not closed", handler1.closed);
  }

  @Test
  public void testRefCount() {
    SolrCore core = h.getCore();
    assertEquals("Refcount != 1", 1, core.getOpenCount());

    final CoreContainer cores = h.getCoreContainer();
    SolrCore c1 = cores.getCore(SolrTestCaseJ4.DEFAULT_TEST_CORENAME);
    assertEquals("Refcount != 2", 2, core.getOpenCount());

    ClosingRequestHandler handler1 = new ClosingRequestHandler();
    handler1.inform(core);

    String path = "/this/is A path /that won't be registered!";
    SolrRequestHandler old = core.registerRequestHandler(path, handler1);
    assertNull(old); // should not be anything...
    assertEquals(core.getRequestHandlers().get(path), handler1);

    SolrCore c2 = cores.getCore(SolrTestCaseJ4.DEFAULT_TEST_CORENAME);
    c1.close();
    assertTrue("Refcount < 1", core.getOpenCount() >= 1);
    assertFalse("Handler is closed", handler1.closed);

    c1 = cores.getCore(SolrTestCaseJ4.DEFAULT_TEST_CORENAME);
    assertTrue("Refcount < 2", core.getOpenCount() >= 2);
    assertFalse("Handler is closed", handler1.closed);

    c2.close();
    assertTrue("Refcount < 1", core.getOpenCount() >= 1);
    assertFalse("Handler is closed", handler1.closed);

    c1.close();
    cores.shutdown();
    assertEquals("Refcount != 0", 0, core.getOpenCount());
    assertTrue("Handler not closed", core.isClosed() && handler1.closed == true);
  }

  @Test
  public void testRefCountMT() throws Exception {
    SolrCore core = h.getCore();
    assertEquals("Refcount != 1", 1, core.getOpenCount());

    final ClosingRequestHandler handler1 = new ClosingRequestHandler();
    handler1.inform(core);
    String path = "/this/is A path /that won't be registered!";
    SolrRequestHandler old = core.registerRequestHandler(path, handler1);
    assertNull(old); // should not be anything...
    assertEquals(core.getRequestHandlers().get(path), handler1);

    final int LOOP = 100;
    final int MT = 16;
    ExecutorService service =
        ExecutorUtil.newMDCAwareFixedThreadPool(MT, new SolrNamedThreadFactory("refCountMT"));
    List<Callable<Integer>> callees = new ArrayList<>(MT);
    final CoreContainer cores = h.getCoreContainer();
    for (int i = 0; i < MT; ++i) {
      Callable<Integer> call =
          new Callable<Integer>() {
            void yieldInt(int n) {
              try {
                Thread.sleep(0, (n % 13 + 1) * 10);
              } catch (InterruptedException xint) {
              }
            }

            @Override
            public Integer call() {
              SolrCore core = null;
              int r = 0;
              try {
                for (int l = 0; l < LOOP; ++l) {
                  r += 1;
                  core = cores.getCore(SolrTestCaseJ4.DEFAULT_TEST_CORENAME);
                  // sprinkle concurrency hinting...
                  yieldInt(l);
                  assertTrue("Refcount < 1", core.getOpenCount() >= 1);
                  yieldInt(l);
                  assertTrue("Refcount > 17", core.getOpenCount() <= 17);
                  yieldInt(l);
                  assertFalse("Handler is closed", handler1.closed);
                  yieldInt(l);
                  core.close();
                  core = null;
                  yieldInt(l);
                }
                return r;
              } finally {
                if (core != null) core.close();
              }
            }
          };
      callees.add(call);
    }

    List<Future<Integer>> results = service.invokeAll(callees);
    for (Future<Integer> result : results) {
      assertTrue("loop=" + result.get() + " < " + LOOP, result.get() >= LOOP);
    }

    cores.shutdown();
    assertEquals("Refcount != 0", 0, core.getOpenCount());
    assertTrue("Handler not closed", core.isClosed() && handler1.closed == true);

    service.shutdown();
    assertTrue("Running for too long...", service.awaitTermination(60, TimeUnit.SECONDS));
  }

  @Test
  public void testInfoRegistry() {
    // TEst that SolrInfoMBeans are registered, including SearchComponents
    SolrCore core = h.getCore();

    Map<String, SolrInfoBean> infoRegistry = core.getInfoRegistry();
    assertTrue(
        "infoRegistry Size: " + infoRegistry.size() + " is not greater than: " + 0,
        infoRegistry.size() > 0);
    // try out some that we know are in the config
    SolrInfoBean bean = infoRegistry.get(SpellCheckComponent.COMPONENT_NAME);
    assertNotNull("bean not registered", bean);
    // try a default one
    bean = infoRegistry.get(QueryComponent.COMPONENT_NAME);
    assertNotNull("bean not registered", bean);
    // try a Req Handler, which are stored by name, not clas
    bean = infoRegistry.get("/select");
    assertNotNull("bean not registered", bean);
  }

  @Test
  public void testConfiguration() {
    assertEquals(
        "wrong config for slowQueryThresholdMillis", 2000, solrConfig.slowQueryThresholdMillis);
    assertEquals("wrong config for maxBooleanClauses", 1024, solrConfig.booleanQueryMaxClauseCount);
    assertTrue("wrong config for enableLazyFieldLoading", solrConfig.enableLazyFieldLoading);
    assertEquals("wrong config for queryResultWindowSize", 10, solrConfig.queryResultWindowSize);
  }

  /**
   * Test that's meant to be run with many iterations to expose a leak of SolrIndexSearcher when a
   * core is closed due to a reload. Without the fix, this test fails with most iters=1000 runs.
   */
  @Test
  public void testReloadLeak() throws Exception {
    final ExecutorService executor =
        ExecutorUtil.newMDCAwareFixedThreadPool(1, new SolrNamedThreadFactory("testReloadLeak"));

    // Continuously open new searcher while core is not closed, and reload core to try to reproduce
    // searcher leak. While in practice we never continuously open new searchers, this is trying to
    // make up for the fact that opening a searcher in this empty core is very fast by opening new
    // searchers continuously to increase the likelihood for race.
    SolrCore core = h.getCore();
    assertEquals("Refcount != 1", 1, core.getOpenCount());
    executor.execute(new NewSearcherRunnable(core));

    // Since we called getCore() vs getCoreInc() and don't own a refCount, the container should
    // decRef the core and close it when we call reload.
    h.reload();

    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.MINUTES);

    // Check that all cores are closed and no searcher references are leaked.
    assertTrue("SolrCore " + core + " is not closed", core.isClosed());
    assertTrue(core.areAllSearcherReferencesEmpty());
  }

  /**
   * Best effort attempt to recreate a deadlock between SolrCore initialization and Index metrics
   * poll.
   *
   * <p>See https://issues.apache.org/jira/browse/SOLR-17060
   */
  @Test
  public void testCoreInitDeadlockMetrics() throws Exception {
    SolrMetricManager metricManager = h.getCoreContainer().getMetricManager();
    CoreContainer coreContainer = h.getCoreContainer();

    String coreName = "tmpCore";
    AtomicBoolean created = new AtomicBoolean(false);
    AtomicBoolean atLeastOnePoll = new AtomicBoolean(false);

    final ExecutorService executor =
        ExecutorUtil.newMDCAwareFixedThreadPool(
            1, new SolrNamedThreadFactory("testCoreInitDeadlockMetrics"));
    executor.submit(
        () -> {
          while (!created.get()) {
            var metrics =
                metricManager.getMetrics(
                    "solr.core." + coreName,
                    MetricFilter.startsWith(SolrInfoBean.Category.INDEX.toString()));
            for (var m : metrics.values()) {
              if (m instanceof Gauge) {
                var v = ((Gauge<?>) m).getValue();
                atLeastOnePoll.compareAndSet(false, v != null);
              }
            }

            try {
              TimeUnit.MILLISECONDS.sleep(5);
            } catch (InterruptedException e1) {
              throw new RuntimeException(e1);
            }
          }
        });

    TimeUnit.MILLISECONDS.sleep(25);
    try (var tmpCore = coreContainer.create(coreName, Map.of("configSet", "minimal"))) {
      tmpCore.open();
      for (int i = 0; i < 10; i++) {
        TimeUnit.MILLISECONDS.sleep(50); // to allow metrics to be checked at least once
        if (atLeastOnePoll.get()) {
          break;
        }
      }
    } finally {
      created.set(true);
      ExecutorUtil.shutdownAndAwaitTermination(executor);
    }
    assertTrue(atLeastOnePoll.get());
  }

  private static class NewSearcherRunnable implements Runnable {
    private final SolrCore core;

    NewSearcherRunnable(SolrCore core) {
      this.core = core;
    }

    @Override
    public void run() {
      while (!core.isClosed()) {
        try {
          RefCounted<SolrIndexSearcher> newSearcher = null;
          try {
            newSearcher = core.openNewSearcher(true, true);
          } catch (SolrCoreState.CoreIsClosedException e) {
            // closed
          } finally {
            if (newSearcher != null) {
              newSearcher.decref();
            }
          }
        } catch (SolrException e) {
          if (!core.isClosed()) {
            throw e;
          }
        }
      }
    }
  }
}

class ClosingRequestHandler extends EmptyRequestHandler implements SolrCoreAware {
  boolean closed = false;

  @Override
  public void inform(SolrCore core) {
    core.addCloseHook(
        new CloseHook() {
          @Override
          public void preClose(SolrCore core) {
            closed = true;
          }
        });
  }
}

/** An empty handler for testing */
class EmptyRequestHandler extends RequestHandlerBase {
  @Override
  public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) {
    // nothing!
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public Name getPermissionName(AuthorizationContext request) {
    return Name.ALL;
  }
}
