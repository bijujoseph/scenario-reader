package com.eviware.soapui;

import org.apache.log4j.Logger
import com.eviware.soapui.impl.wsdl.teststeps.*
import com.eviware.soapui.support.types.StringToStringMap
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase
import java.io.File
import java.util.HashMap

class TestCaseAuthorization {
  private Map ENV
  private Map TOKEN_MAP

  private Map getEnvironmentValues(String projectPath) {
    if(ENV == null) {
      ENV = [:]
      new File(projectPath + "/.env").eachLine { line ->
        ENV.put(line.split("=")[0], line.split("=")[1])
      }
    }
    return ENV
  }

  private Map getTokenMap(Logger log) {
    if(TOKEN_MAP == null) {
      TOKEN_MAP = [:]
      def lineNo = 0
      def line
      new File(ENV.get("TOKEN_FILE_PATH")).withReader { reader ->
        while ((line = reader.readLine()) != null) {
          if(lineNo > 0) {
            String[] values = line.split(",")
            TOKEN_MAP.put(values[0].replaceAll("\"", "") + values[1].replaceAll("\"", ""), values[4])
          }
          lineNo++
        }
      }
    }
    return TOKEN_MAP
  }

	public TestCaseAuthorization(Logger log, WsdlTestCase testCase, String projectPath) {
	  def ENV = getEnvironmentValues(projectPath)
	  def TOKEN_MAP = getTokenMap(log)
		for(testStep in testCase.getTestStepList()) {
			if(testStep instanceof RestTestRequestStep) {
			  def headers = new StringToStringMap()
				testStep.getHttpRequest().setEndpoint(ENV.get("ENDPOINT"))
				if(testStep.getHttpRequest().getEndpoint() ==~ /(?s).*dev.*/) {
				  String key = "DEV" + ENV.get("USER_TYPE").toString()
				  String devTokens = TOKEN_MAP.get(key).toString();
          headers.put("Authorization","Bearer " + devTokens.replaceAll("\"", ""))
				} else if(testStep.getHttpRequest().getEndpoint() ==~ /(?s).*imp.*/) {
          String key = "IMP" + ENV.get("USER_TYPE").toString()
          String impTokens = TOKEN_MAP.get(key).toString();
          headers.put("Authorization","Bearer " + impTokens.replaceAll("\"", ""))
        }
				testStep.getHttpRequest().setRequestHeaders(headers)
			}
		}
	}
}
