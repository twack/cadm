/**
 *=============================================================================
 *  Sentient - Current Apps and Drivers Manager (CADM)
 *
 *  App to get and report all currently installed stock and custom apps and 
 *  drivers with versions.
 *
 *  Copyright 2019 Smartport LLC
 *
 *  Initial Release:	20191122
 *  Last Update:		20191122
 *
 *  Change Log:
 *  Version		Date Release	Reason				Author				Ticket#
 *  ---------------------------------------------------------------------------
 *  V0.0.1		20191122		POC            		Todd Wackford		N/A
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
    name: "CADM",
    namespace: "smartport",
    author: '{"authorName": "Todd Wackford", "smartportVersion": "0.0.1"}',
    description: "Manage Apps and Drivers on a Hubitat Hub",
    category: "Convienence",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    oauth: true
)

mappings {       
    path("sentient/cadm/getSmartportAppData") {
        action: [
            GET: "getSmartportAppData"
        ]
    }
    path("sentient/cadm/getSmartportDriverData") {
        action: [
            GET: "getSmartportDriverData"
        ]
    }
    path("sentient/cadm/getUpdateAppCode") {
        action: [
            GET: "getUpdateAppCode"
        ]
    }
    path("sentient/cadm/compareSmartportVersions") {
        action: [
            GET: "compareSmartportVersions"
        ]
    }  
}

def compareSmartportVersions() {
    log.trace "Executing compareSmartportVersions()"
    
    def sbAppList = []
    def sbDriverList = []
    
    def manifest = params?.manifest ? URLDecoder.decode(params?.manifest) : null
    def spaceType = params?.spaceType ? URLDecoder.decode(params?.spaceType) : null
    
    httpGet(manifest) { resp -> jsonData = resp.data.text }
    
    //get the should be (sb) apps/drivers and versions from the manifest json file
    def objManifest = new JsonSlurper().parseText(jsonData)
    def codeBaseUri = objManifest.repoBaseUri
    def sbCodeList = []
    
    for ( space in objManifest.spaces ) {        
        if ( space.spaceType == "Apartment" ) {
            sbAppList = space.apps
            sbDriverList = space.drivers
            sbCodeList = sbAppList + sbDriverList           
        }
    }
    
    //now get the (is) apps/drivers and versions that are loaded on this hub
    def installedAppData = getSmartportAppData(outputToHttp=false)
    def installedDriverData = getSmartportDriverData(outputToHttp=false)    
    def installedCode = installedAppData + installedDriverData
    //log.info "installedCode: ${toJson(installedCode)}"

    //look to see if the should be apps are installed and up to date
    for ( sbCode in sbCodeList ) {
        def sbType = sbCode.key.contains("drivers") ? "driver" : "app"
        def sbTypes = sbCode.key.contains("drivers") ? "drivers" : "apps"
        def isCode = installedCode.find{sbTypes + "/" + it.fileName + ".groovy" == sbCode.key}
        if ( isCode ) {
            def rc = compareVersionNumbers(sbCode.value, isCode.smartPortVersion)
            switch (rc) {
                case -1: // installed is higher version
                    log.warn "Source Code for ${sbCode.key}, version: ${isCode.smartPortVersion} is higher than requested version: ${sbCode.value}. Not updating Source Code."
                    break
                case 0: // installed is same as manifest
                    log.info "Source Code for ${sbCode.key}, version: ${isCode.smartPortVersion} does not need updating."             
                    break
                case 1: // installed needs updating
                    log.warn "Source Code for ${sbCode.key}, version: ${isCode.smartPortVersion} needs to be updated from version: ${sbCode.value}. Updating Source Code now."
                    if ( updateSmartportSourceCode(codeBaseUri, sbCode, sbType, isCode.fileId, isCode.hubitatVersion) ) {
                        log.info "Source Code for ${sbCode.key} has been updated to version: ${sbCode.value}."
                    }
                    break
                default: // value not handled, must be error. Bail!
                    log.error "Error in version checker! Ensure manifest and source code syntax is correct for ${sbCode.key}! Not updated!"               
                    break                
            }
        } else {
            log.warn "Source Code for ${sbCode.key} with version: ${sbCode.value} is not installed. Installing Source Code."
            if ( installSmartportSourceCode(codeBaseUri, sbCode, sbType) ) {
                log.warn "Source Code for ${sbCode.key}, version: ${sbCode.value} has been loaded onto this hub."
            }
        }
    }
}

def compareVersionNumbers(a, b) {
  def VALID_TOKENS = /._/
  a = a.tokenize(VALID_TOKENS)
  b = b.tokenize(VALID_TOKENS)
  for (i in 0..<Math.max(a.size(), b.size())) {
    if (i == a.size()) {
      return b[i].isInteger() ? -1 : 1
    } else if (i == b.size()) {
      return a[i].isInteger() ? 1 : -1
    }  
    if (a[i].isInteger() && b[i].isInteger()) {
      int c = (a[i] as int) <=> (b[i] as int)
      if (c != 0) {
        return c
      }
    } else if (a[i].isInteger()) {
      return 1
    } else if (b[i].isInteger()) {
      return -1
    } else {
      int c = a[i] <=> b[i]
      if (c != 0) {
        return c
      }
    }
  }
  return 0    
}

def updateSmartportSourceCode(codeBaseUri, sbCode, type, hubitatAppId, hubitatCurrentVersion) {
    log.trace "Executing updateSmartportSourceCode()"
    def codeSource = "$codeBaseUri${sbCode.key}"
    codeSource = codeSource.replace(" ", "%20")
    log.info "codeSource: $codeSource"
    String sourceCode = null 
    try {
        httpGet(codeSource) { resp -> sourceCode = resp.data.text }
        if(sourceCode) {
            Map params = [
                uri:"http://localhost:8080/${type}/ajax/update",
                contentType: "application/x-www-form-urlencoded",
                body:[id: hubitatAppId, version:hubitatCurrentVersion, source: sourceCode]
            ]
            try { //now try to load that puppy
                httpPost(params) {}
            } catch (Exception e2) { //crap it failed to load!
                log.error "Unable to update source code for '$codeSource' to this hub. Error: ${e2.message}"
                return false
            }
        }
    } catch (Exception e) {
        log.error "Unable to get source code for '$codeSource'. Error: ${e.message}"
        return false
    }    
    return true    
}

def installSmartportSourceCode(codeBaseUri, sbCode, type) {
    log.trace "Executing installSmartportSourceCode()"
    def codeSource = "$codeBaseUri${sbCode.key}"
    codeSource = codeSource.replace(" ", "%20")
    //log.info "codeSource: $codeSource"
    String sourceCode = null 
    try {
        httpGet(codeSource) { resp -> sourceCode = resp.data.text }
        if(sourceCode) {
            //log.debug "we have got the code"
            Map params = [
                uri:"http://localhost:8080/${type}/save",
                contentType: "application/x-www-form-urlencoded",
                body:[source: sourceCode]
            ]
            try { //now try to load that puppy
                httpPost(params) {}
            } catch (Exception e2) { //crap it failed to load!
                log.error "Unable to post source code for '$codeSource' to this hub. Error: ${e2.message}"
                return false
            }
        }
    } catch (Exception e) {
        log.error "Unable to get source code for '$codeSource'. Error: ${e.message}"
        return false
    }    
    return true
}

def getUpdateAppCode() {
    log.trace "Executing updateAppCode()"
    //return
    //log.debug params
    def codeSource = params?.codeSource ? URLDecoder.decode(params?.codeSource) : null
    def appName = params?.appName ? URLDecoder.decode(params?.appName) : null
    
    if ( codeSource && appName ) {
        //log.warn codeSource
        //log.warn appName
        getAndSaveCode(codeSource, "app")
    }
}

void getAndSaveCode(String url, String type) {
    String sourceCode = null
    log.info url
    log.info type
    httpGet(url) { resp -> sourceCode = resp.data.text }
    
    //log.debug sourceCode
    if(sourceCode) {
        //log.debug "we have gotten the code"
        Map params = [uri:"http://localhost:8080/${type}/ajax/update",
                      contentType: "application/x-www-form-urlencoded",
                      body:[id: 450, version:3, source: sourceCode]]

        httpPost(params) {}
    }
    log.debug "completed update code"
}

def getSmartportDriverData(outputToHttp) {
    def jsonDriverReport = []
    def driverData = []
    httpGet("http://localhost:8080/driver/list/data") { resp ->
        resp.data.each { driverItem ->
            if ( driverItem.namespace.toLowerCase() == "smartport" ) {
                //log.info driverItem
                driverData = [
                    "fileName": driverItem.name,
                    "fileId": driverItem.id,
                    "fileNameSpace": driverItem.namespace,
                ]
                // get the smartport version information if preset. Log error if missing
                if ( driverItem.author.contains("smartportVersion") ) {
                    def object = new JsonSlurper().parseText(driverItem.author)
                    driverData.smartPortVersion = object.smartportVersion
                    driverData.author = object.authorName
                } else {
                    driverData.smartPortVersion = "ERROR: MISSING SMARTPORT VERSIONING"
                    if ( driverItem.author ) { //no version info, but probably has author
                        driverData.author = driverItem.author
                    }
                    log.warn "Missing smartport version data for ${driverItem.name}"
                }
                //get the deeper data on this app. We need the hubitat version to update and index by this version.
                httpGet("http://localhost:8080/driver/ajax/code?id=${driverItem.id}") { resp2 ->
                    driverData.hubitatVersion = resp2.data.version                     
                }  
            }
            if ( driverData ) { jsonDriverReport << driverData }
        }        
    }
    def payload = [ "smartportCustomDriverCodeList" : jsonDriverReport ]
    
    if ( outputToHttp ) {
        render contentType: 'application/json', data: toJson(payload)
    } else {
        return jsonDriverReport
    } 
}

def getSmartportAppData(outputToHttp) {
    def jsonAppReport = []
    def appData = []
    httpGet("http://localhost:8080/app/list/data") { resp ->
        resp.data.each { appItem ->
            if ( appItem.namespace.toLowerCase() == "smartport" ) {
                //log.info "LOADED APP INFORMATION: $appItem"
                appData = [
                    "fileName": appItem.name,
                    "fileId": appItem.id,
                    "fileNameSpace": appItem.namespace,
                    "fileDescription" : appItem.description
                    //"appAuthor": appItem.author
                ]                
                // get the smartport version information if preset. Log error if missing
                if ( appItem.author.contains("smartportVersion") ) {
                    def object = new JsonSlurper().parseText(appItem.author)
                    appData.smartPortVersion = object.smartportVersion
                    appData.author = object.authorName
                } else {
                    appData.appSmartPortVersion = "ERROR: MISSING SMARTPORT VERSIONING"
                    if ( appItem.author ) { //no version info, but probably has author
                        appData.author = appItem.author
                    }
                    log.warn "Missing smartport version data for ${appItem.name}"
                    //appData.appDescription = appItem.description
                }                
                //get the deeper data on this app. We need the hubitat version to update and index by this version.
                httpGet("http://localhost:8080/app/ajax/code?id=${appItem.id}") { resp2 ->
                    appData.hubitatVersion = resp2.data.version
                    //log.warn "should have hubitat version! ${resp2.data.version}"
                }                
            }
            if ( appData ) { jsonAppReport << appData }    
        }  
    }
    //
    //log.info jsonAppReport
    def payload = [ "smartportCustomAppCodeList" : jsonAppReport ]
    if ( outputToHttp ) {
        render contentType: 'application/json', data: toJson(payload)
    } else {
        return jsonAppReport
    }
}


//will need to change this to creating a response payload
def sendToIotcMotherShip(payload) {
    return
    def request = [:]
    //request.headers = [:]
    
    def hitThis = "https://iotc-fnz5nu4inyraeim.azurewebsites.net/api/IoTCIntegration?code=/sVbUXyzr2GtlQ9kqyrxl8ULTM8oUawRx13hHe6tqDev8/GZDLzipA=="
        
    request.uri = hitThis
    request.body = payload
    //request.headers["Content-Type"] = "application/json;charset=UTF-8" 
    log.info request
    try {
        httpPost(request) { response ->
            
            if (response.success) {
                log.debug "response from IOTC is: $response.status"
            }
        }
    } catch (Exception e) {
            log.warn "Sending Post URL Failed: ${e.message}"
    }  
}



def initialize() {
    unsubscribe()
    unschedule()
    
    if(!state.accessToken){	
        //enable OAuth in the app settings or this call will fail
        createAccessToken()	
    }
    
    def uri = getFullLocalApiServerUrl() +    "/sentient/cadm?access_token=${state.accessToken}"
    log.warn uri
    getSmartportAppData()
}

def installed() {
	log.trace "Installed with settings: ${settings}"
    initialize()
}

def updated() {
	log.trace "Updated with settings: ${settings}"
    initialize()
}

def toJson(data) {
    JsonOutput.toJson(data)  
}

private ifDebug(msg) {  
    if (msg && state.isDebug)  log.debug 'Volley:' + msg  
}
