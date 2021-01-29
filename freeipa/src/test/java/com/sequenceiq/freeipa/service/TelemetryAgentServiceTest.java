package com.sequenceiq.freeipa.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sequenceiq.cloudbreak.orchestrator.exception.CloudbreakOrchestratorFailedException;
import com.sequenceiq.cloudbreak.orchestrator.host.TelemetryOrchestrator;
import com.sequenceiq.cloudbreak.orchestrator.state.ExitCriteriaModel;
import com.sequenceiq.freeipa.entity.InstanceGroup;
import com.sequenceiq.freeipa.entity.InstanceMetaData;
import com.sequenceiq.freeipa.entity.Stack;
import com.sequenceiq.freeipa.entity.Template;
import com.sequenceiq.freeipa.repository.InstanceMetaDataRepository;
import com.sequenceiq.freeipa.repository.StackRepository;
import com.sequenceiq.freeipa.service.telemetry.TelemetryAgentService;

@ExtendWith(MockitoExtension.class)
public class TelemetryAgentServiceTest {

    @InjectMocks
    private TelemetryAgentService underTest;

    @Mock
    private TelemetryOrchestrator telemetryOrchestrator;

    @Mock
    private GatewayConfigService gatewayConfigService;

    @Mock
    private StackRepository stackRepository;

    @Mock
    private InstanceMetaDataRepository instanceMetaDataRepository;

    @BeforeEach
    public void setUp() {
        underTest = new TelemetryAgentService(telemetryOrchestrator, gatewayConfigService, stackRepository, instanceMetaDataRepository);
    }

    @Test
    public void testStopTelemetryAgent() throws Exception {
        // GIVEN
        given(stackRepository.findById(1L)).willReturn(Optional.of(createStack()));
        given(instanceMetaDataRepository.findNotTerminatedForStack(1L)).willReturn(createInstanceMetadataSet());
        doNothing().when(telemetryOrchestrator).stopTelemetryAgent(anyList(), anySet(), any(ExitCriteriaModel.class));
        // WHEN
        underTest.stopTelemetryAgent(1L);
        // THEN
        verify(telemetryOrchestrator, times(1)).stopTelemetryAgent(anyList(), anySet(), any(ExitCriteriaModel.class));
    }

    @Test
    public void testStopTelemetryAgentThrowsException() throws Exception {
        // GIVEN
        given(stackRepository.findById(1L)).willReturn(Optional.of(createStack()));
        given(instanceMetaDataRepository.findNotTerminatedForStack(1L)).willReturn(createInstanceMetadataSet());
        doThrow(new CloudbreakOrchestratorFailedException("error")).when(telemetryOrchestrator)
                .stopTelemetryAgent(anyList(), anySet(), any(ExitCriteriaModel.class));
        // WHEN
        underTest.stopTelemetryAgent(1L);
        // THEN
        verify(stackRepository, times(1)).findById(1L);
    }

    private Stack createStack() {
        Stack stack = new Stack();
        stack.setId(1L);
        return stack;
    }

    private Set<InstanceMetaData> createInstanceMetadataSet() {
        Set<InstanceMetaData> instanceMetaDataSet = new HashSet<>();
        InstanceMetaData instanceMetaData = new InstanceMetaData();
        instanceMetaData.setId(1L);
        InstanceGroup instanceGroup = new InstanceGroup();
        Template template = new Template();
        instanceGroup.setTemplate(template);
        instanceMetaData.setInstanceGroup(instanceGroup);
        instanceMetaDataSet.add(instanceMetaData);
        return instanceMetaDataSet;
    }

}
