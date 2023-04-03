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

import java.net.*;
import java.io.File;
import java.io.FilenameFilter;
import java.util.StringTokenizer;

/**
 * A specialized class loader for Japex. The idea here is that we want drivers to operate in an isolated
 * class loader, but we need the japex overhead classes to be shared between the outer hosting environment
 * and the driver. This class loader special cases the packages for japex and jdsl for that purpose.
 * The jsdl delegation is probably not needed, since we would expect any driver using JDSL to include
 * the JDSL jar in its specific classpath.  
 */
class JapexClassLoader extends URLClassLoader {
    
	/**
	 * Create a JapexClassLoader using Japex conventions for parsing a class path from a string.
	 * This constructor forces delegation only from the bootstrap class loader, not the system
	 * class loader. The special delegation for the japex classes happens by code in here
	 * asking the class loader *of this class*.
	 * @param classPath
	 */
    public JapexClassLoader(String classPath) {
        super(new URL[0]);
        addClassPath(classPath);
    }
    
    /**
     * Create a japex class loader. This constructor forces delegation only from the bootstrap class loader, not the system
	 * class loader. The special delegation for the japex classes happens by code in here
	 * asking the class loader *of this class*.
     * @param classPath
     */
    public JapexClassLoader(URL[] classPath) {
    	super(classPath);
    }
    
    public Class<?> findClass(String name) throws ClassNotFoundException {
        // Delegate when loading Japex classes, excluding JDSL drivers
        if (name.startsWith("com.sun.japex.") && !name.startsWith("com.sun.japex.jdsl.")) {
            return DriverImpl.class.getClassLoader().loadClass(name);
        }

        // Otherwise, use class loader based on japex.classPath only
        return super.findClass(name);
    }
    
    public void addURL(URL url) {
        super.addURL(url);
    }
    
    public JapexDriverBase getJapexDriver(String className) 
        throws ClassNotFoundException 
    {        
        try {
            // Use 'this' class loader here
            Class<?> clazz = Class.forName(className, true, this);
            return (JapexDriverBase) clazz.newInstance();
        }
        catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        catch (ClassCastException e) {
            throw new RuntimeException("Class '" + className 
                + "' must extend '" + JapexDriverBase.class.getName() + "'");
        }
    }
   
    public void addClassPath(String classPath) {
        if (classPath == null) {
            return;
        }
        
        String pathSep = System.getProperty("path.separator");
        String fileSep = System.getProperty("file.separator");
        StringTokenizer tokenizer = new StringTokenizer(classPath, pathSep); 
        
        // TODO: Ensure that this code works on Windows too!
	while (tokenizer.hasMoreTokens()) {
            String path = tokenizer.nextToken();            
            try {
                boolean lookForJars = false;
                
                // Strip off '*.jar' at the end if present
                if (path.endsWith("*.jar")) {
                    int k = path.lastIndexOf('/');
                    path = (k >= 0) ? path.substring(0, k + 1) : "./";
                    lookForJars = true;
                }
                
                // Create a file from the resulting path
                File file = new File(path);
                
                // If a directory, add all '.jar'
                if (file.isDirectory() && lookForJars) {
                    String children[] = file.list(
                        new FilenameFilter() {
                            public boolean accept(File dir, String name) {
                                return name.endsWith(".jar");
                            }
                        });
                        
                    for (String c : children) {
                        addURL(new File(path + fileSep + c).toURI().toURL());
                    }
                }
                else {
                    addURL(file.toURI().toURL());
                }
            }
            catch (MalformedURLException e) {
                // ignore
            }
        }        
    }

}    
       