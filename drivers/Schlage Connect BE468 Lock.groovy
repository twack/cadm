/*
	Schlage Zigbee Lock

	Copyright 2016, 2017, 2018, 2019 Hubitat Inc.  All Rights Reserved

	2019-09-08 2.1.5 ravenel
        -add lastCodeName
	2019-08-02 maxwell
		-initial pub

*/

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

@Field static List<Map> autoLockOptions = [
        ["0"   :"Disabled (Default)"],
        ["15"  :"15 Seconds"],
        ["30"  :"30 Seconds"],
        ["60"  :"1 Minute"],
        ["300" :"5 Minutes"],
        ["1800":"30 Minutes"],
        ["3600":"1 Hour"]
]

@Field static List<Map> audioLevelOptions = [
        ["0":"Mute"],
        ["1":"On (Default)"]
]

metadata {
    definition (name: "Schlage Connect BE468 Lock", 
		namespace: "smartport", 
		author: '{"authorName": "Todd Wackford", "smartportVersion": "1.0.0"}', ) {
        capability "Actuator"
        capability "Lock"
        capability "Refresh"
        capability "Lock Codes"
        capability "Battery"
        capability "Configuration"
		
        attribute "lastCodeName", "STRING"
        
        fingerprint profileId: "0104", inClusters: "0000,0001,0003,0009,0020,0101,0B05,FC00", outClusters: "000A,0019", manufacturer: "Schlage", model: "BE468", deviceJoinName: "Schlage Connect Smart Deadbolt"

    }
    preferences{
        input name: "autoLock", type: "enum", title: "Auto Lock Timeout", options: autoLockOptions, defaultValue: "0", description: ""
        input name: "audioLevel", type: "enum", title: "Audio Level", options: audioLevelOptions, defaultValue: "1", description: ""
        input name: "optEncrypt", type: "bool", title: "Enable lockCode encryption", defaultValue: false, description: ""
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false, description: ""
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true, description: ""
    }

}

// Globals - Cluster IDs
private getCLUSTER_POWER()    { 0x0001 }
private getCLUSTER_DOORLOCK() { 0x0101 }
private getCLUSTER_ALARM()    { 0x0009 }

private getDOORLOCK_ATTR_LOCKSTATE()      { 0x0000 }
private getDOORLOCK_ATTR_NUM_PIN_USERS()  { 0x0012 }
private getDOORLOCK_ATTR_MAX_PIN_LENGTH() { 0x0017 }
private getDOORLOCK_ATTR_MIN_PIN_LENGTH() { 0x0018 }
private getBATTERYPERCENTAGEREMAINING()   { 0x0021 }
private getAUTORELOCKTIME()               { 0x0023 }
private getSOUNDVOLUME()                  { 0x0024 }

private getUNIT8()     { 0x20 }
private getUINT16()    { 0x21 }
private getUINT24()    { 0x22 }
private getUINT32()    { 0x23 }
private getINT8()      { 0x28 }
private getINT16()     { 0x29 }
private getINT24()     { 0x2a }
private getINT32()     { 0x2b }


void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

List<String> parse(String description) {
    List<String> result
    if (description) {
        if (description.startsWith('read attr -')) {
            result = parseAttributeResponse(description)
        } else {
            result = parseCommandResponse(description)
        }
    }
    return result ?: []
}

