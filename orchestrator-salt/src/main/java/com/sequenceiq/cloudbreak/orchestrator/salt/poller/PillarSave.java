package com.sequenceiq.cloudbreak.orchestrator.salt.poller;

import static java.util.Collections.singletonMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import com.sequenceiq.cloudbreak.orchestrator.OrchestratorBootstrap;
import com.sequenceiq.cloudbreak.orchestrator.model.GenericResponse;
import com.sequenceiq.cloudbreak.orchestrator.model.Node;
import com.sequenceiq.cloudbreak.orchestrator.model.SaltPillarProperties;
import com.sequenceiq.cloudbreak.orchestrator.salt.client.SaltConnector;
import com.sequenceiq.cloudbreak.orchestrator.salt.domain.Pillar;

public class PillarSave implements OrchestratorBootstrap {

    private final SaltConnector sc;
    private final Pillar pillar;

    public PillarSave(SaltConnector sc, String gateway) {
        this.sc = sc;
        this.pillar = new Pillar("/ambari/server.sls", singletonMap("ambari", singletonMap("server", gateway)));
    }

    public PillarSave(SaltConnector sc, Set<Node> hosts) {
        this.sc = sc;
        Map<String, Map<String, Object>> fqdn = hosts
                .stream()
                .collect(Collectors.toMap(Node::getPrivateIp, node -> discovery(node.getHostname(), node.getPublicIp())));

        this.pillar = new Pillar("/nodes/hosts.sls", singletonMap("hosts", fqdn));
    }

    public PillarSave(SaltConnector sc, SaltPillarProperties pillarProperties) {
        this.sc = sc;
        this.pillar = new Pillar(pillarProperties.getPath(), pillarProperties.getProperties());
    }

    private Map<String, Object> discovery(String hostname, String publicAddress) {
        Map<String, Object> map = new HashMap<>();
        map.put("fqdn", hostname);
        map.put("hostname", hostname.split("\\.")[0]);
        map.put("public_address", StringUtils.isEmpty(publicAddress) ? Boolean.FALSE : Boolean.TRUE);
        return map;
    }

    @Override
    public Boolean call() throws Exception {
        GenericResponse resp = sc.pillar(pillar);
        resp.assertError();
        return true;
    }
}
