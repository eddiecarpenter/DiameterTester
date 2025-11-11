/*
 * Copyright (C) 2024 TradeSwitch (Pty) Ltd
 * All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of MobileData (Pty) Ltd and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to MobileData (Pty) Ltd
 * and its suppliers and may be covered by South African and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from TradeSwitch (Pty) Ltd.
 *
 *
 */

package io.diametertester.service;

import io.diametertester.enums.ServiceType;
import io.diametertester.exceptions.TestClientException;
import io.diametertester.model.Service;
import io.diametertester.model.ServiceConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jdiameter.api.*;
import org.jdiameter.api.cca.ClientCCASession;
import org.jdiameter.api.cca.events.JCreditControlAnswer;
import org.jdiameter.api.cca.events.JCreditControlRequest;
import org.jdiameter.client.api.ISessionFactory;
import org.jdiameter.client.impl.app.cca.ClientCCASessionImpl;
import org.jdiameter.common.impl.DiameterUtilities;
import org.jdiameter.common.impl.app.cca.JCreditControlRequestImpl;

import java.util.*;

@Slf4j
@Data
public class DiameterServiceRunner
{
	// USSD-Information Grouped AVP and its children (from 3GPP TS 32.299)
	private static final int AVP_USSD_INFORMATION = 885;
	private static final int AVP_USSD_STRING = 827;

	private static final int INITIAL_REQUEST = 1;
	private static final int UPDATE_REQUEST = 2;
	private static final int TERMINATION_REQUEST = 3;

	private static final int IN_INFORMATION = 20300;
	private static final int CALLING_PARTY_ADDRESS = 20336;
	private static final int CALLED_PARTY_ADDRESS = 20337;
	private static final int REAL_CALLED_NUMBER = 20327;
	private static final int CONNECT_CALLED_NUMBER = 20373;
	private static final int CHARGE_FLOW_TYPE = 20339;

	private final ServiceConfig serviceConfig;
	private final ISessionFactory sessionFactory;
	private final long vendorId;
	private final String msisdn;
	private final DiameterTestClient client;
	private final Map<Integer, Service> serviceMap;
	private String sessionId;
	private int requestType;
	private int requestNr;
	private int repeats;
	private String destHost;
	private String destRealm;
	private Timer timer;
	private ClientCCASession mySession;
	private long start;

	public DiameterServiceRunner(ServiceConfig serviceConfig, DiameterTestClient client, int repeats, String msisdn, String destHost, String destRealm, long vendorId, ISessionFactory sessionFactory)
	{
		this.serviceConfig  = serviceConfig;
		this.vendorId       = vendorId;
		this.client         = client;
		this.sessionFactory = sessionFactory;
		this.destHost       = destHost;
		this.destRealm      = destRealm;
		this.msisdn         = msisdn;
		this.repeats        = repeats;
		this.serviceMap     = serviceConfig.getServiceMap();

		if (this.repeats <= 0) {
			this.repeats = 1;
		}//if
		timer = null;
	}

	private void startTimer()
	{
		final DiameterServiceRunner self = this;
		start = System.currentTimeMillis();

		timer = new Timer();
		timer.schedule(new TimerTask()
		{
			@Override
			public void run()
			{
				LOG.warn("{}::{} - Timeout waiting for response, terminating the runner", sessionId, msisdn);
				mySession.release();
				if (!initRequest()) {
					client.cancelRunner(self);
				}//if
				timer = null;
			}
		}, 30000);
	}

	private long getElapsedTime()
	{
		return System.currentTimeMillis() - start;
	}

