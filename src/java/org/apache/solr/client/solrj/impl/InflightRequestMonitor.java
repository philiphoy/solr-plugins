package org.apache.solr.client.solrj.impl;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InflightRequestMonitor {
  private static Logger log = LoggerFactory.getLogger(InflightRequestMonitor.class);
  final Random r = new Random();
  private final ConcurrentHashMap<String, AtomicInteger> inFlightRequestCounts;
  private boolean perServer;
  
  public InflightRequestMonitor(boolean perServer){
    this.inFlightRequestCounts = new ConcurrentHashMap<String, AtomicInteger>();
    this.perServer = perServer;
  }
  
  public void increment(String url){
    if(perServer)
      url = serverFromUrl(url);
    else
      url = normalize(url);
    
    inFlightRequestCounts.get(url).incrementAndGet();
  }
  
  public void decrement(String url){
    if(perServer)
      url = serverFromUrl(url);
    else
      url = normalize(url);
    
    inFlightRequestCounts.get(url).decrementAndGet();
  }
  
  public List<String> orderServers(List<String> servers){
    
    if(servers.size()==1){
      String server;
      if(perServer)
        server = serverFromUrl(servers.get(0));
      else
        server = normalize(servers.get(0));      
      
      inFlightRequestCounts.putIfAbsent(server, new AtomicInteger(0));
    }else{    
      Collections.shuffle(servers, r);
      Collections.sort(servers, new RequestCountComparer() ) ;
    }
    return servers;
  }
  
  private static String serverFromUrl(String url){
    
    int start = url.indexOf("://");
    int end = url.indexOf(":",start+3);
    return url.substring(start+3,end);
    
  }  
  
  private static String normalize(String server) {
    if (server.endsWith("/"))
      server = server.substring(0, server.length() - 1);
    return server;
  }
  
  private class RequestCountComparer implements Comparator<String>{

    @Override
    public int compare(String string1, String string2) {
      
      int i1 = inflightRequests(string1);
      int i2 = inflightRequests(string2);
     
      if (i1==i2)
        return 0;
      if(i1>i2)
        return 1;
      
      return -1;        
    }
    
    private int inflightRequests(String url){      
      if(perServer)
        url = serverFromUrl(url); 
      else
        url = normalize(url);
      
      AtomicInteger connectionCount = inFlightRequestCounts.putIfAbsent(url, new AtomicInteger(0));
      
      int cnt;
      if(connectionCount==null)
        cnt = 0;
      else
        cnt = connectionCount.intValue();
      return cnt;
    }      
  }
}
