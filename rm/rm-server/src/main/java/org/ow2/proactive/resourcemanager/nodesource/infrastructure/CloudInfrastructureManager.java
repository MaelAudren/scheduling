package org.ow2.proactive.resourcemanager.nodesource.infrastructure;

import org.json.JSONObject;

import java.util.Set;

/**
 * Created by mael on 18/08/16.
*/
public abstract class CloudInfrastructureManager extends InfrastructureManager{

    public abstract Set<JSONObject> getInstances();
}





