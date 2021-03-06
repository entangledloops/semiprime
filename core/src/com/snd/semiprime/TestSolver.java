package com.snd.semiprime;

import java.io.File;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

/**
 * @author Stephen Dunn
 * @since March 22, 2016
 */
public class TestSolver
{
  private final static int              seed   = 1;
  private final static Random           random = new Random(seed);
  private final static SimpleDateFormat format = new SimpleDateFormat("MM-dd-yyyy");
  private final static String           testDir = "test";
  private final static String           prefix = testDir + "/" + format.format(new Date()) + ".seed-" + seed + ".";

  /**
   * Runs a battery of tests using each heuristic against semiprimes generated
   * between minLen and maxLen a repeat number of times. May be small error on
   * min/max of about +/- 1.
   *
   * @param minLen min len a semiprime should be
   * @param maxLen max len a semiprime should be
   * @param repeat number of times to repeat test
   * @param heuristics heuristics to test
   * @return true if everything goes okay, false otherwise (probably RAM or disk space ran out)
   */
  public static boolean heuristics(int minLen, int maxLen, int repeat, Heuristic... heuristics)
  {
    try (//final PrintWriter log = new PrintWriter(prefix + "heuristics.min-" + minLen + ".max-" + maxLen + ".repeat-" + repeat + ".log");
         final PrintWriter csv = new PrintWriter(prefix + "heuristics.min-" + minLen + ".max-" + maxLen + ".repeat-" + repeat + ".csv"))
      {
        // init
        //Log.init(s -> { log.write(s); log.flush(); });
        Log.disable();
        Solver.init(csv); Solver.callback((n) -> {});

        // run test for factors w/length i
        for (int i = minLen; i <= maxLen; ++i)
        {
          // repeat test j times
          for (int j = 0; j < repeat; ++j)
          {
            // prepare a new target
            final Key key = new Key(i);

            // run all desired searches against target
            for (Heuristic heuristic : heuristics)
            {
              Solver.heuristics(heuristic); // set search heuristics
              new Solver(key.s).start().join(); // execute search
            }

            // release search memory, don't care about history
            Solver.release();
          }
        }

        // cleanup
        Log.o("all tests completed, exiting");
        Solver.shutdown();
        return true;
      }
      catch (Throwable t)
      {
        System.err.println( t.getMessage() ); t.printStackTrace();
        return false;
      }
  }

  /**
   * Records each run of set bits (i.e. n bits in a row) and returns
   * the count of each occurrence. Position 0 in the array should be
   * the sum of all set bits == bitCount().
   *
   * @param i a BigInteger to count the runs in
   * @return the computed counts of runs
   */
  private static int[] runs(BigInteger i)
  {
    final int[] runs = new int[ i.bitLength() ];

    int cur = 0;
    for (int j = 0; j < runs.length; ++j)
    {
      if (i.testBit(j)) ++cur;
      else if (cur > 0) { ++runs[cur-1]; cur = 0; }
    }
    if (cur != 0) ++runs[cur-1];

    return runs;
  }

