/*
 * Japex software ("Software")
 *
 * Copyright, 2004-2007 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Software is licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at:
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations.
 *
 *    Sun supports and benefits from the global community of open source
 * developers, and thanks the community for its important contributions and
 * open standards-based technology, which Sun has adopted into many of its
 * products.
 *
 *    Please note that portions of Software may be provided with notices and
 * open source licenses from such communities and third parties that govern the
 * use of those portions, and any licenses granted hereunder do not alter any
 * rights and obligations you may have under such open source licenses,
 * however, the disclaimer of warranty and limitation of liability provisions
 * in this License will apply to all Software in this distribution.
 *
 *    You acknowledge that the Software is not designed, licensed or intended
 * for use in the design, construction, operation or maintenance of any nuclear
 * facility.
 *
 * Apache License
 * Version 2.0, January 2004
 * http://www.apache.org/licenses/
 *
 */

package com.sun.japex;

import java.util.*;
import java.io.File;
import java.io.PrintWriter;
import java.util.concurrent.*;
import java.lang.management.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sun.japex.Constants.*;

/**
 * The core engine of JAPEX.
 * This class is responsible for running benchmarks. It parses XML configuration files and applies
 * the drivers to the test cases. The results end up in the TestSuiteImpl objects. As specified by
 * the configuration, this class will also print results on the fly to outputWriter.
 * 
 * Normally, drivers are loaded from isolated class loaders constructed according to the paths
 * in the XML files. However, to facilitate integration in some environments, this class
 * supports two classpath alternatives. If japex.contextClassLoader is set to true, this class
 * simply loads driver classes from the current thread context class loader, and propagates that loader
 * to any new threads.
 * 
 * Alternatively, the user of this class can call {@link Engine#getNamedClassPaths()} to obtain a
 * Map&lt;String, ClassLoader&gt;. Class loaders registered in this map are available via 
 * japex.namedClassPath to be called out for individual drivers.
 */
public class Engine {
	private final static Logger LOG = LoggerFactory.getLogger(Engine.class);
    
    /**
     * The test suite being executed by this engine.
     */
    TestSuiteImpl _testSuite;

    /**
     * Thread pool used for the test's execution.
     */
    ThreadPoolExecutor _threadPool;
    
    /**
     * Matrix of driver instances of size nOfThreads * runsPerDriver.
     */
    JapexDriverBase _drivers[][];
    
    /**
     * Current driver being executed.
     */
    DriverImpl _driverImpl;
    
    /**
     * Current driver run being executed.
     */
    int _driverRun;    
    
    /**
     * Used to check if all drivers (or no driver) compute a result.
     */
    Boolean _computeResult = null;
    
    /**
     * Running geometric mean = (sum{i,n} x_i) / n
     */
    double _geomMeanresult = 1.0;
    
    /**
     * Running arithmetic mean = (prod{i,n} x_i)^(1/n)
     */
    double _aritMeanresult = 0.0;
    
    /**
     * Harmonic mean inverse = sum{i,n} 1/(n * x_i)
     */
    double _harmMeanresultInverse = 0.0;

    /**
     * List of GC beans to estimate percentage of GC time (%gctime unit)
     */
    protected List<GarbageCollectorMXBean> _gCCollectors;
    
    /**
     * GC time in millis during measurement period
     */
    protected long _gCTime;

    /**
     * Used to compute per driver heap memory usage
     */
    long _beforeHeapMemoryUsage;
    
    private PrintWriter outputWriter = new PrintWriter(System.out);
    
    private Map<String, ClassLoader> _namedClassPaths;
    
    public Engine() {
        _gCCollectors = ManagementFactory.getGarbageCollectorMXBeans();
        _namedClassPaths = new HashMap<String, ClassLoader>();
    }
    
    public void setOutputWriter(PrintWriter outputWriter) {
    	this.outputWriter = outputWriter;
    }
    
    public PrintWriter getOutputWriter() {
    	return outputWriter;
    }
    
