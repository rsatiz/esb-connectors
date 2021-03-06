/**
 *  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.connector.integration.test.linkedin;

import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Properties;

import javax.activation.DataHandler;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axis2.context.ConfigurationContext;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.api.clients.proxy.admin.ProxyServiceAdminClient;
import org.wso2.carbon.automation.api.clients.utils.AuthenticateStub;
import org.wso2.carbon.automation.core.ProductConstant;
import org.wso2.carbon.automation.utils.axis2client.AxisServiceClient;
import org.wso2.carbon.automation.utils.axis2client.ConfigurationContextProvider;
import org.wso2.carbon.connector.integration.test.common.ConnectorIntegrationUtil;
import org.wso2.carbon.esb.ESBIntegrationTest;
import org.wso2.carbon.mediation.library.stub.MediationLibraryAdminServiceStub;
import org.wso2.carbon.mediation.library.stub.upload.MediationLibraryUploaderStub;

public class LinkedInConnectorIntegrationTest extends ESBIntegrationTest {
	private static final String CONNECTOR_NAME = "linkedin";

	private MediationLibraryUploaderStub mediationLibUploadStub = null;

	private MediationLibraryAdminServiceStub adminServiceStub = null;

	private ProxyServiceAdminClient proxyAdmin;

	private String repoLocation = null;

	private String linkedinConnectorFileName = "linkedin.zip";

	private Properties linkedinConnectorProperties = null;

	@BeforeClass(alwaysRun = true)
	public void setEnvironment() throws Exception {
		super.init();

		ConfigurationContextProvider configurationContextProvider = ConfigurationContextProvider
				.getInstance();
		ConfigurationContext cc = configurationContextProvider
				.getConfigurationContext();
		mediationLibUploadStub = new MediationLibraryUploaderStub(cc,
				esbServer.getBackEndUrl() + "MediationLibraryUploader");
		AuthenticateStub.authenticateStub("admin", "admin",
				mediationLibUploadStub);

		adminServiceStub = new MediationLibraryAdminServiceStub(cc,
				esbServer.getBackEndUrl() + "MediationLibraryAdminService");

		AuthenticateStub.authenticateStub("admin", "admin", adminServiceStub);

		if (System.getProperty("os.name").toLowerCase().contains("windows")) {
			repoLocation = System.getProperty("connector_repo").replace("/",
					"\\");
		} else {
			repoLocation = System.getProperty("connector_repo").replace("/",
					"/");
		}

		proxyAdmin = new ProxyServiceAdminClient(esbServer.getBackEndUrl(),
				esbServer.getSessionCookie());

		ConnectorIntegrationUtil.uploadConnector(repoLocation,
				mediationLibUploadStub, linkedinConnectorFileName);
		log.info("Sleeping for "+60000/1000+" seconds while waiting for synapse import");
		Thread.sleep(60000);
		//adminServiceStub.addImport(CONNECTOR_NAME, "org.wso2.carbon.connectors");
		adminServiceStub.updateStatus("{org.wso2.carbon.connector}"
				+ CONNECTOR_NAME, CONNECTOR_NAME, "org.wso2.carbon.connector",
				"enabled");
		linkedinConnectorProperties = ConnectorIntegrationUtil
				.getConnectorConfigProperties(CONNECTOR_NAME);

	}

	@Override
	protected void cleanup() {
		axis2Client.destroy();
	}

	@Test(groups = { "wso2.esb" }, description = "LinkedIn integration tests.")
	public void testLinkedIn() throws Exception {

		String[] methodsToTest = linkedinConnectorProperties
				.get("methodsToTest").toString().split(",");
		AxisServiceClient axisServiceClient = new AxisServiceClient();
		
		for (int i = 0; i < methodsToTest.length; i++) {
			String methodName = methodsToTest[i];
			log.info("Running " + methodName + " integration test.");

			proxyAdmin.addProxyService(new DataHandler(new URL("file:"
					+ File.separator + File.separator
					+ ProductConstant.SYSTEM_TEST_RESOURCE_LOCATION
					+ ConnectorIntegrationUtil.ESB_CONFIG_LOCATION
					+ File.separator + "proxies" + File.separator
					+ CONNECTOR_NAME + File.separator + CONNECTOR_NAME + "_"
					+ methodName + ".xml")));
			log.info("Sending SOAP message from "+methodName+".xml");
			OMElement getRequest = AXIOMUtil
					.stringToOM(ConnectorIntegrationUtil
							.readSoapRequestFile(
									File.separator
											+ File.separator
											+ ProductConstant.SYSTEM_TEST_RESOURCE_LOCATION
											+ ConnectorIntegrationUtil.ESB_CONFIG_LOCATION
											+ File.separator + "soapRequests"
											+ File.separator + CONNECTOR_NAME
											+ File.separator + methodName
											+ ".xml", Charset.defaultCharset()));
			OMElement response = axisServiceClient.sendReceive(getRequest,
					getProxyServiceURL(CONNECTOR_NAME + "_" + methodName),
					"mediate");
			log.info(ConnectorIntegrationUtil.firstToUpperCase(CONNECTOR_NAME)
					+ methodName + " response:" + response.toString());
			Assert.assertTrue(response.toString().contains(
					(String) linkedinConnectorProperties.getProperty(methodName
							+ "ExpectedResult")));
			proxyAdmin.deleteProxy(CONNECTOR_NAME + "_" + methodName);
		}

	}
}