List<String> parseAttributeResponse(String description) {
    Map descMap = zigbee.parseDescriptionAsMap(description)
    if (logEnable) log.debug "descMap:${descMap}"
    String descriptionText
    Integer value
    List<String> result = []
    
    switch (descMap.clusterInt){
        
        case CLUSTER_POWER: //battery
            value = Math.round(Integer.parseInt(descMap.value, 16) / 2)
            descriptionText = "${device.displayName} battery is ${value}%"
            if (txtEnable) log.info "${descriptionText}"
            sendEvent(name:"battery", value:value, descriptionText: descriptionText, unit:"%", isStateChange: true)
            break
        
        case CLUSTER_DOORLOCK: //lock
            log.warn descMap
            value = hexStrToUnsignedInt(descMap.value)
            switch (descMap.attrInt) {
                case DOORLOCK_ATTR_LOCKSTATE:
                    value = Integer.parseInt(descMap.value, 16)
                    String newValue = "unknown"
                    if (descMap.value == "01") { //locked
                        newValue = "locked"
                    } else if (descMap.value == "02") { //unlocked
                        newValue = "unlocked"
                    }
                    if (newValue != device.currentValue("lock")) { //don't send event if already in that state
                        descriptionText = "${device.displayName} was ${newValue} via read attribute report..."
                        if (txtEnable) log.info "${descriptionText}"
                        sendEvent(name:"lock", value:nv, descriptionText: descriptionText)
                    }
                    break
                case DOORLOCK_ATTR_NUM_PIN_USERS:	//user code count
                    sendEvent(name:"maxCodes", value:value)
                    if (!state.codeFetchComplete) {
                        state.fetchCode = 1
                        result << fetchLockCode(1)
                    }
                    break
                case DOORLOCK_ATTR_MAX_PIN_LENGTH:	//max code length
                    //not used
                    break
                case DOORLOCK_ATTR_MIN_PIN_LENGTH:	//min code length
                    //not used
                    break
                default :
                    if (logEnable) log.info "skipped 0101 ${descMap.attrId}, descMap:${descMap}"
                    break
            }
            break
        
        case CLUSTER_ALARM: //alarm
            value = hexStrToUnsignedInt(descMap.value)
            //switch (descMap.attrId) {
            log.warn "Alarm information coming in! $descMap"
            log.warn descMap.attrId
            log.warn value
            switch (descMap.attrId) {
                
                default :
                    if (logEnable) log.info "skipped 0009 descMap:${descMap}"
                    break
            }
            break
        default :
            if (logEnable) log.info "skipped cluster:${descMap.clusterId}, descMap:${descMap}"
            break
    }
    return result
}