    public TestSuiteImpl start(List<String> configFiles) {
        try { 
            // Load config file
            ConfigFileLoader cfl = new ConfigFileMerger(configFiles);
            _testSuite = cfl.getTestSuite();
            
            // Ensure result of merge is well formed
            List<DriverImpl> driverList = _testSuite.getDriverInfoList();
            if (driverList.size() == 0 
                    || driverList.get(0).getTestCases(0).size() == 0) 
            {
                LOG.error("Error: A Japex test suite must contain at " +
                        "least one driver and at least one test case");
                throw new JapexException("Error: A Japex test suite must contain at " +
                        "least one driver and at least one test case");
            }
            
            if (Japex.test) {
                outputWriter.println("Running in test mode without generating reports ...");
            }

            // Print estimated running time
            if (_testSuite.hasParam(WARMUP_TIME) && 
                    _testSuite.hasParam(RUN_TIME)) 
            {
                int[] hms = estimateRunningTime(_testSuite);
                outputWriter.println("Estimated warmup time + run time is " +
                    (hms[0] > 0 ? (hms[0] + " hours ") : "") +
                    (hms[1] > 0 ? (hms[1] + " minutes ") : "") +
                    (hms[2] > 0 ? (hms[2] + " seconds ") : ""));                    
            }

            forEachDriver();                  
        }
        catch (Exception e) {
            throw new JapexException(e);
        }

        return _testSuite;
    }        
    
    private JapexDriverBase getJapexDriver(ClassLoader loader, String className) throws ClassNotFoundException { 
    	try {
        // 	Use 'this' class loader here
    		Class<? extends JapexDriverBase> clazz = Class.forName(className, true, loader).asSubclass(JapexDriverBase.class);
    		return clazz.newInstance();
    	}
    	catch (InstantiationException e) {
    		throw new JapexException(e);
    	}
    	catch (IllegalAccessException e) {
    		throw new JapexException(e);
    	}
    	catch (ClassCastException e) {
    		throw new JapexException("Class '" + className 
    				+ "' must extend '" + JapexDriverBase.class.getName() + "'");
    	}
}

