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

import java.io.IOException;
import java.util.Properties;

public class Constants {

    public static final String RESULT_UNIT_HEAP = "heap";

    // Global input parameters
    public static final String REPORTS_DIRECTORY = "japex.reportsDirectory";
    public static final String CHART_TYPE        = "japex.chartType";       // barchart | scatterchart | linechart
    public static final String RESULT_UNIT       = "japex.resultUnit";
    public static final String RESULT_UNIT_X     = "japex.resultUnitX";
    public static final String RESULT_AXIS       = "japex.resultAxis";      // normal | logarithmic
    public static final String RESULT_AXIS_X     = "japex.resultAxisX";     // normal | logarithmic
    public static final String PLOT_DRIVERS      = "japex.plotDrivers";
    public static final String PLOT_GROUP_SIZE   = "japex.plotGroupSize";   // max group size
    public static final String SINGLE_CLASS_LOADER = "japex.singleClassLoader";

    public static final String REPORT_PEAK_HEAP_USAGE = "japex.reportPeakHeapUsage";
    public static final String PEAK_HEAP_USAGE_RESET_CONTEXT = "japex.peakHeapUsageResetContext";
    public static final String PEAK_HEAP_USAGE_PER_DRIVER = "driver";
    public static final String PEAK_HEAP_USAGE_PER_TESTCASE = "testCase";
    public static final String PEAK_HEAP_USAGE = "japex.peakHeapUsage";


    /**
     * If set to true, japex simply uses the thread context class loader for all drivers.
     * This is intended for environments where Japex is embedded.
     */
    public static final String CONTEXT_CLASS_LOADER = "japex.contextClassLoader";
    
    // Global output parameters
    public static final String VERSION         = "japex.version";
    public static final String VERSION_VALUE;
    static {
        Properties props = new Properties();
        try {
            props.load(Constants.class.getResourceAsStream("version.properties"));
            VERSION_VALUE = props.getProperty("japex.version");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
    }
    public static final String OS_NAME         = "japex.osName";
    public static final String OS_ARCHITECTURE = "japex.osArchitecture";
    public static final String DATE_TIME       = "japex.dateTime";
    public static final String VM_INFO         = "japex.vmInfo";
    public static final String CONFIG_FILE     = "japex.configFile";
    public static final String NUMBER_OF_CPUS  = "japex.numberOfCpus";
    public static final String HOST_NAME       = "japex.hostName";
    
    // Driver input parameters        
    public static final String DRIVER_CLASS       = "japex.driverClass";
    public static final String CLASS_PATH         = "japex.classPath";
    /**
     * Specify the class path for a driver by a name for a class loader supplied via API to the engine.
     */
    public static final String NAMED_CLASS_PATH = "japex.namedClassPath";
    public static final String NUMBER_OF_THREADS  = "japex.numberOfThreads";
    public static final String RUNS_PER_DRIVER    = "japex.runsPerDriver";
    public static final String WARMUPS_PER_DRIVER = "japex.warmupsPerDriver";
    
    // Driver output parameters
    public static final String RESULT_ARIT_MEAN = "japex.resultAritMean";
    public static final String RESULT_GEOM_MEAN = "japex.resultGeomMean";
    public static final String RESULT_HARM_MEAN = "japex.resultHarmMean";
    public static final String RESULT_ARIT_MEAN_STDDEV = "japex.resultAritMeanStddev";
    public static final String RESULT_GEOM_MEAN_STDDEV = "japex.resultGeomMeanStddev";
    public static final String RESULT_HARM_MEAN_STDDEV = "japex.resultHarmMeanStddev";
    
    public static final String RESULT_ARIT_MEAN_X = "japex.resultAritMeanX";
    public static final String RESULT_GEOM_MEAN_X = "japex.resultGeomMeanX";
    public static final String RESULT_HARM_MEAN_X = "japex.resultHarmMeanX";
    public static final String RESULT_ARIT_MEAN_X_STDDEV = "japex.resultAritMeanXStddev";
    public static final String RESULT_GEOM_MEAN_X_STDDEV = "japex.resultGeomMeanXStddev";
    public static final String RESULT_HARM_MEAN_X_STDDEV = "japex.resultHarmMeanXStddev";
    
    // Testcase input parameters
    public static final String RUN_ITERATIONS    = "japex.runIterations";
    public static final String WARMUP_ITERATIONS = "japex.warmupIterations";
    public static final String WARMUP_TIME       = "japex.warmupTime";
    public static final String RUN_TIME          = "japex.runTime";
    public static final String INPUT_FILE        = "japex.inputFile";
    public static final String RUN_ITERATION_DELAY = "japex.runIterationDelay";
    
    // Testcase output parameters
    public static final String ACTUAL_RUN_ITERATIONS = "japex.actualRunIterations";
    public static final String ACTUAL_WARMUP_ITERATIONS = "japex.actualWarmupIterations";    
    public static final String ACTUAL_RUN_TIME       = "japex.actualRunTime";
    public static final String ACTUAL_WARMUP_TIME    = "japex.actualWarmupTime";
    public static final String ACTUAL_PREPARE_TIME   = "japex.actualPrepareTime";        
    public static final String RESULT_VALUE          = "japex.resultValue";
    public static final String RESULT_ITERATIONS     = "japex.resultIterations";
    public static final String RESULT_TIME           = "japex.resultTime";    
    public static final String RESULT_VALUE_STDDEV   = "japex.resultValueStddev";
    public static final String RESULT_VALUE_X        = "japex.resultValueX";
    public static final String RESULT_VALUE_X_STDDEV = "japex.resultValueXStddev";
    
    public static final String WARMUP_ITERATIONS_SUM = "japex.warmupIterationsSum";
    public static final String WARMUP_TIME_SUM       = "japex.warmupTimeSum";
    public static final String RUN_ITERATIONS_SUM    = "japex.runIterationsSum";
    public static final String RUN_TIME_SUM          = "japex.runTimeSum";
    
    // Default values
    public static final String DEFAULT_WARMUP_ITERATIONS  = "300";
    public static final String DEFAULT_RUN_ITERATIONS     = "300";
    public static final String DEFAULT_RUN_ITERATION_DELAY = "0";
    public static final String DEFAULT_NUMBER_OF_THREADS  = "1";
    public static final String DEFAULT_RUNS_PER_DRIVER    = "1";
    public static final String DEFAULT_REPORTS_DIRECTORY  = "reports";
    public static final String DEFAULT_CHART_TYPE         = "barchart";
    public static final String DEFAULT_RESULT_AXIS        = "normal";
    public static final String DEFAULT_PLOT_DRIVERS       = "false";
    public static final String DEFAULT_PLOT_GROUP_SIZE    = "5";   
    public static final String DEFAULT_SINGLE_CLASS_LOADER = "false";        
    public static final String DEFAULT_DATE_TIME_FORMAT   = "dd MMM yyyy/HH:mm:ss z";
}
