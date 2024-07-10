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

package io.diametertester;

import io.diametertester.service.DiameterTestClient;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.jdiameter.api.IllegalDiameterStateException;

@ApplicationScoped
@Slf4j
public class DiameterTestClientBootstrap
{

	@Inject
	DiameterTestClient testClient;

	void onStart(@Observes StartupEvent ev) throws IllegalDiameterStateException
	{
		LOG.info("Starting the Diameter Test Client");
		testClient.runner();
	}

	void onStop(@Observes ShutdownEvent ev)
	{
		LOG.info("Stopping the Diameter Test Client");
	}
}
