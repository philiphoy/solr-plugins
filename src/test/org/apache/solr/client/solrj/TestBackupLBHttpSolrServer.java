package org.apache.solr.client.solrj;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.FileUtils;
import org.apache.http.client.HttpClient;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.LuceneTestCase.Slow;
import org.apache.lucene.util.QuickPatchThreadsFilter;
import org.apache.solr.SolrIgnoredThreadsFilter;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.client.solrj.impl.BackupRequestLBHttpSolrServer;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.impl.InflightRequestMonitor;
import org.apache.solr.client.solrj.impl.LBHttpSolrServer;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.ExecutorUtil;
import org.apache.solr.util.AbstractSolrTestCase;
import org.apache.solr.util.DefaultSolrThreadFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;


import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;

/**
 * Test for LBHttpSolrServer
 *
 * @since solr 1.4
 */
@Slow
@ThreadLeakFilters(defaultFilters = true, filters = {
    SolrIgnoredThreadsFilter.class, QuickPatchThreadsFilter.class})
public class TestBackupLBHttpSolrServer extends LuceneTestCase {
  HashMap<String,SolrInstance> solr;
  HttpClient httpClient;
  ThreadPoolExecutor commExecutor;
  InflightRequestMonitor inFlightRequestMonitor;
  private BackupRequestLBHttpSolrServer lbSolrServer;
  // TODO: fix this test to not require FSDirectory
  static String savedFactory;

  @BeforeClass
  public static void beforeClass() {
    savedFactory = System.getProperty("solr.DirectoryFactory");
    System.setProperty("solr.directoryFactory",
        "org.apache.solr.core.MockFSDirectoryFactory");
  }

  @AfterClass
  public static void afterClass() {
    if (savedFactory == null) {
      System.clearProperty("solr.directoryFactory");
    } else {
      System.setProperty("solr.directoryFactory", savedFactory);
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    httpClient = HttpClientUtil.createClient(null);
    HttpClientUtil.setConnectionTimeout(httpClient, 1000);
    inFlightRequestMonitor = new InflightRequestMonitor(false);
    solr = new HashMap<String,SolrInstance>();
    commExecutor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 5,
        TimeUnit.SECONDS, // terminate idle threads after 5 sec
        new SynchronousQueue<Runnable>(), // directly hand off tasks
        new DefaultSolrThreadFactory("TestBackupLBHttpSolrServer"));
  }

  private void addDocs(SolrInstance solrInstance) throws IOException,
      SolrServerException {
    List<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();
    for (int i = 0; i < 10; i++) {
      SolrInputDocument doc = new SolrInputDocument();
      doc.addField("id", i);
      doc.addField("name", solrInstance.name);
      docs.add(doc);
    }
    HttpSolrServer solrServer = new HttpSolrServer(solrInstance.getUrl(), httpClient);
    UpdateResponse resp = solrServer.add(docs);
    assertEquals(0, resp.getStatus());
    resp = solrServer.commit();
    assertEquals(0, resp.getStatus());
  }

  @Override
  public void tearDown() throws Exception {
    ExecutorUtil.shutdownNowAndAwaitTermination(commExecutor);
    if (lbSolrServer != null) lbSolrServer.shutdown();
    for (SolrInstance aSolr : solr.values()) {
      aSolr.tearDown();
    }
    httpClient.getConnectionManager().shutdown();
    super.tearDown();
  }

  public void testConcurrentRequestsHitAllServers() throws Exception {
    int concurrentRequests = 5;
    lbSolrServer = new BackupRequestLBHttpSolrServer(httpClient, commExecutor, inFlightRequestMonitor, concurrentRequests, 0, false);
    List<String> serverList = new ArrayList<String>();
    for (int i = 0; i < concurrentRequests; i++) {
      SolrInstance si = new SolrInstance("solr/collection1" + i, 0);
      si.setUp();
      si.startJetty();
      serverList.add(si.getUrl());
      addDocs(si);
      solr.put("solr/collection1" + i, si);
    }

    SolrQuery solrQuery = new SolrQuery("*:*");
    QueryResponse resp = null;
    QueryRequest request = new QueryRequest(solrQuery);

    long requestCountBeforeRequest = 0;
    for (SolrInstance si : solr.values()) {
      requestCountBeforeRequest += si.jetty.getDebugFilter().getTotalRequests();
    }

    resp = submitRequest(lbSolrServer, serverList, request);
    while(commExecutor.getActiveCount()>0) Thread.sleep(1); //give servers a chance;

    assertEquals(10, resp.getResults().getNumFound());

    long requestCountAfterRequest = 0;
    for (SolrInstance si : solr.values()) {
      requestCountAfterRequest += si.jetty.getDebugFilter().getTotalRequests();
    }

    assertEquals(requestCountAfterRequest - requestCountBeforeRequest,
        concurrentRequests);
  }