	private void sendRequest(ClientCCASession session) throws InternalException
	{
		try {
			JCreditControlRequest request = new JCreditControlRequestImpl(session, destRealm, destHost);

			AvpSet reqAvps = request.getMessage()
			                        .getAvps();
			AvpSet vSubscriberId = reqAvps.addGroupedAvp(Avp.SUBSCRIPTION_ID);


			reqAvps.addAvp(Avp.EVENT_TIMESTAMP, new Date(System.currentTimeMillis()));
			vSubscriberId.addAvp(Avp.SUBSCRIPTION_ID_TYPE, 0);
			vSubscriberId.addAvp(Avp.SUBSCRIPTION_ID_DATA, msisdn, false);

			reqAvps.addAvp(Avp.CC_REQUEST_TYPE, requestType);
			reqAvps.addAvp(Avp.CC_REQUEST_NUMBER, requestNr++);
			reqAvps.addAvp(Avp.VENDOR_ID, vendorId);
			reqAvps.addAvp(Avp.SERVICE_CONTEXT_ID, serviceConfig.getContext(), false);
			reqAvps.addAvp(Avp.SERVICE_IDENTIFIER_CCA, serviceConfig.getServiceId());

			String reqType = switch (requestType) {
				case INITIAL_REQUEST -> {
					LOG.info("{}::{} - Call Setup", session.getSessionId(), msisdn);
					yield "SETUP";
				}
				case UPDATE_REQUEST -> {
					LOG.info("{}::{} - Call Update", session.getSessionId(), msisdn);
					yield "UPDATE";
				}
				case TERMINATION_REQUEST -> {
					LOG.info("{}::{} - Call Terminated", session.getSessionId(), msisdn);
					yield "TERMINATE";
				}
				default -> {
					LOG.info("{}::{} - Call Event", session.getSessionId(), msisdn);
					yield "EVENT";
				}
			};

			switch (serviceConfig.getServiceType()) {
				case VOICE -> {
					AvpSet voiceServiceInfo = reqAvps.addGroupedAvp(Avp.SERVICE_INFORMATION, 10415, false, false);
					AvpSet inInfo = voiceServiceInfo.addGroupedAvp(Avp.IMS_INFORMATION, 10415, false, false);
					inInfo.addAvp(Avp.CALLING_PARTY_ADDRESS, msisdn, 10415, false, false, false);
					inInfo.addAvp(Avp.REQUESTED_PARTY_ADDRESS, serviceConfig.getDestination(), 10415, false, false, false);
					inInfo.addAvp(Avp.ROLE_OF_NODE, 0, 10415, false, false, true);
				}

				case DATA -> {
					AvpSet dataServiceInfo = reqAvps.addGroupedAvp(Avp.SERVICE_INFORMATION, 10415, false, false);
					AvpSet psInfo = dataServiceInfo.addGroupedAvp(Avp.PS_INFORMATION, 10415, false, false);
					psInfo.addAvp(Avp.TGPP_CHARGING_ID, 12345, 10415, false, false, true);
				}

				case SMS -> {
					AvpSet smsServiceInfo = reqAvps.addGroupedAvp(Avp.SERVICE_INFORMATION, 10415, false, false);
					AvpSet smsInfo = smsServiceInfo.addGroupedAvp(Avp.SMS_INFORMATION, 10415, false, false);
					if (requestType == TERMINATION_REQUEST) {
						smsInfo.addAvp(Avp.SM_STATUS, 0, 10415, false, false, false);
					}
					AvpSet vRecInfo = smsInfo.addGroupedAvp(Avp.RECIPIENT_INFO, 10415, false, false);
					AvpSet vDestAddr = vRecInfo.addGroupedAvp(Avp.RECIPIENT_ADDRESS, 10415, false, false);
					vDestAddr.addAvp(Avp.ADDRESS_TYPE, 0, 10415, false, false, false);
					vDestAddr.addAvp(Avp.ADDRESS_DATA, serviceConfig.getDestination(), 10415, false, false, false);
				}
				case USSD, USSD2 -> {
					AvpSet voiceServiceInfo = reqAvps.addGroupedAvp(Avp.SERVICE_INFORMATION, 10415, false, false);
					AvpSet inInfo = voiceServiceInfo.addGroupedAvp(AVP_USSD_INFORMATION, 10415, false, false);
					inInfo.addAvp(AVP_USSD_STRING, serviceConfig.getDestination(), 10415, false, false, false);
				}

				default -> throw new TestClientException("Unknown serviceConfig type " + serviceConfig.getServiceType());
			}//switch


			if (serviceConfig.hasMultiServices()) {
				reqAvps.addAvp(Avp.MULTIPLE_SERVICES_INDICATOR, 1, serviceConfig.hasMultiServiceIndicator());
			} else {
				reqAvps.addAvp(Avp.MULTIPLE_SERVICES_INDICATOR, 0, true);
			}

			serviceMap.forEach((ratingGroup, service) -> {
				AvpSet serviceControl;
				if (serviceConfig.hasMultiServices()) {
					serviceControl = reqAvps.addGroupedAvp(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL, false, false);
					serviceControl.addAvp(Avp.RATING_GROUP, ratingGroup, true);
				} else {
					serviceControl = reqAvps;
				}

				if (service.getUnitUsed() > 0) {
					AvpSet usedServiceUnitAvp = serviceControl.addGroupedAvp(Avp.USED_SERVICE_UNIT);
					usedServiceUnitAvp.addAvp(serviceConfig.getServiceType().getUnitType().getType(),
					                          service.getUnitUsed(),
					                          serviceConfig.getServiceType().getUnitType().getType() == Avp.CC_TIME);
				}//if

				if (requestType != TERMINATION_REQUEST && service.getRequestUnits() > 0) {
					AvpSet requestServiceUnitAvp = serviceControl.addGroupedAvp(Avp.REQUESTED_SERVICE_UNIT);
					requestServiceUnitAvp.addAvp(serviceConfig.getServiceType().getUnitType().getType(),
					                             service.getRequestUnits(),
					                             serviceConfig.getServiceType().getUnitType().getType() == Avp.CC_TIME);
				}//if

				LOG.info("{}::{} - Sending {} message, requesting for {} units, marking {} units as used, total used {}", session.getSessionId(), msisdn, reqType, service.getRequestUnits(), service.getUnitUsed(), service.getTotalUsed());
			});

			LOG.trace("Sending request:");
			DiameterUtilities.printMessage(request.getMessage());
			startTimer();
			mySession = session;
			session.sendCreditControlRequest(request);
		}//try
		catch (IllegalDiameterStateException | InternalException | OverloadException | RouteException ex) {
			LOG.error("Error sending request", ex);
			throw new TestClientException("Error Sending Request");
		}//catch
	}//createCCR

