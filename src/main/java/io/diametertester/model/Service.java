package io.diametertester.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Service
{
	@Builder.Default
	private long totalUsed = 0;
	@Builder.Default
	private long unitUsed = 0;
	private long totalUnits;
	private long requestUnits;
	@Builder.Default
	private boolean finalUnitInd = false;
}
