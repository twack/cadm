/**
 *=============================================================================
 *  Virtual Apartment Device Creator
 *
 *  App to setup devices according to apartment device schema
 *
 *  Copyright 2019 Smartport LLC
 *
 *  Initial Release:	20191213
 *  Last Update:		20191213
 *
 *  Change Log:
 *  Version		Date Release	Reason				Author				Ticket#
 *  ---------------------------------------------------------------------------
 *  V0.0.1		20191213		initial        		Todd Wackford		N/A
 *
 *
 *
 *=============================================================================
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

definition(
    name: "Apartment Virtual Device Engine",
    namespace: "smartport",
    author: '{"authorName": "Todd Wackford", "smartportVersion": "0.0.1"}',
    description: "Create Virtual Apartment Devices",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)

preferences {
  page(name: "setupPage")
}

def setupPage(){
    state.isDebug = isDebug
    
    def connectLink = [
                url:		"https://www.youtube.com/watch?v=CvpJkMrAxjI", //replace with real URL when ready
                style:		"embedded",
                required:	false,
                image:      "https://cdn3.iconfinder.com/data/icons/marketing-e-commerce/128/icons_-_marketing-56-128.png",
                title:		"Apartment Virtual Device Engine",
                description: "Create/Delete Apartment Virtual Devices"
    ]
    
    return dynamicPage(name: "setupPage", uninstall: true, install: true){
        section {
            href connectLink
        }
		section () {
            input "desired", "enum", options: ["Make","Delete"], required: false, defaultValue: "Make", title: "Make or Delete Virtual Devices..."
    	}        
		section () {
            input "spaceType", "enum", options: ["1A","1B",], required: false, defaultValue: "1A", title: "Select Apt Type...(Only 1A works for now)"
    	}    
        section("") {
       		input "isDebug", "bool", title: "Enable Debug Logging", required: false, multiple: false, defaultValue: false, submitOnChange: true
    	}
    }
}


def initialize() {
    if ( desired == "Make" ) {
        makeVirtualDevices()
    } else { 
        deleteVirtualDevices()
    }
}

def getUnitDeviceSchema() {
     def config = [
         "1A" : [
             1 : [ "namespace" : "hubitat", "driver": "Generic Z-Wave Smart Switch" ],
             2 : [ "namespace" : "hubitat", "driver": "Zooz Central Scene Dimmer" ],
             3 : [ "namespace" : "hubitat", "driver": "Zooz Central Scene Dimmer" ],
             4 : [ "namespace" : "hubitat", "driver": "Zooz Central Scene Dimmer" ],           
             5 : [ "namespace" : "hubitat", "driver": "Zooz Central Scene Dimmer" ],
             6 : [ "namespace" : "hubitat", "driver": "GE Smart Fan Control" ],
             7 : [ "namespace" : "hubitat", "driver": "Zooz Central Scene Dimmer" ],
             8 : [ "namespace" : "hubitat", "driver": "Zooz Central Scene Dimmer" ],             
             9 : [ "namespace" : "hubitat", "driver": "GE Smart Fan Control" ],
             10: [ "namespace" : "hubitat", "driver": "Zooz Central Scene Dimmer" ],
             11: [ "namespace" : "hubitat", "driver": "Generic Z-Wave Outlet" ],
             12: [ "namespace" : "hubitat", "driver": "Zooz Central Scene Switch" ],            
             13: [ "namespace" : "hubitat", "driver": "Zooz Central Scene Dimmer" ],
             14: [ "namespace" : "hubitat", "driver": "Zooz Central Scene Dimmer" ],
             15: [ "namespace" : "hubitat", "driver": "Zooz Central Scene Dimmer" ],
             16: [ "namespace" : "hubitat", "driver": "Generic Z-Wave Smoke/CO Detector" ],
             17: [ "namespace" : "hubitat", "driver": "Generic Z-Wave Thermostat" ],         
             18: [ "namespace" : "hubitat", "driver": "Generic ZigBee RGBW Light" ],
             19: [ "namespace" : "smartport", "driver": "Peanut Plug" ],
             20: [ "namespace" : "smartport", "driver": "Schlage Connect BE468 Lock" ],
             21: [ "namespace" : "hubitat", "driver": "SmartThings Multipurpose Sensor V5" ],
             22: [ "namespace" : "hubitat", "driver": "Generic Zigbee Motion/Humidity Sensor" ],          
             23: [ "namespace" : "hubitat", "driver": "Generic Zigbee Motion/Humidity Sensor" ],
             24: [ "namespace" : "hubitat", "driver": "Generic Zigbee Motion/Humidity Sensor" ],
             25: [ "namespace" : "hubitat", "driver": "SmartThings Multipurpose Sensor V5" ],
             26: [ "namespace" : "hubitat", "driver": "Generic Zigbee Motion/Humidity Sensor" ],
             27: [ "namespace" : "hubitat", "driver": "Generic Zigbee Moisture Sensor" ],
             28: [ "namespace" : "hubitat", "driver": "Generic Zigbee Moisture Sensor" ]
         ]
     ]
    return config
}

void deleteVirtualDevices() {
    devices = getAllChildDevices()
    for ( device in devices) {
        deleteChildDevice(device.deviceNetworkId)
    }  
}

void makeVirtualDevices() {
    def vDevices = getUnitDeviceSchema()
    def deviceCntr = 1000
    for ( device in vDevices["1A"] ) {
        def childDevice = addChildDevice(device.value.namespace, device.value.driver, "$deviceCntr", null, [name: device.key, label: device.key])
        deviceCntr++
    }   
}

def installed() {
	ifDebug("Installed with settings: ${settings}")
    initialize()
}

def updated() {
	ifDebug("Updated with settings: ${settings}")
    initialize()
}

def toJson(data) {
    JsonOutput.toJson(data)  
}

private ifDebug(msg) {  
    if (msg && state.isDebug)  log.debug 'AVDE:' + msg  
}
