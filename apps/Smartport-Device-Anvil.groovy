/**
 *=============================================================================
 *  Smartport Device Anvil
 *
 *  App to setup devices according to apartment device schema
 *
 *  Copyright 2019 Smartport LLC
 *
 *  Initial Release:	20191020
 *  Last Update:		20191020
 *
 *  Change Log:
 *  Version		Date Release	Reason				Author				Ticket#
 *  ---------------------------------------------------------------------------
 *  V0.0.1		20191020		POC            		Todd Wackford		N/A
 *
 *
 *
 *=============================================================================
 */
 
 /**
 * TODO List:
 * : first item
 *
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

definition(
    name: "Smartport Device Anvil",
    namespace: "Smartport",
    author: '{"authorName": "Todd Wackford", "smartportVersion": "0.0.1"}',
    description: "Configure Apartment Devices",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    oauth: true
)

preferences {
  page(name: "setupPage")
}

def setupPage(){
    state.isDebug = isDebug
    
    //def devices = getDevices().sort { it.value }
    //def preselected = devices.collect{it.key}
    //log.warn preselected

    def connectLink = [
                url:		"https://www.youtube.com/watch?v=PD3FuIASFFg", //replace with real URL when ready
                style:		"embedded",
                required:	false,
                image:      "https://cdn0.iconfinder.com/data/icons/tools-of-the-trade-vol-2/48/Sed-84-128.png",
                title:		"Smartport Device Anvil",
                description: "Configure devices to follow identification schema"
    ]
    
    
    
    return dynamicPage(name: "setupPage", uninstall: true, install: true){
        section {
            href connectLink
        }
		section () {
            input "unitNumber", "number", title: "Unit Number", multiple: false
    		input "unitConfig", "enum", options: unitTypes, required: false, defaultValue: "1A", title: "Unit Type"
    	}  
		section () {
            input "manifestFile", "text", title: "Manifest File:", multiple: false
    	}         
		section () {
    		input "selectedDevices", "capability.*", type: "enum", title: "Which Devices to Configure?", multiple: true, required: false
    	}
	    section() {
       		input "isDebug", "bool", title: "Enable Debug Logging", required: false, multiple: false, defaultValue: false, submitOnChange: true
    	}
    }
}

def getDevices() {
    def respData = [:]
    httpGet("http://localhost:8080/device/list/data") { resp ->
        resp.data.each { deviceItem ->
            log.info deviceItem
            def thisKey = deviceItem.name
            def thisValue = deviceItem.deviceNetworkId
            respData << [ "$thisKey" : "$thisValue" ]
        }
    }
    respData
}

def getUnitTypes() {
     def types = [ "1A", "1B", "Common1", "Common2" ]
}

void refactorDeviceNames() {
    log.trace "Executing refactorDeviceNames()"
    def manifestJson = null
    if ( manifestFile ) {
        try {
            httpGet(manifestFile) { resp -> manifestJson = resp.data.text }
            //log.info manifest
        } catch (Exception e) {
            log.error "Unable to access manifest file: '$manifestFile'. Error: ${e.message}"
            return
        }
    }
    def manifest = new JsonSlurper().parseText(manifestJson)
    for ( space in manifest.spaces ) {
        if ( unitConfig == space.spaceconfiguration ) {
            for ( deviceData in space.renameDevices ) {
                if ( selectedDevices && deviceData ) {
                    log.info "Device Number: ${deviceData.key} is renaming to: ${deviceData.value.location} ${deviceData.value.type}"
                    def device = selectedDevices.find{it.name == deviceData.key}
                    if ( device ) {                       
                        def nameLabel = "${deviceData.value.location} ${deviceData.value.type}"
                        device.label = nameLabel.trim()
                        device.name = nameLabel.trim()
                    } else {
                        log.error "Error: Device ${deviceData.key} could not be found. Check Manifest file."
                    }
                } else {
                    log.error "Error: No devices selected. Go back and select the devices."
                }
            }
        }
    }
    runInMillis(1000, nameChanger) //Bug in hubitat where name did not get changed in same process. Do a "runIn" for workaround.
}

void nameChanger() {
    for ( d in selectedDevices ) {
        d.name = d.displayName   
    }
    log.info "_______________ Device Anvil is done!"
}

def initialize() {
    //refactorDeviceNames()
    log.info "initializing with: ${settings.selectedDevices}"
    refactorDeviceNames()
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
    if (msg && state.isDebug)  log.debug 'DeviceAnvil:' + msg  
}
