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

package io.diametertester.jsonserialisers;

public class UnitOfMeasureConverter
{
	private UnitOfMeasureConverter()
	{
	}

	public static Long getValueFromString(String value)
	{

		if (value != null) {

			value = value.replace(" ", "");

			if (value.toLowerCase().endsWith("s")) {
				return Long.parseLong(value.substring(0, value.length() - 1));
			}

			if (value.toLowerCase().endsWith("m")) {
				return Long.parseLong(value.substring(0, value.length() - 1)) * 60L;
			}

			if (value.toLowerCase().endsWith("min")) {
				return Long.parseLong(value.substring(0, value.length() - 3)) * 60L;
			}

			if (value.toLowerCase().endsWith("kb")) {
				return Long.parseLong(value.substring(0, value.length() - 2)) * 1024;
			}

			if (value.toLowerCase().endsWith("mb")) {
				return Long.parseLong(value.substring(0, value.length() - 2)) * 1024 * 1024;
			}

			if (value.toLowerCase().endsWith("gb")) {
				return Long.parseLong(value.substring(0, value.length() - 2)) * 1024 * 1024 * 1024;
			}

			if (value.toLowerCase().endsWith("b")) {
				return Long.parseLong(value.substring(0, value.length() - 1));
			}

			if (value.toLowerCase().endsWith("unit")) {
				return Long.parseLong(value.substring(0, value.length() - 4));
			}

			if (value.toLowerCase().endsWith("uni")) {
				return Long.parseLong(value.substring(0, value.length() - 3));
			}

			return Long.parseLong(value);
		}

		return 1L;
	}
}
