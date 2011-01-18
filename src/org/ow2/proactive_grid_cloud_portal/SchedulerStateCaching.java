/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2011 INRIA/University of
 *                 Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 *
 *  Initial developer(s):               The ActiveEon Team
 *                        http://www.activeeon.com/
 *  Contributor(s):
 *
 * ################################################################
 * $$ACTIVEEON_INITIAL_DEV$$
 */
package org.ow2.proactive_grid_cloud_portal;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.core.util.log.ProActiveLogger;
import org.objectweb.proactive.utils.Sleeper;
import org.ow2.proactive.authentication.crypto.Credentials;
import org.ow2.proactive.scheduler.common.SchedulerState;
import org.ow2.proactive.scheduler.common.exception.NotConnectedException;
import org.ow2.proactive.scheduler.common.exception.PermissionException;
import org.ow2.proactive.scheduler.common.util.CachingSchedulerProxyUserInterface;
import org.ow2.proactive.scheduler.common.util.SchedulerLoggers;

/**
 * This class keeps a cache of the scheduler state and periodically refresh it.
 * This prevents to directly call the active object CachingSchedulerProxyUserInterface and thus
 * to have the scheduler state being copied each time.
 * the refresh rate can be configured using @see {org.ow2.proactive_grid_cloud_portal.PortalConfiguration.scheduler_cache_refreshrate}
 * 
 *
 */
public class SchedulerStateCaching {
    private static Logger logger = ProActiveLogger.getLogger(SchedulerLoggers.PREFIX + ".rest.caching");
    
    private static CachingSchedulerProxyUserInterface scheduler;
    private static SchedulerState localState;
    private static long schedulerRevision;
    private static int refreshInterval;
    private static boolean kill = false;
    private static Thread cachedSchedulerStateThreadUpdater;
    private static Thread leaseRenewerThreadUpdater;

    
    protected static Map<AtomicLong, SchedulerState> revisionAndSchedulerState;

    private static int leaseRenewRate;
    
    
    public static CachingSchedulerProxyUserInterface getScheduler() {
        return scheduler;
    }

    public static void setScheduler(CachingSchedulerProxyUserInterface scheduler) {
        SchedulerStateCaching.scheduler = scheduler;
    } 
    
    public static void init() {
        init_();
        start_();
    }
    
    private static void init_() {
        leaseRenewRate = Integer.parseInt(PortalConfiguration.getProperties().getProperty(PortalConfiguration.lease_renew_rate));
        refreshInterval = Integer.parseInt(PortalConfiguration.getProperties().getProperty(PortalConfiguration.scheduler_cache_refreshrate));
        String url = PortalConfiguration.getProperties().getProperty(PortalConfiguration.scheduler_url);
        String cred_path = PortalConfiguration.getProperties().getProperty(PortalConfiguration.scheduler_cache_credential);
        
        while (scheduler == null ) {
        try {
            
            if (scheduler == null) {
            
            scheduler = PAActiveObject.newActive(
                         CachingSchedulerProxyUserInterface.class, new Object[] {});

          
            // check is we use a credential file 
            
           
            File f = new File(cred_path);
            
            if (f.exists()) {
                Credentials credential = Credentials.getCredentials(cred_path);
                scheduler.init(url, credential);
            } else {
                String login =  PortalConfiguration.getProperties().getProperty(PortalConfiguration.scheduler_cache_login);
                String password =  PortalConfiguration.getProperties().getProperty(PortalConfiguration.scheduler_cache_password);           
                scheduler.init(url, login, password);
            }
            
            
            }
           } catch (Exception e) {
               logger.warn("no scheduler found on " + url + "retrying in 8 seconds", e);
               scheduler = null;
               new Sleeper(8 * 1000).sleep();
               continue;
           }
        }
        
    }


    
    private static void start_() {
        cachedSchedulerStateThreadUpdater = new Thread(new Runnable() {
            public void run() {
               while (!kill) {
  
                long currentSchedulerStateRevision = scheduler.getSchedulerStateRevision();
                
                if (currentSchedulerStateRevision != schedulerRevision) {
                    revisionAndSchedulerState =   scheduler.getRevisionVersionAndSchedulerState();
                    Entry<AtomicLong, SchedulerState> tmp = revisionAndSchedulerState.entrySet().iterator().next();
                    localState = tmp.getValue();
                    schedulerRevision = tmp.getKey().longValue();
                    logger.debug("updated scheduler state revision at " + schedulerRevision);
                }

                new Sleeper(refreshInterval).sleep();
               }
            }
        },"State Updater Thread");
        
        
        cachedSchedulerStateThreadUpdater.start();
        
        leaseRenewerThreadUpdater = new Thread(new Runnable() {
            public void run() {
                if ( scheduler != null) {
                    try {
                        scheduler.getStatus();
                    } catch (Exception e) {
                        logger.info("leaseRenewerThread was not able to call the getStatus method, exception message is " + e.getMessage());
                        init_();
                    }
                }
                new Sleeper(leaseRenewRate).sleep(); 
            }
        });
        
        leaseRenewerThreadUpdater.start();
        
    }
    
    public static SchedulerState getLocalState() {
        return localState;
    }

    public static void setLocalState(SchedulerState localState) {
        SchedulerStateCaching.localState = localState;
    }

    public static long getSchedulerRevision() {
        return schedulerRevision;
    }
    
    

    public static void setSchedulerRevision(long schedulerRevision) {
        SchedulerStateCaching.schedulerRevision = schedulerRevision;
    }

    public static int getRefreshInterval() {
        return refreshInterval;
    }

    public static void setRefreshInterval(int refreshInterval) {
        SchedulerStateCaching.refreshInterval = refreshInterval;
    }

    public static boolean isKill() {
        return kill;
    }

    public static void setKill(boolean kill) {
        SchedulerStateCaching.kill = kill;
    }
    
    public static Map<AtomicLong, SchedulerState> getRevisionAndSchedulerState() {
        return revisionAndSchedulerState;
    }
}
