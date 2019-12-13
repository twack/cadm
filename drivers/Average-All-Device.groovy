/**
 *  Average Virtual Illuminance/Temperature/Humidity/Pressure/Motion/Power Device
 *
 *  Copyright 2019 Andrew Parker
 *
 *  
 *  

 *-------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *-------------------------------------------------------------------------------------------------------------------
 *
 *  If modifying this project, please keep the above header intact and add your comments/credits below - Thank you! -  @Cobra
 *
*-------------------------------------------------------------------------------------------------------------------
 *
 *  Last Update 12/09/2019
 *
 *
 *  V2.0.0 - Set defaults for 'F', 'mbar' & Date format
 *  V1.9.0 - Revised 'Data' display for versioning etc - Revised Update Checking
 *  V1.8.0 - Added Temperature average peak/min 
 *  V1.7.0 - added Humidity average peak/min
 *  V1.6.0 - Added 'Power' average, peak & min
 *  V1.5.0 - Added 'LastEventTime' & 'LastEventDate' and date format option to show when a device reports
 *  V1.4.1 - Debug issue with driver not working correctly after reboot
 *  V1.4.0 - New update json
 *  V1.3.3 - Debug - Typo in lastDeviceHumidity
 *  V1.3.2 - Debug UI
 *  V1.3.1 - Debug 'LastDevice'
 *  V1.3.0 - Added switchable logging
 *  V1.2.0 - Added 'Motion' average
 *  V1.1.0 - Debug and added 'last device' separation - one for each attribute
 *  V1.0.0 - POC
 */



metadata {
	definition (name: "Average All Device", 
                namespace: "smartport", 
                author: '{"authorName": "Cobra", "smartportVersion": "1.9.0"}',) {
                //author: "Cobra") {
		capability "Illuminance Measurement"
		capability "Relative Humidity Measurement"
        capability "Temperature Measurement"
        capability "Motion Sensor"
		capability "Sensor"
		capability "Energy Meter"
		capability "Power Meter"
        
        command "setTemperature", ["decimal"]
        command "setHumidity", ["decimal"]
        command "setLux", ["decimal"]
        command "setPressure", ["decimal"]
		command "setPower", ["decimal"]
        
        command "lastDeviceLux"
        command "lastDeviceTemperature"
        command "lastDeviceHumidity"
        command "lastDevicePressure"
		command "lastDevicePower", ["string"]
        command "lastDeviceMotion"
        command "setMotion", ["string"]

        attribute "LastDeviceLux", "string"
        attribute "LastDeviceTemperature", "string"
        attribute "LastDevicePressure", "string"
		attribute "LastDevicePower", "string"
        attribute "LastDeviceHumidity", "string"
        attribute "LastDeviceMotion", "string"
		attribute "LastEventDate", "string"
		attribute "LastEventTime", "string"
		attribute "PeakPowerToday", "string"
		attribute "MinPowerToday", "string"
		attribute "PeakPowerEvent", "string"
		attribute "MinPowerEvent", "string"
        attribute "PeakHumidityToday", "string"
		attribute "MinHumidityToday", "string"
		attribute "PeakHumidityEvent", "string"
		attribute "MinHumidityEvent", "string"
		attribute "PeakTemperatureToday", "string"
		attribute "MinTemperatureToday", "string"
		attribute "PeakTemperatureEvent", "string"
		attribute "MinTemperatureEvent", "string"
        attribute "pressure", "string"
	}
    
    
  preferences() {
    
     section("") {
   		input "unitSelect", "enum",  title: "Temperature Units (If using temperature)", required: true, options: ["C", "F"] , defaultValue: "F"
   		input "pressureUnit", "enum", title: "Pressure Unit (If using pressure)", required:true, options: ["inhg", "mbar"], defaultValue:"mbar"
		input "powerMode1", "bool", title: "Power - Convert Watts to KW ", required: true, defaultValue: false
		input "powerMode2", "bool", title: "Power - Auto Convert Watts to KW (if above 1000 watts)", required: true, defaultValue: false
        input "showMinMax", "bool", title: "Calculate & show Min/Max for today?", required: true, defaultValue: false 
		input "dateFormatNow", "enum", title: "Last Event Date Format", required:true, defaultValue: "MMM dd yyyy", options: ["dd MMM yyyy", "MMM dd yyyy"]
		input "debugMode", "bool", title: "Enable debug logging", required: true, defaultValue: false 
 		}  
 }   
}