	public boolean initRequest()
	{
		if (repeats > 0) {
			try {
				requestNr   = 0;
				requestType = INITIAL_REQUEST;
				ApplicationId application = ApplicationId.createByAuthAppId(vendorId, 4);
				ClientCCASessionImpl session = sessionFactory.getNewAppSession(null, application, ClientCCASession.class, Collections.emptyList());
				sessionId = session.getSessionId();
				sendRequest(session);
				repeats--;
				return true;
			}//try
			catch (InternalException ex) {
				LOG.error("Error starting a new request", ex);
			}
		}//if

		return false;
	}

	private void processServiceControl(Service service, AvpSet serviceSet) throws AvpDataException
	{
		if (service != null) {
			Avp grantedUnitsAvp = serviceSet.getAvp(Avp.GRANTED_SERVICE_UNIT);
			long unitsGranted = 0;
			if (grantedUnitsAvp != null) {
				if (serviceConfig.getServiceType() == ServiceType.DATA) {
					unitsGranted = grantedUnitsAvp.getGrouped()
					                              .getAvp(serviceConfig.getServiceType()
					                                                   .getUnitType()
					                                                   .getType())
					                              .getInteger64();
				}//if
				else {
					unitsGranted = grantedUnitsAvp.getGrouped()
					                              .getAvp(serviceConfig.getServiceType()
					                                                   .getUnitType()
					                                                   .getType())
					                              .getUnsigned32();
				}//else
			}

			Avp finalUnitInd = serviceSet.getAvp(Avp.FINAL_UNIT_INDICATION);
			if (finalUnitInd != null) {
				service.setFinalUnitInd(true);
			}

			if (unitsGranted > 0) {
				long unitsUsed;
				if (service.isFinalUnitInd()) {
					unitsUsed = unitsGranted;
				}//if
				else {
					unitsUsed = (long) (unitsGranted * serviceConfig.getUsagePercentage());
				}

				if (unitsUsed > service.getTotalUnits()) {
					unitsUsed = service.getTotalUnits();
				}//if
				service.setTotalUnits(service.getTotalUnits() + unitsUsed);
				service.setUnitUsed(unitsUsed);

				long waitTime = (unitsUsed / serviceConfig.getUsageRateSec()) / serviceConfig.getUsageRate().toSeconds();
				LOG.info("{}::{} - For '{}' Granted {} units, {} units used, {} units remains. Sleep time {} seconds", sessionId, msisdn, serviceConfig.getService(), unitsGranted, unitsUsed, service.getUnitUsed(), waitTime);
				try {
					//						Thread.sleep(waitTime *waitTime * 1000L);
					Thread.sleep(1000L);
				}//try
				catch (InterruptedException ex) {
					Thread.currentThread()
					      .interrupt();
				}//catch
			}//if
		}
	}

