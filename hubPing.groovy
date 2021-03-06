/*
 * Hubitat Ping
 *
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 *    2021-03-13  thebearmay	 Original version 0.1.0
 *                               add responseReady, additional minor fixes v0.5.0
 *                               add PresenceSensor capability v0.6.0
 *    2021-03-14  thebearmay     Add repeat value, tighten to release v1.0.0
 *    2021-03-15  thebearmay     Add lastIpAddress, leave presence at last value when starting new ing
 *
 */

static String version()	{  return '1.1.0'  }

metadata {
    definition (
		name: "Hubitat Ping", 
		namespace: "thebearmay", 
		author: "Jean P. May, Jr.",
	        importUrl:"https://raw.githubusercontent.com/thebearmay/hubitat/main/hubPing.groovy"
	) {
        capability "Actuator"
        capability "Configuration"
        capability "PresenceSensor"
       
        attribute "pingReturn", "string"
        attribute "percentLoss", "number"
        attribute "max", "number"
        attribute "avg", "number"
        attribute "min", "number"
        attribute "mdev", "number"
        attribute "pingStats", "string"
	    attribute "responseReady", "bool"
        attribute "lastIpAddress", "string"
        
        
        command "sendPing", [[name:"ipAddress*", type:"STRING", description:"IP Address (IPv4) for the hub to ping"]]   
            
            
    }   
}

preferences {
    input("debugEnable", "bool", title: "Enable debug logging?")
    input("pingPeriod", "number", title: "Ping Repeat in Seconds\n Zero to disable", defaultValue: 0, required:true, submitOnChange: true)
    input("security", "bool", title: "Hub Security Enabled", defaultValue: false, submitOnChange: true)
    if (security) { 
        input("username", "string", title: "Hub Security Username", required: false)
        input("password", "password", title: "Hub Security Password", required: false)
    }
}

def installed() {
	log.trace "installed()"
}

def configure() {
    if(debugEnable) log.debug "configure()"
    updateAttr("percentLoss"," ")
    updateAttr("pingStats"," ") 
    updateAttr("min"," ")
    updateAttr("avg"," ")
    updateAttr("max"," ")
    updateAttr("mdev"," ")
    updateAttr("pingReturn"," ")
    updateAttr("responseReady",false)
    if (device.currentValue("presence") == null) updateAttr("presence","not present")

}

def updateAttr(aKey, aValue){
    sendEvent(name:aKey, value:aValue)
}

def updateAttr(aKey, aValue, aUnit){
    sendEvent(name:aKey, value:aValue, unit:aUnit)
}

def initialize(){

}


def sendPing(ipAddress){
    if(ipAddress == null) ipAddress = data.ip
    configure()
    updateAttr("lastIpAddress", ipAddress)
    
    // start - Modified from dman2306 Rebooter app

    if(security) {
        httpPost(
            [
                uri: "http://127.0.0.1:8080",
                path: "/login",
                query: [ loginRedirect: "/" ],
                body: [
                    username: username,
                    password: password,
                    submit: "Login"
                ]
            ]
        ) { resp -> cookie = resp?.headers?.'Set-Cookie'?.split(';')?.getAt(0) }
    }
    // End - Modified from dman2306 Rebooter app
    
    params = [
        uri: "http://${location.hub.localIP}:8080",
        path:"/hub/networkTest/ping/"+ipAddress,
        headers: [ "Cookie": cookie ]
    ]
    if(debugEnable)log.debug params
    updateAttr("responseReady",false)
    updateAttr("pingReturn","Pinging $ipAddress")  
	
    asynchttpGet("sendPingHandler", params)
    
}

def sendPingHandler(resp, data) {
    def errFlag = 0
    try {
	    if(resp.getStatus() == 200 || resp.getStatus() == 207) {
		    strWork = resp.data.toString()
    		if(debugEnable) log.debug strWork
	        sendEvent(name:"pingReturn",value:strWork)
  	    }
    } catch(Exception ex) { 
        errFlag = 1
        respStatus = resp.getStatus()
        sendEvent(name:"pingReturn", value: "httpResp = $respStatus but returned invalid data")
        updateAttr("presence","not present")
        log.warn "sendPing httpResp = $respStatus but returned invalid data"
        
    } 
    if (!errFlag) extractValues(strWork)

    if(pingPeriod > 0) runIn(pingPeriod, "sendPing", [data:ipAddress])

}

def extractValues(strWork) {
    startInx = strWork.indexOf("%")
    if(debubEnable)log.debug startInx
    if (startInx == -1){
        updateAttr("percentLoss",100,"%")
        updateAttr("pingStats"," ") 
        updateAttr("min"," ")
        updateAttr("avg"," ")
        updateAttr("max"," ")
        updateAttr("mdev"," ")
        percentLoss = 100
    } else {
        startInx -=3
        strWork=strWork.substring(startInx)
        if(strWork.substring(0,1)==","){
            percentLoss = strWork.substring(1,3).toInteger()
        } else
            percentLoss = strWork.substring(0,3).toInteger()
        updateAttr("percentLoss",percentLoss,"%")
        
        startInx = strWork.indexOf("=")
        pingStats= strWork.substring(startInx+2,strWork.length()-4).tokenize("/")
        updateAttr("pingStats",pingStats) 
        updateAttr("min",pingStats[0]," ms")
        updateAttr("avg",pingStats[1]," ms")
        updateAttr("max",pingStats[2]," ms")
        updateAttr("mdev",pingStats[3], " ms")
    }
    if (percentLoss < 100 ) updateAttr("presence","present")
    else updateAttr("presence","not present")
    updateAttr("responseReady", true)
}

def updated(){
	log.trace "updated()"
	if(debugEnable) runIn(1800,logsOff)
}

void logsOff(){
     device.updateSetting("debugEnable",[value:"false",type:"bool"])
}
