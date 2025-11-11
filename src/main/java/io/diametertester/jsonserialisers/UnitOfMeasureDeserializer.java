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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class UnitOfMeasureDeserializer extends JsonDeserializer<Long>
{
	@Override
	public Long deserialize(JsonParser parser, DeserializationContext ctx) throws IOException
	{
		JsonNode node = parser.getCodec().readTree(parser);

		if (node == null || node.isNull() || node.isMissingNode()) {
			return 0L;
		}

		String value = node.asText();

		if (value == null || value.trim().isEmpty()) {
			return 0L;
		}

		return UnitOfMeasureConverter.getValueFromString(value);
	}
}