    private void forEachDriver() {
        try {
            List<DriverImpl> driverList = _testSuite.getDriverInfoList();

            // Init class loader variables
            JapexClassLoader jcLoader = null;
            boolean singleClassLoader = 
                    _testSuite.getBooleanParam(SINGLE_CLASS_LOADER);
            ClassLoader effectiveClassLoader = null;
            boolean contextClassLoader = _testSuite.getBooleanParam(CONTEXT_CLASS_LOADER);
            
            // Iterate through each driver in final list
            for (int k = 0; k < driverList.size(); k++) {
                _driverImpl = driverList.get(k);

                int nOfCpus = _driverImpl.getIntParam(NUMBER_OF_CPUS);
                int nOfThreads = _driverImpl.getIntParam(NUMBER_OF_THREADS);
                int runsPerDriver = _driverImpl.getIntParam(RUNS_PER_DRIVER);
                int warmupsPerDriver = _driverImpl.getIntParam(WARMUPS_PER_DRIVER);
                
                if (contextClassLoader) {
                	effectiveClassLoader = Thread.currentThread().getContextClassLoader();
                } else {
                	String namedClassPath = _driverImpl.getParam(NAMED_CLASS_PATH);
                	if (namedClassPath != null) {
                		effectiveClassLoader = _namedClassPaths.get(namedClassPath);
                		if (effectiveClassLoader == null) {
                			throw new JapexException("Reference to undefined class path " + namedClassPath);
                		}
                	} else {
                		// Create new class loader or extend path of existing one
                		if (singleClassLoader) {
                			if (jcLoader == null) {
                				jcLoader = new JapexClassLoader(_driverImpl.getParam(CLASS_PATH));
                				Thread.currentThread().setContextClassLoader(jcLoader);
                				effectiveClassLoader = jcLoader;
                			}
                			else {
                				jcLoader.addClassPath(_driverImpl.getParam(CLASS_PATH));
                			}
                		} else {
                			jcLoader = new JapexClassLoader(_driverImpl.getParam(CLASS_PATH));
                			Thread.currentThread().setContextClassLoader(jcLoader);
                			effectiveClassLoader = jcLoader;
                		}
                	}
                }
 
                outputWriter.print("  " + _driverImpl.getName() + " using " 
                    + nOfThreads + " thread(s) on " + nOfCpus + " cpu(s)");
                
                // Allocate a matrix of nOfThreads * actualRuns size and initialize each instance
                int actualRuns = warmupsPerDriver + runsPerDriver;
                try {
                    _drivers = new JapexDriverBase[nOfThreads][actualRuns];
                    for (int i = 0; i < nOfThreads; i++) {
                        for (int j = 0; j < actualRuns; j++) {
                        	String driverClassName = _driverImpl.getParam(DRIVER_CLASS);
                        	_drivers[i][j] = getJapexDriver(effectiveClassLoader, driverClassName); // returns fresh copy.

                            _drivers[i][j].setDriver(_driverImpl);
                            _drivers[i][j].setTestSuite(_testSuite);
                            _drivers[i][j].initializeDriver();
                        }
                    }
                }
                catch (Throwable e) {
                    outputWriter.println("\n  Warning: Unable to load driver '" 
                        + _driverImpl.getName() + "'");
                    outputWriter.println("           " + e.toString());
                    
                    // Remove driver from final list, adjust k and continue
                    _testSuite.getDriverInfoList().remove(_driverImpl);
                    k--; 
                    
                    // Increment Japex exit code as a counter for errors
                    Japex.exitCode++;
                    
                    continue;                    
                }
                
		// Created thread pool of nOfThreads size and pre-start threads                
		if (nOfThreads > 1) {
		    _threadPool = new ThreadPoolExecutor(nOfThreads, nOfThreads, 0L,
			TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
			new JapexThreadFactory(effectiveClassLoader));      // Use Japex thread factory
		    _threadPool.prestartAllCoreThreads();
		}

                // Reset memory usage before starting runs
                resetPeakMemoryUsage();
                        
                // Display driver's name
                forEachRun();
                
                // Set memory usage param and display info
                if (_testSuite.getBooleanParam(REPORT_PEAK_HEAP_USAGE)
                        && PEAK_HEAP_USAGE_PER_DRIVER.equals(getHeapResetContext())) {
                    setPeakMemoryUsage(_driverImpl);
                    outputWriter.println("    Peak heap usage: "
                        + _driverImpl.getParam(PEAK_HEAP_USAGE)
                        + " KB");
                }
                
                // Call terminate on all driver instances
                for (int i = 0; i < nOfThreads; i++) {
                    for (int j = 0; j < actualRuns; j++) {
                        _drivers[i][j].terminateDriver();
                    }
                }                
                
                // Shutdown thread pool
                if (nOfThreads > 1) {
		    _threadPool.shutdown();
                }                
            }   

            // If number drives is zero, abort as no drivers were loaded
            if (_testSuite.getDriverInfoList().size() == 0) {
                LOG.error("Error: Unable to load any of the " +
                        "drivers in the test suite");
                throw new JapexException("Error: Unable to load any of the " +
                        "drivers in the test suite");
            }
        }
        catch (Exception e) {
            throw new JapexException(e);
        }
    }
    