def installed(){
    initialize()
}

def updated(){
    initialize()
	minMaxReset()
	
}



def initialize() {
	unschedule()
    logCheck()
    version()
    if(state.TemperatureUnit == null){ state.TemperatureUnit = "F"}
    else{state.TemperatureUnit = unitSelect}
    if(pressureUnit == null){state.PressureUnit = "mbar"}
    else{state.PressureUnit = pressureUnit}
	if(showMinMax){schedule("0 1 0 1/1 * ? *", minMaxReset)}
    
}

def minMaxReset(){
	state.minPower = 0
	state.maxPower = 0	
	state.minHumidity = 0
	state.maxHumidity = 0	
	state.minTemperature = 0
	state.maxTemperature = 0	

}



def lastDeviceLux(dev1){  
    state.LastDeviceLux = dev1
	sendLastEvent()
	sendEvent(name: "LastDeviceLux", value: state.LastDeviceLux, isStateChange: true)
	
}
def lastDeviceHumidity(dev2){  
	state.LastDeviceHumid = dev2
	sendLastEvent()
    sendEvent(name: "LastDeviceHumidity", value: state.LastDeviceHumid, isStateChange: true)
}
def lastDevicePressure(dev3){ 
	state.LastDevicePressure = dev3
	sendLastEvent()
    sendEvent(name: "LastDevicePressure", value: state.LastDevicePressure, isStateChange: true)
}
def lastDeviceTemperature(dev4){ 
	state.LastDeviceTemperature = dev4
	sendLastEvent()
    sendEvent(name: "LastDeviceTemperature", value: state.LastDeviceTemperature, isStateChange: true)
}

def lastDeviceMotion(dev5){ 
	state.LastDeviceMotion = dev5
	sendLastEvent()
    sendEvent(name: "LastDeviceMotion", value: state.LastDeviceMotion, isStateChange: true)
}

def lastDevicePower(dev6){ 
	state.LastDevicePower = dev6
	sendLastEvent()
    sendEvent(name: "LastDevicePower", value: state.LastDevicePower, isStateChange: true)

}


def sendLastEvent(){
	if(dateFormatNow == null){log.warn "Date format not set"}
	if(dateFormatNow == "dd MMM yyyy"){
	def date = new Date()
	state.LastTime = date.format('HH:mm', location.timeZone)
	state.LastDate = date.format('dd MMM yyyy', location.timeZone)	
	}
	
	if(dateFormatNow == "MMM dd yyyy"){
	def date = new Date()
	state.LastTime = date.format('HH:mm', location.timeZone)
	state.LastDate = date.format('MMM dd yyyy', location.timeZone)	
	}	
	
	sendEvent(name: "LastEventTime", value: state.LastTime, isStateChange: true)
	sendEvent(name: "LastEventDate", value: state.LastDate, isStateChange: true)

}

def active(motion1) {
//	state.ReceivedMotion = motion1
    LOGDEBUG( "Setting motion for ${device.displayName} from external input ($state.LastDeviceMotion), Motion = ${motion1}.")
	sendEvent(name: "motion", value: 'active', isStateChange: true)
}

def inactive(motion1) {
//	state.ReceivedMotion = motion1
    LOGDEBUG( "Setting motion for ${device.displayName} from external input ($state.LastDeviceMotion), Motion = ${motion1}.")
	sendEvent(name: "motion", value: 'inactive', isStateChange: true)
}

def setMotion(motion1){
 state.ReceivedMotion = motion1
    LOGDEBUG( "Setting motion for ${device.displayName} from external input ($state.LastDeviceMotion), Motion = ${state.ReceivedMotion}.")
   sendEvent(name: "motion", value: state.ReceivedMotion, isStateChange: true) 
}

