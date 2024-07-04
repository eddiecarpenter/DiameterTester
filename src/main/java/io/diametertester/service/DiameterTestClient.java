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

import io.diametertester.exceptions.TestClientException;
import io.diametertester.mapper.LoadRunnerModelMapper;
import io.diametertester.model.ServiceConfig;
import io.diametertester.model.TestClientModel;
import io.go.diameter.DiameterConfig;
import io.quarkus.runtime.Quarkus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jdiameter.api.Answer;
import org.jdiameter.api.ApplicationAlreadyUseException;
import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.Configuration;
import org.jdiameter.api.IllegalDiameterStateException;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.Mode;
import org.jdiameter.api.Network;
import org.jdiameter.api.NetworkReqListener;
import org.jdiameter.api.Request;
import org.jdiameter.api.Stack;
import org.jdiameter.api.cca.ClientCCASession;
import org.jdiameter.api.cca.ServerCCASession;
import org.jdiameter.api.cca.events.JCreditControlAnswer;
import org.jdiameter.api.cca.events.JCreditControlRequest;
import org.jdiameter.client.api.ISessionFactory;
import org.jdiameter.common.impl.DiameterUtilities;
import org.jdiameter.common.impl.app.cca.CCASessionFactoryImpl;
import org.jdiameter.server.impl.StackImpl;
import org.jdiameter.server.impl.app.cca.ServerCCASessionImpl;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@ApplicationScoped
@Slf4j
//@DiameterAppFactory(ClientCCASession.class)
public class DiameterTestClient extends CCASessionFactoryImpl implements NetworkReqListener
{
	@Inject
	@ConfigProperty(name = "testclient.testconfig", defaultValue = "testclient.yaml")
	String testClientConfig;

	@Inject
	ManagedExecutor executor;

	@DiameterConfig
	Configuration diameterConfiguration;

	private final Stack stack = new StackImpl();

	private long vendorId = 0;

	private List<DiameterServiceRunner> runners = new ArrayList<>();

	private TestClientModel loadConfigFile()
	{
		LoadRunnerModelMapper mapper = new LoadRunnerModelMapper();

		try (InputStream inputStream = new FileInputStream(testClientConfig)) {
			String yaml = new BufferedReader(
					new InputStreamReader(inputStream, StandardCharsets.UTF_8))
					.lines()
					.collect(Collectors.joining("\n"));
			return mapper.yamlToModel(yaml);
		}//try
		catch (IOException ex) {
			throw new TestClientException("File not found - " + testClientConfig);
		}
	}

	public void startService()
	{
		try {
			stack.init(diameterConfiguration);

			// Let it stabilize...
			try {
				Thread.sleep(500);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}

			vendorId = stack.getMetaData().getLocalPeer().getVendorId();
			Network network = stack.unwrap(Network.class);
			Set<ApplicationId> applIds = stack.getMetaData().getLocalPeer().getCommonApplications();
			for (ApplicationId applId : applIds) {
				LOG.info("Diameter Charge Client: Adding Listener for [{}].",applId);
				network.addNetworkReqListener(this, applId);
			}
			LOG.info("Diameter Charge Client: Supporting {} applications.",applIds.size());

			stack.start(Mode.ALL_PEERS, 30000, TimeUnit.MILLISECONDS);

			sessionFactory = (ISessionFactory) stack.getSessionFactory();
			init(sessionFactory);

			sessionFactory.registerAppFacory(ClientCCASession.class, this);
		}//try
		catch (ApplicationAlreadyUseException | IllegalDiameterStateException | InternalException ex) {
			LOG.error("Failure initializing Diameter Charge Client", ex);
			throw new TestClientException("Failure initializing Diameter Charge Client", ex);
		}//catch
	}//startService

	public void runner()
	{
		LOG.info("Starting the Diameter Stack");
		TestClientModel model = loadConfigFile();

		startService();

		if (model.getServices().stream()
				 .noneMatch(ServiceConfig::isEnabled)) {
			LOG.error("No ACTIVE test service found - terminating");
			Quarkus.asyncExit();
		}

		model.getServices().stream()
			 .filter(ServiceConfig::isEnabled)
			 .forEach(service -> IntStream.range(0, model.getConcurrency().getNrThreads()).forEach(i -> {
				 DiameterServiceRunner runner = new DiameterServiceRunner(service,
																		  this,
																		  model.getConcurrency().getNrRepeats(),
																		  model.nextMsisdn(),
																		  model.getConcurrency().getDestinationHost(),
																		  model.getConcurrency().getDestinationRealm(),
																		  vendorId,
																		  sessionFactory);
				 runners.add(runner);
				 runner.initRequest();
			 }));
	}

	@Override
	public void doCreditControlAnswer(ClientCCASession session, JCreditControlRequest request, JCreditControlAnswer answer) throws InternalException
	{
		LOG.debug("Received Answer:");
		DiameterUtilities.printMessage(answer.getMessage());

		String sessionId = answer.getMessage().getSessionId();
		LOG.debug("Search for linked runner. Total active runners: {}", runners.size());

		for (DiameterServiceRunner runner : runners) {
			if (runner.getSessionId().equals(sessionId)) {
				executor.runAsync(() -> {
					if (runner.doCreditControlAnswer(session, request, answer)) {
						cancelRunner(runner);
					}//if
				});
				break;
			}
		}
	}//doCreditControlAnswer

	public void cancelRunner(DiameterServiceRunner runner)
	{
		runners.remove(runner);
		if (runners.isEmpty()) {
			stack.destroy();
			Quarkus.asyncExit();
		}//if
	}

	@Override
	public Answer processRequest(Request request)
	{
		LOG.debug("<< Received Request [{}]",request);
		try {
			ServerCCASessionImpl session = sessionFactory.getNewAppSession(request.getSessionId(), ApplicationId.createByAuthAppId(vendorId, 4), ServerCCASession.class, Collections.emptyList());
			return session.processRequest(request);
		}
		catch (InternalException e) {
			LOG.error(">< Failure handling received request.", e);
		}

		return null;
	}
}
