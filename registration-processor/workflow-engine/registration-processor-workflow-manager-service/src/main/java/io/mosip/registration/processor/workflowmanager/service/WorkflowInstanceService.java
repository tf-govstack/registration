package io.mosip.registration.processor.workflowmanager.service;

import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.validator.internal.util.privilegedactions.GetDeclaredFields;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.util.HMACUtils2;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.code.EventId;
import io.mosip.registration.processor.core.code.EventName;
import io.mosip.registration.processor.core.code.EventType;
import io.mosip.registration.processor.core.code.ModuleName;
import io.mosip.registration.processor.core.code.RegistrationTransactionStatusCode;
import io.mosip.registration.processor.core.code.RegistrationTransactionTypeCode;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.exception.WorkflowInstanceException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.exception.util.PlatformSuccessMessages;
import io.mosip.registration.processor.core.logger.LogDescription;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.status.util.StatusUtil;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.core.workflow.dto.WorkflowInstanceRequestDTO;
import io.mosip.registration.processor.packet.storage.dto.ConfigEnum;
import io.mosip.registration.processor.packet.storage.utils.PriorityBasedPacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.rest.client.audit.builder.AuditLogRequestBuilder;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.dao.SyncRegistrationDao;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationAdditionalInfoDTO;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.encryptor.Encryptor;
import io.mosip.registration.processor.status.entity.SyncRegistrationEntity;
import io.mosip.registration.processor.status.exception.RegStatusAppException;
import io.mosip.registration.processor.status.exception.TablenotAccessibleException;
import io.mosip.registration.processor.status.service.RegistrationStatusService;
import io.vertx.core.json.JsonObject;

/**
 * The Class WorkflowInstanceService.
 */
@Component
public class WorkflowInstanceService {

	/** The registration status service. */
	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;
	
	/** The sync registration dao. */
	@Autowired
	private SyncRegistrationDao syncRegistrationDao;

	/** The core audit request builder. */
	@Autowired
	AuditLogRequestBuilder auditLogRequestBuilder;
	
	/** The encryptor. */
	@Autowired
	private Encryptor encryptor;
	
	@Autowired
	private PriorityBasedPacketManagerService packetManagerService;

	/** The Constant USER. */
	private static final String USER = "MOSIP_SYSTEM";

	/** The resume from beginning stage. */
	@Value("${mosip.regproc.workflow-manager.instance.beginning.stage}")
	private String beginningStage;
	
	@Value("${mosip.registration.processor.lostrid.iteration.max.count:10000}")
	private int iteration;
	
	@Autowired
	private Utilities utility;

	/** The module name. */
	public static String MODULE_NAME = ModuleName.WORKFLOW_INSTANCE_SERVICE.toString();

	/** The module id. */
	public static String MODULE_ID = PlatformSuccessMessages.RPR_WORKFLOW_INSTANCE_SERVICE_SUCCESS.getCode();

	/** The reg proc logger. */
	private static Logger regProcLogger = RegProcessorLogger.getLogger(WorkflowInstanceService.class);
	
	/** The Constant VALUE. */
	//private static final String VALUE = "value";

