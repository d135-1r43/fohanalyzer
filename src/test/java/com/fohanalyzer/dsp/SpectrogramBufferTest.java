package com.fohanalyzer.dsp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Ring order, wrap-around, and what clears the spectrograph history. */
class SpectrogramBufferTest
{
	static final int CAPACITY = 4;
	static final int BANDS = 3;

	SpectrogramBuffer buf;

	@BeforeEach
	void setUp()
	{
		buf = new SpectrogramBuffer(CAPACITY);
		buf.configure(12, BANDS, 30);
	}

	/**
	 * A row whose every band reads {@code v}, so a row is told apart by value.
	 */
	static float[] row(float v)
	{
		return new float[] { v, v, v };
	}

	void pushRows(int count)
	{
		for (int i = 0; i < count; i++)
			buf.push(row(i), row(-i));
	}

	@Nested
	class Order
	{
		@Test
		void startsEmpty()
		{
			assertEquals(0, buf.size());
			assertThrows(IndexOutOfBoundsException.class, () -> buf.mic(0));
		}

		@Test
		void newestRowIsAgeZero()
		{
			pushRows(3);

			assertEquals(3, buf.size());
			assertEquals(2, buf.mic(0)[0]);
			assertEquals(1, buf.mic(1)[0]);
			assertEquals(0, buf.mic(2)[0]);
			assertEquals(-2, buf.solo(0)[0]);
		}

		@Test
		void copiesTheRowSoTheCallerCanReuseIt()
		{
			float[] reused = row(7);
			buf.push(reused, reused);
			reused[0] = 99;

			assertEquals(7, buf.mic(0)[0]);
		}

		@Test
		void olderRowsSitInAscendingSlotsFromTheHead()
		{
			pushRows(3);

			assertEquals(buf.head(), buf.slot(0));
			for (int age = 1; age < buf.size(); age++)
				assertEquals((buf.head() + age) % CAPACITY, buf.slot(age));
		}

		@Test
		void rejectsARowOfTheWrongBandCount()
		{
			assertThrows(IllegalArgumentException.class, () -> buf.push(new float[2], new float[2]));
		}
	}

	@Nested
	class WrapAround
	{
		@Test
		void keepsOnlyTheNewestCapacityRows()
		{
			pushRows(CAPACITY + 2);

			assertEquals(CAPACITY, buf.size());
			assertEquals(CAPACITY + 1, buf.mic(0)[0]);
			assertEquals(2, buf.mic(CAPACITY - 1)[0]);
			assertThrows(IndexOutOfBoundsException.class, () -> buf.mic(CAPACITY));
		}

		@Test
		void countsEveryRowWrittenAcrossTheWrap()
		{
			pushRows(CAPACITY * 3 + 1);

			assertEquals(CAPACITY * 3 + 1, buf.written());
		}
	}

	@Nested
	class Clearing
	{
		@Test
		void sameShapeKeepsTheHistory()
		{
			pushRows(2);
			int gen = buf.generation();

			assertFalse(buf.configure(12, BANDS, 30));
			assertEquals(2, buf.size());
			assertEquals(gen, buf.generation());
		}

		@Test
		void historyChangeClearsIt()
		{
			pushRows(2);
			int gen = buf.generation();

			assertTrue(buf.configure(12, BANDS, 60));
			assertEquals(0, buf.size());
			assertEquals(60, buf.historySec());
			assertNotEquals(gen, buf.generation());
		}

		@Test
		void resolutionChangeClearsIt()
		{
			pushRows(2);

			assertTrue(buf.configure(24, 5, 30));
			assertEquals(0, buf.size());
			assertEquals(5, buf.bands());
			buf.push(new float[5], new float[5]);
			assertEquals(1, buf.size());
		}

		@Test
		void fractionChangeWithTheSameBandCountStillClearsIt()
		{
			pushRows(2);

			assertTrue(buf.configure(6, BANDS, 30));
			assertEquals(0, buf.size());
		}

		@Test
		void writtenKeepsCountingAcrossAClear()
		{
			pushRows(3);
			buf.clear();
			pushRows(1);

			assertEquals(4, buf.written());
			assertEquals(1, buf.size());
		}
	}
}
