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

package io.diametertester.enums;


import org.jdiameter.api.Avp;

public enum UnitType
{
	TIME(Avp.CC_TIME),
	OCTET(Avp.CC_TOTAL_OCTETS),
	UNIT(Avp.CC_SERVICE_SPECIFIC_UNITS);

	private final int type;

	UnitType(int type)
	{
		this.type = type;
	}

	public int getType()
	{
		return type;
	}

	public String getName()
	{
		return name();
	}
}
