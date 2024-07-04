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

import static io.diametertester.enums.UnitType.*;

public enum ServiceType
{
	VOICE(TIME),
	DATA(OCTET),
	USSD(UNIT),
	USSD2(TIME),
	SMS(UNIT);
	private final UnitType unitType;

	ServiceType(UnitType unitType)
	{
		this.unitType = unitType;
	}

	public UnitType getUnitType()
	{
		return unitType;
	}
}