def setHumidity(hum1) {
	state.maxHEvent = false
	state.minHEvent = false
	state.ReceivedHumidity1 = hum1.toDouble()
	state.ReceivedHumidity = state.ReceivedHumidity1.toInteger()
	if(state.minHumidity == 0){state.minHumidity = state.ReceivedHumidity}
	
    LOGDEBUG( "Setting humidity for ${device.displayName} from external input ($state.LastDeviceHumid), Humidity = ${state.ReceivedHumidity}.")
	sendEvent(name: "humidity", value: state.ReceivedHumidity, unit: "%", isStateChange: true)
	if(showMinMax){
		if(state.ReceivedHumidity > state.maxHumidity){
		state.maxHEvent = true
			log.warn "Max Humidity Event"
		state.maxHumidity = state.ReceivedHumidity}
	
		if(state.ReceivedHumidity < state.minHumidity){
		state.minHEvent = true
		log.warn "Min Humidity Event"
		state.minHumidity = state.ReceivedHumidity}
	
	
	log.warn "Min = $state.minHumidity - received = $state.ReceivedHumidity"
	sendEvent(name: "MinHumidityToday", value: state.minHumidity, isStateChange: true)
	sendEvent(name: "PeakHumidityToday", value: state.maxHumidity, isStateChange: true)
	if(state.minHEvent == true){sendEvent(name: "MinHumidityEvent", value: state.LastTime, isStateChange: true)}
	if(state.maxHEvent == true){sendEvent(name: "PeakHumidityEvent", value: state.LastTime, isStateChange: true)}
	}
	
}


def setLux(ilum1) {

    state.ReceivedIlluminance = ilum1
    LOGDEBUG("Setting illuminance for ${device.displayName} from external input ($state.LastDeviceLux), Illuminance = ${state.ReceivedIlluminance}.")
	sendEvent(name: "illuminance", value: state.ReceivedIlluminance, unit: "lux", isStateChange: true)
	sendEvent(name: "LastDeviceLux", value: state.LastDeviceLux, isStateChange: true)
	sendEvent(name: "LastEventTime", value: state.LastTime, isStateChange: true)
	sendEvent(name: "LastEventDate", value: state.LastDate, isStateChange: true)
}


def setTemperature(temp1){ 
	
	state.maxTEvent = false
	state.minTEvent = false
	state.ReceivedTemperature1 = temp1.toDouble()
	state.ReceivedTemperature = state.ReceivedTemperature1.toInteger()
	if(state.minTemperature == 0){state.minTemperature = state.ReceivedTemperature}
	LOGDEBUG( "Setting temperature for ${device.displayName} from external input ($state.LastDeviceTemperature), Temperature = ${state.ReceivedTemperature}.")
    sendEvent(name:"temperature", value: state.ReceivedTemperature , unit: state.TemperatureUnit, isStateChange: true)
   if(showMinMax){
		if(state.ReceivedTemperature >state.maxTemperature){
		state.maxTEvent = true
		state.maxTemperature = state.ReceivedTemperature}
	if(state.ReceivedTemperature <state.minTemperature){
		state.minTEvent = true
		state.minTemperature = state.ReceivedTemperature}
	sendEvent(name: "MinTemperatureToday", value: state.minTemperature, isStateChange: true)
	sendEvent(name: "PeakTemperatureToday", value: state.maxTemperature, isStateChange: true)
	if(state.minTEvent == true){sendEvent(name: "MinTemperatureEvent", value: state.LastTime, isStateChange: true)}
	if(state.maxTEvent == true){sendEvent(name: "PeakTemperatureEvent", value: state.LastTime, isStateChange: true)}
   }
}

def setPressure(pres1){ 
 state.ReceivedPressure = pres1
	LOGDEBUG("Setting pressure for ${device.displayName} from external input ($state.LastDevicePressure), Pressure = ${state.ReceivedPressure}.")
    sendEvent(name:"pressure", value: state.ReceivedPressure , unit: state.PressureUnit, isStateChange: true)
    
}