	public boolean doCreditControlAnswer(ClientCCASession session, JCreditControlRequest request, JCreditControlAnswer answer)
	{
		if (timer != null) {
			timer.cancel();
			timer = null;
		}//if

		try {
			AvpSet answerAvps = answer.getMessage()
			                          .getAvps();

			int vResultCode = answerAvps.getAvp(Avp.RESULT_CODE)
			                            .getInteger32();
			LOG.info("{}::{} - Answer for '{}' received in {}ms ({}) - Result {}", sessionId, msisdn, serviceConfig.getService(), getElapsedTime(), requestType, vResultCode);
			if (requestType == TERMINATION_REQUEST) {
				LOG.info("{}::{} - Session for '{}' terminated", sessionId, msisdn, serviceConfig.getService());

				try {
					Thread.sleep(1000L);
				}//try
				catch (InterruptedException ex) {
					Thread.currentThread()
					      .interrupt();
				}//catch

				return !initRequest();
			}//if

			requestType = UPDATE_REQUEST;
			if (vResultCode == ResultCode.SUCCESS) {
				if (answerAvps.getAvp(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL) != null) {
					for (Avp msccAvp : answerAvps.getAvps(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL).asArray()) {
						AvpSet msccSet = msccAvp.getGrouped();
						if (msccSet != null && msccSet.getAvp(Avp.RATING_GROUP) != null) {
							Service service = serviceMap.get(msccSet.getAvp(Avp.RATING_GROUP).getInteger32());
							processServiceControl(service, msccSet);
						}
					}
				} else {
					Service service = serviceMap.get(0);
					processServiceControl(service, answerAvps);
				}

				requestType = serviceMap.values()
					              .stream()
					              .allMatch(Service::isFinalUnitInd) ? TERMINATION_REQUEST : UPDATE_REQUEST;
				sendRequest(session);
			}//if
			else {
				String reason = answerAvps.getAvp(Avp.ERROR_MESSAGE) != null ? answerAvps.getAvp(Avp.ERROR_MESSAGE)
				                                                                         .getUTF8String() : "Unknown reason";
				LOG.debug("{}::{} - Terminating unsuccessful session - {}", msisdn, session.getSessionId(), reason);
				return !initRequest();
			}//else
		}//try
		catch (InternalException | NumberFormatException | AvpDataException | TestClientException ex) {
			LOG.error("Error processing CCA Answer", ex);
			return true;
		}//catch

		return false;
	}
}