	/**
	 * Add record to registration table
	 * @throws WorkflowInstanceException 
	 */
	public InternalRegistrationStatusDto addRegistrationProcess(WorkflowInstanceRequestDTO regRequest) throws WorkflowInstanceException {
		regProcLogger.debug("addRegistrationProcess called for request {}", regRequest.toString());
		LogDescription description = new LogDescription();
		boolean isTransactionSuccessful = false;
		String rid = regRequest.getRegistrationId();
		InternalRegistrationStatusDto dto = new InternalRegistrationStatusDto();
		try {
			String referenceId=null;
			String timeStamp=DateUtils.formatToISOString(LocalDateTime.now());
			
			String additionalInfo = JsonUtils.javaObjectToJsonString(regRequest.getAdditionalInfo());
			byte[] encryptedInfo = encryptor.encrypt(additionalInfo, utility.getRefId(regRequest.getRegistrationId(), referenceId), timeStamp);
			
			String workflowInstanceId = UUID.randomUUID().toString();
			SyncRegistrationEntity syncRegistrationEntity = new SyncRegistrationEntity();
			syncRegistrationEntity.setWorkflowInstanceId(workflowInstanceId);
			syncRegistrationEntity.setRegistrationId(rid);
			syncRegistrationEntity.setSupervisorStatus("APPROVED");
			syncRegistrationEntity.setRegistrationType(regRequest.getProcess());
			syncRegistrationEntity.setLangCode("eng");
			syncRegistrationEntity.setCreatedBy(USER);
			syncRegistrationEntity.setCreateDateTime(LocalDateTime.now(ZoneId.of("UTC")));
			syncRegistrationEntity.setIsDeleted(false);
			syncRegistrationEntity.setPacketHashValue("");
			syncRegistrationEntity.setPacketSize(BigInteger.valueOf(1295230));
			//syncRegistrationEntity.setName(getHashCode(name));
			//syncRegistrationEntity.setEmail(getHashCode(email));
			//syncRegistrationEntity.setPhone(getHashCode(phone));
			syncRegistrationEntity.setOptionalValues(encryptedInfo);
			syncRegistrationDao.save(syncRegistrationEntity);
			
			int iteration = 1;// getIterationForSyncRecord(regEntity);
			dto.setRegistrationId(regRequest.getRegistrationId());
			dto.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.WORKFLOW_RESUME.toString());
			// dto.setLatestTransactionTimes(DateUtils.getUTCCurrentDateTime());
			dto.setRegistrationStageName(beginningStage);
			dto.setRegistrationType(regRequest.getProcess());
			dto.setReferenceRegistrationId(null);
			dto.setStatusCode(RegistrationStatusCode.RESUMABLE.toString());
			dto.setLangCode("eng");
			dto.setStatusComment(PlatformSuccessMessages.RPR_WORKFLOW_INSTANCE_SERVICE_SUCCESS.getMessage());
			dto.setSubStatusCode(StatusUtil.WORKFLOW_INSTANCE_SERVICE_SUCCESS.getCode());
			dto.setReProcessRetryCount(0);
			dto.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.REPROCESS.toString());
			dto.setIsActive(true);
			dto.setCreatedBy(USER);
			dto.setIsDeleted(false);
			dto.setSource(regRequest.getSource());
			dto.setIteration(iteration);
			dto.setWorkflowInstanceId(workflowInstanceId);

			//addRuleIdsToTag(internalRegistrationStatusDto);
			
			registrationStatusService.addRegistrationStatus(dto, MODULE_ID, MODULE_NAME, false);
			description
			.setMessage(PlatformSuccessMessages.RPR_WORKFLOW_INSTANCE_SERVICE_SUCCESS.getMessage());
			isTransactionSuccessful = true;
		} catch (TablenotAccessibleException e) {
			logAndThrowError(e, e.getErrorCode(), e.getMessage(), rid, description);
		} catch (Exception e) {
			logAndThrowError(e, PlatformErrorMessages.RPR_WIS_UNKNOWN_EXCEPTION.getCode(),
					PlatformErrorMessages.RPR_WIS_UNKNOWN_EXCEPTION.getMessage(), rid, description);

		} finally {
			regProcLogger.debug("WorkflowInstanceService status for registration id {} {}", rid,
					description.getMessage());
			updateAudit(description, rid, isTransactionSuccessful);
		}

		regProcLogger.debug("addRegistrationProcess call ended for request {}", regRequest.toString());
		return dto;
	}

	/**
	 * Update audit.
	 *
	 * @param description             the description
	 * @param registrationId          the registration id
	 * @param isTransactionSuccessful the is transaction successful
	 */
	private void updateAudit(LogDescription description, String registrationId, boolean isTransactionSuccessful) {

		String moduleId = isTransactionSuccessful ? MODULE_ID : description.getCode();

		String eventId = isTransactionSuccessful ? EventId.RPR_402.toString() : EventId.RPR_405.toString();
		String eventName = isTransactionSuccessful ? EventName.UPDATE.toString() : EventName.EXCEPTION.toString();
		String eventType = isTransactionSuccessful ? EventType.BUSINESS.toString() : EventType.SYSTEM.toString();

		auditLogRequestBuilder.createAuditRequestBuilder(description.getMessage(), eventId, eventName, eventType,
				moduleId, MODULE_NAME, registrationId);
	}

	/**
	 * Log and throw error.
	 *
	 * @param e              the e
	 * @param errorCode      the error code
	 * @param errorMessage   the error message
	 * @param registrationId the registration id
	 * @param description    the description
	 * @throws WorkflowInstanceException the workflow instance exception
	 */
	private void logAndThrowError(Exception e, String errorCode, String errorMessage, String registrationId,
			LogDescription description) throws WorkflowInstanceException {
		description.setCode(errorCode);
		description.setMessage(errorMessage);
		regProcLogger.error("Error in  addRegistrationProcess  for registration id  {} {} {} {}", registrationId,
				errorMessage, e.getMessage(), ExceptionUtils.getStackTrace(e));
		throw new WorkflowInstanceException(errorCode, errorMessage);
	}
	
