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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.diametertester.exceptions.TestClientException;
import io.diametertester.model.TestClientModel;
import lombok.extern.slf4j.Slf4j;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.MINIMIZE_QUOTES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.SPLIT_LINES;

@Slf4j
public class LoadRunnerModelMapper
{
	public TestClientModel yamlToModel(String yaml)
	{
		try {
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			mapper.registerModule(new JavaTimeModule());
			return mapper.readValue(yaml, TestClientModel.class);
		}
		catch (JsonProcessingException ex) {
			throw new TestClientException("Error parsing the load runner configuration", ex);
		}
	}

	public String modelToYaml(TestClientModel model)
	{
		try {
			ObjectMapper mapper = new ObjectMapper(YAMLFactory.builder()
															  .enable(MINIMIZE_QUOTES)
															  .disable(SPLIT_LINES)
															  .build());
			mapper.registerModule(new JavaTimeModule());
			return mapper.writeValueAsString(model);
		}
		catch (JsonProcessingException ex) {
			throw new TestClientException("Error generating the load runner configuration", ex);
		}
	}
}
