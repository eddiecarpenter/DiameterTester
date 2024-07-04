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
import io.diametertester.model.ServiceConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.Avp;
import org.jdiameter.api.AvpDataException;
import org.jdiameter.api.AvpSet;
import org.jdiameter.api.IllegalDiameterStateException;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.OverloadException;
import org.jdiameter.api.ResultCode;
import org.jdiameter.api.RouteException;
import org.jdiameter.api.cca.ClientCCASession;
import org.jdiameter.api.cca.events.JCreditControlAnswer;
import org.jdiameter.api.cca.events.JCreditControlRequest;
import org.jdiameter.client.api.ISessionFactory;
import org.jdiameter.client.impl.app.cca.ClientCCASessionImpl;
import org.jdiameter.common.impl.DiameterUtilities;
import org.jdiameter.common.impl.app.cca.JCreditControlRequestImpl;

import java.util.Collections;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

@Slf4j
@Data
public class DiameterServiceRunner
{
	private static final int INITIAL_REQUEST = 1;
	private static final int UPDATE_REQUEST = 2;
	private static final int TERMINATION_REQUEST = 3;

	private static final int IN_INFORMATION = 20300;
	private static final int CALLING_PARTY_ADDRESS = 20336;
	private static final int CALLED_PARTY_ADDRESS = 20337;
	private static final int REAL_CALLED_NUMBER = 20327;
	private static final int CONNECT_CALLED_NUMBER = 20373;
	private static final int CHARGE_FLOW_TYPE = 20339;

	private final ServiceConfig service;
	private final ISessionFactory sessionFactory;
	private final long vendorId;
	private final String msisdn;
	private final DiameterTestClient client;
	private long totalUnits = 0;
	private long totalUsed = 0;
	private String sessionId;
	private int requestType;
	private int requestNr;
	private int repeats;
	private String destHost;
	private String destRealm;
	private Timer timer;
	private ClientCCASession mySession;
	private long start;

