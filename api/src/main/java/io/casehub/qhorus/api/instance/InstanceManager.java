package io.casehub.qhorus.api.instance;

import java.util.List;

public interface InstanceManager {

    Instance register(String instanceId, String description,
                      List<String> capabilities, boolean readOnly);

    List<InstanceInfo> listInfo();

    List<InstanceInfo> findInfoByCapability(String capability);

    InstanceInfo findInfo(String instanceId);

    void deregister(String instanceId);
}
