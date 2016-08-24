package org.ow2.proactive.resourcemanager.nodesource.infrastructure;

import org.json.JSONObject;

import java.util.Set;

/**
 * Created by mael on 18/08/16.
*/
public interface CloudInfrastructure {

    Set<JSONObject> getInstances();
}





