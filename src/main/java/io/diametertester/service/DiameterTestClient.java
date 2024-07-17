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
import io.go.diameter.*;
import io.quarkus.runtime.Quarkus;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jdiameter.api.IllegalDiameterStateException;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.Stack;
import org.jdiameter.api.cca.ClientCCASession;
import org.jdiameter.api.cca.ClientCCASessionListener;
import org.jdiameter.api.cca.events.JCreditControlAnswer;
import org.jdiameter.api.cca.events.JCreditControlRequest;
import org.jdiameter.client.api.ISessionFactory;
import org.jdiameter.common.impl.DiameterUtilities;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@DiameterServiceOptions(
		mode = ApplicationMode.CLIENT,
		type = DiameterApplication.CCA
)
@DiameterService
public class DiameterTestClient implements ClientCCASessionListener//, NetworkReqListener
{
	@Inject
	@ConfigProperty(name = "testclient.testconfig", defaultValue = "testclient.yaml")
	String testClientConfig;

	@Inject
	ManagedExecutor executor;

	@DiameterConfig
	Stack stack;

	ISessionFactory sessionFactory;

	private final List<DiameterServiceRunner> runners = new ArrayList<>();

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

	public void runner() throws IllegalDiameterStateException
	{
		LOG.info("Loading the load testing configuration");
		TestClientModel model = loadConfigFile();

		//startService();
		sessionFactory = (ISessionFactory) stack.getSessionFactory();
		if (model.getServices().stream()
				.noneMatch(ServiceConfig::isEnabled)) {
			LOG.error("No ACTIVE test service found - terminating");
			Quarkus.asyncExit();
		}

		long vendorId = stack.getMetaData().getLocalPeer().getVendorId();

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

	public void cancelRunner(DiameterServiceRunner runner)
	{
		runners.remove(runner);
		if (runners.isEmpty()) {
			stack.destroy();
			Quarkus.asyncExit();
		}//if
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
	}
}