  public void testTimeoutExceededTurnsServerZombie() throws Exception {

    LBHttpSolrServer lbSolrServer = new BackupRequestLBHttpSolrServer(
        httpClient, commExecutor,inFlightRequestMonitor , 3, 250, false);
    List<String> serverList = new ArrayList<String>();

    SolrInstance slow = new SolrInstance("solr/slow", 0);
    slow.setUp();
    SlowFilter slowFilter = new SlowFilter(750);
    slow.startJetty(slowFilter);
    serverList.add(slow.getUrl());
    addDocs(slow);
    solr.put("solr/slow", slow);

    SolrInstance si = new SolrInstance("solr/normal", 0);
    si.setUp();
    si.startJetty();
    serverList.add(si.getUrl());
    addDocs(si);
    solr.put("solr/normal", si);

    HttpClientUtil.setSoTimeout(httpClient, 500);

    SolrQuery solrQuery = new SolrQuery("*:*");
    QueryResponse resp = null;
    QueryRequest request = new QueryRequest(solrQuery);

    resp = submitRequest(lbSolrServer, serverList, request);

    assertEquals(10, resp.getResults().getNumFound());
    String name = resp.getResults().get(0).getFieldValue("name").toString();
    assertEquals("solr/normal", name);

    while(commExecutor.getActiveCount()>0) Thread.sleep(1); // allow timeout to be exceeded.

    slowFilter.setSleepTime(0);

    long requestCountBeforeRequest = slow.jetty.getDebugFilter()
        .getTotalRequests();

    resp = submitRequest(lbSolrServer, serverList, request);
    assertEquals(10, resp.getResults().getNumFound());
    name = resp.getResults().get(0).getFieldValue("name").toString();

    assertEquals(requestCountBeforeRequest, slow.jetty.getDebugFilter()
        .getTotalRequests());

    assertEquals("solr/normal", name);

  }

  public void testLeastBusyServerHit() throws Exception {
        inFlightRequestMonitor = new InflightRequestMonitor(false);
        LBHttpSolrServer lbSolrServer = new BackupRequestLBHttpSolrServer(
            httpClient, commExecutor,inFlightRequestMonitor, 3, 100, true);
        HttpClientUtil.setSoTimeout(httpClient, 1200);
    
        List<String> serverList = new ArrayList<String>();
        SolrInstance si = new SolrInstance("solr/collectionreallyslow", 0);
        
        si.setUp();
        SlowFilter reallySlowFilter = new SlowFilter(500);
        si.startJetty(reallySlowFilter);
        serverList.add(si.getUrl());
        addDocs(si);
        solr.put("solr/collectionreallyslow", si);
        
        si = new SolrInstance("solr/collectionslow", 0);
        si.setUp();
        SlowFilter slowFilter = new SlowFilter(250);
        si.startJetty(slowFilter);
        serverList.add(si.getUrl());
        addDocs(si);
        solr.put("solr/collectionslow", si);
        
        si = new SolrInstance("solr/collectionfast", 0);
        si.setUp();
        SlowFilter fastFilter = new SlowFilter(0);
        si.startJetty(fastFilter);
        serverList.add(si.getUrl());
        addDocs(si);
        solr.put("solr/collectionfast", si);
        
        
        SolrQuery solrQuery = new SolrQuery("*:*");
        QueryResponse resp = null;
        QueryRequest request = new QueryRequest(solrQuery);    
        
        resp = submitRequest(lbSolrServer, serverList, request);        
        String name = resp.getResults().get(0).getFieldValue("name").toString();
        assertEquals("solr/collectionfast", name);
        //two in flight        
        slowFilter.SleepTime=0; 
        //help one out        
        while(commExecutor.getActiveCount()>0) Thread.sleep(1);    
        resp = submitRequest(lbSolrServer, serverList, request);
        name = resp.getResults().get(0).getFieldValue("name").toString();
        assertEquals("solr/collectionslow", name);
        
        reallySlowFilter.SleepTime=0; 
        //help another out        
        while(commExecutor.getActiveCount()>0) Thread.sleep(1);    
        resp = submitRequest(lbSolrServer, serverList, request);
        name = resp.getResults().get(0).getFieldValue("name").toString();
        assertEquals("solr/collectionreallyslow", name);
        
  }
  