List<String> parseCommandResponse(String description) {
    Map descMap = zigbee.parseDescriptionAsMap(description)
    if (logEnable) log.debug "descMap:${descMap}"
    List<String> result = []
    Map codeMap = [:]
    String descriptionText
    if (descMap.isClusterSpecific) {
        switch (descMap.clusterId){
            case "0101": //lock
                switch (descMap.command){
                    //case "00": // Lock Door Response M
                    //case "01": // Unlock Door Response M
                    //case "02": // Toggle Response O
                    //case "03": // Unlock with Timeout Response O
                    //case "04": // Get Log Record Response O
                    //case "05": // Set PIN Code Response O
                    case "06": //Get PIN Code Response O
                        codeNumber = hexStrToUnsignedInt(descMap.data[1] + descMap.data[0])
                        codeMap = getCodeMap(lockCodes,codeNumber)
                        String code = "empty"
                        if (descMap.data[2] == "01"){ //code status
                            if (codeNumber) {
                                code = descMap.data[5..-1].collect{ (char)Integer.parseInt(it, 16) }.join()
                                setLockCode(lockCodes,codeMap,codeNumber, code, null)
                            }
                        } else {
                            deleteLockCode(lockCodes,codeNumber)
                        }
                        if (!state.codeFetchComplete) {
                            if (state.fetchCode != (device.currentValue("maxCodes").toInteger() - 1)) {
                                if ( state.fetchCode == codeNumber ) {
                                    if (txtEnable) log.info "code:${codeNumber} fetched"
                                    state.fetchCode++
                                    if (txtEnable) log.trace "trying to fetch code:${state.fetchCode}"
                                    fetchLockCode(state.fetchCode)
                                }
                            } else if (state.fetchCode == (device.currentValue("maxCodes").toInteger() - 1)) {
                                state.codeFetchComplete = 1
                                if (txtEnable) log.info "last code ${codeNumber} fetched, fetch complete"
                            }
                        }
                        break
                    //case "07": // Clear PIN Code Response O
                    //case "08": // Clear All PIN Codes Response O
                    //case "09": // Set User Status Response O
                    //case "0A": // Get User Status Response O
                    //case "0B": // Set Weekday Schedule Response O
                    //case "0C": // Get Weekday Schedule Response O
                    //case "0D": // Clear Weekday Schedule Response O
                    //case "0E": // Set Year Day Schedule Response O
                    //case "0F": // Get Year Day Schedule Response O
                    //case "10": // Clear Year Day Schedule Response O
                    //case "11": // Set Holiday Schedule Response O
                    //case "12": // Get Holiday Schedule Response O
                    //case "13": // Clear Holiday Schedule Response O
                    //case "14": // Set User Type Response O
                    //case "15": // Get User Type Response O
                    //case "16": // Set RFID Code Response O
                    //case "17": // Get RFID Code Response O
                    //case "18": // Clear RFID Code Response O
                    //case "19": // Clear All RFID Codes Response O
                    case "20": // Operating Event Notification O
                        String type = "physical"
                        String via  = "unknown"
                        String value = "unknown"
                        String name = "unknown"
                        switch (descMap.data[0]){ //Operation Event Source
                            case "00": //keypad
                                via = "keypad"
                                Integer codeNumber = hexStrToUnsignedInt(descMap.data[3] + descMap.data[2])
                                codeMap = getCodeMap(lockCodes,codeNumber)
                                if (codeMap) {
                                    name = codeMap.name
                                } else if (codeNumber == 0) {
                                    name = "master code"
                                    codeMap.name = name
                                    codeMap.code = "unknown"
                                } else {
                                    codeMap.name = "code #${codeNumber}"
                                    codeMap.code = "unknown"
                                    result << fetchLockCode(codeNumber)
                                }
                                codeMap = ["${codeNumber}":codeMap]
                                break
                            case "01": //RF, command
                                via = "command"
                                type = "digital"
                                break
                            case "02": //manual
                                via = "manual"
                                break
                            case "03": //RFID
                                via = "RFID"
                                //log.trace "user id:${hexStrToUnsignedInt(descMap.data[3] + descMap.data[2])}"
                                break
                        }
                        switch (descMap.data[1]){ //Operation Event Code
                            case "01":
                                value = "locked"
                                break
                            case "02":
                                value = "unlocked"
                                break
                            case "03":
                                log.info "Lock Failure: Invalid Pin Used!"
                                break
                            case "04":
                                //schedule error not supported
                                break
                            case "05":
                                log.info "Unlock Failure: Invalid Pin Used!"
                                break
                            case "06":
                                //schedule error not supportedlog.info
                                break
                            case "07":
                                value = "locked"
                                via = "button"
                                break
                            case "08":
                                value = "locked"
                                via = "key"
                                break
                            case "09":
                                value = "unlocked"
                                via = "key"
                                break
                            case "0A":
                                value = "locked"
                                via = "auto lock"
                                break
                            case "0B":
                                value = "locked"
                                via = "schedule"
                                break
                            case "0C":
                                value = "unlocked"
                                via = "schedule"
                                break
                            case "0D":
                                value = "locked"
                                via = "key or thumbturn"
                                break
                            case "0E":
                                value = "unlocked"
                                via = "key or thumbturn"
                                break
                        }
                        if (codeMap && optEncrypt) {
                            descriptionText = "${device.displayName} was ${value} by ${name} [${type}]"
                            sendEvent(name:"lock", value:value, descriptionText: descriptionText, type:type, data:encrypt(JsonOutput.toJson(codeMap)), isStateChange: true)
                            if(value == "unlocked") sendEvent(name: "lastCodeName", value: name, descriptionText: descriptionText, isStateChange: true)
                        } else if (codeMap) {
                            descriptionText = "${device.displayName} was ${value} by ${name} [${type}]"
                            sendEvent(name:"lock", value:value, descriptionText: descriptionText, type:type, data:codeMap, isStateChange: true)
                            if(value == "unlocked") sendEvent(name: "lastCodeName", value: name, descriptionText: descriptionText, isStateChange: true)
                        } else {
                            descriptionText = "${device.displayName} was ${value} via ${via} [${type}]"
                            sendEvent(name:"lock", value:value, descriptionText: descriptionText, type:type, isStateChange: true)
                        }
                        if (txtEnable) log.info "${descriptionText}"
                        break
                    case "21": // Programming Event Notification O
                        String action = "unknown"
                        codeNumber = hexStrToUnsignedInt(descMap.data[3] + descMap.data[2])
                        switch (descMap.data[1]){
                            case "01":
                                action = "master code changed"
                                break
                            case "02":
                                action = "code added"
                                break
                            case "03":
                                deleteLockCode(lockCodes,codeNumber)
                                action = "code deleted"
                                break
                            case "04":
                                action = "code changed"
                                break
                        }
                        if (logEnable) log.warn "programming event- action:${action}, codeNumber:${codeNumber}"
                        break
                    default :
                        if (logEnable) log.info "skipped 0101, command:${descMap.command}, descMap:${descMap}"
                        break
                }
                break
            case "0009": //alarm
                if (logEnable) log.info "skipped 0009 descMap:${descMap}"
                break
            default :
                if (logEnable) log.info "skipped cluster:${descMap.clusterId}, descMap:${descMap}"
                break
        }
    } else {
        if (descMap.clusterId == "0101" && descMap.command == "0B" && descMap.data[0] == "06" && descMap.data[1] == "81") { //response to get code failed
            if (logEnable) "get code read failed, setting code export bits..."
            result << response(zigbee.writeAttribute(0x0101, 0x0032, 0x10,1,[:],0))
            runIn(2,"fetchLockCode")
        } else {
            if (logEnable) log.debug "default response:${descMap}"
        }
    }

    if (result) return result.flatten()

}