  /**
   * Calculates statistics on generated semiprimes and writes out a csv file with
   * the results, as well as a log file with the corresponding source values for
   * verification. Can't guarantee generated semiprimes will be exactly == len,
   * but most will be and the highest error rate should +/- 1.
   *
   * @param len Target semiprime len to generate.
   * @param repeat Number of semiprimes to generate.
   * @return true if everything goes okay, false otherwise (probably RAM or disk space ran out)
   */
  public static boolean semiprimes(int len, int repeat)
  {
    try (//final PrintWriter log = new PrintWriter(Test.prefix + "semiprimes.len-" + len + ".repeat-" + repeat + ".log");
         final PrintWriter csv = new PrintWriter(TestSolver.prefix + "semiprimes.len-" + len + ".repeat-" + repeat + ".csv"))
    {
      //Log.init(log::write);
      Log.disable();
      double pLenSum = 0, pCountSum = 0, qLenSum = 0, qCountSum = 0, sLenSum = 0, sCountSum = 0;

      // table header in 2 rows:

      // 1) 3 header columns / value
      for (int i = 0; i < 5; ++i) csv.write(",p,q,s");
      csv.write("\n");

      // 2) 3 statistic columns / value
      csv.write(",");
      for (int i = 0; i < 3; ++i) csv.write("count,");
      for (int i = 0; i < 3; ++i) csv.write("len,");
      for (int i = 0; i < 3; ++i) csv.write("count/len,");
      for (int i = 0; i < 3; ++i) csv.write("max run,");
      for (int i = 0; i < 3; ++i) csv.write("max run count,");
      for (int i = 1; i < (len/2)+1; ++i) csv.write("p " + i + ",");
      for (int i = 1; i < (len/2)+1; ++i) csv.write("q " + i +",");
      for (int i = 1; i < len+1; ++i) csv.write("s " + i + ",");
      csv.write("\n");

      final long[] pRuns = new long[len / 2];
      final long[] qRuns = new long[len / 2];
      final long[] sRuns = new long[len];

      // 3) write run stats
      for (int i = 0; i < repeat; ++i)
      {
        Log.o((1+i) + ":\n");
        final Key key = new Key(len);

        // track number of identical consecutive set bits of each possible length in primes and their product
        final int[] curPRuns = runs(key.p); int maxPRunIndex = 0; for (int j = 1; j < curPRuns.length; ++j) { pRuns[j] += curPRuns[j]; if (curPRuns[j] > 0) maxPRunIndex = j; }
        final int[] curQRuns = runs(key.q); int maxQRunIndex = 0; for (int j = 1; j < curQRuns.length; ++j) { qRuns[j] += curQRuns[j]; if (curQRuns[j] > 0) maxQRunIndex = j; }
        final int[] curSRuns = runs(key.s); int maxSRunIndex = 0; for (int j = 1; j < curSRuns.length; ++j) { sRuns[j] += curSRuns[j]; if (curSRuns[j] > 0) maxSRunIndex = j; }

        // calculate some stats
        final double curPLen = key.p.bitLength(); pLenSum += curPLen;
        final double curPCount = key.p.bitCount(); pCountSum += curPCount;
        final double curQLen = key.q.bitLength(); qLenSum += curQLen;
        final double curQCount = key.q.bitCount(); qCountSum += curQCount;
        final double curSLen = key.s.bitLength(); sLenSum += curSLen;
        final double curSCount = key.s.bitCount(); sCountSum += curSCount;

        csv.write(
            (1+i) + "," +
            curPCount + "," + curQCount + "," + curSCount + "," +
            curPLen + "," + curQLen + "," + curSLen + "," +
            (curPCount/curPLen) + "," + (curQCount/curQLen) + "," + (curSCount/curSLen) + "," +
            (1+maxPRunIndex) + "," + (1+maxQRunIndex) + "," + (1+maxSRunIndex) + "," +
            curPRuns[maxPRunIndex] + "," + curQRuns[maxQRunIndex] + "," + curSRuns[maxSRunIndex] + ","
        );

        for (int j = 0; j < curPRuns.length; ++j) csv.write(curPRuns[j] + ",");
        for (int j = 0; j < curQRuns.length; ++j) csv.write(curQRuns[j] + ",");
        for (int j = 0; j < curSRuns.length; ++j) csv.write(curSRuns[j] + ",");

        csv.write("\n");
      }

      csv.write("\n,avg\np,q,s\n" + (pCountSum/pLenSum) + "," + (qCountSum/qLenSum) + "," + (sCountSum/sLenSum) + "\n");
      csv.write("\n\n,sum / run,,,total bits\nrun,p,q,s,p,q,s\n");
      for (int i = 1; i < pRuns.length; ++i)
      {
        csv.write(
            (1+i) + "," +
            pRuns[i] + "," + qRuns[i] + "," + sRuns[i] + "," +
            (1+i)*pRuns[i] + "," + (i+1)*qRuns[i] + "," + (1+i)*sRuns[i] + "\n"
        );
      }

      return true;
    }
    catch (Throwable t)
    {
      System.err.println( t.getMessage() ); t.printStackTrace();
      return false;
    }
  }

  public static void main(String[] args)
  {
    try { new File(testDir).mkdir(); } catch (Throwable ignored) {}
    //if (!semiprimes(1024, 1000)) System.exit(1);
    long startTime = System.nanoTime();
    if (!heuristics(80, 80, 1, Heuristic.values())) System.exit(2);
    double elapsedMs = (System.nanoTime() - startTime) / 1e6;
    System.out.println("elapsed: " + elapsedMs + "ms");
  }
}
