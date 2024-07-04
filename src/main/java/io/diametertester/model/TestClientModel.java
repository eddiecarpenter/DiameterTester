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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class TestClientModel
{
	private List<String> numbers;
	private ConcurrencyConfig concurrency;
	private List<ServiceConfig> services;

	@JsonIgnore
	private int nextNumber = 0;

	public String nextMsisdn()
	{
		String msisdn = numbers.get(nextNumber);
		nextNumber++;
		if (nextNumber >= numbers.size()) {
			nextNumber = 0;
		}//if

		return msisdn;
	}
}