	public DiameterServiceRunner(ServiceConfig service, DiameterTestClient client, int repeats, String msisdn, String destHost, String destRealm, long vendorId, ISessionFactory sessionFactory)
	{
		this.service = service;
		this.vendorId = vendorId;
		this.client = client;
		this.sessionFactory = sessionFactory;
		this.destHost = destHost;
		this.destRealm = destRealm;
		this.msisdn = msisdn;
		this.repeats = repeats;
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

	private void sendRequest(ClientCCASession session, long unitsUsed) throws InternalException
	{
		try {
			JCreditControlRequest request = new JCreditControlRequestImpl(session, destRealm, destHost);

			AvpSet reqAvps = request.getMessage().getAvps();
			AvpSet vSubscriberId = reqAvps.addGroupedAvp(Avp.SUBSCRIPTION_ID);


			reqAvps.addAvp(Avp.EVENT_TIMESTAMP, new Date(System.currentTimeMillis()));
			vSubscriberId.addAvp(Avp.SUBSCRIPTION_ID_TYPE, 0);
			vSubscriberId.addAvp(Avp.SUBSCRIPTION_ID_DATA, msisdn, false);

			reqAvps.addAvp(Avp.CC_REQUEST_TYPE, requestType);
			reqAvps.addAvp(Avp.CC_REQUEST_NUMBER, requestNr);
			requestNr++;
			totalUsed += unitsUsed;
			reqAvps.addAvp(Avp.VENDOR_ID, vendorId);

			AvpSet vUsedServiceUnitAvp = null;
			AvpSet vRequestServiceUnitAvp = null;

			switch (service.getServiceType()) {
				case VOICE -> {
					reqAvps.addAvp(Avp.SERVICE_CONTEXT_ID, service.getContext(), false);
					reqAvps.addAvp(Avp.SERVICE_IDENTIFIER_CCA, service.getServiceId());

					AvpSet voiceServiceInfo = reqAvps.addGroupedAvp(Avp.SERVICE_INFORMATION, 10415, false, false);
					AvpSet inInfo = voiceServiceInfo.addGroupedAvp(IN_INFORMATION, 2011, false, false);
					inInfo.addAvp(CALLING_PARTY_ADDRESS, msisdn, 2011, false, false, false);
					inInfo.addAvp(CALLED_PARTY_ADDRESS, service.getDestination(), 2011, false, false, false);
					inInfo.addAvp(REAL_CALLED_NUMBER, service.getDestination(), 2011, false, false, false);
					inInfo.addAvp(CONNECT_CALLED_NUMBER, service.getDestination(), 2011, false, false, false);
					inInfo.addAvp(CHARGE_FLOW_TYPE, 0, 2011, false, false, true);

					if (unitsUsed > 0) {
						vUsedServiceUnitAvp = reqAvps.addGroupedAvp(Avp.USED_SERVICE_UNIT);
						vUsedServiceUnitAvp.addAvp(service.getServiceType().getUnitType().getType(), unitsUsed, true);
					}//if
					if (requestType != TERMINATION_REQUEST) {
						vRequestServiceUnitAvp = reqAvps.addGroupedAvp(Avp.REQUESTED_SERVICE_UNIT);
						vRequestServiceUnitAvp.addAvp(service.getServiceType().getUnitType().getType(), service.getRequestUnits(), true);
					}//if
				}

				case DATA -> {
					reqAvps.addAvp(Avp.SERVICE_CONTEXT_ID, service.getContext(), false);
					reqAvps.addAvp(Avp.MULTIPLE_SERVICES_INDICATOR, 1, true);
					AvpSet vMultiCtrl = reqAvps.addGroupedAvp(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL, false, false);
					reqAvps.addAvp(Avp.SERVICE_IDENTIFIER_CCA, service.getServiceId());
					vMultiCtrl.addAvp(Avp.RATING_GROUP, service.getRatingGroup(), true);

					AvpSet dataServiceInfo = reqAvps.addGroupedAvp(Avp.SERVICE_INFORMATION, 10415, false, false);
					AvpSet psInfo = dataServiceInfo.addGroupedAvp(Avp.PS_INFORMATION, 10415, false, false);
					psInfo.addAvp(Avp.TGPP_CHARGING_ID, 12345, 10415, false, false, true);

					if (unitsUsed > 0) {
						vUsedServiceUnitAvp = vMultiCtrl.addGroupedAvp(Avp.USED_SERVICE_UNIT);
						vUsedServiceUnitAvp.addAvp(service.getServiceType().getUnitType().getType(), unitsUsed, false);
					}//if

					if (requestType != TERMINATION_REQUEST) {
						vRequestServiceUnitAvp = vMultiCtrl.addGroupedAvp(Avp.REQUESTED_SERVICE_UNIT);
						vRequestServiceUnitAvp.addAvp(service.getServiceType().getUnitType().getType(), service.getRequestUnits(), false);
					}//if
				}

				case SMS -> {
					reqAvps.addAvp(Avp.SERVICE_CONTEXT_ID, service.getContext(), false);
					reqAvps.addAvp(Avp.SERVICE_IDENTIFIER_CCA, service.getServiceId());
					if (unitsUsed > 0) {
						vUsedServiceUnitAvp = reqAvps.addGroupedAvp(Avp.USED_SERVICE_UNIT);
						vUsedServiceUnitAvp.addAvp(service.getServiceType().getUnitType().getType(), unitsUsed, false);
					}//if
					if (requestType != TERMINATION_REQUEST) {
						vRequestServiceUnitAvp = reqAvps.addGroupedAvp(Avp.REQUESTED_SERVICE_UNIT);
						vRequestServiceUnitAvp.addAvp(service.getServiceType().getUnitType().getType(), service.getRequestUnits(), false);
					}//if

					AvpSet smsServiceInfo = reqAvps.addGroupedAvp(Avp.SERVICE_INFORMATION, 10415, false, false);
					AvpSet smsInfo = smsServiceInfo.addGroupedAvp(Avp.SMS_INFORMATION, 10415, false, false);

					AvpSet vRecInfo = smsInfo.addGroupedAvp(Avp.RECIPIENT_INFO, 10415, false, false);
					AvpSet vDestAddr = vRecInfo.addGroupedAvp(Avp.RECIPIENT_ADDRESS, 10415, false, false);
					vDestAddr.addAvp(Avp.ADDRESS_TYPE, 1, 10415, false, false, true);
					vDestAddr.addAvp(Avp.ADDRESS_DATA, service.getDestination(), 10415, false, false, false);
				}
				default -> throw new TestClientException("Unknown service type " + service.getServiceType());
			}//switch

			String reqType = "SETUP";
			switch (requestType) {
				case INITIAL_REQUEST -> {
					reqType = "SETUP";
					LOG.info("{}::{} - Call Setup", session.getSessionId(), msisdn);
				}
				case UPDATE_REQUEST -> {
					reqType = "UPDATE";
					LOG.info("{}::{} - Call Update", session.getSessionId(), msisdn);
				}
				case TERMINATION_REQUEST -> {
					reqType = "TERMINATE";
					LOG.info("{}::{} - Call Terminated", session.getSessionId(), msisdn);
				}
			}//switch

			LOG.info("{}::{} - Sending {} message, requesting for {} units, marking {} units as used, total used {}", session.getSessionId(), msisdn, reqType, service.getRequestUnits(), unitsUsed, totalUsed);

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
				totalUnits = service.getUnits();
				totalUsed = 0;
				requestNr = 0;
				requestType = INITIAL_REQUEST;
				sessionId = UUID.randomUUID().toString();
				ApplicationId application = ApplicationId.createByAuthAppId(vendorId, 4);
				ClientCCASessionImpl session = sessionFactory.getNewAppSession(sessionId, application, ClientCCASession.class, Collections.emptyList());
				sendRequest(session, 0);
				repeats--;
				return true;
			}//try
			catch (InternalException ex) {
				LOG.error("Error starting a new request", ex);
			}
		}//if

		return false;
	}

