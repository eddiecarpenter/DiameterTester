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

package io.diametertester.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.diametertester.enums.ServiceType;
import io.diametertester.jsonserialisers.DurationDeserializer;
import io.diametertester.jsonserialisers.UnitOfMeasureDeserializer;
import lombok.Data;

import java.time.Duration;

@Data
public class ServiceConfig
{
	private String service;
	private boolean enabled = true;
	private int serviceId;
	private String context;
	private String destination;
	private long ratingGroup;
	private ServiceType serviceType;

	@JsonDeserialize(using = UnitOfMeasureDeserializer.class, as = Long.class)
	private long units;

	@JsonDeserialize(using = UnitOfMeasureDeserializer.class, as = Long.class)
	private long requestUnits;

	@JsonDeserialize(using = UnitOfMeasureDeserializer.class, as = Long.class)
	private long usageRateSec;//: 80mb  # The rate the units are consumed

	@JsonDeserialize(using = DurationDeserializer.class, as = Duration.class)
	private Duration usageRate = Duration.ofSeconds(1);

	private double usagePercentage = 1.0;
}