  public void testTimeoutExceeded() throws Exception {

    LBHttpSolrServer lbSolrServer = new BackupRequestLBHttpSolrServer(
        httpClient, commExecutor,inFlightRequestMonitor, 2, 0, true);
    List<String> serverList = new ArrayList<String>();
    SlowFilter slowFilter = new SlowFilter(500);

    for (int i = 0; i < 2; i++) {
      SolrInstance si = new SolrInstance("solr/collection1" + i, 0);
      si.setUp();
      si.startJetty(slowFilter);
      serverList.add(si.getUrl());
      addDocs(si);
      solr.put("solr/collection1" + i, si);
    }

    SolrQuery solrQuery = new SolrQuery("*:*");
    QueryResponse resp = null;
    QueryRequest request = new QueryRequest(solrQuery);

    try {
      HttpClientUtil.setSoTimeout(httpClient, 250);
      resp = submitRequest(lbSolrServer, serverList, request);
    } catch (SolrServerException ex) {
      assertNotNull(ex);
    }
    HttpClientUtil.setSoTimeout(httpClient, 0);
    slowFilter.setSleepTime(0);
    while(commExecutor.getActiveCount()>0) Thread.sleep(1);
    resp = submitRequest(lbSolrServer, serverList, request);
    assertEquals(10, resp.getResults().getNumFound());

  }

  public void testBackupRequest() throws Exception {

    LBHttpSolrServer lbSolrServer = new BackupRequestLBHttpSolrServer(
        httpClient, commExecutor,inFlightRequestMonitor, 2, 200, false);
    List<String> serverList = new ArrayList<String>();

    SolrInstance slow = new SolrInstance("solr/collection10", 0);
    slow.setUp();
    SlowFilter slowFilter = new SlowFilter(300);
    slow.startJetty(slowFilter);
    serverList.add(slow.getUrl());
    addDocs(slow);
    solr.put("solr/collection10", slow);

    SolrInstance fast = new SolrInstance("solr/collection11", 0);
    fast.setUp();
    fast.startJetty();
    serverList.add(fast.getUrl());
    addDocs(fast);
    solr.put("solr/collection11", fast);

    SolrQuery solrQuery = new SolrQuery("*:*");
    QueryResponse resp = null;
    QueryRequest request = new QueryRequest(solrQuery);
    long requestCountBeforeRequest = slow.jetty.getDebugFilter() .getTotalRequests();

    resp = submitRequest(lbSolrServer, serverList, request);
    assertEquals(10, resp.getResults().getNumFound());
    String name = resp.getResults().get(0).getFieldValue("name").toString();
    assertEquals("solr/collection11", name);

    while(commExecutor.getActiveCount()>0) Thread.sleep(1); // wait for slow filter to stop sleeping.

    assertEquals(slow.jetty.getDebugFilter().getTotalRequests()
        - requestCountBeforeRequest, 1);

    slowFilter.setSleepTime(0);

    resp = submitRequest(lbSolrServer, serverList, request);
    assertEquals(10, resp.getResults().getNumFound());
    name = resp.getResults().get(0).getFieldValue("name").toString();
    assertEquals("solr/collection10", name);

  }