//	private String getHashCode(String value) throws RegStatusAppException {
//		String encodedHash = null;
//		if (value == null) {
//			return null;
//		}
//		try {
//			byte[] hashCode = getHMACHash(value);
//			byte[] nonce = Arrays.copyOfRange(hashCode, hashCode.length - 2, hashCode.length);
//			String result = convertBytesToHex(nonce);
//			Long hashValue = Long.parseLong(result, 16);
//			Long saltIndex = hashValue % 10000;
//			String salt = syncRegistrationDao.getSaltValue(saltIndex);
//			byte[] saltBytes=null;
//			try {
//				saltBytes= CryptoUtil.decodeURLSafeBase64(salt);
//			} catch (IllegalArgumentException exception) {
//				saltBytes = CryptoUtil.decodePlainBase64(salt);
//			}
//			byte[] hashBytes = value.getBytes();
//			for (int i = 0; i <= iteration; i++) {
//				hashBytes = getHMACHashWithSalt(hashBytes, saltBytes);
//			}
//			encodedHash = CryptoUtil.encodeToURLSafeBase64(hashBytes);
//		} catch (NoSuchAlgorithmException e) {
//			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
//					"", e.getMessage() + ExceptionUtils.getStackTrace(e));
//			throw new RegStatusAppException(PlatformErrorMessages.RPR_RGS_INVALID_SEARCH, e);
//		}
//		return encodedHash;
//	}
//	
//	private static byte[] getHMACHash(String value) throws java.security.NoSuchAlgorithmException {
//		if (value == null)
//			return null;
//		return HMACUtils2.generateHash(value.getBytes());
//	}
//	
//	private static String convertBytesToHex(byte[] bytes) {
//		StringBuilder result = new StringBuilder();
//		for (byte temp : bytes) {
//			result.append(String.format("%02x", temp));
//		}
//		return result.toString();
//	}
//	
//	private static byte[] getHMACHashWithSalt(byte[] valueBytes, byte[] saltBytes)
//			throws java.security.NoSuchAlgorithmException {
//		if (valueBytes == null)
//			return null;
//		return HMACUtils2.digestAsPlainTextWithSalt(valueBytes, saltBytes).getBytes();
//	}
//	
//	private void getFields(String id, String process) throws PacketManagerException, JsonProcessingException, IOException {
//		JSONObject mapperIdentity = utility.getRegistrationProcessorMappingJson(MappingJsonConstants.IDENTITY);
//
//		List<String> mapperJsonValues = new ArrayList<>();
//		JsonUtil.getJSONValue(JsonUtil.getJSONObject(mapperIdentity, MappingJsonConstants.INDIVIDUAL_BIOMETRICS), VALUE);
//		mapperIdentity.keySet().forEach(key -> mapperJsonValues.add(JsonUtil.getJSONValue(JsonUtil.getJSONObject(mapperIdentity, key), VALUE)));
//
//		String source = utility.getDefaultSource(process, ConfigEnum.READER);
//		Map<String, String> fieldMap =null;
//		try {
//		 fieldMap = packetManagerService.getFields(id, mapperJsonValues, process, ProviderStageName.WORKFLOW_MANAGER);
//		}catch(ApisResourceAccessException e) {
//			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
//					id, PlatformErrorMessages.RPR_PGS_API_RESOURCE_NOT_AVAILABLE.name() + e.getMessage()
//							+ ExceptionUtils.getStackTrace(e));
//		}
//		
//		String name = JsonUtil.getJSONValue(
//				JsonUtil.getJSONObject(mapperIdentity, MappingJsonConstants.NAME),
//				MappingJsonConstants.VALUE);
//		String email = JsonUtil.getJSONValue(
//				JsonUtil.getJSONObject(mapperIdentity, MappingJsonConstants.EMAIL),
//				MappingJsonConstants.VALUE);
//		String phone = JsonUtil.getJSONValue(
//				JsonUtil.getJSONObject(mapperIdentity, MappingJsonConstants.PHONE),
//				MappingJsonConstants.VALUE);
//		String nameValue = fieldMap.get(name);
//		String emailValue = fieldMap.get(email);
//		String phoneNumberValue = fieldMap.get(phone);
//	}

}