    private void forEachRun() {
        try {
            int runsPerDriver = _driverImpl.getIntParam(RUNS_PER_DRIVER);
            int warmupsPerDriver = _driverImpl.getIntParam(WARMUPS_PER_DRIVER);

            int actualRuns = warmupsPerDriver + runsPerDriver;
            for (_driverRun = 0; _driverRun < actualRuns; _driverRun++) {
                if (_driverRun < warmupsPerDriver) {
                    outputWriter.print("\n    Warmup " + (_driverRun + 1) + ": ");
                }
                else {
                    outputWriter.print("\n    Run " + (_driverRun - warmupsPerDriver + 1) + ": ");                        
                }
                
                if (Japex.resultPerLine) {
                    outputWriter.println("");
                }
                
                // geometric mean = (sum{i,n} x_i) / n
                _geomMeanresult = 1.0;
                // arithmetic mean = (prod{i,n} x_i)^(1/n)
                _aritMeanresult = 0.0;
                // harmonic mean inverse = sum{i,n} 1/(n * x_i)
                _harmMeanresultInverse = 0.0;

                forEachTestCase();

                if (Japex.resultPerLine) {
                    outputWriter.print(
                            "      aritmean," + Util.formatDouble(_aritMeanresult) +
                            ",\n      geommean," + Util.formatDouble(_geomMeanresult) +
                            ",\n      harmmean," + Util.formatDouble(1.0 / _harmMeanresultInverse));
                } else {
                    outputWriter.print(
                            "aritmean," + Util.formatDouble(_aritMeanresult) +
                            ",geommean," + Util.formatDouble(_geomMeanresult) +
                            ",harmmean," + Util.formatDouble(1.0 / _harmMeanresultInverse));
                }
            }

            int startRun = warmupsPerDriver;
            if (actualRuns - startRun > 1) {
                // Print average for all runs
                outputWriter.print("\n     Avgs: ");
                Iterator<TestCaseImpl> tci = _driverImpl.getAggregateTestCases().iterator();
                while (tci.hasNext()) {
                    TestCaseImpl tc = (TestCaseImpl) tci.next();
                    outputWriter.print(tc.getName() + ",");                        
                    outputWriter.print(
                        Util.formatDouble(tc.getDoubleParam(RESULT_VALUE)) 
                        + ",");
                }
                outputWriter.print(
                    "aritmean," +
                    _driverImpl.getParam(RESULT_ARIT_MEAN) + 
                    ",geommean," +
                    _driverImpl.getParam(RESULT_GEOM_MEAN) + 
                    ",harmmean," +
                    _driverImpl.getParam(RESULT_HARM_MEAN));   

                // Print standardDevs for all runs
                outputWriter.print("\n    Stdev: ");
                tci = _driverImpl.getAggregateTestCases().iterator();
                while (tci.hasNext()) {
                    TestCaseImpl tc = (TestCaseImpl) tci.next();
                    outputWriter.print(tc.getName() + ",");                        
                    outputWriter.print(
                        Util.formatDouble(tc.getDoubleParam(RESULT_VALUE_STDDEV)) 
                        + ",");
                }
                outputWriter.println(
                    "aritmean," +
                    _driverImpl.getParam(RESULT_ARIT_MEAN_STDDEV) + 
                    ",geommean," +
                    _driverImpl.getParam(RESULT_GEOM_MEAN_STDDEV) + 
                    ",harmmean," +
                    _driverImpl.getParam(RESULT_HARM_MEAN_STDDEV));   
            }
            else {
                outputWriter.println("");
            }
        }
        catch (Exception e) {
            throw new JapexException(e);
        }
    }
    
