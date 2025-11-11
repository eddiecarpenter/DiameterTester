package io.diametertester.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.diametertester.jsonserialisers.UnitOfMeasureDeserializer;
import lombok.Data;

@Data
public class MultiService
{
	private int ratingGroup;

	@JsonDeserialize(using = UnitOfMeasureDeserializer.class, as = Long.class)
	private long units;

	@JsonDeserialize(using = UnitOfMeasureDeserializer.class, as = Long.class)
	private long requestUnits;
}