  public void testBackupRequestBothSlow() throws Exception {

    LBHttpSolrServer lbSolrServer = new BackupRequestLBHttpSolrServer(
        httpClient, commExecutor, inFlightRequestMonitor, 2, 250, false);
    List<String> serverList = new ArrayList<String>();

    SolrInstance slow = new SolrInstance("solr/collection10", 0);
    slow.setUp();
    slow.startJetty(new SlowFilter(500));
    serverList.add(slow.getUrl());
    addDocs(slow);
    solr.put("solr/collection10", slow);

    SolrInstance slowToo = new SolrInstance("solr/collection11", 0);
    slowToo.setUp();
    slowToo.startJetty(new SlowFilter(500));
    serverList.add(slowToo.getUrl());
    addDocs(slowToo);
    solr.put("solr/collection11", slowToo);

    SolrQuery solrQuery = new SolrQuery("*:*");
    QueryResponse resp = null;
    QueryRequest request = new QueryRequest(solrQuery);

    long requestCountBeforeRequest =
        slow.jetty.getDebugFilter().getTotalRequests()
        + slowToo.jetty.getDebugFilter().getTotalRequests();
    resp = submitRequest(lbSolrServer, serverList, request);
    while(commExecutor.getActiveCount()>0) Thread.sleep(1);
    assertEquals(10, resp.getResults().getNumFound());
    assertEquals( slow.jetty.getDebugFilter().getTotalRequests()
        + slowToo.jetty.getDebugFilter().getTotalRequests()
        - requestCountBeforeRequest, 2);

  }

  public void testSimple() throws Exception {

    List<String> serverList = new ArrayList<String>();
    for (int i = 0; i < 3; i++) {
      SolrInstance si = new SolrInstance("solr/collection1" + i, 0);
      si.setUp();
      si.startJetty();
      serverList.add(si.getUrl());
      addDocs(si);
      solr.put("solr/collection1" + i, si);
    }

    LBHttpSolrServer lbSolrServer = new BackupRequestLBHttpSolrServer(
        httpClient, commExecutor, inFlightRequestMonitor, 1, 1, false);
    lbSolrServer.setAliveCheckInterval(500);

    SolrQuery solrQuery = new SolrQuery("*:*");
    QueryResponse resp = null;
    QueryRequest request = new QueryRequest(solrQuery);

    resp = submitRequest(lbSolrServer, serverList, request);
    assertEquals(10, resp.getResults().getNumFound());
    String name = resp.getResults().get(0).getFieldValue("name").toString();
    assertEquals("solr/collection10", name);

    // Kill a server and test again
    solr.get("solr/collection10").jetty.stop();
    solr.get("solr/collection10").jetty = null;

    resp = submitRequest(lbSolrServer, serverList, request);
    assertEquals(10, resp.getResults().getNumFound());
    name = resp.getResults().get(0).getFieldValue("name").toString();
    assertEquals("solr/collection11", name);

    solr.get("solr/collection11").jetty.stop();
    solr.get("solr/collection11").jetty = null;

    resp = submitRequest(lbSolrServer, serverList, request);
    assertEquals(10, resp.getResults().getNumFound());
    name = resp.getResults().get(0).getFieldValue("name").toString();
    assertEquals("solr/collection12", name);

    // Start the killed server once again
    solr.get("solr/collection10").startJetty();
    // Wait for the alive check to complete
    Thread.sleep(1200);

    resp = submitRequest(lbSolrServer, serverList, request);
    assertEquals(10, resp.getResults().getNumFound());
    name = resp.getResults().get(0).getFieldValue("name").toString();
    assertEquals("solr/collection10", name);

  }

  
  public void testExceptionForIllformedQuery() throws Exception {

    List<String> serverList = new ArrayList<String>();

    SolrInstance si = new SolrInstance("solr/collection", 0);
    si.setUp();
    si.startJetty();
    serverList.add(si.getUrl());
    addDocs(si);
    solr.put("solr/collection", si);

    LBHttpSolrServer lbSolrServer = new BackupRequestLBHttpSolrServer(
        httpClient, commExecutor, inFlightRequestMonitor, 1, 1, false);

    SolrQuery solrQuery = new SolrQuery("not_a_field::*");
    QueryResponse resp = null;
    QueryRequest request = new QueryRequest(solrQuery);
    SolrServerException exc = null;
    try{
      resp = submitRequest(lbSolrServer, serverList, request);
    }catch(SolrServerException ex){
      exc = ex;
    }
    assertNotNull(exc);

  }

