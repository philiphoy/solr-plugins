package org.apache.solr.handler.component;
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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.client.HttpClient;
import org.apache.solr.client.solrj.impl.BackupRequestLBHttpSolrServer;
import org.apache.solr.client.solrj.impl.InflightRequestMonitor;

import org.apache.solr.client.solrj.impl.LBHttpSolrServer;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.core.PluginInfo;
import org.apache.solr.handler.component.HttpShardHandlerFactory;


public class HttpBackupRequestShardHandlerFactory extends
    HttpShardHandlerFactory {
  private int maximumConcurrentRequests = 2;
  private int backupRequestDelay = 10 * 1000;
  private boolean tryDeadServers = true;
  private String scheme;
  // Configure the amount of time before a backup request is sent to the next server in the list in milliseconds
  static final String BACKUP_REQUEST_DELAY = "backupRequestDelay";
  // Configure the maximum request in flight due to backup requests
  static final String MAX_CONCURRENT_REQUESTS = "maximumConcurrentRequests";
  static final String TRY_DEAD_SERVERS = "tryDeadServers";
  static final String MONITOR_PER_SERVER = "monitorPerServer";
  private InflightRequestMonitor inFlightRequestMonitor;
  
  @Override
  public void init(PluginInfo info) {
    NamedList args = info.initArgs;
    this.backupRequestDelay = getParameter(args, BACKUP_REQUEST_DELAY, backupRequestDelay);
    this.maximumConcurrentRequests = getParameter(args, MAX_CONCURRENT_REQUESTS, maximumConcurrentRequests);
    this.tryDeadServers = getParameter(args, TRY_DEAD_SERVERS, tryDeadServers);
    this.scheme = getParameter(args, INIT_URL_SCHEME, "http://");
    boolean monitorPerServer = getParameter(args, MONITOR_PER_SERVER, true);    
    this.inFlightRequestMonitor = new InflightRequestMonitor(monitorPerServer);
    super.init(info);
  }

  @Override
  protected LBHttpSolrServer createLoadbalancer(HttpClient httpClient) {
    try {
      return new BackupRequestLBHttpSolrServer(
          httpClient, getThreadPoolExecutor(),inFlightRequestMonitor,
          maximumConcurrentRequests, backupRequestDelay,tryDeadServers);
    } catch (MalformedURLException e) {
      // should be impossible since we're not passing any URLs here
      throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, e);
    }
  }
  
  /**
   * Creates a list of urls for the given shards, ordered by the number of in flight requests.
   *
   * @param shard the urls for the shard (minus "http://"), separated by '|'
   * @return A list of valid urls (including protocol) that are replicas for the shard
   */
  public List<String> makeURLList(String shard) {
    List<String> urls = StrUtils.splitSmart(shard, "|", true);
    for(int i=0; i<urls.size(); i++){
      urls.set(i, scheme + urls.get(i)) ;   
    }
    return inFlightRequestMonitor.orderServers(urls);
  }
}