    private void forEachTestCase() {
        try {
            double endTime;
            int nOfCpus = _driverImpl.getIntParam(NUMBER_OF_CPUS);
            int nOfThreads = _driverImpl.getIntParam(NUMBER_OF_THREADS);
            
            // Get list of tests
            List<TestCaseImpl> tcList = _driverImpl.getTestCases(_driverRun);
            int nOfTests = tcList.size();
            
            // Iterate through list of test cases
            Iterator<TestCaseImpl> tci = tcList.iterator();
            while (tci.hasNext()) {
                TestCaseImpl tc = tci.next();
                
				if (PEAK_HEAP_USAGE_PER_TESTCASE.equals(getHeapResetContext())) {
                    // Reset memory usage before starting runs
                    resetPeakMemoryUsage();
                }
				
                if (Japex.verbose) {
                    outputWriter.println(tc.getName());
                } 
                else if (Japex.resultPerLine) {
                    outputWriter.print("      " + tc.getName() + ",");
                }
                else {
                    outputWriter.print(tc.getName() + ",");
                }
                
                Future<?>[] futures = null;
                List<Long> gCStartTimes = null;
                
                try {
                    // If nOfThreads == 1, re-use this thread
                    if (nOfThreads == 1) {
                        // -- Prepare phase --------------------------------------
                        
                        _drivers[0][_driverRun].setTestCase(tc);     // tc is shared!
                        _drivers[0][_driverRun].prepare();
                        
                        // -- Warmup phase ---------------------------------------
                        
                        endTime = tc.hasParam(WARMUP_TIME) ?
                            Util.currentTimeMillis() +
                                Util.parseDuration(tc.getParam(WARMUP_TIME)) : 0L;
                        
                        // First time call does warmup
                        _drivers[0][_driverRun].setEndTime(endTime);
                        _drivers[0][_driverRun].call();
                        
                        // Set actual warmup time using sum if just one thread
                        tc.setDoubleParam(ACTUAL_WARMUP_TIME,
                            tc.getDoubleParam(WARMUP_TIME_SUM));
                        
                        // -- Run phase -------------------------------------------
                        
                        endTime = tc.hasParam(RUN_TIME) ?
                            Util.currentTimeMillis() +
                                Util.parseDuration(tc.getParam(RUN_TIME)) : 0L;
 
                        // Run GC and reset GC start times
                        System.gc();                       
                        gCStartTimes = getGCAbsoluteTimes();
                        
                        // Second time call does run
                        _drivers[0][_driverRun].setEndTime(endTime);
                        _drivers[0][_driverRun].call();
                        
                        // Set actual run time using sum if there's one thread
                        tc.setDoubleParam(ACTUAL_RUN_TIME,
                            tc.getDoubleParam(RUN_TIME_SUM));
                    } 
                    else {  // nOfThreads > 1
                        
                        // -- Prepare phase --------------------------------------
                        
                        // Initialize driver instance with test case object do prepare
                        for (int i = 0; i < nOfThreads; i++) {
                            _drivers[i][_driverRun].setTestCase(tc);     // tc is shared!
                            _drivers[i][_driverRun].prepare();
                        }
                        
                        // -- Warmup phase ---------------------------------------
                        
                        // Fork all threads -- first time drivers will warmup
                        futures = new Future<?>[nOfThreads];
                        
                        endTime = tc.hasParam(WARMUP_TIME) ?
                            Util.currentTimeMillis() +
                                Util.parseDuration(tc.getParam(WARMUP_TIME)) : 0L;
                        
                        for (int i = 0; i < nOfThreads; i++) {
                            _drivers[i][_driverRun].setEndTime(endTime);
                            futures[i] = _threadPool.submit(_drivers[i][_driverRun]);
                        }
                        
                        // Wait for all threads to finish
                        for (int i = 0; i < nOfThreads; i++) {
                            futures[i].get();
                        }
                        
                        // Set actual warmup time using average over threads
                        tc.setDoubleParam(ACTUAL_WARMUP_TIME,
                            tc.getDoubleParam(WARMUP_TIME_SUM) / nOfThreads);
                        
                        // -- Run phase -------------------------------------------
                        
                        endTime = tc.hasParam(RUN_TIME) ?
                            Util.currentTimeMillis() +
                                Util.parseDuration(tc.getParam(RUN_TIME)) : 0L;
                        
                        // Run GC and reset GC start times
                        System.gc();                       
                        gCStartTimes = getGCAbsoluteTimes();
                        
                        // Fork all threads -- second time drivers will run
                        for (int i = 0; i < nOfThreads; i++) {
                            _drivers[i][_driverRun].setEndTime(endTime);
                            futures[i] = _threadPool.submit(_drivers[i][_driverRun]);
                        }
                        
                        // Wait for all threads to finish
                        for (int i = 0; i < nOfThreads; i++) {
                            futures[i].get();
                        }
                        
                        // Set actual run time using average over threads
                        tc.setDoubleParam(ACTUAL_RUN_TIME,
                            tc.getDoubleParam(RUN_TIME_SUM) / nOfThreads);                        
                    }
                    
                    // Get the total time take for GC over the measurement period
                    _gCTime = getGCRelativeTotalTime(gCStartTimes);
                    
                    // Finish phase
                    for (int i = 0; i < nOfThreads; i++) {
                        _drivers[i][_driverRun].finish();
                    }
                } 
                catch (Exception e) {
                    // Set output parameters for computeResultValue()
                    tc.setDoubleParam(RESULT_VALUE, Double.NaN);
                    tc.setLongParam(RUN_ITERATIONS_SUM, 0L);
                    tc.setDoubleParam(RUN_TIME_SUM, Double.NaN);
                    tc.setDoubleParam(ACTUAL_RUN_TIME, Double.NaN);

                    // Print stack trace unless in silent mode
                    if (!Japex.silent) {
                        e.printStackTrace();
                    }
                    
                    // Increment Japex exit code as a counter for errors
                    Japex.exitCode++;
                } 
                finally {
                    if (futures != null) {
                        // Cancel all remaining threads
                        for (int i = 0; i < nOfThreads; i++) {
                            futures[i].cancel(true);
                        }
                    }
					
					if (_testSuite.getBooleanParam(REPORT_PEAK_HEAP_USAGE)
                            && PEAK_HEAP_USAGE_PER_TESTCASE.equals(getHeapResetContext())) {
                        setPeakMemoryUsage(tc);
                        outputWriter.println("    Peak heap usage: " + tc.getParam(PEAK_HEAP_USAGE) + " KB");
                    }
                }
                
                double result;
                if (tc.hasParam(RESULT_VALUE)) {
                    result = tc.getDoubleParam(RESULT_VALUE);
                } 
                else {
                    result = computeResultValue(tc, nOfThreads, nOfCpus);
                    tc.setDoubleParam(RESULT_VALUE, result);
                }
                
                // If japex.resultUnitX is set and the driver has not computed
                // a value for japex.resultValueX, then compute it
                if (tc.hasParam(RESULT_UNIT_X) && !tc.hasParam(RESULT_VALUE_X)) {
                    result = computeResultValue(tc, nOfThreads, nOfCpus);
                    tc.setDoubleParam(RESULT_VALUE_X, result);
                }
                
                // Compute running means
                _aritMeanresult += result / nOfTests;
                _geomMeanresult *= Math.pow(result, 1.0 / nOfTests);
                _harmMeanresultInverse += 1.0 / (nOfTests * result);
                
                // Display results for this test
                if (Japex.verbose) {
                    outputWriter.println("           " + tc.getParam(RESULT_VALUE));
                    outputWriter.print("           ");
                } 
                else if (Japex.resultPerLine) {
                    outputWriter.println(tc.getParam(RESULT_VALUE) + ",");
                }
                else {
                    outputWriter.print(tc.getParam(RESULT_VALUE) + ",");
                    outputWriter.flush();
                }
            }
        } 
        catch (Exception e) {
            throw new JapexException(e);
        }
    }

