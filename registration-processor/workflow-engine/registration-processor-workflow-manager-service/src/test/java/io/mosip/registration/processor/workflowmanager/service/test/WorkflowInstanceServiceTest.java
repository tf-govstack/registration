package io.mosip.registration.processor.workflowmanager.service.test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.map.HashedMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.WebApplicationContext;

import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.kernel.websub.api.exception.WebSubClientException;
import io.mosip.registration.processor.core.code.RegistrationTransactionStatusCode;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.exception.WorkflowActionException;
import io.mosip.registration.processor.core.exception.WorkflowInstanceException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.workflow.dto.RegistrationAdditionalInfoDTO;
import io.mosip.registration.processor.core.workflow.dto.WorkflowCompletedEventDTO;
import io.mosip.registration.processor.core.workflow.dto.WorkflowInstanceRequestDTO;
import io.mosip.registration.processor.packet.storage.utils.PacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.rest.client.audit.builder.AuditLogRequestBuilder;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.dao.SyncRegistrationDao;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.encryptor.Encryptor;
import io.mosip.registration.processor.status.exception.TablenotAccessibleException;
import io.mosip.registration.processor.status.service.RegistrationStatusService;
import io.mosip.registration.processor.workflowmanager.service.WorkflowActionService;
import io.mosip.registration.processor.workflowmanager.service.WorkflowInstanceService;
import io.mosip.registration.processor.workflowmanager.util.WebSubUtil;


@RunWith(SpringRunner.class)
@WebMvcTest
@ContextConfiguration(classes = { TestContext.class, WebApplicationContext.class })
public class WorkflowInstanceServiceTest {
	/** The registration status service. */
	@Mock
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

	/** The packet manager service. */
	@Mock
	private PacketManagerService packetManagerService;

	/** The core audit request builder. */
	@Mock
	AuditLogRequestBuilder auditLogRequestBuilder;
	
	@Mock
	private SyncRegistrationDao syncRegistrationDao;

	/** The web sub util. */
	@Mock
	WebSubUtil webSubUtil;

	@InjectMocks
	WorkflowInstanceService workflowInstanceService;
	
	@Mock
	private Encryptor encryptor;
	
	@Mock
	private Utilities utility;

	private InternalRegistrationStatusDto registrationStatusDto;
	
	private WorkflowInstanceRequestDTO workflowInstanceRequestDto;
	

	@Before
	public void setUp()
			throws Exception {
		workflowInstanceRequestDto = new WorkflowInstanceRequestDTO();
		workflowInstanceRequestDto.setRegistrationId("10003100030001520190422074511");
		workflowInstanceRequestDto.setProcess("NEW");
		workflowInstanceRequestDto.setSource("REGISTRATION_CLIENT");
		workflowInstanceRequestDto.setAdditionalInfoReqId("");
		RegistrationAdditionalInfoDTO additionalInfoDto = new RegistrationAdditionalInfoDTO();
		additionalInfoDto.setName("");
		additionalInfoDto.setEmail("");
		additionalInfoDto.setPhone("");
		workflowInstanceRequestDto.setAdditionalInfo(additionalInfoDto);
		Mockito.when(syncRegistrationDao.save(any())).thenReturn(null);
		Mockito.when(encryptor.encrypt(anyString(),anyString(),anyString())).thenReturn(null);
		Mockito.when(utility.getRefId(anyString(),anyString())).thenReturn("");
		ReflectionTestUtils.setField(workflowInstanceService, "beginningStage", "PacketValidatorStage");
		Mockito.doNothing().when(registrationStatusService).addRegistrationStatus(any(), anyString(),
				anyString(), Mockito.anyBoolean());
		Mockito.when(auditLogRequestBuilder.createAuditRequestBuilder(any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(null);
	}

	@Test
	public void testAddRegistrationProcess() throws WorkflowInstanceException {
		workflowInstanceService.addRegistrationProcess(workflowInstanceRequestDto);
	}

	@Test(expected = WorkflowInstanceException.class)
	public void testAddRegistrationProcessTablenotAccessibleException() throws WorkflowInstanceException {
		TablenotAccessibleException tablenotAccessibleException = new TablenotAccessibleException(
				PlatformErrorMessages.RPR_RGS_REGISTRATION_TABLE_NOT_ACCESSIBLE.getMessage());
		Mockito.doThrow(tablenotAccessibleException).when(registrationStatusService)
				.addRegistrationStatus(any(), anyString(),
						anyString(), Mockito.anyBoolean());
		workflowInstanceService.addRegistrationProcess(workflowInstanceRequestDto);
	}
	
//	@Test(expected = WorkflowInstanceException.class)
//	public void testAddRegistrationProcessUnknownException() throws WorkflowInstanceException {
//		workflowInstanceService.addRegistrationProcess(null);
//	}
}
