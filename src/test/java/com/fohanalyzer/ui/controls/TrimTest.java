package com.fohanalyzer.ui.controls;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TrimTest
{
	@Test
	void typedValuesReadAsTheyAreShownOrWritten()
	{
		assertEquals(-3.5, Trim.parse("−3.5 dB"));
		assertEquals(-3.5, Trim.parse("-3.5"));
		assertEquals(2, Trim.parse("+2"));
		assertEquals(98.5, Trim.parse("98,5 dB"));
		assertEquals(98, Trim.parse(" 98dB "));
	}

	@Test
	void anythingElseIsNotANumber()
	{
		assertNull(Trim.parse(""));
		assertNull(Trim.parse("loud"));
		assertNull(Trim.parse("NaN"));
	}
}