    private void resetPeakMemoryUsage() {
        // Force GC before collecting current usage (from JLS 4th)
        Runtime rt = Runtime.getRuntime();
        long wasFree, isFree = rt.freeMemory();
        do {
            wasFree = isFree;
            rt.runFinalization();
            rt.gc();
            isFree = rt.freeMemory();
        } while (isFree > wasFree);
                
        // Accumulate usage from all heap-type pools
        _beforeHeapMemoryUsage = 0L;
        for (MemoryPoolMXBean b : ManagementFactory.getMemoryPoolMXBeans()) {
            b.resetPeakUsage();     // Sets it to current usage
            if (b.getType() == MemoryType.HEAP) {
                _beforeHeapMemoryUsage += b.getPeakUsage().getUsed();
            }
        }
    }
    
    private void setPeakMemoryUsage(ParamsImpl params) {
        long afterHeapMemoryUsage = 0L;
        
        // Accumulate usage from all heap-type pools
        for (MemoryPoolMXBean b : ManagementFactory.getMemoryPoolMXBeans()) {
            if (b.getType() == MemoryType.HEAP) {
                afterHeapMemoryUsage += b.getPeakUsage().getUsed();
            }
        }

        // Set output parameter
        params.setDoubleParam(PEAK_HEAP_USAGE,
                (afterHeapMemoryUsage - _beforeHeapMemoryUsage) / 1024.0);
    }
	
	private String getHeapResetContext()
    {
        String resetContext = _testSuite.getParam(PEAK_HEAP_USAGE_RESET_CONTEXT);
        if (resetContext == null) {
            resetContext = PEAK_HEAP_USAGE_PER_DRIVER;
        }
        return resetContext;
    }
    
    private List<Long> getGCAbsoluteTimes() {
        List<Long> gCTimes = new ArrayList<Long>();
        for (GarbageCollectorMXBean gcc : _gCCollectors) {
            gCTimes.add(gcc.getCollectionTime());
        }        
        return gCTimes;
    }
    
