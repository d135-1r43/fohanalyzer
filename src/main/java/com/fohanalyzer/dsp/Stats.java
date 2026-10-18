package com.fohanalyzer.dsp;

/**
 * Snapshot of analyzer readouts. {@code micRmsDbfs} is {@code null} when no
 * live mic is connected (SPL is only meaningful for a live source).
 *
 * <p>
 * The solo figures include {@code soloTrim}, as the trace on screen does;
 * subtract it for the level the solo bus actually arrived at.
 */
public record Stats(
	double peakFreq,
	double micPeak,
	double micAvg,
	double soloAvg,
	double soloPeak,
	double soloTrim,
	Double micRmsDbfs)
{
	public Stats withMicRms(Double rms)
	{
		return new Stats(peakFreq, micPeak, micAvg, soloAvg, soloPeak, soloTrim, rms);
	}

	/**
	 * The solo trim that would put the solo trace's average on the mic's — a
	 * first guess at lining the two up, to be nudged from.
	 */
	public double matchingSoloTrim()
	{
		return soloTrim + micAvg - soloAvg;
	}
}
