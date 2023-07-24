package io.mosip.registration.processor.core.workflow.dto;

import lombok.Data;
@Data
public class WorkflowInstanceRequestDTO {

	private String registrationId;

	private String process;
	
	private String source;
	
	private String additionalInfoReqId;

}