    private long getGCRelativeTotalTime(List<Long> start) {
        List<Long> end = getGCAbsoluteTimes();        
        long time = 0;
        for (int i = 0; i < start.size(); i++) {
            time += end.get(i) - start.get(i);
        }        
        return time;
    }

    /**
     * Compute Tx, L and Mbps
     * 
     * T = test duration in seconds
     * N = number of threads 
     * C = number of CPUs available on the system
     * I = number of iterations
     */        
    private double computeResultValue(TestCase tc, int nOfThreads, int nOfCpus) {
        String resultUnit = _testSuite.getParam(RESULT_UNIT);
        
        if (Japex.verbose) {
            outputWriter.println("             " + 
                Thread.currentThread().getName() + 
                    " japex.runIterationsSum = " +
                        tc.getLongParam(RUN_ITERATIONS_SUM)); 
            outputWriter.println("             " + 
                Thread.currentThread().getName() + 
                    " japex.runTimeSum (ms) = " +
                        tc.getDoubleParam(RUN_TIME_SUM));
        }

        // Get actual run time
        double actualTime = tc.getDoubleParam(ACTUAL_RUN_TIME);

        // Tx = sum(I_k) / T for k in 1..N
        double tps = tc.getLongParam(RUN_ITERATIONS_SUM) /
              (actualTime / 1000.0);
        
        // Compute latency as L = (min(C, N) / Tx) * 1000
        double l = Math.min(nOfCpus, nOfThreads) / tps * 1000.0;
        
        if (resultUnit == null || resultUnit.equalsIgnoreCase("tps")) {
            return tps;
        }
        else if (resultUnit.equalsIgnoreCase("ms")) {
            return l;
        }
        // Mbps = size-in-mbits * Tx 
        else if (resultUnit.equalsIgnoreCase("mbps")) {
            // Check if japex.inputFile was defined
            String inputFile = tc.getParam(INPUT_FILE);            
            if (inputFile == null) {
                throw new JapexException("Unable to compute japex.resultValue " + 
                    " because japex.inputFile is not defined or refers to an illegal path.");
            }            
            return new File(inputFile).length() * 0.000008d * tps;
        }     
        else if (resultUnit.equalsIgnoreCase("%GCTIME")) {
            // Calculate % of GC relative to the run time
            return (_gCTime / actualTime) * 100.0;
        }
		else if (resultUnit.equalsIgnoreCase(RESULT_UNIT_HEAP)) {
            return tc.getDoubleParam(PEAK_HEAP_USAGE);
        }
        else {
            throw new JapexException("Unknown value '" + 
                resultUnit + "' for global param japex.resultUnit.");
        }
    }
    
    /**
     * Calculates the time of the warmup and run phases. Returns an array 
     * of size 3 with hours, minutes and seconds. Note: if japex.runsPerDriver
     * is redefined by any driver, this estimate will be off.
     */
    private int[] estimateRunningTime(TestSuiteImpl testSuite) {        
        int nOfDrivers = testSuite.getDriverInfoList().size();
        int nOfTests = ((DriverImpl) testSuite.getDriverInfoList().get(0)).getTestCases(0).size();
    
        String runTime = testSuite.getParam(RUN_TIME);
        String warmupTime = testSuite.getParam(WARMUP_TIME);
        int actualRuns = testSuite.getIntParam(RUNS_PER_DRIVER) +
            testSuite.getIntParam(WARMUPS_PER_DRIVER);
        
        long seconds = (long)
            (nOfDrivers * nOfTests * (Util.parseDuration(warmupTime) / 1000.0) +
            nOfDrivers * nOfTests * (Util.parseDuration(runTime) / 1000.0)) *
            actualRuns;     
        
        int[] hms = new int[3];
        hms[0] = (int) (seconds / 60 / 60);
        hms[1] = (int) ((seconds / 60) % 60);
        hms[2] = (int) (seconds % 60);
        return hms;
    }
    
    public Map<String, ClassLoader> getNamedClassPaths() {
     	return _namedClassPaths;
    }
}
