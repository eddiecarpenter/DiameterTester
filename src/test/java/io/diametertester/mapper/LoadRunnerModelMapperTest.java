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

package io.diametertester.mapper;

import io.diametertester.enums.ServiceType;
import io.diametertester.model.ServiceConfig;
import io.diametertester.model.TestClientModel;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadRunnerModelMapperTest
{

	private String loadYamlIntoString()
	{
		InputStream inputStream = getClass().getClassLoader().getResourceAsStream("testrun.yaml");
		return new BufferedReader(
				new InputStreamReader(inputStream, StandardCharsets.UTF_8))
				.lines()
				.collect(Collectors.joining("\n"));
	}

	@Test
	void yamlToModel()
	{
		LoadRunnerModelMapper mapper = new LoadRunnerModelMapper();
		TestClientModel model = mapper.yamlToModel(loadYamlIntoString());

		assertEquals(2, model.getNumbers().size());
		assertEquals(5, model.getConcurrency().getNrThreads());
		assertEquals("ocs.frei.one", model.getConcurrency().getDestinationHost());

		assertEquals(4, model.getServices().size());

		ServiceConfig service = model.getServices().getFirst();
		assertEquals("call national number", service.getService());
		assertTrue(service.isEnabled());
		assertEquals(0, service.getServiceId());
		assertEquals("voice@tradeswitch.com", service.getContext());
		assertEquals(ServiceType.VOICE, service.getServiceType());
		assertEquals("0828938386", service.getDestination());
		assertEquals(500, service.getUnits());
		assertEquals(30, service.getRequestUnits());
		assertEquals(5, service.getUsageRateSec());
		assertEquals(Duration.ofSeconds(1), service.getUsageRate());

		service = model.getServices().get(1);
		assertEquals("Consume data", service.getService());
		assertTrue(service.isEnabled());
		assertEquals(400, service.getServiceId());
		assertEquals("data@tradeswitch.com", service.getContext());
		assertEquals(ServiceType.DATA, service.getServiceType());
		assertNull(service.getDestination());
		assertEquals(10 * 1024 * 1024, service.getUnits());
		assertEquals(500 * 1024, service.getRequestUnits());
		assertEquals(80 * 1024 * 1024, service.getUsageRateSec());
		assertEquals(Duration.ofSeconds(10), service.getUsageRate());
	}


}