	public boolean doCreditControlAnswer(ClientCCASession session, JCreditControlRequest request, JCreditControlAnswer answer)
	{
		if (timer != null) {
			timer.cancel();
			timer = null;
		}//if

		try {
			AvpSet answerAvps = answer.getMessage().getAvps();

			int vResultCode = answerAvps.getAvp(Avp.RESULT_CODE).getInteger32();
			LOG.info("{}::{} - Answer for '{}' received in {}ms ({}) - Result {}", sessionId, msisdn, service.getService(), getElapsedTime(), requestType, vResultCode);
			if (requestType == TERMINATION_REQUEST) {
				LOG.info("{}::{} - Session for '{}' terminated", sessionId, msisdn, service.getService());

				try {
					Thread.sleep(1000L);
				}//try
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}//catch

				return !initRequest();
			}//if

			requestType = UPDATE_REQUEST;
			if (vResultCode == ResultCode.SUCCESS) {
				long unitsGranted = 0;

				Avp grantedUnitsAvp;
				AvpSet serviceControl = answerAvps;
				Avp multiCtrl = answerAvps.getAvp(Avp.MULTIPLE_SERVICES_CREDIT_CONTROL);
				if (multiCtrl != null) {
					serviceControl = multiCtrl.getGrouped();
				}//if
				grantedUnitsAvp = serviceControl.getAvp(Avp.GRANTED_SERVICE_UNIT);
				if (grantedUnitsAvp != null) {
					if (service.getServiceType() == ServiceType.VOICE) {
						unitsGranted = grantedUnitsAvp.getGrouped().getAvp(service.getServiceType().getUnitType().getType()).getInteger32();
					}//if
					else {
						unitsGranted = grantedUnitsAvp.getGrouped().getAvp(service.getServiceType().getUnitType().getType()).getInteger64();
					}//else
				}//if

				Avp finalUnitInd = serviceControl.getAvp(Avp.FINAL_UNIT_INDICATION);
				long unitsUsed = 0;
				if (unitsGranted > 0) {
					if (finalUnitInd == null) {
						unitsUsed = (long) (unitsGranted * service.getUsagePercentage());
					}//if
					else {
						unitsUsed = unitsGranted;
					}

					if (unitsUsed > totalUnits) {
						unitsUsed = totalUnits;
					}//if
					totalUnits -= unitsUsed;

					long waitTime = (unitsUsed / service.getUsageRateSec()) / service.getUsageRate().toSeconds();
					LOG.info("{}::{} - For '{}' Granted {} units, {} units used, {} units remains. Sleep time {} seconds", sessionId, msisdn, service.getService(), unitsGranted, unitsUsed, totalUnits, waitTime);
					try {
						//						Thread.sleep(waitTime *waitTime * 1000L);
						Thread.sleep(1000L);
					}//try
					catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}//catch
				}//if

				requestType = (totalUnits <= 0 || unitsGranted <= 0 || finalUnitInd != null) ? TERMINATION_REQUEST : UPDATE_REQUEST;
				sendRequest(session, unitsUsed);
			}//if
			else {
				LOG.debug("{}::{} - Terminating unsuccessful session", msisdn, session.getSessionId());
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