def setPower(pow1) {
	
	state.maxPEvent = false
	state.minPEvent = false
    state.ReceivedPower1 = pow1.toDouble()
	state.ReceivedPower = state.ReceivedPower1.toInteger()
	if(state.minPower == 0){state.minPower = state.ReceivedPower}
//	log.warn "power in = $pow1" // debug code
    LOGDEBUG("Setting power for ${device.displayName} from external input ($state.LastDevicePower), Power = ${state.ReceivedPower}.")
	if(powerMode1 == true){
		state.ReceivedPower = state.ReceivedPower/1000
		sendEvent(name: "power", value: state.ReceivedPower, unit: "kw", isStateChange: true)
	}
	if(powerMode2 == true && state.ReceivedPower > 1000){
	state.ReceivedPower = state.ReceivedPower/1000
	sendEvent(name: "power", value: state.ReceivedPower, unit: "kw", isStateChange: true)
	}
	else{
	sendEvent(name: "power", value: state.ReceivedPower, unit: "watts", isStateChange: true)
	}
	
	if(showMinMax){
	if(state.ReceivedPower >state.maxPower){
		state.maxPEvent = true
		state.maxPower = state.ReceivedPower}
	if(state.ReceivedPower <state.minPower){
		state.minPEvent = true
		state.minPower = state.ReceivedPower}
	
	
	sendEvent(name: "MinPowerToday", value: state.minPower, isStateChange: true)
	sendEvent(name: "PeakPowerToday", value: state.maxPower, isStateChange: true)
	if(state.minPEvent == true){sendEvent(name: "MinPowerEvent", value: state.LastTime, isStateChange: true)}
	if(state.maxPEvent == true){sendEvent(name: "PeakPowerEvent", value: state.LastTime, isStateChange: true)}
	}
	
}



def logCheck(){
state.checkLog = debugMode
if(state.checkLog == true){
log.info "All Logging Enabled"
}
else if(state.checkLog == false){
log.info "Debug Logging Disabled"
}
	if(debugMode){runIn(1800, logsOff)}
}
def logsOff() {
    log.warn "Debug logging disabled..."
	device.updateSetting("debugMode", [value: "false", type: "bool"])
}
def LOGDEBUG(txt){
    try {
		if (settings.debugMode) { log.debug("${device.name} - Version: ${state.version}) - ${txt}") }
    } catch(ex) {
    	log.error("LOGDEBUG unable to output requested data!")
    }
}


def version(){
    updateCheck()
    def random = new Random()
    Integer randomHour = random.nextInt(18-10) + 10
    Integer randomDayOfWeek = random.nextInt(7-1) + 1 // 1 to 7
    schedule("0 0 " + randomHour + " ? * " + randomDayOfWeek, updateCheck) 
}


def updateCheck(){
    setVersion()
	def paramsUD = [uri: "http://update.hubitat.uk/json/${state.CobraAppCheck}"] 
       	try {
        httpGet(paramsUD) { respUD ->
//  log.warn " Version Checking - Response Data: ${respUD.data}"   // Troubleshooting Debug Code **********************
       		def copyrightRead = (respUD.data.copyright)
       		state.Copyright = copyrightRead
            def newVerRaw = (respUD.data.versions.Driver.(state.InternalName))
	//		log.warn "$state.InternalName = $newVerRaw"
  			def newVer = newVerRaw.replace(".", "")
//			log.warn "$state.InternalName = $newVer"
			state.newUpdateDate = (respUD.data.Comment)
       		def currentVer = state.version.replace(".", "")
      		state.UpdateInfo = "Updated: "+state.newUpdateDate + " - "+(respUD.data.versions.UpdateInfo.Driver.(state.InternalName))
            state.author = (respUD.data.author)
			state.icon = (respUD.data.icon)
           
		if(newVer == "NLS"){
            state.Status = "<b>** This driver is no longer supported by $state.author  **</b>"       
            log.warn "** This driver is no longer supported by $state.author **"      
      		}           
		else if(currentVer < newVer){
        	state.Status = "<b>New Version Available (Version: $newVerRaw)</b>"
        	log.warn "** There is a newer version of this driver available  (Version: $newVerRaw) **"
        	log.warn "** $state.UpdateInfo **"
       		}
		else if(currentVer > newVer){
        	state.Status = "You are using a BETA ($state.version) - Release Version: $newVerRaw"
        	log.warn "** <b>$state.Status</b>) **"
        	state.UpdateInfo = "N/A"
       		} 	
		else{ 
      		state.Status = "Current"
      		log.info "You are using the current version of this driver"
			state.UpdateInfo = "N/A"
       		}
      					}
        	} 
        catch (e) {
        	log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI -  $e"
    		}
	updateData()
}

def setVersion(){
    state.version = "1.9.0"
    state.InternalName = "AverageAllDriver"
   	state.CobraAppCheck = "averagealldriver.json"	
}

def updateData(){
	updateDataValue("Author", "Andrew Parker")
	updateDataValue("Status", state.Status)
	updateDataValue("Update", state.UpdateInfo)
	updateDataValue("Version", state.version)
	LOGDEBUG("Driver Version: ${getDataValue("Version")}")
}
