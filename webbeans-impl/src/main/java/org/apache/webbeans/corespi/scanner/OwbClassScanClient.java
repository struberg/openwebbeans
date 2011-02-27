/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.webbeans.corespi.scanner;

import javax.enterprise.classscan.ClassScanClient;
import javax.enterprise.classscan.ClassScanner;
import javax.enterprise.classscan.ScanJob;

/**
 * @author <a href="mailto:struberg@yahoo.de">Mark Struberg</a>
 */
public class OwbClassScanClient implements ClassScanClient
{

    public final static String SCANNER_CLIENT_NAME = "org.apache.openwebbeans";

    @Override
    public void invokeRegistration(ClassScanner classScanner)
    {
        ScanJob scanJob = new ScanJob(new String[]{"META-INF/beans.xml"}, null, null, true, true, true, true );
        classScanner.registerClient(SCANNER_CLIENT_NAME, scanJob);
    }


}