List<String> updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    log.warn "encryption is: ${optEncrypt == true}"
    updateEncryption()
    if (logEnable) runIn(1800,logsOff)
    List<String> cmds = []
    if (autoLock)   cmds.addAll(zigbee.writeAttribute(CLUSTER_DOORLOCK, AUTORELOCKTIME, UINT32, autoLock.toInteger(),[:],0))
    if (audioLevel) cmds.addAll(zigbee.writeAttribute(CLUSTER_DOORLOCK, SOUNDVOLUME, UNIT8, audioLevel.toInteger(),[:],0))
    if (cmds) return delayBetween(cmds,200)
}

void installed(){
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

List<String> refresh() {
    return zigbee.readAttribute(CLUSTER_DOORLOCK, DOORLOCK_ATTR_LOCKSTATE, [:],200) + zigbee.readAttribute(CLUSTER_POWER, BATTERYPERCENTAGEREMAINING, [:],0)
}

List<String> configure() {
    state.codeFetchComplete = 0
    state.requestedChange = [:]
    List<String> cmds = [
            //bindings and reporting
            "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0101 {${device.zigbeeId}} {}","delay 500", // lock
            "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0001 {${device.zigbeeId}} {}","delay 500",	//battery
            "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0009 {${device.zigbeeId}} {}","delay 500",	//alarm

            //reporting
            "he cr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0101 0x0000 0x30 0 0xFFFF {} {}","delay 500", // lock
            "he cr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0001 0x0021 0x20 0 86400 {01} {}","delay 500", //battery
            "he cr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0009 0x0000 0x21 0 86400 {00} {}","delay 500", //alarm
            "he wattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0101 0x0032 0x10 {01} {}","delay 500", //send pin over the air...
            "he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0101 0x12 {}","delay 500",             //max codes
            //long poll interval set (3 seconds)
            "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0020 0x02 {0C 00 00 00}","delay 500",
    ]
    return cmds + refresh()
}

//capability commands
List<String> lock() {
    return ["he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0101 0x00 {}"]
}

List<String> unlock() {
    return ["he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0101 0x01 {}"]
}

List<String> setCode(codeNumber, code, name = null) {
    if (codeNumber == null || codeNumber == 0 || code == null) return
    if (logEnable) log.debug "setCode- ${codeNumber}"
    if (!name) name = "code ${codeNumber}"
    Map lockCodes = getLockCodes()
    Map codeMap = getCodeMap(lockCodes,codeNumber)
    if (changeIsValid(lockCodes,codeMap,codeNumber,code,name)) {
        if (codeMap.code == code && codeMap.name != name) {
            setLockCode(lockCodes,codeMap,codeNumber, code, name)
        } else if (codeMap.code != code) {
            state.requestedChange = encrypt( JsonOutput.toJson([codeNumber:"${codeNumber}", code:"${code}", name:"${name}",status:1]))
            return [
                    "he wattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0101 0x0032 0x10 {01} {}","delay 200",
                    "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0101 0x05 { ${setCodePayload(codeNumber,code)} }", "delay 200",
                    "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0101 0x06 { ${zigbee.swapOctets(intToHexStr(codeNumber.toInteger(), 2))} }"
            ]
        } //else log.info "no changes detected..."
    } //else log.info "requested change denied..."
    return []
}

List<String> deleteCode(codeNumber) {
    if (codeNumber == null) return []
    state.requestedChange = encrypt( JsonOutput.toJson([codeNumber:"${codeNumber}",status:0]) )
    return [
            "he wattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0101 0x0032 0x10 {01} {}","delay 200",
            "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0101 0x07 { ${zigbee.swapOctets(intToHexStr(codeNumber.toInteger(), 2))} }"
    ]
}

void setCodeLength(length){
    String descriptionText = "${device.displayName} codeLength set to ${length}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name:"codeLength",value:length,descriptionText:descriptionText)
}

List<String> getCodes(){
    Integer maxCodes = (device?.currentValue("maxCodes") ?: 1).toInteger() - 1
    if (maxCodes) {
        state.codeFetchComplete = 0
        state.requestedChange = [:]
        if (state.fetchCode == null || state.fetchCode == maxCodes) {
            state.fetchCode = 1
        }
        if (txtEnable) log.info "starting code fetch, trying to fetch code:${state.fetchCode}"
        return fetchLockCode(state.fetchCode)
    } else {
        return getMaxCodes()
    }
}

List<String> getMaxCodes(){
    return zigbee.readAttribute(0x0101, 0x0012,[:],0)
}

void setLockCode(lockCodes,codeMap,codeNumber, code, name = null) {
    Map data = [:]
    String value
    Boolean noCodeSent = code.startsWith("*")
    Map requestedChange = state.requestedChange ? new JsonSlurper().parseText(decrypt(state.requestedChange)) : null
    if (requestedChange) {
        name = requestedChange.name
        if (noCodeSent) code = requestedChange.code
        if (logEnable) log.debug "clear pending change"
        state.requestedChange = null
        value = codeMap ? "changed" : "added"
    } else if (codeMap && name != null && codeMap.name != name) {
        if (logEnable) log.info "changed codeMap:${codeMap}, name:${name}"
        value = "changed"
    } else if (codeMap && codeMap.code != code) {
        if (logEnable) log.info "changed codeMap:${codeMap}, code:${code}"
        name = codeMap.name
        value = "changed"
    } else if (!codeMap) {
        if (logEnable) log.info "added codeMap:${codeMap}, name:${name}, code:${code}"
        name = "code #${codeNumber}"
        value = "added"
    }
    if (value) {
        if (logEnable) log.debug "${value} code ${codeNumber} to ${code} for lock code name ${name}"

        if (value == "changed") {
            codeMap = ["name":"${name}", "code":"${code}"]
            lockCodes."${codeNumber}" = codeMap
            data = ["${codeNumber}":codeMap]
        } else {
            codeMap = ["name":"${name}", "code":"${code}"]
            data = ["${codeNumber}":codeMap]
            lockCodes << data
        }
        if (optEncrypt) data = encrypt(JsonOutput.toJson(data))
        updateLockCodes(lockCodes)
        String descriptionText = "${device.displayName} ${value} lock code for user ${name}"
        if (txtEnable) log.info descriptionText
        sendEvent(name:"codeChanged",value:value,data:data,descriptionText:descriptionText, isStateChange: true)
    } else if (logEnable) log.debug "no changes to lockCode:${codeMap}"
}

void deleteLockCode(lockCodes,codeNumber) {
    Map codeMap = getCodeMap(lockCodes,"${codeNumber}")
    Map requestedChange = state.requestedChange ? new JsonSlurper().parseText(decrypt(state.requestedChange)) : null
    if (requestedChange && requestedChange.codeNumber.toInteger() == codeNumber) {
        state.requestedChange = null
    }
    Map result = [:]
    if (codeMap) {
        //build new lockCode map, exclude deleted code
        lockCodes.each{
            if (it.key != "${codeNumber}"){
                result << it
            }
        }
        updateLockCodes(result)
        Map data =  ["${codeNumber}":codeMap]
        if (optEncrypt) data = encrypt(JsonOutput.toJson(data))
        String descriptionText = "${device.displayName} deleted lock code for user ${codeMap.name}"
        if (txtEnable) log.info descriptionText
        sendEvent(name:"codeChanged",value:"deleted",data:data,descriptionText:descriptionText, isStateChange: true)
    }
}

Boolean changeIsValid(lockCodes,codeMap,codeNumber,code,name){
    //validate proposed lockCode change
    Boolean result = true
    Integer maxCodeLength = device.currentValue("codeLength")?.toInteger() ?: 8
    Integer maxCodes = device.currentValue("maxCodes")?.toInteger() ?: 20
    Boolean isBadLength = code.size() > maxCodeLength
    Boolean isBadCodeNum = maxCodes < codeNumber
    if (lockCodes) {
        List<String> nameSet = lockCodes.collect{ it.value.name }
        List<String> codeSet = lockCodes.collect{ it.value.code }
        if (codeMap) {
            nameSet = nameSet.findAll{ it != codeMap.name }
            codeSet = codeSet.findAll{ it != codeMap.code }
        }
        Boolean nameInUse = name in nameSet
        Boolean codeInUse = code in codeSet
        if (nameInUse || codeInUse) {
            if (nameInUse) { log.warn "changeIsValid:false, name:${name} is in use:${ lockCodes.find{ it.value.name == "${name}" } }" }
            if (codeInUse) { log.warn "changeIsValid:false, code:${code} is in use:${ lockCodes.find{ it.value.code == "${code}" } }" }
            result = false
        }
    }
    if (isBadLength || isBadCodeNum) {
        if (isBadLength) { log.warn "changeIsValid:false, length of code ${code} does not match codeLength of ${maxCodeLength}" }
        if (isBadCodeNum) { log.warn "changeIsValid:false, codeNumber ${codeNumber} is larger than maxCodes of ${maxCodes}" }
        result = false
    }
    return result
}

Map getCodeMap(lockCodes,codeNumber){
    Map codeMap = [:]
    Map lockCode = lockCodes?."${codeNumber}"
    if (lockCode) {
        codeMap = ["name":"${lockCode.name}", "code":"${lockCode.code}"]
    }
    return codeMap
}

Map getLockCodes() {
    String lockCodes = device.currentValue("lockCodes")
    Map result = [:]
    if (lockCodes?.size() > 1) {
        if (lockCodes[0] == "{") result = new JsonSlurper().parseText(lockCodes)
        else result = new JsonSlurper().parseText(decrypt(lockCodes))
    }
    return result
}

void updateLockCodes(lockCodes){
    String data = new groovy.json.JsonBuilder(lockCodes)
    if (optEncrypt) data = encrypt(data.toString())
    sendEvent(name:"lockCodes",value:data,isStateChange:true)
}

void updateEncryption(){
    String lockCodes = device.currentValue("lockCodes") //encrypted or decrypted
    if (lockCodes?.size() > 1){
        if (optEncrypt && lockCodes[0] == "{") {	//resend encrypted
            sendEvent(name:"lockCodes",value: encrypt(lockCodes),isStateChange:true)
        } else if (!optEncrypt && lockCodes[0] != "{") {	//resend decrypted
            sendEvent(name:"lockCodes",value: decrypt(lockCodes),isStateChange:true)
        }
    }
}

//commands to fetch lock data
void fetchLockCode(codeNumber = null){
    if (codeNumber == null) codeNumber = state.fetchCode
    else state.fetchCode = codeNumber
    sendHubCommand(new hubitat.device.HubAction("he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0101 0x06 { ${zigbee.swapOctets(intToHexStr(codeNumber, 2))} }",  hubitat.device.Protocol.ZIGBEE))
}

//internal utility methods
String setCodePayload(codeNumber, code){
    //yes we cheated here with the codeNumber, limiting it to 8 bits
    String codeType = "00"
    if (codeNumber == 0) codeType = "03"
    return "${intToHexStr(codeNumber.toInteger(), 1)}00 01 ${codeType} ${getCodeHex(code)}"
}

String getCodeHex(code) {
    String str = intToHexStr(code.length(), 1)
    for(int i = 0; i < code.length(); i++) {
        str += " " +  intToHexStr((int) code.charAt(i), 1)
    }
    return str
}