  public void testInFlight() throws Exception {
    inFlightRequestMonitor = new InflightRequestMonitor(true);
    
    List<String> servers = new ArrayList<String>();
    servers.add("http://1:30");
    servers.add("http://2:30");
    servers.add("http://3:30");
    
    inFlightRequestMonitor.orderServers(servers);
    
    inFlightRequestMonitor.increment("http://1:30");
    inFlightRequestMonitor.increment("http://1:30");
    inFlightRequestMonitor.increment("http://2:30");
   
    inFlightRequestMonitor.orderServers(servers);
    
    assertEquals("http://3:30", servers.get(0));
    assertEquals("http://2:30", servers.get(1));
    assertEquals("http://1:30", servers.get(2));
    
    inFlightRequestMonitor.decrement("http://1:30");
    inFlightRequestMonitor.decrement("http://1:30");
    inFlightRequestMonitor.increment("http://3:30");
    inFlightRequestMonitor.increment("http://3:30");
    
    inFlightRequestMonitor.orderServers(servers);
    
    assertEquals("http://1:30", servers.get(0));
    assertEquals("http://2:30", servers.get(1));
    assertEquals("http://3:30", servers.get(2));
    
  }
  
  private QueryResponse submitRequest(LBHttpSolrServer lbSolrServer,
      List<String> serverList, QueryRequest request)
      throws SolrServerException, IOException {
    
    List<String> newList = inFlightRequestMonitor.orderServers(new ArrayList<String>(serverList));
    
    return new QueryResponse(lbSolrServer.request(
        new LBHttpSolrServer.Req(request, newList)).getResponse(),
        lbSolrServer);
  }

  private class SolrInstance {
    String name;
    File homeDir;
    File dataDir;
    File confDir;
    int port;
    JettySolrRunner jetty;

    public SolrInstance(String name, int port) {
      this.name = name;
      this.port = port;      
    }

    public String getHomeDir() {
      return homeDir.toString();
    }

    public String getUrl() {
      return "http://127.0.0.1:" + port + "/solr";
    }

    public String getSchemaFile() {
      return "src/test/test-files/solr/collection1/conf/schema-replication1.xml";
    }

    public String getConfDir() {
      return confDir.toString();
    }

    public String getDataDir() {
      return dataDir.toString();
    }

    public String getSolrConfigFile() {
      return "src/test/test-files/solr/collection1/conf/solrconfig-slave1.xml";
    }

    public void setUp() throws Exception {
      File home = new File(LuceneTestCase.TEMP_DIR, getClass().getName() + "-"
          + System.currentTimeMillis());

      homeDir = new File(home, name);
      dataDir = new File(homeDir + "/collection1", "data");
      confDir = new File(homeDir + "/collection1", "conf");

      homeDir.mkdirs();
      dataDir.mkdirs();
      confDir.mkdirs();

      File f = new File(confDir, "solrconfig.xml");
      FileUtils.copyFile(SolrTestCaseJ4.getFile(getSolrConfigFile()), f);
      f = new File(confDir, "schema.xml");
      FileUtils.copyFile(SolrTestCaseJ4.getFile(getSchemaFile()), f);

    }

    public void tearDown() throws Exception {
      try {
        jetty.stop();
      } catch (Exception e) {}
      AbstractSolrTestCase.recurseDelete(homeDir);
    }

    public void startJetty() throws Exception {
      startJetty(null);
    }

    public void startJetty(Filter filter) throws Exception {
      jetty = new JettySolrRunner(getHomeDir(), "/solr", port,
          "bad_solrconfig.xml", null, true,null, filter);
      System.setProperty("solr.data.dir", getDataDir());
      jetty.start();
      int newPort = jetty.getLocalPort();
      if (port != 0 && newPort != port) {
        fail("TESTING FAILURE: could not grab requested port.");
      }
      this.port = newPort;
    }
  }

  private class SlowFilter implements Filter {

    private int SleepTime;

    public void setSleepTime(int sleepTime) {
      SleepTime = sleepTime;
    }

    public SlowFilter(int sleepTime) {
      SleepTime = sleepTime;
    }

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest servletRequest,
        ServletResponse servletResponse, FilterChain filterChain)
        throws IOException, ServletException {
      try {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        if (req.getMethod() == "GET") {
          Thread.sleep(SleepTime);
        }
      } catch (InterruptedException e) {
        throw new RuntimeException();
      }
      filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void init(FilterConfig arg0) throws ServletException {}

  }
}